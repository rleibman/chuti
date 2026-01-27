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

package game

import api.token.TokenHolder
import api.{ChutiEnvironment, ChutiSession}
import caliban.*
import caliban.CalibanError.ExecutionError
import caliban.execution.ExecutionRequest
import caliban.interop.zio.*
import caliban.interop.zio.json.*
import caliban.introspection.adt.__Type
import caliban.schema.*
import caliban.schema.ArgBuilder.auto.*
import caliban.schema.Schema.auto.*
import caliban.wrappers.Wrapper.ExecutionWrapper
import caliban.wrappers.Wrappers.*
import chat.ChatService
import chuti.{*, given}
import dao.{RepositoryError, ZIORepository}
import mail.Postman
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.stream.ZStream

import java.util.Locale

object GameApi {

  import caliban.interop.circe.json.*

  case class PlayArgs(
    gameId:    GameId,
    gameEvent: Json
  ) derives ArgBuilder
  case class GameInviteArgs(
    userId: UserId,
    gameId: GameId
  ) derives ArgBuilder

  case class GameStreamArgs(
    gameId:       GameId,
    connectionId: ConnectionId
  ) derives ArgBuilder

  case class NewGameArgs(satoshiPerPoint: Long) derives ArgBuilder

  case class InviteByEmailArgs(
    name:   String,
    email:  String,
    gameId: GameId
  ) derives ArgBuilder

  case class Queries(
    getGame:                GameId => ZIO[GameService & ChutiSession & ZIORepository, GameError, Json],
    getGameForUser:         ZIO[GameService & ChutiSession & ZIORepository, GameError, Json],
    getFriends:             ZIO[GameService & ChutiSession & ZIORepository, GameError, Seq[User]],
    getGameInvites:         ZIO[GameService & ChutiSession & ZIORepository, GameError, Json],
    getLoggedInUsers:       ZIO[GameService & ChutiSession & ZIORepository, GameError, Seq[User]],
    getHistoricalUserGames: ZIO[GameService & ChutiSession & ZIORepository, GameError, Json],
    getWallet:              ZIO[GameService & ChutiSession & ZIORepository, GameError, Option[UserWallet]],
    isFirstLoginToday:      ZIO[GameService & ChutiSession & ZIORepository, GameError, Boolean]
  )
  case class Mutations(
    newGame: NewGameArgs => ZIO[GameService & ChutiSession & ZIORepository, GameError, Json],
    newGameSameUsers: GameId => ZIO[
      GameService & TokenHolder & ChutiSession & ChatService & ZIORepository & ChutiSession & Postman,
      GameError,
      Json
    ],
    joinRandomGame: ZIO[GameService & ChutiSession & ZIORepository, GameError, Json],
    abandonGame:    GameId => ZIO[GameService & ChutiSession & ZIORepository, GameError, Boolean],
    inviteByEmail: InviteByEmailArgs => ZIO[
      GameService & TokenHolder & ChutiSession & ChatService & ZIORepository & ChutiSession & Postman,
      GameError,
      Boolean
    ],
    startGame: GameId => ZIO[GameService & ZIORepository & Postman & TokenHolder & ChutiSession, GameError, Boolean],
    inviteToGame: GameInviteArgs => ZIO[
      TokenHolder & ChutiSession & ChatService & ZIORepository & ChutiSession & Postman & GameService,
      GameError,
      Boolean
    ],
    acceptGameInvitation: GameId => ZIO[GameService & ChutiSession & ZIORepository, GameError, Json],
    declineGameInvitation: GameId => ZIO[
      GameService & ChutiSession & ZIORepository & ChatService,
      GameError,
      Boolean
    ],
    cancelUnacceptedInvitations: GameId => ZIO[
      GameService & ChutiSession & ZIORepository & ChatService,
      GameError,
      Boolean
    ],
    friend:         UserId => ZIO[GameService & ChutiSession & ZIORepository & ChatService, GameError, Boolean],
    unfriend:       UserId => ZIO[GameService & ChutiSession & ZIORepository & ChatService, GameError, Boolean],
    play:           PlayArgs => ZIO[GameService & ChutiSession & ZIORepository, GameError, Boolean],
    changePassword: String => ZIO[GameService & ChutiSession & ZIORepository, GameError, Boolean],
  )
  case class Subscriptions(
    gameStream: GameStreamArgs => ZStream[GameService & ChutiSession & ZIORepository, GameError, Json],
    userStream: ConnectionId => ZStream[GameService & ChutiSession & ZIORepository, GameError, UserEvent]
  )

  private given Schema[Any, GameId] = Schema.longSchema.contramap(_.value)
  private given Schema[Any, UserId] = Schema.longSchema.contramap(_.value)
  private given Schema[Any, ConnectionId] = Schema.stringSchema.contramap(_.value)
  private given Schema[Any, Locale] = Schema.stringSchema.contramap(_.toString)
  private given Schema[Any, User] = Schema.gen[Any, User]
  private given Schema[Any, UserWallet] = Schema.gen[Any, UserWallet]
  private given Schema[Any, Game] = Schema.gen[Any, Game]
  private given ArgBuilder[User] = ArgBuilder.gen[User]

  private given Schema[ChutiEnvironment & ChutiSession & GameService & ChatService, Queries] =
    Schema.gen[ChutiEnvironment & ChutiSession & GameService & ChatService, Queries]
  private given Schema[ChutiEnvironment & ChutiSession & GameService & ChatService, Mutations] =
    Schema.gen[ChutiEnvironment & ChutiSession & GameService & ChatService, Mutations]
  private given Schema[ChutiEnvironment & ChutiSession & GameService & ChatService, Subscriptions] =
    Schema.gen[ChutiEnvironment & ChutiSession & GameService & ChatService, Subscriptions]
  private given ArgBuilder[UserId] = ArgBuilder.long.map(UserId.apply)
  private given ArgBuilder[GameId] = ArgBuilder.long.map(GameId.apply)
  private given ArgBuilder[ConnectionId] = ArgBuilder.string.map(ConnectionId.apply)
  private given ArgBuilder[Locale] =
    ArgBuilder.string.flatMap(s =>
      Locale.forLanguageTag(s) match {
        case l: Locale => Right(l)
        case null => Left(CalibanError.ExecutionError(s"invalid locale $s"))
      }
    )

  def sanitizeGame(
    game: Game,
    user: User
  ): Game =
    game.copy(
      jugadores = game.modifiedJugadores(
        _.user.id == user.id,
        identity,
        j =>
          j.copy(
            fichas = j.fichas.map(_ => FichaTapada)
            //            , // No tapa probablemente tengamos que taparlas
            //            filas = j.filas.map(f => f.copy(fichas = f.fichas.map(_ => FichaTapada)))
          )
      )
    )

  def sanitizeGame(game: Game): ZIO[ChutiSession, GameError, Game] =
    for {
      user <- ZIO
        .serviceWith[ChutiSession](_.user).someOrFail(
          RepositoryError("User is required for this operation")
        )
    } yield sanitizeGame(game, user)

  // Custom error handler that exposes GameError messages in GraphQL responses
  private val gameErrorHandler: ExecutionWrapper[Any] = new ExecutionWrapper[Any] {

    def wrap[R1 <: Any](
      f: ExecutionRequest => ZIO[R1, Nothing, GraphQLResponse[CalibanError]]
    ): ExecutionRequest => ZIO[R1, Nothing, GraphQLResponse[CalibanError]] =
      request =>
        f(request).map { response =>
          response.copy(
            errors = response.errors.map {
              case e: ExecutionError =>
                e.innerThrowable match {
                  case Some(gameError: GameError) =>
                    e.copy(msg = s"GameError: ${gameError.msg}")
                  case Some(other) =>
                    e.copy(msg = s"Error: ${other.getMessage}")
                  case None => e
                }
              case other => other
            }
          )
        }

  }

  lazy val api: GraphQL[ChutiEnvironment & ChutiSession & GameService & ChatService] =
    graphQL[
      ChutiEnvironment & ChutiSession & GameService & ChatService,
      Queries,
      Mutations,
      Subscriptions
    ](
      RootResolver(
        Queries(
          getGame = gameId =>
            for {
              gameOpt   <- ZIO.serviceWithZIO[ZIORepository](_.gameOperations.get(gameId))
              sanitized <- ZIO.foreach(gameOpt)(sanitizeGame)
              jsonOpt   <- ZIO.fromEither(sanitized.toRight("").flatMap(_.toJsonAST)).mapError(GameError.apply)
            } yield jsonOpt,
          getGameForUser = for {
            gameOpt   <- ZIO.serviceWithZIO[GameService](_.getGameForUser)
            sanitized <- ZIO.foreach(gameOpt)(sanitizeGame)
            jsonOpt   <- ZIO.fromEither(sanitized.toRight("").flatMap(_.toJsonAST)).mapError(GameError.apply)
          } yield jsonOpt,
          getFriends = ZIO.serviceWithZIO[ZIORepository](_.userOperations.friends),
          getGameInvites = for {
            gameSeq <- ZIO.serviceWithZIO[GameService](_.getGameInvites)
            games   <- ZIO.foreach(gameSeq)(sanitizeGame)
            json    <- ZIO.fromEither(games.toJsonAST).mapError(GameError.apply)
          } yield json,
          getLoggedInUsers = ZIO.serviceWithZIO[GameService](_.getLoggedInUsers),
          getHistoricalUserGames = for {
            gameSeq <- ZIO.serviceWithZIO[GameService](_.getHistoricalUserGames)
            games   <- ZIO.foreach(gameSeq)(sanitizeGame)
            json    <- ZIO.fromEither(games.toJsonAST).mapError(GameError.apply)
          } yield json,
          getWallet = ZIO.serviceWithZIO[GameService](_.getWallet),
          isFirstLoginToday = ZIO.serviceWithZIO[GameService](_.isFirstLoginToday)
        ),
        Mutations(
          newGame = newGameArgs =>
            for {
              game      <- ZIO.serviceWithZIO[GameService](_.newGame(satoshiPerPoint = newGameArgs.satoshiPerPoint))
              sanitized <- sanitizeGame(game)
              jsonOpt   <- ZIO.fromEither(sanitized.toJsonAST).mapError(GameError.apply)
            } yield jsonOpt,
          newGameSameUsers = gameId =>
            for {
              game      <- ZIO.serviceWithZIO[GameService](_.newGameSameUsers(gameId))
              sanitized <- sanitizeGame(game)
              json      <- ZIO.fromEither(sanitized.toJsonAST).mapError(GameError.apply)
            } yield json,
          joinRandomGame = for {
            game      <- ZIO.serviceWithZIO[GameService](_.joinRandomGame())
            sanitized <- sanitizeGame(game)
            json      <- ZIO.fromEither(sanitized.toJsonAST).mapError(GameError.apply)
          } yield json,
          abandonGame = gameId => ZIO.serviceWithZIO[GameService](_.abandonGame(gameId)),
          inviteToGame = userInviteArgs =>
            ZIO.serviceWithZIO[GameService](_.inviteToGame(userInviteArgs.userId, userInviteArgs.gameId)),
          inviteByEmail = inviteByEmailArgs =>
            ZIO.serviceWithZIO[GameService](
              _.inviteByEmail(
                inviteByEmailArgs.name,
                inviteByEmailArgs.email,
                inviteByEmailArgs.gameId
              )
            ),
          startGame = gameId => ZIO.serviceWithZIO[GameService](_.startGame(gameId)),
          acceptGameInvitation = gameId =>
            for {
              game      <- ZIO.serviceWithZIO[GameService](_.acceptGameInvitation(gameId))
              sanitized <- sanitizeGame(game)
              jsonOpt   <- ZIO.fromEither(sanitized.toJsonAST).mapError(GameError.apply)
            } yield jsonOpt,
          declineGameInvitation = gameId => ZIO.serviceWithZIO[GameService](_.declineGameInvitation(gameId)),
          cancelUnacceptedInvitations =
            gameId => ZIO.serviceWithZIO[GameService](_.cancelUnacceptedInvitations(gameId)),
          friend = userId => ZIO.serviceWithZIO[GameService](_.friend(userId)),
          unfriend = userId => ZIO.serviceWithZIO[GameService](_.unfriend(userId)),
          play = playArgs =>
            for {
              json   <- ZIO.fromEither(playArgs.gameEvent.toJsonAST).mapError(GameError.apply)
              played <- GameService.play(playArgs.gameId, json)
            } yield played,
          changePassword = newPassword =>
            ZIO.serviceWithZIO[ZIORepository](_.userOperations.changePassword(newPassword)),
        ),
        Subscriptions(
          gameStream = gameStreamArgs =>
            ZStream.serviceWithStream[GameService](
              _.gameStream(gameStreamArgs.gameId, gameStreamArgs.connectionId).flatMap(event =>
                ZStream.fromZIOOption(ZIO.fromOption(event.toJsonAST.toOption))
              )
            ),
          userStream = connectionId => ZStream.serviceWithStream[GameService](_.userStream(connectionId))
        )
      )
    ) @@ maxFields(20)
      @@ maxDepth(30)
      @@ printErrors
      @@ gameErrorHandler
      @@ timeout(3.seconds)

}
