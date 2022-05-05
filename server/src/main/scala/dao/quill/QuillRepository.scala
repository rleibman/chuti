/*
 * Copyright 2020 Roberto Leibman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dao.quill

import api.ChutiSession
import api.config.*
import api.token.*
import chuti.*
import dao.*
import io.getquill.*
import io.getquill.context.ZioJdbc.DataSourceLayer
import scalacache.Cache
import scalacache.caffeine.CaffeineCache
import zio.*
import zio.clock.Clock
import zio.logging.Logging

import java.sql.{Timestamp, Types}
import java.time.*
import scala.concurrent.duration.Duration

object QuillRepository {

  implicit val gameCache: Cache[Option[Game]] = CaffeineCache[Option[Game]]
  val live: URLayer[Config, Repository] =
    (for {
      config <- ZIO.service[Config.Service]
    } yield QuillRepository(config)).toLayer

}

case class QuillRepository(config: Config.Service) extends Repository.Service {

  private val godSession: ULayer[SessionProvider] = SessionProvider.layer(ChutiSession(chuti.god))

  private object ctx extends MysqlZioJdbcContext(MysqlEscape)
  import ctx.*
  import ctx.extras.*

  private val users = quote {
    querySchema[UserRow]("user")
  }
  private val userLogins = quote {
    querySchema[UserLogRow]("userLog")
  }
  private val friends = quote {
    querySchema[FriendsRow]("friends")
  }
  private val userWallets = quote {
    querySchema[FriendsRow]("userWallet")
  }

  private val dataSourceLayer = DataSourceLayer.fromConfig(config.config.getConfig("chuti.db"))

  implicit private val TimestampDecoder: ctx.Decoder[Timestamp] = JdbcDecoder { (index: Index, row: ResultRow, _: Session) =>
    row.getTimestamp(index)
  }

  implicit private val TimestampEncoder: ctx.Encoder[Timestamp] = encoder(Types.TIMESTAMP, (index, value, row) => row.setTimestamp(index, value))

  private def assertAuth(
    authorized: ChutiSession => Boolean,
    errorFn:    ChutiSession => String
  ): ZIO[SessionProvider, RepositoryError, ChutiSession] = {
    for {
      session <- ZIO.service[SessionProvider.Session].map(_.session)
      _ <- ZIO
        .fail(RepositoryPermissionError(errorFn(session)))
        .when(!authorized(session))
    } yield session
  }

  override val userOperations: Repository.UserOperations = new Repository.UserOperations {

    override def get(pk: UserId): RepositoryIO[Option[User]] = {
      val q = quote {
        users.filter(u => u.id === lift(pk) && !u.deleted)
      }
      ctx.run(q).map(_.headOption.map(_.toUser)).provideLayer(dataSourceLayer).mapError(RepositoryError.apply)
    }
    override def delete(
      pk:         UserId,
      softDelete: Boolean
    ): RepositoryIO[Boolean] = {
      for {
        _ <- assertAuth(
          session => session.user == chuti.god || session.user.id.fold(false)(_ == pk),
          session => s"${session.user} Not authorized"
        )
        now <- ZIO.service[Clock.Service].flatMap(_.instant)
        result <- {
          if (softDelete) {
            val q = quote {
              users.filter(u => u.id === lift(pk) && !u.deleted).update(_.deleted -> true, _.deletedDate -> Some(lift(Timestamp.from(now))))
            }
            ctx.run(q).map(_ > 0)
          } else {
            val q = quote {
              users.filter(_.id === lift(pk)).delete
            }
            ctx.run(q).map(_ > 0)
          }
        }
      } yield result
    }.provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def search(search: Option[PagedStringSearch]): RepositoryIO[Seq[User]] = {
      search
        .fold(ctx.run(quote(users.filter(!_.deleted)))) { s =>
          {
            ctx.run(quote {
              users
                .filter(u => (u.email like lift(s"%${s.text}%")) && !u.deleted)
                .drop(lift(s.pageSize * s.pageIndex))
                .take(lift(s.pageSize))
            })
          }
        }
    }.map(_.map(_.toUser)).provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def count(search: Option[PagedStringSearch]): RepositoryIO[Long] = {
      search.fold(ctx.run(quote(users.filter(!_.deleted).size))) { s =>
        {
          val ss = s"%${s.text}%"
          ctx.run(quote {
            users.filter(u => (u.email like lift(s"%${s.text}%")) && !u.deleted).size
          })
        }
      }
    }.provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def upsert(user: User): RepositoryIO[User] =
      (for {
        _ <- assertAuth(
          session => session.user == chuti.god || session.user.id == user.id,
          session => s"${session.user} Not authorized"
        )
        now <- ZIO.service[Clock.Service].flatMap(_.instant)

        upserted <- {
          user.id.fold {
            // It's an insert, make sure te user does not exist by email
            for {
              exists <- ctx.run(quote(users.filter(u => u.email === lift(user.email) && !u.deleted)).size)
              _ <- ZIO
                .fail(RepositoryError(s"Insert Error: A user with the email ${user.email} already exists, choose a different one"))
                .when(exists > 0)
              saveMe = UserRow.fromUser(user.copy(lastUpdated = now, created = now))
              pk <- ctx
                .run(quote(users.insertValue(lift(saveMe.copy(active = false, deleted = false))).returningGenerated(_.id)))
            } yield saveMe.toUser.copy(id = Some(pk))
          } { id =>
            // It's an update, make sure that if the email has changed, it doesn't already exist
            for {
              exists <- ctx.run(quote(users.filter(u => u.id != lift(id) && u.email === lift(user.email) && !u.deleted)).size)
              _ <- ZIO
                .fail(RepositoryError(s"Update error: A user with the email ${user.email} already exists, choose a different one"))
                .when(exists > 0)
              saveMe = UserRow.fromUser(user.copy(lastUpdated = now))
              updateCount <- ctx.run(quote(users.filter(u => u.id == lift(id) && !u.deleted).updateValue(lift(saveMe))))
              _           <- ZIO.fail(RepositoryError("User not found")).when(updateCount == 0)
            } yield saveMe.toUser
          }
        }
      } yield upserted).provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def firstLogin: RepositoryIO[Option[Instant]] =
      (for {
        chutiSession <- ZIO.service[SessionProvider.Session].map(_.session)
        res <- ctx.run(userLogins.filter(_.userId === lift(chutiSession.user.id.getOrElse(UserId(-1)))).map(_.time).min).map(_.map(_.toInstant))
      } yield res).provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def login(
      email:    String,
      password: String
    ): ZIO[Clock & Logging, RepositoryError, Option[User]] = {
      val sql = quote(infix"""select u.id
               from `user` u
               where u.deleted = 0 and
               u.active = 1 and
               u.email = ${lift(email)} and
               u.hashedpassword = SHA2(${lift(password)}, 512)""".as[Query[Int]])
      (for {
        now    <- ZIO.service[Clock.Service].flatMap(_.instant)
        userId <- ctx.run(sql).map(_.headOption.map(UserId))
        user   <- ZIO.foreach(userId)(id => get(id)).provideSomeLayer[Clock & Logging](godSession)
        saveTime = Timestamp.from(now)
        _ <- ZIO.foreach_(userId)(id => ctx.run(quote(userLogins.insertValue(UserLogRow(lift(id), lift(saveTime))))))
      } yield user.flatten).provideSomeLayer[Clock & Logging](dataSourceLayer).mapError(RepositoryError.apply)
    }

    override def changePassword(
      user:        User,
      newPassword: String
    ): RepositoryIO[Boolean] =
      (for {
        _ <- assertAuth(
          session => session.user == chuti.god || session.user.id == user.id,
          session => s"${session.user} Not authorized"
        )
        res <- ctx
          .run(quote(infix"update `user` set hashedPassword=SHA2(${lift(newPassword)}, 512) where id = ${lift(user.id)}".as[Update[Int]])).map(_ > 0)
      } yield res).provideSomeLayer[SessionProvider & Clock & Logging](dataSourceLayer).mapError(RepositoryError.apply)


    override def userByEmail(email: String): RepositoryIO[Option[User]] = {
      ctx
        .run(users.filter(u => u.email == lift(email) && !u.deleted && u.active))
        .map(_.headOption.map(_.toUser))
        .provideSomeLayer[Clock & Logging](dataSourceLayer)
        .mapError(RepositoryError.apply)
    }

    override def unfriend(enemy: User): RepositoryIO[Boolean] = ???

    override def friend(friend: User): RepositoryIO[Boolean] = ???

    override def friends: RepositoryIO[Seq[User]] = ???

    override def getWallet: RepositoryIO[Option[UserWallet]] = ???

    override def getWallet(userId: UserId): RepositoryIO[Option[UserWallet]] = ???

    override def updateWallet(userWallet: UserWallet): RepositoryIO[UserWallet] = ???

  }

  override val gameOperations: Repository.GameOperations = new Repository.GameOperations {

    override def getHistoricalUserGames: RepositoryIO[Seq[Game]] = ???

    override def userInGame(id: GameId): RepositoryIO[Boolean] = ???

    override def updatePlayers(game: Game): RepositoryIO[Game] = ???

    override def gameInvites: RepositoryIO[Seq[Game]] = ???

    override def gamesWaitingForPlayers(): RepositoryIO[Seq[Game]] = ???

    override def getGameForUser: RepositoryIO[Option[Game]] = ???

    override def upsert(e: Game): RepositoryIO[Game] = ???

    override def get(pk: GameId): RepositoryIO[Option[Game]] = ???

    override def delete(
      pk:         GameId,
      softDelete: Boolean
    ): RepositoryIO[Boolean] = ???

    override def search(search: Option[EmptySearch]): RepositoryIO[Seq[Game]] = ???

    override def count(search: Option[EmptySearch]): RepositoryIO[Long] = ???

  }
  override val tokenOperations: Repository.TokenOperations = new Repository.TokenOperations {

    override def cleanup: RepositoryIO[Boolean] = ???

    override def validateToken(
      token:   Token,
      purpose: TokenPurpose
    ): RepositoryIO[Option[User]] = ???

    override def createToken(
      user:    User,
      purpose: TokenPurpose,
      ttl:     Option[Duration]
    ): RepositoryIO[Token] = ???

    override def peek(
      token:   Token,
      purpose: TokenPurpose
    ): RepositoryIO[Option[User]] = ???

  }

}
