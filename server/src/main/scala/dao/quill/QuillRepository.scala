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
import game.GameService.godLayer
import io.circe.parser.*
import io.getquill.context.ZioJdbc.DataSourceLayer
import io.getquill.*
import io.getquill.extras.*
import io.getquill.{query as qquery, *}
import zio.*
import zio.cache.*
import zio.clock.Clock
import zio.duration.*
import zio.logging.Logging
import zio.random.Random
import io.getquill.extras.*

import java.math.BigInteger
import java.sql.{SQLException, Timestamp, Types}
import java.time.*
import javax.sql.DataSource
import scala.annotation.targetName

object QuillRepository {

  val uncached: URLayer[Config, Repository] =
    (for {
      config <- ZIO.service[Config.Service]
    } yield QuillRepository(config)).toLayer

  // TODO move to Repository
  val cached = {
    (for {
      repository <- ZIO.service[Repository.Service]
      cache <- zio.cache.Cache.make[GameId, Logging & Clock, RepositoryError, Game](
        capacity = 10,
        timeToLive = 1.hour,
        lookup = Lookup { gameId =>
          for {
            game <- repository.gameOperations
              .get(gameId)
              .flatMap(ZIO.fromOption)
              .orElseFail(RepositoryError(s"Could not find game: $gameId"))
              .provideSomeLayer[Logging & Clock](godLayer)
          } yield game
        }
      )
    } yield CachedQuillRepository(cache, repository): Repository.Service).toLayer
  }

}

//TODO move to Repository, rename to CachedRepository
case class CachedQuillRepository(
  gameCache:  Cache[GameId, RepositoryError, Game],
  repository: Repository.Service
) extends Repository.Service {

  override def gameOperations =
    new Repository.GameOperations {

      override def getHistoricalUserGames: RepositoryIO[Seq[Game]] = repository.gameOperations.getHistoricalUserGames

      override def userInGame(id: GameId): RepositoryIO[Boolean] = repository.gameOperations.userInGame(id)

      override def updatePlayers(game: Game): RepositoryIO[Game] =
        game.id.fold(ZIO.unit)(i => gameCache.invalidate(i)) *>
          repository.gameOperations.updatePlayers(game)

      override def gameInvites: RepositoryIO[Seq[Game]] = repository.gameOperations.gameInvites

      override def gamesWaitingForPlayers(): RepositoryIO[Seq[Game]] = repository.gameOperations.gamesWaitingForPlayers()

      override def getGameForUser: RepositoryIO[Option[Game]] = repository.gameOperations.getGameForUser

      override def upsert(game: Game): RepositoryIO[Game] =
        game.id.fold(ZIO.unit)(i => gameCache.invalidate(i)) *>
          repository.gameOperations.upsert(game)

      override def get(pk: GameId): RepositoryIO[Option[Game]] = gameCache.get(pk).map(Option.apply)

      override def delete(
        pk:         GameId,
        softDelete: Boolean
      ): RepositoryIO[Boolean] =
        gameCache.invalidate(pk) *>
          repository.gameOperations.delete(pk, softDelete)

      override def search(search: Option[EmptySearch]): RepositoryIO[Seq[Game]] = repository.gameOperations.search(search)

      override def count(search: Option[EmptySearch]): RepositoryIO[Long] = repository.gameOperations.count(search)

    }

  override def userOperations = repository.userOperations

  override def tokenOperations = repository.tokenOperations

}

case class QuillRepository(config: Config.Service) extends Repository.Service {

  private object ctx extends MysqlZioJdbcContext(MysqlEscape)

  import ctx.*

  given MappedEncoding[UserId, Int] = MappedEncoding[UserId, Int](_.userId)

  given MappedEncoding[Int, UserId] = MappedEncoding[Int, UserId](UserId.apply)

  given MappedEncoding[GameId, Int] = MappedEncoding[GameId, Int](_.gameId)

  given MappedEncoding[Int, GameId] = MappedEncoding[Int, GameId](GameId.apply)

  private given TimestampDecoder: ctx.Decoder[Timestamp] = JdbcDecoder((index: Index, row: ResultRow, _: Session) => row.getTimestamp(index).nn)

  private given TimestampEncoder: ctx.Encoder[Timestamp] = encoder(Types.TIMESTAMP, (index, value, row) => row.setTimestamp(index, value))

  private given JsonEncoder: ctx.Encoder[io.circe.Json] = encoder(Types.VARCHAR, (index, value, row) => row.setString(index, value.noSpaces))

  private given JsonDecoder: ctx.Decoder[io.circe.Json] =
    JdbcDecoder { (index: Index, row: ResultRow, _: Session) =>
      parse(row.getString(index).nn).fold(e => throw RepositoryError(e), json => json)
    }

  private val godSession: ULayer[SessionContext] = SessionContext.live(ChutiSession(chuti.god))

  inline private def users =
    quote {
      querySchema[UserRow]("user")
    }

  inline private def userLogins =
    quote {
      querySchema[UserLogRow]("userLog")
    }

  inline private def friends =
    quote {
      querySchema[FriendsRow]("friends")
    }

  inline private def userWallets =
    quote {
      querySchema[UserWalletRow]("userWallet")
    }

  inline private def games =
    quote {
      querySchema[GameRow]("game")
    }

  inline private def gamePlayers =
    quote {
      querySchema[GamePlayersRow]("game_players", _.gameId -> "game_id", _.userId -> "user_id", _.order -> "sort_order")
    }

  inline private def tokens =
    quote {
      querySchema[TokenRow]("token")
    }

  private val dataSourceLayer = DataSourceLayer.fromConfig(config.config.getConfig("chuti.db").nn)

  private given GameStatusDecoder: ctx.Decoder[GameStatus] =
    JdbcDecoder { (index: Index, row: ResultRow, _: Session) =>
      GameStatus.valueOf(row.getString(index).nn)
    }

  private given GameStatusEncoder: ctx.Encoder[GameStatus] = encoder(Types.VARCHAR, (index, value, row) => row.setString(index, value.value))

  extension (inline a: Timestamp) {

    inline def >=(inline b: Timestamp): Quoted[Boolean] =
      quote {
        infix"$a >= $b".as[Boolean]
      }

  }

  private def assertAuth(
    authorized: ChutiSession => Boolean,
    errorFn:    ChutiSession => String
  ): ZIO[SessionContext, RepositoryError, ChutiSession] = {
    for {
      session <- ZIO.service[SessionContext.Session].map(_.session)
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
          .run(users.filter(u => u.id === lift(pk.userId) && !u.deleted))
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
                users
                  .filter(u => u.id === lift(pk.userId) && !u.deleted).update(_.deleted -> true, _.deletedDate -> Some(lift(Timestamp.from(now).nn)))
              ).map(_ > 0)
          } else {
            ctx.run(users.filter(_.id === lift(pk.userId)).delete).map(_ > 0)
          }
        }
      } yield result
    }.provideSomeLayer[SessionContext & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

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
    }.map(_.map(_.toUser)).provideSomeLayer[SessionContext & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def count(search: Option[PagedStringSearch]): RepositoryIO[Long] = {
      search.fold(ctx.run(users.filter(!_.deleted).size)) { s =>
        {
          ctx.run(users.filter(u => (u.email like lift(s"%${s.text}%")) && !u.deleted).size)
        }
      }
    }.provideSomeLayer[SessionContext & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

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
            } yield saveMe.toUser.copy(id = Some(UserId(pk)))
          } { id =>
            // It's an update, make sure that if the email has changed, it doesn't already exist
            for {
              exists <- ctx.run(users.filter(u => u.id != lift(id.userId) && u.email === lift(user.email) && !u.deleted).nonEmpty)
              _ <- ZIO
                .fail(RepositoryError(s"Update error: A user with the email ${user.email} already exists, choose a different one"))
                .when(exists)
              saveMe = UserRow.fromUser(user.copy(lastUpdated = now))
              updateCount <- ctx.run(
                users
                  .filter(u => u.id === lift(id.userId) && !u.deleted)
                  .updateValue(lift(saveMe))
              )
              _ <- ZIO.fail(RepositoryError("User not found")).when(updateCount == 0)
            } yield saveMe.toUser
          }
        }
      } yield upserted).provideSomeLayer[SessionContext & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def firstLogin: RepositoryIO[Option[Instant]] =
      (for {
        chutiSession <- ZIO.service[SessionContext.Session].map(_.session)
        res <- ctx.run(userLogins.filter(_.userId === lift(chutiSession.user.id.fold(-1)(_.userId))).map(_.time).min).map(_.map(_.toInstant.nn))
      } yield res).provideSomeLayer[SessionContext & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def login(
      email:    String,
      password: String
    ): ZIO[Clock & Logging, RepositoryError, Option[User]] = {
      inline def sql =
        quote(infix"""select u.id
               from `user` u
               where u.deleted = 0 and
               u.active = 1 and
               u.email = ${lift(email)} and
               u.hashedpassword = SHA2(${lift(password)}, 512)""".as[Query[Int]])
      (for {
        now    <- ZIO.service[Clock.Service].flatMap(_.instant)
        userId <- ctx.run(sql).map(_.headOption.map(UserId.apply))
        user   <- ZIO.foreach(userId)(id => get(id)).provideSomeLayer[Clock & Logging](godSession)
        saveTime = Timestamp.from(now).nn
        _ <- ZIO.foreach_(userId)(id => ctx.run(userLogins.insertValue(UserLogRow(lift(id.userId), lift(saveTime)))))
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
      } yield res).provideSomeLayer[SessionContext & Clock & Logging](dataSourceLayer).mapError(RepositoryError.apply)

    override def userByEmail(email: String): RepositoryIO[Option[User]] = {
      ctx
        .run(users.filter(u => u.email == lift(email) && !u.deleted && u.active))
        .map(_.headOption.map(_.toUser))
        .provideSomeLayer[Clock & Logging](dataSourceLayer)
        .mapError(RepositoryError.apply)
    }

    override def unfriend(enemy: User): RepositoryIO[Boolean] = {
      (for {
        session <- ZIO.service[SessionContext.Session].map(_.session)
        rowOpt = for {
          one <- session.user.id
          two <- enemy.id
        } yield FriendsRow(one.userId, two.userId)
        deleted <- rowOpt.fold(ZIO.succeed(true): ZIO[Has[DataSource], SQLException, Boolean])(row =>
          ctx
            .run(
              QuillRepository.this.friends
                .filter(r => (r.one === lift(row.one) && r.two === lift(row.two)) || (r.one === lift(row.two) && r.two === lift(row.one)))
                .delete
            ).map(_ > 0)
        )
      } yield deleted)
        .provideSomeLayer[SessionContext & Clock & Logging](dataSourceLayer)
        .mapError(RepositoryError.apply)
    }

    override def friend(friend: User): RepositoryIO[Boolean] =
      (for {
        session <- ZIO.service[SessionContext.Session].map(_.session)
        friends <- friends
        res <- friends
          .find(_.id == friend.id).fold {
            val rowOpt = for {
              one <- session.user.id
              two <- friend.id
            } yield FriendsRow(one.userId, two.userId)
            rowOpt.fold(
              ZIO.succeed(false): zio.ZIO[Has[DataSource], SQLException, Boolean]
            ) { row =>
              ctx.run(QuillRepository.this.friends.insertValue(lift(row))).map(_ > 0)
            }
          }(_ => ZIO.succeed(true))
      } yield res)
        .provideSomeLayer[SessionContext & Clock & Logging](dataSourceLayer)
        .mapError(RepositoryError.apply)

    override def friends: RepositoryIO[Seq[User]] =
      (for {
        session <- ZIO.service[SessionContext.Session].map(_.session)
        res <- session.user.id.fold(ZIO.succeed(Seq.empty): ZIO[Has[DataSource], SQLException, Seq[User]]) { id =>
          ctx
            .run {
              users
                .join(QuillRepository.this.friends).on((a, b) => a.id === b.one || a.id === b.two).filter { case (u, f) =>
                  (f.one == lift(id.userId) || f.two == lift(id.userId)) && u.id != lift(id.userId)
                }
            }.map(_.map(_._1.toUser))
        }
      } yield res)
        .provideSomeLayer[SessionContext & Clock & Logging](dataSourceLayer)
        .mapError(RepositoryError.apply)

    override def getWallet: RepositoryIO[Option[UserWallet]] =
      for {
        session <- ZIO.service[SessionContext.Session].map(_.session)
        wallet  <- session.user.id.fold(ZIO.none: RepositoryIO[Option[UserWallet]])(getWallet)
      } yield wallet

    override def getWallet(userId: UserId): RepositoryIO[Option[UserWallet]] =
      (for {
        _ <- assertAuth(
          session => session.user == god || session.user.id.fold(false)(_ == userId),
          _ => "You can't see someone else's wallet"
        )
        walletOpt <- ctx.run(userWallets.filter(_.userId == lift(userId.userId))).map(_.headOption)
        wallet <- walletOpt.fold {
          val newWallet = UserWalletRow(userId.userId, 10000)
          ctx.run(userWallets.insertValue(lift(newWallet))).as(Option(newWallet.toUserWallet))
        }(w => ZIO.succeed(Option(w.toUserWallet)))
      } yield wallet)
        .provideSomeLayer[SessionContext & Clock & Logging](dataSourceLayer)
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
        }(_ => ctx.run(userWallets.filter(_.userId == lift(userWallet.userId.userId)).updateValue(lift(row))))
      } yield userWallet)
        .provideSomeLayer[SessionContext & Clock & Logging](dataSourceLayer)
        .mapError(RepositoryError.apply)

  }

  def unCachedGet(pk: GameId): RepositoryIO[Option[Game]] =
    ctx
      .run(games.filter(g => g.id == lift(pk.gameId) && !g.deleted))
      .map(_.headOption.map(_.toGame))
      .provideLayer(dataSourceLayer)
      .mapError(RepositoryError.apply)

  override val gameOperations: Repository.GameOperations = new Repository.GameOperations {

    override def getHistoricalUserGames: RepositoryIO[Seq[Game]] =
      (for {
        session <- ZIO.service[SessionContext.Session].map(_.session)
        players <- ctx
          .run(
            gamePlayers
              .filter(p => p.userId == lift(session.user.id.fold(-1)(_.userId)) && !p.invited)
              .join(games.filter(g => !g.deleted && g.status == lift(GameStatus.partidoTerminado: GameStatus))).on((players, game) =>
                players.gameId == game.id
              )
              .sortBy(_._2.lastUpdated)(Ord.descNullsLast)
          ).map(_.map(_._2.toGame))
      } yield players).provideSomeLayer[SessionContext & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def userInGame(id: GameId): RepositoryIO[Boolean] =
      (for {
        session <- ZIO.service[SessionContext.Session].map(_.session)
        ret <-
          if (session.user.isBot)
            ZIO.succeed(true)
          else {
            ctx.run(gamePlayers.filter(gp => gp.gameId == lift(id.gameId) && gp.userId == lift(session.user.id.fold(-1)(_.userId))).nonEmpty)
          }

      } yield ret).provideSomeLayer[SessionContext & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

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
          ctx.run(gamePlayers.filter(_.gameId == lift(id.gameId)).delete).mapError(RepositoryError.apply)
        }
        _ <- ctx.run(
          insertValues(
            game.jugadores.filter(!_.user.isBot).zipWithIndex.map { case (player, index) =>
              GamePlayersRow(
                userId = player.user.id.fold(0)(_.userId),
                gameId = game.id.fold(0)(_.gameId),
                order = index,
                invited = player.invited
              )
            }
          )
        )
      } yield game
    }.provideSomeLayer[SessionContext & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def gameInvites: RepositoryIO[Seq[Game]] =
      (for {
        session <- ZIO.service[SessionContext.Session].map(_.session)
        ret <- ctx
          .run(
            gamePlayers
              .filter(player => player.userId == lift(session.user.id.fold(-1)(_.userId)) && player.invited)
              .join(games).on((player, game) => player.gameId == game.id).map(_._2)
          ).map(_.map(_.toGame))
      } yield ret).provideSomeLayer[SessionContext & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def gamesWaitingForPlayers(): RepositoryIO[Seq[Game]] =
      ctx
        .run(
          games
            .filter(g => g.status == lift(GameStatus.esperandoJugadoresAzar: GameStatus) && !g.deleted)
        )
        .map(_.map(_.toGame))
        .provideSomeLayer[SessionContext & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

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
        session <- ZIO.service[SessionContext.Session].map(_.session)
        ret <-
          ctx
            .run(
              gamePlayers
                .filter(gp => gp.userId == lift(session.user.id.fold(-1)(_.userId)) && !gp.invited)
                .join(games.filter(g => liftQuery(runningGames).contains(g.status)))
                .on(_.gameId == _.id)
                .map(_._2)
            ).map(_.headOption.map(_.toGame))
      } yield ret).provideSomeLayer[SessionContext & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def upsert(game: Game): RepositoryIO[Game] =
      (for {
        _ <- assertAuth(
          session => session.user == chuti.god || game.jugadores.exists(_.user.id == session.user.id),
          session => s"${session.user} Not authorized"
        )
        now <- ZIO.service[Clock.Service].flatMap(_.instant)
        upsertMe = GameRow.fromGame(game.copy(created = game.id.fold(now)(_ => game.created))).copy(lastUpdated = Timestamp.from(now).nn)

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
                  .filter(g => g.id == lift(id.gameId) && !g.deleted)
                  .updateValue(lift(upsertMe))
              )
              .mapError(RepositoryError.apply)
            _ <- ZIO.fail(RepositoryError("Game not found")).when(updateCount == 0): ZIO[Has[DataSource], RepositoryError, Unit]
          } yield upsertMe
        }
        res = upserted.toGame
      } yield res).provideSomeLayer[SessionContext & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def get(pk: GameId): RepositoryIO[Option[Game]] =
      ctx
        .run(games.filter(g => g.id === lift(pk.gameId) && !g.deleted))
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
              .run(games.filter(g => g.id === lift(pk.gameId) && !g.deleted).update(_.deleted -> true))
              .map(_ > 0)
          } else {
            ctx.run(games.filter(_.id === lift(pk.gameId)).delete).map(_ > 0)
          }
        }
      } yield result
    }.provideSomeLayer[SessionContext & Logging & Clock](dataSourceLayer).mapError(RepositoryError.apply)

    override def search(search: Option[EmptySearch]): RepositoryIO[Seq[Game]] =
      ctx
        .run(games.filter(!_.deleted))
        .map(_.map(_.toGame))
        .provideSomeLayer[SessionContext & Logging & Clock](dataSourceLayer)
        .mapError(RepositoryError.apply)

    override def count(search: Option[EmptySearch]): RepositoryIO[Long] =
      ctx
        .run(games.filter(!_.deleted).size)
        .provideSomeLayer[SessionContext & Logging & Clock](dataSourceLayer)
        .mapError(RepositoryError.apply)

  }
  override val tokenOperations: Repository.TokenOperations = new Repository.TokenOperations {

    override def cleanup: RepositoryIO[Boolean] =
      (for {
        now <- ZIO.service[Clock.Service].flatMap(_.instant).map(a => Timestamp.from(a).nn)
        b   <- ctx.run(quote(tokens.filter(_.expireTime >= lift(now))).delete).map(_ > 0)
      } yield b)
        .provideSomeLayer[SessionContext & Logging & Clock](dataSourceLayer)
        .mapError(RepositoryError.apply)

    override def validateToken(
      token:   Token,
      purpose: TokenPurpose
    ): RepositoryIO[Option[User]] =
      (for {
        user <- peek(token, purpose)
        _    <- ctx.run(tokens.filter(t => t.tok == lift(token.tok) && t.tokenPurpose == lift(purpose.toString)).delete)
      } yield user)
        .provideSomeLayer[SessionContext & Logging & Clock](dataSourceLayer)
        .mapError(RepositoryError.apply)

    override def createToken(
      user:    User,
      purpose: TokenPurpose,
      ttl:     Option[Duration]
    ): ZIO[SessionContext & Logging & Clock & Random, RepositoryError, Token] =
      (
        for {
          tok <- ZIO.service[Random.Service].flatMap(_.nextBytes(8)).map(r => new BigInteger(r.toArray).toString(32))
          now <- ZIO.service[Clock.Service].flatMap(_.instant).map(_.toEpochMilli)
          row = TokenRow(
            tok = tok,
            tokenPurpose = purpose.toString,
            expireTime = new Timestamp(ttl.fold(Long.MaxValue)(_.toMillis + now)),
            userId = user.id.fold(-1)(_.userId)
          )
          inserted <- ctx.run(tokens.insertValue(lift(row))).as(Token(row.tok))
        } yield inserted
      ).provideSomeLayer[SessionContext & Logging & Clock & Random](dataSourceLayer)
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
        .provideSomeLayer[SessionContext & Logging & Clock](dataSourceLayer)
        .mapError(RepositoryError.apply)

  }

}
