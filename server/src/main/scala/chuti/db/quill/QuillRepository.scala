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

package chuti.db.quill

import chuti.*
import chuti.api.token.{Token, TokenPurpose}
import chuti.api.{*, given}
import chuti.db.*
import io.getquill.jdbczio.Quill
import io.getquill.{query as qquery, *}
import zio.json.*
import zio.json.ast.Json
import zio.{Clock, Random, *}

import java.math.BigInteger
import java.sql.{SQLException, Timestamp, Types}
import java.time.*
import javax.sql.DataSource
import scala.concurrent.duration.Duration as ScalaDuration

object QuillRepository {

  val uncached: ZLayer[ConfigurationService & FlywayMigration, ConfigurationError, QuillRepository] =
    ZLayer.fromZIO(for {
      migration <- ZIO.service[FlywayMigration]
      _         <- migration.migrate.mapError(e => ConfigurationError(s"Migration failed: ${e.getMessage}", Some(e)))
      configService <- ZIO.service[ConfigurationService]
      appConfig     <- configService.appConfig
    } yield QuillRepository(appConfig))

}

case class QuillRepository(config: AppConfig) extends ZIORepository {

  private object ctx extends MysqlZioJdbcContext(MysqlEscape)

  import ctx.*

  private val dataSourceLayer: TaskLayer[DataSource] = {
    println("=========================== dataSourceLayerCreation should only happen once!")
    Quill.DataSource.fromDataSource(config.dataSource)
  }

  given MappedEncoding[UserId, Long] = MappedEncoding[UserId, Long](_.value)

  given MappedEncoding[Long, UserId] = MappedEncoding[Long, UserId](UserId.apply)

  given MappedEncoding[GameId, Long] = MappedEncoding[GameId, Long](_.value)

  given MappedEncoding[Long, GameId] = MappedEncoding[Long, GameId](GameId.apply)

  private given ctx.Encoder[Json] =
    encoder(
      Types.VARCHAR,
      (
        index,
        value,
        row
      ) => row.setString(index, value.toString)
    )

  private def requiredUserId: ZIO[ChutiSession, RepositoryError, UserId] =
    ZIO
      .serviceWith[ChutiSession](_.user.map(_.id)).someOrFail(
        RepositoryError("User is required for this operation")
      )

  private given ctx.Decoder[Json] =
    JdbcDecoder {
      (
        index: Index,
        row:   ResultRow,
        _:     Session
      ) =>
        row.getString(index).fromJson[Json].fold(e => throw RepositoryError(e), json => json)
    }

  private val godSession: ULayer[ChutiSession] = ChutiSession.godSession.toLayer

  inline def qUsers =
    quote {
      querySchema[UserRow](
        "user",
        _.id                      -> "id",
        _.email                   -> "email",
        _.name                    -> "name",
        _.deleted                 -> "deleted",
        _.lastUpdated             -> "lastUpdated",
        _.oauth.map(_.provider)   -> "oauthProvider",
        _.oauth.map(_.providerId) -> "oauthProviderId",
        _.oauth.map(_.data)       -> "oauthProviderData"
      )
    }

  inline private def qUserLogs =
    quote {
      querySchema[UserLogRow]("userLog")
    }

  inline private def qFriends =
    quote {
      querySchema[FriendsRow]("friends")
    }

  inline private def qUserWallet =
    quote {
      querySchema[UserWalletRow]("userWallet")
    }

  inline private def qGames =
    quote {
      querySchema[GameRow]("game")
    }

  inline private def qGamePlayers =
    quote {
      querySchema[GamePlayersRow](
        "game_players",
        _.gameId -> "game_id",
        _.userId -> "user_id",
        _.order  -> "sort_order"
      )
    }

  inline private def qTokens =
    quote {
      querySchema[TokenRow]("token")
    }

  private given ctx.Decoder[GameStatus] =
    JdbcDecoder {
      (
        index: Index,
        row:   ResultRow,
        _:     Session
      ) =>
        GameStatus.valueOf(row.getString(index).nn)
    }

  private given ctx.Encoder[GameStatus] =
    encoder(
      Types.VARCHAR,
      (
        index,
        value,
        row
      ) => row.setString(index, value.value)
    )

  extension (inline a: Timestamp) {

    inline def >=(inline b: Timestamp): Quoted[Boolean] =
      quote {
        infix"$a >= $b".as[Boolean]
      }

  }

  private def assertAuth(
    authorized:     ChutiSession => Boolean,
    errorFn:        ChutiSession => String
  )(implicit trace: Trace
  ): ZIO[ChutiSession, RepositoryError, ChutiSession] = {
    for {
      session <- ZIO.service[ChutiSession]
      _ <- ZIO
        .fail(RepositoryPermissionError(errorFn(session)))
        .when(!authorized(session))
    } yield session
  }

  override val userOperations: UserOperations[RepositoryIO] = new UserOperations[RepositoryIO] {

    override def get(pk: UserId): RepositoryIO[Option[User]] = {
      for {
        _ <- assertAuth(
          session =>
            session.user.contains(chuti.god) || session.user.contains(chuti.godless) || session.user
              .map(_.id).fold(false)(_ == pk),
          session => s"get ${session.user} Not authorized"
        )
        res <- ctx
          .run(qUsers.filter(u => u.id == lift(pk.value) && !u.deleted))
          .map(_.headOption.map(_.toUser))
          .provideSomeLayer[ChutiSession](dataSourceLayer)
          .mapError(RepositoryError.apply)
      } yield res
    }

    override def delete(
      pk:         UserId,
      softDelete: Boolean
    ): RepositoryIO[Boolean] = {
      for {
        _ <- assertAuth(
          session => session.user.contains(chuti.god) || session.user.map(_.id).fold(false)(_ == pk),
          session => s"delete ${session.user} Not authorized"
        )
        now <- Clock.instant
        result <- {
          if (softDelete) {
            ctx
              .run(
                qUsers
                  .filter(u => u.id == lift(pk.value) && !u.deleted).update(
                    _.deleted     -> true,
                    _.deletedDate -> Some(lift(Timestamp.from(now).nn))
                  )
              ).map(_ > 0)
          } else {
            ctx.run(qUsers.filter(_.id == lift(pk.value)).delete).map(_ > 0)
          }
        }
      } yield result
    }
      .provideSomeLayer[ChutiSession](dataSourceLayer)
      .mapError(RepositoryError.apply)

    override def search(search: Option[PagedStringSearch]): RepositoryIO[Seq[User]] = {
      search
        .fold(ctx.run(qUsers.filter(!_.deleted))) { s =>
          {
            ctx.run(
              qUsers
                .filter(u => (u.email like lift(s"%${s.text}%")) && !u.deleted)
                .drop(lift(s.pageSize * s.pageIndex))
                .take(lift(s.pageSize))
            )
          }
        }
    }.map(_.map(_.toUser)).provideSomeLayer[ChutiSession](dataSourceLayer).mapError(RepositoryError.apply)

    override def count(search: Option[PagedStringSearch]): RepositoryIO[Long] = {
      search.fold(ctx.run(qUsers.filter(!_.deleted).size)) { s =>
        {
          ctx.run(qUsers.filter(u => (u.email like lift(s"%${s.text}%")) && !u.deleted).size)
        }
      }
    }.provideSomeLayer[ChutiSession](dataSourceLayer).mapError(RepositoryError.apply)

    override def upsert(user: User): RepositoryIO[User] =
      (for {
        _ <- assertAuth(
          session => session.user.contains(chuti.god) || session.user.map(_.id).contains(user.id),
          session => s"upsert ${session.user} Not authorized"
        )
        now <- Clock.instant

        upserted <- {
          if (user.id.isEmpty) {
            // It's an insert, make sure te user does not exist by email
            for {
              exists <- ctx.run(qUsers.filter(u => u.email == lift(user.email) && !u.deleted).nonEmpty)
              _ <- ZIO
                .fail(
                  RepositoryError(
                    s"Insert Error: A user with the email ${user.email} already exists, choose a different one"
                  )
                )
                .when(exists)
              saveMe = UserRow.fromUser(user.copy(lastUpdated = now, created = now))
              pk <- ctx
                .run(qUsers.insertValue(lift(saveMe.copy(active = false, deleted = false))).returningGenerated(_.id))
            } yield saveMe.toUser.copy(id = UserId(pk))
          } else {
            // It's an update, make sure that if the email has changed, it doesn't already exist
            for {
              exists <- ctx.run(
                qUsers.filter(u => u.id != lift(user.id.value) && u.email == lift(user.email) && !u.deleted).nonEmpty
              )
              _ <- ZIO
                .fail(
                  RepositoryError(
                    s"Update error: A user with the email ${user.email} already exists, choose a different one"
                  )
                )
                .when(exists)
              saveMe = UserRow.fromUser(user.copy(lastUpdated = now))
              updateCount <- ctx.run(
                qUsers
                  .filter(u => u.id == lift(user.id.value) && !u.deleted)
                  .updateValue(lift(saveMe))
              )
              _ <- ZIO.fail(RepositoryError("User not found")).when(updateCount == 0)
            } yield saveMe.toUser
          }
        }
      } yield upserted).provideSomeLayer[ChutiSession](dataSourceLayer).mapError(RepositoryError.apply)

    override def firstLogin: RepositoryIO[Option[Instant]] =
      (for {
        userId <- ZIO
          .serviceWith[ChutiSession](_.user.map(_.id)).someOrFail(
            RepositoryError("User is required for this operation")
          )
        res <- ctx
          .run(
            qUserLogs
              .filter(_.userId == lift(userId.value)).map(_.time).min
          ).map(
            _.map(_.toInstant.nn)
          )
      } yield res).provideSomeLayer[ChutiSession](dataSourceLayer).mapError(RepositoryError.apply)

    override def login(
      email:    String,
      password: String
    ): ZIO[Any, RepositoryError, Option[User]] = {
      inline def sql =
        quote(infix"""select u.id
               from `user` u
               where u.deleted = 0 and
               u.active = 1 and
               u.email = ${lift(email)} and
               u.hashedpassword = SHA2(${lift(password)}, 512)""".as[Query[Long]])
      (for {
        now    <- Clock.instant
        userId <- ctx.run(sql).map(_.headOption.map(UserId.apply))
        user   <- ZIO.foreach(userId)(id => get(id)).provide(godSession)
        saveTime = Timestamp.from(now).nn
        _ <- ZIO.foreachDiscard(userId)(id =>
          ctx.run(qUserLogs.insertValue(UserLogRow(lift(id.value), lift(saveTime))))
        )
      } yield user.flatten)
        .provideSomeLayer(dataSourceLayer)
        .mapError(RepositoryError.apply)
    }

    override def changePassword(
      user:        User,
      newPassword: String
    ): RepositoryIO[Boolean] =
      (for {
        _ <- assertAuth(
          session => session.user.contains(chuti.god) || session.user.map(_.id).contains(user.id),
          session => s"change password ${session.user} Not authorized"
        )
        res <- ctx
          .run(
            quote(
              infix"update `user` set hashedPassword=SHA2(${lift(newPassword)}, 512) where id = ${lift(user.id)}"
                .as[Update[Int]]
            )
          ).map(_ > 0)
      } yield res).provideSomeLayer[ChutiSession](dataSourceLayer).mapError(RepositoryError.apply)

    override def changePassword(newPassword: String): RepositoryIO[Boolean] =
      for {
        user   <- ZIO.serviceWith[ChutiSession](_.user).someOrFail(RepositoryError("User is required"))
        result <- changePassword(user, newPassword)
      } yield result

    override def userByEmail(email: String): RepositoryIO[Option[User]] = {
      ctx
        .run(qUsers.filter(u => u.email == lift(email) && !u.deleted && u.active))
        .map(_.headOption.map(_.toUser))
        .provideSomeLayer[ChutiSession](dataSourceLayer)
        .mapError(RepositoryError.apply)
    }

    override def unfriend(enemy: UserId): RepositoryIO[Boolean] = {
      (for {
        userOpt <- ZIO.serviceWith[ChutiSession](_.user)
        rowOpt = userOpt.map(u => FriendsRow(u.id.value, enemy.value))
        deleted <- rowOpt.fold(ZIO.succeed(true): ZIO[DataSource, SQLException, Boolean])(row =>
          ctx
            .run(
              qFriends
                .filter(r =>
                  (r.one == lift(row.one) && r.two == lift(row.two)) || (r.one == lift(row.two) && r.two == lift(
                    row.one
                  ))
                )
                .delete
            ).map(_ > 0)
        )
      } yield deleted)
        .provideSomeLayer[ChutiSession](dataSourceLayer)
        .mapError(RepositoryError.apply)
    }

    override def friend(friend: UserId): RepositoryIO[Boolean] =
      (for {
        userOpt <- ZIO.serviceWith[ChutiSession](_.user)
        friends <- friends
        res <- friends
          .find(_.id == friend).fold {
            val rowOpt = for {
              one <- userOpt.map(_.id)
            } yield FriendsRow(one.value, friend.value)
            rowOpt.fold(
              ZIO.succeed(false): zio.ZIO[DataSource, SQLException, Boolean]
            ) { row =>
              ctx.run(qFriends.insertValue(lift(row))).map(_ > 0)
            }
          }(_ => ZIO.succeed(true))
      } yield res)
        .provideSomeLayer[ChutiSession](dataSourceLayer)
        .mapError(RepositoryError.apply)

    override def friends: RepositoryIO[Seq[User]] =
      (for {
        id <- ZIO
          .serviceWith[ChutiSession](_.user.map(_.id)).someOrFail(
            RepositoryError("User is required for this operation")
          )
        res <-
          ctx
            .run {
              qUsers
                .join(qFriends).on(
                  (
                    a,
                    b
                  ) => a.id == b.one || a.id == b.two
                ).filter { case (u, f) =>
                  (f.one == lift(id.value) || f.two == lift(id.value)) && u.id != lift(id.value)
                }
            }.map(_.map(_._1.toUser))
      } yield res)
        .provideSomeLayer[ChutiSession](dataSourceLayer)
        .mapError(RepositoryError.apply)

    override def getWallet: RepositoryIO[Option[UserWallet]] =
      for {
        userId <- ZIO
          .serviceWith[ChutiSession](_.user.map(_.id)).someOrFail(
            RepositoryError("User is required for this operation")
          )
        wallet <- getWallet(userId)
      } yield wallet

    override def getWallet(userId: UserId): RepositoryIO[Option[UserWallet]] =
      (for {
        _ <- assertAuth(
          session => session.user.contains(god) || session.user.map(_.id).fold(false)(_ == userId),
          _ => "You can't see someone else's wallet"
        )
        walletOpt <- ctx.run(qUserWallet.filter(_.userId == lift(userId.value))).map(_.headOption)
        wallet <- walletOpt.fold {
          val newWallet = UserWalletRow(userId.value, 10000)
          ctx.run(qUserWallet.insertValue(lift(newWallet))).as(Option(newWallet.toUserWallet))
        }(w => ZIO.succeed(Option(w.toUserWallet)))
      } yield wallet)
        .provideSomeLayer[ChutiSession](dataSourceLayer)
        .mapError(RepositoryError.apply)

    override def updateWallet(userWallet: UserWallet): RepositoryIO[UserWallet] =
      (for {
        _ <- assertAuth(
          session => session.user.contains(god),
          _ => "Only god (or the Fed) can update a wallet"
        )
        existing <- getWallet(userWallet.userId)
        row = UserWalletRow.fromUserWallet(userWallet)
        _ <- existing.fold {
          ctx.run(qUserWallet.insertValue(lift(row)))
        }(_ => ctx.run(qUserWallet.filter(_.userId == lift(userWallet.userId.value)).updateValue(lift(row))))
      } yield userWallet)
        .provideSomeLayer[ChutiSession](dataSourceLayer)
        .mapError(RepositoryError.apply)

    override def isFirstLoginToday: RepositoryIO[Boolean] =
      (for {
        firstLoginOpt <- firstLogin
        now           <- Clock.instant
        isFirstToday = firstLoginOpt.fold(true) { firstLogin =>
          val firstLoginDate = firstLogin.atZone(java.time.ZoneId.systemDefault()).toLocalDate
          val todayDate = now.atZone(java.time.ZoneId.systemDefault()).toLocalDate
          firstLoginDate == todayDate
        }
      } yield isFirstToday)
        .mapError(RepositoryError.apply)

    override def userByOAuthProvider(
      provider:   String,
      providerId: String
    ): RepositoryIO[Option[User]] =
      assertAuth(_.user.contains(chuti.god), session => s"get ${session.user} Not authorized") *> ctx
        .run(
          qUsers.filter(v =>
            !v.deleted &&
              v.oauth.map(_.provider).contains(lift(provider)) &&
              v.oauth.map(_.providerId).contains(lift(providerId))
          )
        )
        .map(_.headOption.map(_.toUser))
        .provideLayer(dataSourceLayer)
        .mapError(RepositoryError(_))
        .tapError(e => ZIO.logErrorCause(Cause.fail(e)))
  }

  def unCachedGet(pk: GameId): RepositoryIO[Option[Game]] =
    ctx
      .run(qGames.filter(g => g.id == lift(pk.value) && !g.deleted))
      .flatMap(a => ZIO.foreach(a.headOption)(_.toGame))
      .provideSomeLayer[ChutiSession](dataSourceLayer)
      .mapError(RepositoryError.apply)

  override val gameOperations: GameOperations[RepositoryIO] = new GameOperations[RepositoryIO] {

    override def getHistoricalUserGames: RepositoryIO[Seq[Game]] =
      (for {
        userId <- requiredUserId
        players <- ctx
          .run(
            qGamePlayers
              .filter(p => p.userId == lift(userId.value) && !p.invited)
              .join(qGames.filter(g => !g.deleted && g.status == lift(GameStatus.partidoTerminado: GameStatus))).on(
                (
                  players,
                  game
                ) => players.gameId == game.id
              )
              .sortBy(_._2.lastUpdated)(Ord.descNullsLast)
          )
          .flatMap(a => ZIO.foreach(a)(_._2.toGame))
      } yield players).provideSomeLayer[ChutiSession](dataSourceLayer).mapError(RepositoryError.apply)

    override def userInGame(id: GameId): RepositoryIO[Boolean] =
      (for {
        user <- ZIO.serviceWith[ChutiSession](_.user).someOrFail(RepositoryError("User is required for this operation"))
        ret <-
          if (user.isBot)
            ZIO.succeed(true)
          else {
            ctx.run(
              qGamePlayers
                .filter(gp => gp.gameId == lift(id.value) && gp.userId == lift(user.id.value)).nonEmpty
            )
          }

      } yield ret).provideSomeLayer[ChutiSession](dataSourceLayer).mapError(RepositoryError.apply)

    override def updatePlayers(game: Game): RepositoryIO[Game] = {
      def insertValues(players: List[GamePlayersRow]) =
        quote {
          liftQuery(players).foreach(c => qGamePlayers.insertValue(c))
        }

      for {
        _ <- assertAuth(
          session =>
            session.user.contains(chuti.god) || game.jugadores.exists(j => session.user.map(_.id).contains(j.user.id)),
          session => s"update players ${session.user} Not authorized"
        )
        _ <-
          if (game.id.isEmpty)
            ZIO.fail(RepositoryError("can't update players of unsaved game")): ZIO[DataSource, RepositoryError, Long]
          else
            ctx.run(qGamePlayers.filter(_.gameId == lift(game.id.value)).delete).mapError(RepositoryError.apply)
        _ <- ctx.run(
          insertValues(
            game.jugadores.filter(!_.user.isBot).zipWithIndex.map { case (player, index) =>
              GamePlayersRow(
                userId = player.user.id.value,
                gameId = game.id.value,
                order = index,
                invited = player.invited
              )
            }
          )
        )
      } yield game
    }.provideSomeLayer[ChutiSession](dataSourceLayer).mapError(RepositoryError.apply)

    override def gameInvites: RepositoryIO[Seq[Game]] =
      (for {
        userId <- requiredUserId
        ret <- ctx
          .run(
            qGamePlayers
              .filter(player => player.userId == lift(userId.value) && player.invited)
              .join(qGames).on(
                (
                  player,
                  game
                ) => player.gameId == game.id
              ).map(_._2)
          )
          .flatMap(a => ZIO.foreach(a)(_.toGame))
      } yield ret).provideSomeLayer[ChutiSession](dataSourceLayer).mapError(RepositoryError.apply)

    override def gamesWaitingForPlayers(): RepositoryIO[Seq[Game]] =
      ctx
        .run(
          qGames
            .filter(g => g.status == lift(GameStatus.esperandoJugadoresAzar: GameStatus) && !g.deleted)
        )
        .flatMap(a => ZIO.foreach(a)(_.toGame))
        .provideSomeLayer[ChutiSession](dataSourceLayer).mapError(RepositoryError.apply)

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
        userId <- requiredUserId
        ret <-
          ctx
            .run(
              qGamePlayers
                .filter(gp => gp.userId == lift(userId.value) && !gp.invited)
                .join(qGames.filter(g => liftQuery(runningGames).contains(g.status)))
                .on(_.gameId == _.id)
                .sortBy { case (_, game) =>
                  (game.status == lift(GameStatus.partidoTerminado: GameStatus), game.lastUpdated)
                }(Ord(Ord.asc, Ord.descNullsLast))
                .map(_._2)
            )
            .flatMap(a => ZIO.foreach(a.headOption)(_.toGame))
      } yield ret).provideSomeLayer[ChutiSession](dataSourceLayer).mapError(RepositoryError.apply)

    override def upsert(game: Game): RepositoryIO[Game] =
      (for {
        _ <- assertAuth(
          session =>
            session.user.contains(chuti.god) || game.jugadores.exists(j => session.user.map(_.id).contains(j.user.id)),
          session => s"upsert game ${session.user} Not authorized"
        )
        now <- Clock.instant
        upsertMe = GameRow
          .fromGame(game.copy(created = if (game.id.isEmpty) now else game.created)).copy(lastUpdated =
            Timestamp.from(now).nn
          )

        upserted <-
          if (game.id.isEmpty) {
            // It's an insert
            ctx
              .run(
                qGames
                  .insertValue(lift(upsertMe))
                  .returningGenerated(_.id)
              )
              .map(newId => upsertMe.copy(id = newId))
              .mapError(RepositoryError.apply)
          } else {
            for {
              updateCount <- ctx
                .run(
                  qGames
                    .filter(g => g.id == lift(game.id.value) && !g.deleted)
                    .updateValue(lift(upsertMe))
                )
                .mapError(RepositoryError.apply)
              _ <- ZIO.fail(RepositoryError("Game not found")).when(updateCount == 0)
            } yield upsertMe
          }
        res <- upserted.toGame
      } yield res).provideSomeLayer[ChutiSession](dataSourceLayer).mapError(RepositoryError.apply)

    override def get(pk: GameId): RepositoryIO[Option[Game]] =
      ctx
        .run(qGames.filter(g => g.id == lift(pk.value) && !g.deleted))
        .flatMap(a => ZIO.foreach(a.headOption)(_.toGame))
        .provideSomeLayer[ChutiSession](dataSourceLayer)
        .mapError(RepositoryError.apply)

    override def delete(
      pk:         GameId,
      softDelete: Boolean
    ): RepositoryIO[Boolean] = {
      for {
        _ <- assertAuth(
          session => session.user.contains(chuti.god),
          session => s"delete game ${session.user} Not authorized"
        )
        result <- {
          if (softDelete) {
            ctx
              .run(qGames.filter(g => g.id == lift(pk.value) && !g.deleted).update(_.deleted -> true))
              .map(_ > 0)
          } else {
            ctx.run(qGames.filter(_.id == lift(pk.value)).delete).map(_ > 0)
          }
        }
      } yield result
    }.provideSomeLayer[ChutiSession](dataSourceLayer).mapError(RepositoryError.apply)

    override def search(search: Option[EmptySearch]): RepositoryIO[Seq[Game]] =
      ctx
        .run(qGames.filter(!_.deleted))
        .flatMap(a => ZIO.foreach(a)(_.toGame))
        .provideSomeLayer[ChutiSession](dataSourceLayer)
        .mapError(RepositoryError.apply)

    override def count(search: Option[EmptySearch]): RepositoryIO[Long] =
      ctx
        .run(qGames.filter(!_.deleted).size)
        .provideSomeLayer[ChutiSession](dataSourceLayer)
        .mapError(RepositoryError.apply)

  }
  override val tokenOperations: TokenOperations[RepositoryIO] = new TokenOperations[RepositoryIO] {

    override def cleanup: RepositoryIO[Boolean] =
      (for {
        now <- Clock.instant.map(a => Timestamp.from(a).nn)
        b   <- ctx.run(quote(qTokens.filter(_.expireTime >= lift(now))).delete).map(_ > 0)
      } yield b)
        .provideSomeLayer[ChutiSession](dataSourceLayer)
        .mapError(RepositoryError.apply)

    override def validateToken(
      token:   Token,
      purpose: TokenPurpose
    ): RepositoryIO[Option[User]] =
      (for {
        user <- peek(token, purpose)
        _ <- ctx.run(qTokens.filter(t => t.tok == lift(token.tok) && t.tokenPurpose == lift(purpose.toString)).delete)
      } yield user)
        .provideSomeLayer[ChutiSession](dataSourceLayer)
        .mapError(RepositoryError.apply)

    override def createToken(
      user:    User,
      purpose: TokenPurpose,
      ttl:     Option[ScalaDuration]
    ): ZIO[ChutiSession, RepositoryError, Token] =
      (
        for {
          userId <- requiredUserId
          tok    <- Random.nextBytes(8).map(r => BigInteger(r.toArray).toString(32))
          now    <- Clock.instant.map(_.toEpochMilli)
          row = TokenRow(
            tok = tok,
            tokenPurpose = purpose.toString,
            expireTime = Timestamp(ttl.fold(Long.MaxValue)(_.toMillis + now)),
            userId = userId.value
          )
          inserted <- ctx.run(qTokens.insertValue(lift(row))).as(Token(row.tok))
        } yield inserted
      ).provideSomeLayer[ChutiSession](dataSourceLayer)
        .mapError(RepositoryError.apply)

    override def peek(
      token:   Token,
      purpose: TokenPurpose
    ): RepositoryIO[Option[User]] =
      ctx
        .run(
          qTokens
            .filter(t => t.tok == lift(token.tok) && t.tokenPurpose == lift(purpose.toString))
            .join(qUsers).on(_.userId == _.id).map(_._2)
        ).map(_.headOption.map(_.toUser))
        .provideSomeLayer[ChutiSession](dataSourceLayer)
        .mapError(RepositoryError.apply)

  }

}
