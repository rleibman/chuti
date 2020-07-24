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

package dao

import java.math.BigInteger
import java.security.SecureRandom
import java.sql.{SQLException, Timestamp}
import java.time.LocalDateTime

import api.ChutiSession
import api.token.{Token, TokenPurpose}
import chuti._
import dao.Repository.{GameOperations, TokenOperations, UserOperations}
import dao.gen.Tables
import dao.gen.Tables._
import game.GameService
import scalacache.Cache
import scalacache.ZioEffect.modes._
import scalacache.caffeine.CaffeineCache
import slick.SlickException
import slick.dbio.DBIO
import slick.jdbc.MySQLProfile.api._
import zio.{URLayer, ZIO, ZLayer}
import zioslick.RepositoryException

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.implicitConversions

object SlickRepository {
  implicit val gameCache: Cache[Option[Game]] = CaffeineCache[Option[Game]]
  val live: URLayer[DatabaseProvider, Repository] =
    ZLayer.fromFunction(db => new SlickRepository(db))
}

final class SlickRepository(databaseProvider: DatabaseProvider)
    extends Repository.Service with SlickToModelInterop {
  private val dbProviderLayer = ZLayer.succeed(databaseProvider)
  import SlickRepository.gameCache

  implicit val dbExecutionContext: ExecutionContext = zio.Runtime.default.platform.executor.asEC
  implicit def fromDBIO[A](dbio: DBIO[A]): RepositoryIO[A] =
    for {
      db <- databaseProvider.get.db
      ret <- ZIO.fromFuture(implicit ec => db.run(dbio)).mapError {
        case e: SlickException =>
          e.printStackTrace()
          RepositoryException("Slick Repository Error", Some(e))
        case e: SQLException =>
          e.printStackTrace()
          RepositoryException("SQL Repository Error", Some(e))
      }
    } yield ret

  implicit def fromDBIO[A](fn: ChutiSession => DBIO[A]): RepositoryIO[A] =
    for {
      session <- ZIO.access[SessionProvider](_.get.session)
      db      <- databaseProvider.get.db
      ret <- ZIO.fromFuture(implicit ec => db.run(fn(session))).mapError {
        case e: SlickException =>
          e.printStackTrace()
          RepositoryException("Slick Repository Error", Some(e))
        case e: SQLException =>
          e.printStackTrace()
          RepositoryException("SQL Repository Error", Some(e))
      }
    } yield ret

  override val userOperations: Repository.UserOperations = new UserOperations {
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
                .filter(r =>
                  (r.one === row.one && r.two === row.two) || (r.one === row.two && r.two === row.one)
                ).delete
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
        ).map(_.flatten.map(UserRow2User))
    }

    override def upsert(user: User): RepositoryIO[User] = { session: ChutiSession =>
      if (session.user != GameService.god && session.user.id != user.id)
        throw RepositoryException(s"${session.user} Not authorized")
      (UserQuery returning UserQuery.map(_.id) into ((_, id) => user.copy(id = Some(id))))
        .insertOrUpdate(User2UserRow(user))
        .map(_.getOrElse(user))
    }

    def get(pk: UserId): RepositoryIO[Option[User]] =
      fromDBIO {
        UserQuery
          .filter(u => u.id === pk && !u.deleted).result.headOption.map(
            _.map(UserRow2User)
          )
      }

    override def delete(
      pk:         UserId,
      softDelete: Boolean
    ): RepositoryIO[Boolean] = { session: ChutiSession =>
      if (session.user != GameService.god && session.user.id.fold(false)(_ != pk))
        throw RepositoryException(s"${session.user} Not authorized")
      if (softDelete) {
        val q = for {
          u <- UserQuery if u.id === pk
        } yield (u.deleted, u.deleteddate)
        q.update(true, Option(new Timestamp(System.currentTimeMillis()))).map(_ > 0)
      } else
        UserQuery.filter(_.id === pk).delete.map(_ > 0)
    }

    override def search(search: Option[PagedStringSearch]): RepositoryIO[Seq[User]] = {
      search
        .fold(UserQuery: Query[Tables.UserTable, Tables.UserRow, Seq]) { s =>
          UserQuery
            .filter(u => u.email.like(s"%${s.text}%"))
            .drop(s.pageSize * s.pageIndex)
            .take(s.pageSize)
        }.result.map(_.map(UserRow2User))
    }

    override def count(search: Option[PagedStringSearch]): RepositoryIO[Long] =
      search
        .fold(UserQuery: Query[Tables.UserTable, Tables.UserRow, Seq]) { s =>
          UserQuery
            .filter(u => u.email.like(s"%${s.text}%"))
        }.length.result.map(_.toLong)

    override def login(
      email:    String,
      password: String
    ): RepositoryIO[Option[User]] = {
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
              _.flatten.map(UserRow2User).headOption
            )
        //Log the log in.
        _ <- DBIO.sequence(
          idOpt.toSeq.map(id =>
            UserLogQuery += UserLogRow(id, new Timestamp(System.currentTimeMillis()))
          )
        )
      } yield userOpt).transactionally
    }

    override def userByEmail(email: String): RepositoryIO[Option[User]] =
      UserQuery
        .filter(u => u.email === email && !u.deleted && u.active).result.headOption.map(
          _.map(UserRow2User)
        )

    override def changePassword(
      user:        User,
      newPassword: String
    ): RepositoryIO[Boolean] = { session: ChutiSession =>
      if (session.user != GameService.god && session.user.id != user.id)
        throw RepositoryException(s"${session.user} Not authorized")
      user.id.fold(DBIO.successful(false))(id =>
        sqlu"UPDATE user SET hashedPassword=SHA2($newPassword, 512) WHERE id = ${id.value}"
          .map(_ > 0)
      )
    }

    override def getWallet: RepositoryIO[Option[UserWallet]] = { session: ChutiSession =>
      UserWalletQuery
        .filter(_.userId === session.user.id.getOrElse(UserId(-1)))
        .result.headOption.map(
          _.map(UserWalletRow2UserWallet)
        ).flatMap {
          case None =>
            val newWallet = UserWalletRow(session.user.id.getOrElse(UserId(-1)), 10000)
            (UserWalletQuery += newWallet).map(_ => Option(UserWalletRow2UserWallet(newWallet)))
          case Some(wallet) => DBIO.successful(Option(wallet))
        }
    }

    override def getWallet(userId: UserId): RepositoryIO[Option[UserWallet]] = {
      session: ChutiSession =>
        if (session.user != GameService.god) //Only god can get a user's wallet by user id
          throw RepositoryException(s"${session.user} Not authorized")
        UserWalletQuery
          .filter(_.userId === userId)
          .result.headOption.map(
            _.map(UserWalletRow2UserWallet)
          ).flatMap {
            case None =>
              val newWallet = UserWalletRow(userId, 10000)
              (UserWalletQuery += newWallet).map(_ => Option(UserWalletRow2UserWallet(newWallet)))
            case Some(wallet) => DBIO.successful(Option(wallet))
          }
    }

    override def updateWallet(userWallet: UserWallet): RepositoryIO[UserWallet] = {
      session: ChutiSession =>
        if (session.user != GameService.god) //Only god can update a user's wallet.
          throw RepositoryException(s"${session.user} Not authorized")

        val row = UserWallet2UserWalletRow(userWallet)

        val filtered = UserWalletQuery.filter(_.userId === userWallet.userId)
        val dbio = for {
          exists <- filtered.result.headOption
          saved  <- exists.fold(UserWalletQuery.forceInsert(row))(_ => filtered.update(row))
        } yield saved
        dbio.map(_ => userWallet)
    }

    override def firstLogin: RepositoryIO[Option[LocalDateTime]] = { chutiSession: ChutiSession =>
      UserLogQuery
        .filter(_.userId === chutiSession.user.id.getOrElse(UserId(-1))).map(_.time).min.result.map(
          _.map(_.toLocalDateTime)
        )
    }
  }

  override val gameOperations: Repository.GameOperations = new GameOperations {
    override def upsert(game: Game): RepositoryIO[Game] = {
      val upserted = fromDBIO { session: ChutiSession =>
        if (session.user != GameService.god && !game.jugadores.exists(_.user.id == session.user.id))
          throw RepositoryException(s"${session.user} Not authorized")
        for {
          upserted <-
            (GameQuery returning GameQuery.map(_.id) into ((_, id) => game.copy(id = Some(id))))
              .insertOrUpdate(Game2GameRow(game))
              .map(_.getOrElse(game))
        } yield upserted
      }
      for {
        _        <- scalacache.remove(game.id).mapError(e => RepositoryException("Cache error", Some(e)))
        upserted <- upserted
        _ <-
          scalacache
            .put(game.id)(Option(upserted), Option(3.hours)).mapError(e =>
              RepositoryException("Cache error", Some(e))
            )
      } yield upserted

    }

    override def get(pk: GameId): RepositoryIO[Option[Game]] = {
      val zio = fromDBIO {
        GameQuery
          .filter(_.id === pk)
          .result
          .map(_.headOption.map(row => GameRow2Game(row)))
      }
      for {
        gameOpt <- zio
        _ <- ZIO.foreach(gameOpt)(game =>
          scalacache
            .put(pk)(Option(game), Option(3.hours)).mapError(e =>
              RepositoryException("Cache error", Some(e))
            )
        )
      } yield gameOpt
    }

    override def delete(
      pk:         GameId,
      softDelete: Boolean
    ): RepositoryIO[Boolean] = { session: ChutiSession =>
      if (session.user != GameService.god)
        throw RepositoryException(s"${session.user} Not authorized")
      GameQuery.filter(_.id === pk).delete.map(_ > 0)

    }

    override def search(search: Option[EmptySearch]): RepositoryIO[Seq[Game]] =
      GameQuery.result
        .map(_.map(row => GameRow2Game(row)))

    override def count(search: Option[EmptySearch]): RepositoryIO[Long] =
      GameQuery.length.result.map(_.toLong)

    override def gamesWaitingForPlayers(): RepositoryIO[Seq[Game]] =
      GameQuery
        .filter(g => g.gameStatus === (GameStatus.esperandoJugadoresAzar: GameStatus))
        .result
        .map(_.map(row => GameRow2Game(row)))

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
        .map(_.map(row => GameRow2Game(row._2)))
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
        .map(_.headOption.map(row => GameRow2Game(row._2)))
    }

    override def gameInvites: RepositoryIO[Seq[Game]] = { session: ChutiSession =>
      GamePlayersQuery
        .filter(player => player.userId === session.user.id.getOrElse(UserId(-1)) && player.invited)
        .join(GameQuery).on(_.gameId === _.id)
        .result
        .map(_.map(row => GameRow2Game(row._2)))
    }

    override def updatePlayers(game: Game): RepositoryIO[Game] = {
      for {
        _ <- GamePlayersQuery.filter(_.gameId === game.id.get).delete
        _ <-
          DBIO
            .sequence(game.jugadores.zipWithIndex.map {
              case (player, index) =>
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
      GamePlayersQuery
        .filter(gp =>
          gp.gameId === id && gp.userId === chutiSession.user.id.getOrElse(UserId(-1))
        ).exists.result
    }
  }

  override val tokenOperations: Repository.TokenOperations = new TokenOperations {
    private val random = SecureRandom.getInstanceStrong
    override def validateToken(
      token:   Token,
      purpose: TokenPurpose
    ): RepositoryIO[Option[User]] = {
      for {
        user <-
          TokenQuery
            .filter(t => t.tok === token.tok && t.tokenPurpose === purpose.toString)
            .join(UserQuery).on(_.userId === _.id)
            .result.map(_.headOption.map(r => UserRow2User(r._2)))
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
    ): RepositoryIO[Token] = {
      val row = TokenRow(
        tok = new BigInteger(12 * 5, random).toString(32),
        tokenPurpose = purpose.toString,
        expireTime =
          new Timestamp(ttl.fold(Long.MaxValue)(_.toMillis + System.currentTimeMillis())),
        userId = user.id.getOrElse(UserId(-1))
      )
      TokenQuery.forceInsert(row).map(_ => Token(row.tok))
    }

    override def peek(
      token:   Token,
      purpose: TokenPurpose
    ): RepositoryIO[Option[User]] =
      TokenQuery
        .filter(t => t.tok === token.tok && t.tokenPurpose === purpose.toString)
        .join(UserQuery).on(_.userId === _.id)
        .result.map(_.headOption.map(r => UserRow2User(r._2)))

    override def cleanup: RepositoryIO[Boolean] = {
      TokenQuery.filter(_.expireTime >= new Timestamp(System.currentTimeMillis())).delete.map(_ > 0)
    }
  }

}
