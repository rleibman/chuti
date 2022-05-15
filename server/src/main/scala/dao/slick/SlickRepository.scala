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

package dao.slick

import api.ChutiSession
import api.token.{Token, TokenPurpose}
import chuti.*
import dao.Repository.{GameOperations, TokenOperations, UserOperations}
import dao.slick.gen.Tables
import dao.slick.gen.Tables.*
import dao.*
import scalacache.Cache
import scalacache.ZioEffect.modes.*
import scalacache.caffeine.CaffeineCache
import _root_.slick.SlickException
import _root_.slick.dbio.DBIO
import _root_.slick.jdbc.MySQLProfile.api.*
import zio.clock.Clock
import zio.logging.Logging
import zio.random.Random
import zio.{ULayer, URLayer, ZIO, ZLayer}

import java.math.BigInteger
import java.security.SecureRandom
import java.sql.{SQLException, Timestamp}
import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*

object SlickRepository {

  implicit val gameCache: Cache[Option[Game]] = CaffeineCache[Option[Game]]
  val live: URLayer[DatabaseProvider, Repository] =
    ZLayer.fromFunction(db => new SlickRepository(db))

}

final class SlickRepository(databaseProvider: DatabaseProvider) extends Repository.Service {

  import SlickRepository.gameCache
  private val godSession: ULayer[SessionProvider] = SessionProvider.layer(ChutiSession(chuti.god))

  implicit val dbExecutionContext: ExecutionContext = zio.Runtime.default.platform.executor.asEC
  implicit def fromDBIO[A](dbio: DBIO[A]): RepositoryIO[A] =
    for {
      db <- databaseProvider.get.db
      ret <- ZIO.fromFuture(implicit ec => db.run(dbio)).mapError {
        case e: SlickException =>
          e.printStackTrace()
          RepositoryError("Slick Repository Error", Some(e))
        case e: SQLException =>
          e.printStackTrace()
          RepositoryError("SQL Repository Error", Some(e))
      }
    } yield ret

  implicit def fromDBIO[A](fn: ChutiSession => DBIO[A]): RepositoryIO[A] =
    for {
      session <- ZIO.access[SessionProvider](_.get.session)
      db      <- databaseProvider.get.db
      ret <- ZIO.fromFuture(implicit ec => db.run(fn(session))).mapError {
        case e: SlickException =>
          e.printStackTrace()
          RepositoryError("Slick Repository Error", Some(e))
        case e: SQLException =>
          e.printStackTrace()
          RepositoryError("SQL Repository Error", Some(e))
      }
    } yield ret

  override val userOperations: Repository.UserOperations = new UserOperations {

    def get(pk: UserId): RepositoryIO[Option[User]] =
      fromDBIO {
        UserQuery
          .filter(u => u.id === pk && !u.deleted).result.headOption.map(
            _.map(_.toUser)
          )
      }

    override def delete(
      pk:         UserId,
      softDelete: Boolean
    ): RepositoryIO[Boolean] = { session: ChutiSession =>
      if (session.user != chuti.god && session.user.id.fold(false)(_ != pk))
        throw RepositoryError(s"${session.user} Not authorized")
      if (softDelete) {
        val q = for {
          u <- UserQuery if u.id === pk
        } yield (u.deleted, u.deletedDate)
        q.update(true, Option(new Timestamp(System.currentTimeMillis()))).map(_ > 0)
      } else UserQuery.filter(_.id === pk).delete.map(_ > 0)
    }

    override def upsert(user: User): RepositoryIO[User] = { session: ChutiSession =>
      if (session.user != chuti.god && session.user.id != user.id)
        throw RepositoryError(s"${session.user} Not authorized")
      (UserQuery returning UserQuery.map(_.id) into ((_, id) => user.copy(id = Some(id))))
        .insertOrUpdate(UserRow.fromUser(user))
        .map(_.getOrElse(user))
    }

    override def search(search: Option[PagedStringSearch]): RepositoryIO[Seq[User]] = {
      search
        .fold(UserQuery: Query[Tables.UserTable, UserRow, Seq]) { s =>
          UserQuery
            .filter(u => u.email.like(s"%${s.text}%"))
            .drop(s.pageSize * s.pageIndex)
            .take(s.pageSize)
        }.result.map(_.map(_.toUser))
    }

    override def count(search: Option[PagedStringSearch]): RepositoryIO[Long] =
      search
        .fold(UserQuery: Query[Tables.UserTable, UserRow, Seq]) { s =>
          UserQuery
            .filter(u => u.email.like(s"%${s.text}%"))
        }.length.result.map(_.toLong)

    override def unfriend(enemy: User): RepositoryIO[Boolean] = { session: ChutiSession =>
      val rowOpt = for {
        one <- session.user.id
        two <- enemy.id
      } yield FriendsRow(one, two)
      DBIO
        .sequence(
          rowOpt.toSeq
            .map(row =>
              FriendsQuery
                .filter(r => (r.one === row.one && r.two === row.two) || (r.one === row.two && r.two === row.one))
                .delete
            ).map(_.map(_ > 0))
        ).map(_.headOption.getOrElse(false))
    }

    override def friend(friend: User): RepositoryIO[Boolean] = { session: ChutiSession =>
      val rowOpt = for {
        one <- session.user.id
        two <- friend.id
      } yield FriendsRow(one, two)
      DBIO
        .sequence(rowOpt.toSeq.map(row => FriendsQuery += row).map(_.map(_ > 0))).map(
          _.headOption.getOrElse(false)
        )
    }

    override def friends: RepositoryIO[Seq[User]] = { session: ChutiSession =>
      DBIO
        .sequence(
          session.user.id.toSeq
            .map { id =>
              UserQuery
                .join(FriendsQuery.filter(f => f.one === id || f.two === id)).on { (a, b) =>
                  a.id === b.one || a.id === b.two
                }.map(_._1).result
            }
        ).map(_.flatten.map(_.toUser))
    }

    override def login(
      email:    String,
      password: String
    ): ZIO[Clock & Logging, RepositoryError, Option[User]] = {
      def dbio(now: Long) =
        (for {
          idOpt <- sql"""select u.id
               from user u
               where u.deleted = 0 and
               u.active = 1 and
               u.email = $email and
               u.hashedpassword = SHA2($password, 512)""".as[Int].map(_.headOption.map(UserId))
          userOpt <-
            DBIO
              .sequence(idOpt.toSeq.map(id => UserQuery.filter(_.id === id).result)).map(
                _.flatten.map(_.toUser).headOption
              )
          // Log the log in.
          _ <- DBIO.sequence(
            idOpt.toSeq.map(id => UserLogQuery += UserLogRow(id, new Timestamp(now)))
          )
        } yield userOpt).transactionally
      (for {
        now <- ZIO.service[Clock.Service].flatMap(_.instant).map(_.toEpochMilli)
        res <- fromDBIO(dbio(now))
      } yield res).provideSomeLayer[Clock & Logging](godSession)

    }

    override def userByEmail(email: String): RepositoryIO[Option[User]] =
      UserQuery
        .filter(u => u.email === email && !u.deleted && u.active).result.headOption.map(
          _.map(_.toUser)
        )

    override def changePassword(
      user:        User,
      newPassword: String
    ): RepositoryIO[Boolean] = { session: ChutiSession =>
      if (session.user != chuti.god && session.user.id != user.id)
        throw RepositoryError(s"${session.user} Not authorized")
      user.id.fold(DBIO.successful(false))(id =>
        sqlu"UPDATE user SET hashedPassword=SHA2($newPassword, 512) WHERE id = ${id.value}"
          .map(_ > 0)
      )
    }

    override def getWallet: RepositoryIO[Option[UserWallet]] = { session: ChutiSession =>
      UserWalletQuery
        .filter(_.userId === session.user.id.getOrElse(UserId(-1)))
        .result.headOption.map(
          _.map(_.toUserWallet)
        ).flatMap {
          case None =>
            val newWallet = UserWalletRow(session.user.id.getOrElse(UserId(-1)), 10000)
            (UserWalletQuery += newWallet).map(_ => Option(newWallet.toUserWallet))
          case Some(wallet) => DBIO.successful(Option(wallet))
        }
    }

    override def getWallet(userId: UserId): RepositoryIO[Option[UserWallet]] = { session: ChutiSession =>
      if (session.user != chuti.god) // Only god can get a user's wallet by user id
        throw RepositoryError(s"${session.user} Not authorized")
      UserWalletQuery
        .filter(_.userId === userId)
        .result.headOption.map(
          _.map(_.toUserWallet)
        ).flatMap {
          case None =>
            val newWallet = UserWalletRow(userId, 10000)
            (UserWalletQuery += newWallet).map(_ => Option(newWallet.toUserWallet))
          case Some(wallet) => DBIO.successful(Option(wallet))
        }
    }

    override def updateWallet(userWallet: UserWallet): RepositoryIO[UserWallet] = { session: ChutiSession =>
      if (session.user != chuti.god) // Only god can update a user's wallet.
        throw RepositoryError(s"${session.user} Not authorized")

      val row = UserWalletRow.fromUserWallet(userWallet)

      val filtered = UserWalletQuery.filter(_.userId === userWallet.userId)
      val dbio = for {
        exists <- filtered.result.headOption
        saved  <- exists.fold(UserWalletQuery.forceInsert(row))(_ => filtered.update(row))
      } yield saved
      dbio.map(_ => userWallet)
    }

    override def firstLogin: RepositoryIO[Option[Instant]] = { chutiSession: ChutiSession =>
      UserLogQuery
        .filter(_.userId === chutiSession.user.id.getOrElse(UserId(-1))).map(_.time).min.result.map(
          _.map(_.toInstant)
        )
    }

  }

  override val gameOperations: Repository.GameOperations = new GameOperations {

    override def upsert(game: Game): RepositoryIO[Game] = {
      val upserted = fromDBIO { session: ChutiSession =>
        if (session.user != chuti.god && !game.jugadores.exists(_.user.id == session.user.id))
          throw RepositoryError(s"${session.user} Not authorized")
        for {
          upserted <-
            (GameQuery returning GameQuery.map(_.id) into ((_, id) => game.copy(id = Some(id))))
              .insertOrUpdate(GameRow.fromGame(game))
              .map(_.getOrElse(game))
        } yield upserted
      }
      for {
        _        <- scalacache.remove(game.id).mapError(e => RepositoryError("Cache error", Some(e)))
        upserted <- upserted
        _ <-
          scalacache
            .put(game.id)(Option(upserted), Option(3.hours)).mapError(e => RepositoryError("Cache error", Some(e)))
      } yield upserted

    }

    override def get(pk: GameId): RepositoryIO[Option[Game]] = {
      val zio = fromDBIO {
        GameQuery
          .filter(_.id === pk)
          .result
          .map(_.headOption.map(_.toGame))
      }
      for {
        gameOpt <- zio
        _ <- ZIO.foreach_(gameOpt) { game =>
          scalacache
            .put(pk)(Option(game), Option(3.hours)).mapError(e => RepositoryError("Cache error", Some(e)))
        }
      } yield gameOpt
    }

    override def delete(
      pk:         GameId,
      softDelete: Boolean
    ): RepositoryIO[Boolean] = { session: ChutiSession =>
      if (session.user != chuti.god)
        throw RepositoryError(s"${session.user} Not authorized")
      GameQuery.filter(_.id === pk).delete.map(_ > 0)

    }

    override def search(search: Option[EmptySearch]): RepositoryIO[Seq[Game]] =
      GameQuery.result
        .map(_.map(_.toGame))

    override def count(search: Option[EmptySearch]): RepositoryIO[Long] = GameQuery.length.result.map(_.toLong)

    override def gamesWaitingForPlayers(): RepositoryIO[Seq[Game]] =
      GameQuery
        .filter(g => g.gameStatus === (GameStatus.esperandoJugadoresAzar: GameStatus))
        .result
        .map(_.map(_.toGame))

    override def getHistoricalUserGames: RepositoryIO[Seq[Game]] = { session: ChutiSession =>
      GamePlayersQuery
        .filter(p => p.userId === session.user.id.getOrElse(UserId(-1)) && !p.invited)
        .join(
          GameQuery.filter(
            _.gameStatus.inSet(
              Set(
                GameStatus.partidoTerminado
              )
            )
          )
        ).on(_.gameId === _.id)
        .sortBy(_._2.lastupdated.desc)
        .result
        .map(_.map(_._2.toGame))
    }

    override def getGameForUser: RepositoryIO[Option[Game]] = { session: ChutiSession =>
      GamePlayersQuery
        .filter(p => p.userId === session.user.id.getOrElse(UserId(-1)) && !p.invited)
        .join(
          GameQuery.filter(
            _.gameStatus.inSet(
              Set(
                GameStatus.comienzo,
                GameStatus.requiereSopa,
                GameStatus.cantando,
                GameStatus.jugando,
                GameStatus.partidoTerminado,
                GameStatus.esperandoJugadoresAzar,
                GameStatus.esperandoJugadoresInvitados
              )
            )
          )
        ).on(_.gameId === _.id)
        .sortBy(_._2.lastupdated.desc)
        .result
        .map(_.headOption.map(_._2.toGame))
    }

    override def gameInvites: RepositoryIO[Seq[Game]] = { session: ChutiSession =>
      GamePlayersQuery
        .filter(player => player.userId === session.user.id.getOrElse(UserId(-1)) && player.invited)
        .join(GameQuery).on(_.gameId === _.id)
        .result
        .map(_.map(_._2.toGame))
    }

    override def updatePlayers(game: Game): RepositoryIO[Game] = {
      for {
        _ <- GamePlayersQuery.filter(_.gameId === game.id.get).delete
        _ <-
          DBIO
            .sequence(game.jugadores.filter(!_.user.isBot).zipWithIndex.map { case (player, index) =>
              GamePlayersQuery.insertOrUpdate(
                GamePlayersRow(
                  userId = player.user.id.getOrElse(UserId(0)),
                  gameId = game.id.getOrElse(GameId(0)),
                  order = index,
                  invited = player.invited
                )
              )
            }).map(_.sum)
      } yield game
    }

    override def userInGame(id: GameId): RepositoryIO[Boolean] = { chutiSession: ChutiSession =>
      if (chutiSession.user.isBot) {
        DBIO.successful(false)
      } else {
        GamePlayersQuery
          .filter(gp => gp.gameId === id && gp.userId === chutiSession.user.id.getOrElse(UserId(-1))).exists.result
      }
    }

  }

  override val tokenOperations: Repository.TokenOperations = new TokenOperations {

    override def validateToken(
      token:   Token,
      purpose: TokenPurpose
    ): RepositoryIO[Option[User]] = {
      for {
        user <-
          TokenQuery
            .filter(t => t.tok === token.tok && t.tokenPurpose === purpose.toString)
            .join(UserQuery).on(_.userId === _.id)
            .result.map(_.headOption.map(_._2.toUser))
        _ <-
          TokenQuery
            .filter(t => t.tok === token.tok && t.tokenPurpose === purpose.toString)
            .delete
      } yield user
    }

    override def createToken(
      user:    User,
      purpose: TokenPurpose,
      ttl:     Option[Duration]
    ): ZIO[SessionProvider & Logging & Clock & Random, RepositoryError, Token] = {
      for {
        tok <- ZIO.service[Random.Service].flatMap(_.nextBytes(8)).map(r => new BigInteger(r.toArray).toString(32))
        row = TokenRow(
          tok = tok,
          tokenPurpose = purpose.toString,
          expireTime = new Timestamp(ttl.fold(Long.MaxValue)(_.toMillis + System.currentTimeMillis())),
          userId = user.id.getOrElse(UserId(-1))
        )
        inserted <- TokenQuery.forceInsert(row).map(_ => Token(row.tok))
      } yield inserted
    }

    override def peek(
      token:   Token,
      purpose: TokenPurpose
    ): RepositoryIO[Option[User]] =
      TokenQuery
        .filter(t => t.tok === token.tok && t.tokenPurpose === purpose.toString)
        .join(UserQuery).on(_.userId === _.id)
        .result.map(_.headOption.map(_._2.toUser))

    override def cleanup: RepositoryIO[Boolean] = {
      TokenQuery.filter(_.expireTime >= new Timestamp(System.currentTimeMillis())).delete.map(_ > 0)
    }

  }

}
