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
import zio.random.Random

import java.sql.{SQLException, Timestamp, Types}
import java.time.*
import javax.sql.DataSource
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
    querySchema[UserWalletRow]("userWallet")
  }
  private val games = quote {
    querySchema[GameRow]("game")
  }
  private val gameEvents = quote {
    querySchema[GameEventRow]("game_events")
  }
  private val gamePlayers = quote {
    querySchema[GamePlayersRow]("game_players")
  }
  private val tokens = quote {
    querySchema[TokenRow]("token")
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
      ctx
        .run(users.filter(u => u.id === lift(pk) && !u.deleted))
        .map(_.headOption.map(_.toUser))
        .provideLayer(dataSourceLayer)
        .mapError(RepositoryError.apply)
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
            ctx
              .run(
                users.filter(u => u.id === lift(pk) && !u.deleted).update(_.deleted -> true, _.deletedDate -> Some(lift(Timestamp.from(now))))
              ).map(_ > 0)
          } else {
            ctx.run(users.filter(_.id === lift(pk)).delete).map(_ > 0)
          }
        }
      } yield result
    }.provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def search(search: Option[PagedStringSearch]): RepositoryIO[Seq[User]] = {
      search
        .fold(ctx.run(users.filter(!_.deleted))) { s =>
          {
            ctx.run(
              users
                .filter(u => (u.email like lift(s"%${s.text}%")) && !u.deleted)
                .drop(lift(s.pageSize * s.pageIndex))
                .take(lift(s.pageSize))
            )
          }
        }
    }.map(_.map(_.toUser)).provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def count(search: Option[PagedStringSearch]): RepositoryIO[Long] = {
      search.fold(ctx.run(users.filter(!_.deleted).size)) { s =>
        {
          ctx.run(users.filter(u => (u.email like lift(s"%${s.text}%")) && !u.deleted).size)
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
              exists <- ctx.run(users.filter(u => u.email === lift(user.email) && !u.deleted).size)
              _ <- ZIO
                .fail(RepositoryError(s"Insert Error: A user with the email ${user.email} already exists, choose a different one"))
                .when(exists > 0)
              saveMe = UserRow.fromUser(user.copy(lastUpdated = now, created = now))
              pk <- ctx
                .run(users.insertValue(lift(saveMe.copy(active = false, deleted = false))).returningGenerated(_.id))
            } yield saveMe.toUser.copy(id = Some(pk))
          } { id =>
            // It's an update, make sure that if the email has changed, it doesn't already exist
            for {
              exists <- ctx.run(users.filter(u => u.id != lift(id) && u.email === lift(user.email) && !u.deleted).size)
              _ <- ZIO
                .fail(RepositoryError(s"Update error: A user with the email ${user.email} already exists, choose a different one"))
                .when(exists > 0)
              saveMe = UserRow.fromUser(user.copy(lastUpdated = now))
              updateCount <- ctx.run(users.filter(u => u.id == lift(id) && !u.deleted).updateValue(lift(saveMe)))
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
        _ <- ZIO.foreach_(userId)(id => ctx.run(userLogins.insertValue(UserLogRow(lift(id), lift(saveTime)))))
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

    override def unfriend(enemy: User): RepositoryIO[Boolean] = {
      (for {
        session <- ZIO.service[SessionProvider.Session].map(_.session)
        rowOpt = for {
          one <- session.user.id
          two <- enemy.id
        } yield FriendsRow(one, two)
        deleted <- rowOpt.fold(ZIO.succeed(true): ZIO[Has[DataSource], SQLException, Boolean])(row =>
          ctx
            .run(
              QuillRepository.this.friends
                .filter(r => (r.one === lift(row.one) && r.two === lift(row.two)) || (r.one === lift(row.two) && r.two === lift(row.one)))
                .delete
            ).map(_ > 0)
        )
      } yield deleted)
        .provideSomeLayer[SessionProvider & Clock & Logging](dataSourceLayer)
        .mapError(RepositoryError.apply)
    }

    override def friend(friend: User): RepositoryIO[Boolean] =
      (for {
        session <- ZIO.service[SessionProvider.Session].map(_.session)
        friends <- friends
        res <- friends
          .find(_.id == friend.id).fold {
            val rowOpt = for {
              one <- session.user.id
              two <- friend.id
            } yield FriendsRow(one, two)
            rowOpt.fold(
              ZIO.succeed(false): zio.ZIO[Has[DataSource], SQLException, Boolean]
            ) { row =>
              ctx.run(QuillRepository.this.friends.insertValue(lift(row))).map(_ > 0)
            }
          }(_ => ZIO.succeed(true))
      } yield res)
        .provideSomeLayer[SessionProvider & Clock & Logging](dataSourceLayer)
        .mapError(RepositoryError.apply)

    override def friends: RepositoryIO[Seq[User]] =
      (for {
        session <- ZIO.service[SessionProvider.Session].map(_.session)
        res <- session.user.id.fold(ZIO.succeed(Seq.empty): ZIO[Has[DataSource], SQLException, Seq[User]]) { id =>
          ctx
            .run {
              users
                .join(QuillRepository.this.friends).on((a, b) => a.id === b.one || a.id === b.two).filter { case (u, f) =>
                  (f.one == lift(id) || f.two == lift(id)) && u.id != lift(id)
                }
            }.map(_.map(_._1.toUser))
        }
      } yield res)
        .provideSomeLayer[SessionProvider & Clock & Logging](dataSourceLayer)
        .mapError(RepositoryError.apply)

    override def getWallet: RepositoryIO[Option[UserWallet]] =
      for {
        session <- ZIO.service[SessionProvider.Session].map(_.session)
        wallet  <- session.user.id.fold(ZIO.none: RepositoryIO[Option[UserWallet]])(getWallet)
      } yield wallet

    override def getWallet(userId: UserId): RepositoryIO[Option[UserWallet]] =
      (for {
        _ <- assertAuth(
          session => session.user == god || session.user.id.fold(false)(_ == userId),
          _ => "You can't see someone else's wallet"
        )
        walletOpt <- ctx.run(userWallets.filter(_.userId == lift(userId))).map(_.headOption)
        wallet <- walletOpt.fold {
          val newWallet = UserWalletRow(userId, 10000)
          ctx.run(userWallets.insertValue(lift(newWallet))).as(Option(newWallet.toUserWallet))
        }(w => ZIO.succeed(Option(w.toUserWallet)))
      } yield wallet)
        .provideSomeLayer[SessionProvider & Clock & Logging](dataSourceLayer)
        .mapError(RepositoryError.apply)

    override def updateWallet(userWallet: UserWallet): RepositoryIO[UserWallet] =
      (for {
        _ <- assertAuth(
          session => session.user == god,
          _ => "Only god (or the Fed) can update a wallet"
        )
        existing <- getWallet(userWallet.userId)
        row = UserWalletRow.fromUserWallet(userWallet)
        _ <- existing.fold {
          ctx.run(userWallets.insertValue(lift(row)))
        }(_ => ctx.run(userWallets.filter(_.userId == lift(userWallet.userId)).updateValue(lift(row))))
      } yield userWallet)
        .provideSomeLayer[SessionProvider & Clock & Logging](dataSourceLayer)
        .mapError(RepositoryError.apply)

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
    ): ZIO[SessionProvider & Logging & Clock & Random, RepositoryError, Token] = ???

    override def peek(
      token:   Token,
      purpose: TokenPurpose
    ): RepositoryIO[Option[User]] = ???

  }

}
