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
import io.circe.parser.*
import io.getquill.*
import io.getquill.context.ZioJdbc.DataSourceLayer
import scalacache.Cache
import scalacache.ZioEffect.modes.*
import scalacache.caffeine.CaffeineCache
import zio.*
import zio.clock.Clock
import zio.logging.Logging
import zio.random.Random

import java.math.BigInteger
import java.sql.{SQLException, Timestamp, Types}
import java.time.*
import javax.sql.DataSource
import scala.concurrent.duration.{Duration, *}

object QuillRepository {

  implicit val gameCache: Cache[Option[Game]] = CaffeineCache[Option[Game]]
  val live: URLayer[Config, Repository] =
    (for {
      config <- ZIO.service[Config.Service]
    } yield QuillRepository(config)).toLayer

}

case class QuillRepository(config: Config.Service) extends Repository.Service {

  private val godSession: ULayer[SessionProvider] = SessionProvider.layer(ChutiSession(chuti.god))

  import QuillRepository.gameCache

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

  private val gamePlayers = quote {
    querySchema[GamePlayersRow]("game_players", _.gameId -> "game_id", _.userId -> "user_id", _.order -> "sort_order")
  }
  private val tokens = quote {
    querySchema[TokenRow]("token")
  }

  private val dataSourceLayer = DataSourceLayer.fromConfig(config.config.getConfig("chuti.db"))

  implicit private val TimestampDecoder: ctx.Decoder[Timestamp] = JdbcDecoder { (index: Index, row: ResultRow, _: Session) =>
    row.getTimestamp(index)
  }

  implicit private val TimestampEncoder: ctx.Encoder[Timestamp] = encoder(Types.TIMESTAMP, (index, value, row) => row.setTimestamp(index, value))

  implicit private val JsonEncoder: ctx.Encoder[io.circe.Json] = encoder(Types.VARCHAR, (index, value, row) => row.setString(index, value.noSpaces))

  implicit private val JsonDecoder: ctx.Decoder[io.circe.Json] = JdbcDecoder { (index: Index, row: ResultRow, _: Session) =>
    parse(row.getString(index)).fold(e => throw RepositoryError(e), json => json)
  }

  implicit private val GameStatusDecoder: ctx.Decoder[GameStatus] = JdbcDecoder { (index: Index, row: ResultRow, _: Session) =>
    GameStatus.withName(row.getString(index))
  }
  implicit private val GameStatusEncoder: ctx.Encoder[GameStatus] = encoder(Types.VARCHAR, (index, value, row) => row.setString(index, value.value))

  implicit class TimestampQuotes(left: Timestamp) {

    def >(right: Timestamp) = quote(infix"$left > $right".pure.as[Boolean])

    def <(right: Timestamp) = quote(infix"$left < $right".pure.as[Boolean])

    def >=(right: Timestamp) = quote(infix"$left >= $right".pure.as[Boolean])

    def <=(right: Timestamp) = quote(infix"$left <= $right".pure.as[Boolean])

    def between(
      first: Timestamp,
      last:  Timestamp
    ) = quote(infix"$left between $first and $last".pure.as[Boolean])

  }

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

    override def get(pk: UserId): RepositoryIO[Option[User]] =
      for {
        _ <- assertAuth(
          session => session.user == chuti.god || session.user == chuti.godless || session.user.id.fold(false)(_ == pk),
          session => s"${session.user} Not authorized"
        )
        res <- ctx
          .run(users.filter(u => u.id === lift(pk) && !u.deleted))
          .map(_.headOption.map(_.toUser))
          .provideLayer(dataSourceLayer)
          .mapError(RepositoryError.apply)
      } yield res

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
              exists <- ctx.run(users.filter(u => u.email === lift(user.email) && !u.deleted).nonEmpty)
              _ <- ZIO
                .fail(RepositoryError(s"Insert Error: A user with the email ${user.email} already exists, choose a different one"))
                .when(exists)
              saveMe = UserRow.fromUser(user.copy(lastUpdated = now, created = now))
              pk <- ctx
                .run(users.insertValue(lift(saveMe.copy(active = false, deleted = false))).returningGenerated(_.id))
            } yield saveMe.toUser.copy(id = Some(pk))
          } { id =>
            // It's an update, make sure that if the email has changed, it doesn't already exist
            for {
              exists <- ctx.run(users.filter(u => u.id != lift(id) && u.email === lift(user.email) && !u.deleted).nonEmpty)
              _ <- ZIO
                .fail(RepositoryError(s"Update error: A user with the email ${user.email} already exists, choose a different one"))
                .when(exists)
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

    override def getHistoricalUserGames: RepositoryIO[Seq[Game]] =
      (for {
        session <- ZIO.service[SessionProvider.Session].map(_.session)
        players <- ctx
          .run(
            gamePlayers
              .filter(p => p.userId == lift(session.user.id.getOrElse(UserId(-1))) && !p.invited)
              .join(games.filter(g => !g.deleted && g.status == lift(GameStatus.partidoTerminado: GameStatus))).on((players, game) =>
                players.gameId == game.id
              )
              .sortBy(_._2.lastUpdated)(Ord.descNullsLast)
          ).map(_.map(_._2.toGame))
      } yield players).provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def userInGame(id: GameId): RepositoryIO[Boolean] =
      (for {
        session <- ZIO.service[SessionProvider.Session].map(_.session)
        ret <-
          if (session.user.isBot)
            ZIO.succeed(true)
          else {
            ctx.run(gamePlayers.filter(gp => gp.gameId == lift(id) && gp.userId == lift(session.user.id.getOrElse(UserId(-1)))).nonEmpty)
          }

      } yield ret).provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def updatePlayers(game: Game): RepositoryIO[Game] = {
      def insertValues(players: List[GamePlayersRow]) =
        quote {
          liftQuery(players).foreach(c => gamePlayers.insertValue(c))
        }

      for {
        _ <- assertAuth(
          session => session.user == chuti.god || game.jugadores.exists(_.user.id == session.user.id),
          session => s"${session.user} Not authorized"
        )
        _ <- game.id.fold(ZIO.fail(RepositoryError("can't update players of unsaved game")): ZIO[Has[DataSource], RepositoryError, Long]) { id =>
          ctx.run(gamePlayers.filter(_.gameId == lift(id)).delete).mapError(RepositoryError.apply)
        }
        _ <- ctx.run(
          insertValues(
            game.jugadores.filter(!_.user.isBot).zipWithIndex.map { case (player, index) =>
              GamePlayersRow(
                userId = player.user.id.getOrElse(UserId(0)),
                gameId = game.id.getOrElse(GameId(0)),
                order = index,
                invited = player.invited
              )
            }
          )
        )
      } yield game
    }.provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def gameInvites: RepositoryIO[Seq[Game]] =
      (for {
        session <- ZIO.service[SessionProvider.Session].map(_.session)
        ret <- ctx
          .run(
            gamePlayers
              .filter(player => player.userId == lift(session.user.id.getOrElse(UserId(-1))) && player.invited)
              .join(games).on((player, game) => player.gameId == game.id).map(_._2)
          ).map(_.map(_.toGame))
      } yield ret).provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def gamesWaitingForPlayers(): RepositoryIO[Seq[Game]] =
      ctx
        .run(
          games
            .filter(g => g.status == lift(GameStatus.esperandoJugadoresAzar: GameStatus) && !g.deleted)
        )
        .map(_.map(_.toGame))
        .provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    private val runningGames: Set[GameStatus] = Set(
      GameStatus.comienzo,
      GameStatus.requiereSopa,
      GameStatus.cantando,
      GameStatus.jugando,
      GameStatus.partidoTerminado,
      GameStatus.esperandoJugadoresAzar,
      GameStatus.esperandoJugadoresInvitados
    )

    override def getGameForUser: RepositoryIO[Option[Game]] =
      (for {
        session <- ZIO.service[SessionProvider.Session].map(_.session)
        ret <-
          ctx
            .run(
              gamePlayers
                .filter(gp => gp.userId == lift(session.user.id.getOrElse(UserId(-1))) && !gp.invited)
                .join(games.filter(g => liftQuery(runningGames).contains(g.status)))
                .on(_.gameId == _.id)
                .map(_._2)
            ).map(_.headOption.map(_.toGame))
      } yield ret).provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def upsert(game: Game): RepositoryIO[Game] =
      (for {
        _ <- assertAuth(
          session => session.user == chuti.god || game.jugadores.exists(_.user.id == session.user.id),
          session => s"${session.user} Not authorized"
        )
        now <- ZIO.service[Clock.Service].flatMap(_.instant)
        _   <- scalacache.remove(game.id).mapError(e => RepositoryError("Cache error", Some(e)))
        upsertMe = GameRow.fromGame(game.copy(created = game.id.fold(now)(_ => game.created))).copy(lastUpdated = Timestamp.from(now))

        upserted <- game.id.fold {
          // It's an insert
          ctx
            .run(
              games
                .insertValue(lift(upsertMe))
                .returningGenerated(_.id)
            )
            .map(newId => upsertMe.copy(id = newId))
            .mapError(RepositoryError.apply)
        } { id =>
          for {
            updateCount <- ctx
              .run(
                games
                  .filter(g => g.id == lift(id) && !g.deleted)
                  .updateValue(lift(upsertMe))
              )
              .mapError(RepositoryError.apply)
            _ <- ZIO.fail(RepositoryError("Game not found")).when(updateCount == 0): ZIO[Has[DataSource], RepositoryError, Unit]
          } yield upsertMe
        }
        res = upserted.toGame
        _ <-
          scalacache
            .put(game.id)(Option(res), Option(3.hours)).mapError(e => RepositoryError("Cache error", Some(e)))
      } yield res).provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def get(pk: GameId): RepositoryIO[Option[Game]] =
      ctx
        .run(games.filter(g => g.id === lift(pk) && !g.deleted))
        .map(_.headOption.map(_.toGame))
        .provideLayer(dataSourceLayer)
        .mapError(RepositoryError.apply)

    override def delete(
      pk:         GameId,
      softDelete: Boolean
    ): RepositoryIO[Boolean] = {
      for {
        _ <- assertAuth(
          session => session.user == chuti.god,
          session => s"${session.user} Not authorized"
        )
        result <- {
          if (softDelete) {
            ctx
              .run(games.filter(u => u.id === lift(pk) && !u.deleted).update(_.deleted -> true))
              .map(_ > 0)
          } else {
            ctx.run(games.filter(_.id === lift(pk)).delete).map(_ > 0)
          }
        }
      } yield result
    }.provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def search(search: Option[EmptySearch]): RepositoryIO[Seq[Game]] =
      ctx
        .run(games.filter(!_.deleted))
        .map(_.map(_.toGame))
        .provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer)
        .mapError(RepositoryError.apply)

    override def count(search: Option[EmptySearch]): RepositoryIO[Long] =
      ctx
        .run(games.filter(!_.deleted).size)
        .provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer)
        .mapError(RepositoryError.apply)

  }
  override val tokenOperations: Repository.TokenOperations = new Repository.TokenOperations {

    override def cleanup: RepositoryIO[Boolean] =
      (for {
        now <- ZIO.service[Clock.Service].flatMap(_.instant).map(Timestamp.from)
        b   <- ctx.run(quote(tokens.filter(_.expireTime >= lift(now))).delete).map(_ > 0)
      } yield b)
        .provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer)
        .mapError(RepositoryError.apply)

    override def validateToken(
      token:   Token,
      purpose: TokenPurpose
    ): RepositoryIO[Option[User]] =
      (for {
        user <- peek(token, purpose)
        _    <- ctx.run(tokens.filter(t => t.tok == lift(token.tok) && t.tokenPurpose == lift(purpose.toString)).delete)
      } yield user)
        .provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer)
        .mapError(RepositoryError.apply)

    override def createToken(
      user:    User,
      purpose: TokenPurpose,
      ttl:     Option[Duration]
    ): ZIO[SessionProvider & Logging & Clock & Random, RepositoryError, Token] =
      (
        for {
          tok <- ZIO.service[Random.Service].flatMap(_.nextBytes(8)).map(r => new BigInteger(r.toArray).toString(32))
          row = TokenRow(
            tok = tok,
            tokenPurpose = purpose.toString,
            expireTime = new Timestamp(ttl.fold(Long.MaxValue)(_.toMillis + System.currentTimeMillis())),
            userId = user.id.getOrElse(UserId(-1))
          )
          inserted <- ctx.run(tokens.insertValue(lift(row))).as(Token(row.tok))
        } yield inserted
      ).provideSomeLayer[SessionProvider & Logging & Clock & Random](dataSourceLayer)
        .mapError(RepositoryError.apply)

    override def peek(
      token:   Token,
      purpose: TokenPurpose
    ): RepositoryIO[Option[User]] =
      ctx
        .run(
          tokens
            .filter(t => t.tok == lift(token.tok) && t.tokenPurpose == lift(purpose.toString))
            .join(users).on(_.userId == _.id).map(_._2)
        ).map(_.headOption.map(_.toUser))
        .provideSomeLayer[SessionProvider & Logging & Clock](dataSourceLayer)
        .mapError(RepositoryError.apply)

  }

}
