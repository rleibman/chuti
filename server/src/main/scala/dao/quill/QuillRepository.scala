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
import java.time.LocalDateTime
import scala.concurrent.duration.Duration

object QuillRepository {

  implicit val gameCache: Cache[Option[Game]] = CaffeineCache[Option[Game]]
  val live: URLayer[Config, Repository] =
    (for {
      config <- ZIO.service[Config.Service]
    } yield QuillRepository(config)).toLayer

}

case class QuillRepository(config: Config.Service) extends Repository.Service {

  private object ctx extends MysqlZioJdbcContext(MysqlEscape)
  import ctx.*
  import ctx.extras.*

  private val users = quote {
    querySchema[UserRow]("user")
  }

  private val dataSourceLayer = DataSourceLayer.fromConfig(config.config)

  private implicit val TimestampDecoder: ctx.Decoder[Timestamp] = JdbcDecoder { (index: Index, row: ResultRow, _: Session) =>
    row.getTimestamp(index)
  }

  private implicit val TimestampEncoder: ctx.Encoder[Timestamp] = encoder(Types.TIMESTAMP, (index, value, row) => row.setTimestamp(index, value))

  private def assertAuth(
    authCheck: ChutiSession => Boolean,
    errorFn:   ChutiSession => String
  ): ZIO[SessionProvider, RepositoryException, Unit] = {
    for {
      session <- ZIO.service[SessionProvider.Session].map(_.session)
      _ <- ZIO
        .fail(RepositoryException(errorFn(session)))
        .when(authCheck(session))
    } yield ()
  }

  override val userOperations: Repository.UserOperations = new Repository.UserOperations {

    override def get(pk: UserId): RepositoryIO[Option[User]] = {
      val q = quote {
        users.filter(u => u.id === lift(pk) && !u.deleted)
      }
      ctx.run(q).map(_.headOption.map(UserRow2User)).provideLayer(dataSourceLayer).mapError(RepositoryException.apply)
    }
    override def delete(
      pk:         UserId,
      softDelete: Boolean
    ): RepositoryIO[Boolean] = {
      for {
        _ <- assertAuth(
          session => session.user != chuti.god && session.user.id.fold(false)(_ == pk),
          session => s"${session.user} Not authorized"
        )
        now <- ZIO.service[Clock.Service].flatMap(_.instant).map(_.toEpochMilli)
        result <- {
          if (softDelete) {
            val q = quote {
              users.filter(_.id === lift(pk)).update(_.deleted -> true, _.deletedDate -> Some(lift(new Timestamp(now))))
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
    }.provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer).mapError(RepositoryException.apply)

    override def search(search: Option[PagedStringSearch]): RepositoryIO[Seq[User]] = {
      search.fold(ctx.run(quote(users))) { s =>
        {
          val ss = s"%${s.text}%"
          ctx.run(quote {
            users
              .filter(u => u.email like lift(ss))
              .drop(lift(s.pageSize * s.pageIndex))
              .take(lift(s.pageSize))
          })
        }
      }
    }.map(_.map(UserRow2User)).provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer).mapError(RepositoryException.apply)

    override def count(search: Option[PagedStringSearch]): RepositoryIO[Long] = {
      search.fold(ctx.run(quote(users.size))) { s =>
        {
          val ss = s"%${s.text}%"
          ctx.run(quote {
            users.filter(u => u.email like lift(ss)).size
          })
        }
      }
    }.provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer).mapError(RepositoryException.apply)

    override def firstLogin: RepositoryIO[Option[LocalDateTime]] = ???

    override def login(
      email:    String,
      password: String
    ): RepositoryIO[Option[User]] = ???

    override def userByEmail(email: String): RepositoryIO[Option[User]] = ???

    override def changePassword(
      user:     User,
      password: String
    ): RepositoryIO[Boolean] = ???

    override def unfriend(enemy: User): RepositoryIO[Boolean] = ???

    override def friend(friend: User): RepositoryIO[Boolean] = ???

    override def friends: RepositoryIO[Seq[User]] = ???

    override def getWallet: RepositoryIO[Option[UserWallet]] = ???

    override def getWallet(userId: UserId): RepositoryIO[Option[UserWallet]] = ???

    override def updateWallet(userWallet: UserWallet): RepositoryIO[UserWallet] = ???

    override def upsert(e: User): RepositoryIO[User] = ???

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
