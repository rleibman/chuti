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
import auth.{AuthConfig, AuthServer}
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
    connectionId: ConnectionId,
    token:        String
  ) derives ArgBuilder

  case class UserStreamArgs(
    connectionId: ConnectionId,
    token:        String
  ) derives ArgBuilder

  case class NewGameArgs(satoshiPerPoint: Long) derives ArgBuilder

  case class InviteByEmailArgs(
    name:   String,
    email:  String,
    gameId: GameId
  ) derives ArgBuilder

  case class Queries(
    getGame:                GameId => ZIO[GameService & ChutiSession & ZIORepository, GameError, Json],
    getGameForUser:         ZIO[GameService & GameEnvironment, GameError, Json],
    getFriends:             ZIO[GameService & GameEnvironment, GameError, Seq[User]],
    getGameInvites:         ZIO[GameService & GameEnvironment, GameError, Json],
    getLoggedInUsers:       ZIO[GameService & GameEnvironment, GameError, Seq[User]],
    getHistoricalUserGames: ZIO[GameService & GameEnvironment, GameError, Json],
    getWallet:              ZIO[GameService & GameEnvironment, GameError, Option[UserWallet]],
    isFirstLoginToday:      ZIO[GameService & GameEnvironment, GameError, Boolean]
  )
  case class Mutations(
    newGame:                     NewGameArgs => ZIO[GameService & GameEnvironment, GameError, Json],
    newGameSameUsers:            GameId => ZIO[GameService & GameEnvironment, GameError, Json],
    joinRandomGame:              ZIO[GameService & GameEnvironment, GameError, Json],
    abandonGame:                 GameId => ZIO[GameService & GameEnvironment, GameError, Boolean],
    inviteByEmail:               InviteByEmailArgs => ZIO[GameService & GameEnvironment, GameError, Boolean],
    startGame:                   GameId => ZIO[GameService & GameEnvironment, GameError, Boolean],
    inviteToGame:                GameInviteArgs => ZIO[GameService & GameEnvironment, GameError, Boolean],
    acceptGameInvitation:        GameId => ZIO[GameService & GameEnvironment, GameError, Json],
    declineGameInvitation:       GameId => ZIO[GameService & GameEnvironment, GameError, Boolean],
    cancelUnacceptedInvitations: GameId => ZIO[GameService & GameEnvironment, GameError, Boolean],
    friend:                      UserId => ZIO[GameService & GameEnvironment, GameError, Boolean],
    unfriend:                    UserId => ZIO[GameService & GameEnvironment, GameError, Boolean],
    play:                        PlayArgs => ZIO[GameService & GameEnvironment, GameError, Boolean],
    changePassword:              String => ZIO[GameService & GameEnvironment, GameError, Boolean]
  )
  case class Subscriptions(
    gameStream: GameStreamArgs => ZStream[
      GameService & ZIORepository & AuthConfig & AuthServer[User, Option[UserId], ConnectionId],
      GameError,
      Json
    ],
    userStream: UserStreamArgs => ZStream[
      GameService & ZIORepository & AuthConfig & AuthServer[User, Option[UserId], ConnectionId],
      GameError,
      UserEvent
    ]
  )

  private given Schema[Any, GameId] = Schema.longSchema.contramap(_.value)
  private given Schema[Any, UserId] = Schema.longSchema.contramap(_.value)
  private given Schema[Any, ConnectionId] = Schema.stringSchema.contramap(_.value)
  private given Schema[Any, Locale] = Schema.stringSchema.contramap(_.toString)
  private given Schema[Any, User] = Schema.gen[Any, User]
  private given Schema[Any, UserWallet] = Schema.gen[Any, UserWallet]
  private given Schema[Any, Game] = Schema.gen[Any, Game]

  private given ArgBuilder[UserId] = ArgBuilder.long.map(UserId.apply)
  private given ArgBuilder[GameId] = ArgBuilder.long.map(GameId.apply)
  private given ArgBuilder[ConnectionId] = ArgBuilder.string.map(ConnectionId.apply)
  private given ArgBuilder[User] = ArgBuilder.gen[User]
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
              json <- sanitized.fold(ZIO.succeed(Json.Null))(game =>
                ZIO.fromEither(game.toJsonAST).mapError(GameError.apply)
              )
            } yield json,
          getGameForUser = for {
            gameOpt   <- ZIO.serviceWithZIO[ZIORepository](_.gameOperations.getGameForUser)
            sanitized <- ZIO.foreach(gameOpt)(sanitizeGame)
            json <- sanitized.fold(ZIO.succeed(Json.Null))(game =>
              ZIO.fromEither(game.toJsonAST).mapError(GameError.apply)
            )
          } yield json,
          getFriends = ZIO.serviceWithZIO[ZIORepository](_.userOperations.friends),
          getGameInvites = for {
            gameSeq <- ZIO.serviceWithZIO[ZIORepository](_.gameOperations.gameInvites)
            games   <- ZIO.foreach(gameSeq)(sanitizeGame)
            json    <- ZIO.fromEither(games.toJsonAST).mapError(GameError.apply)
          } yield json,
          getLoggedInUsers = ZIO.serviceWithZIO[GameService](_.getLoggedInUsers),
          getHistoricalUserGames = for {
            gameSeq <- ZIO.serviceWithZIO[ZIORepository](_.gameOperations.getHistoricalUserGames)
            games   <- ZIO.foreach(gameSeq)(sanitizeGame)
            json    <- ZIO.fromEither(games.toJsonAST).mapError(GameError.apply)
          } yield json,
          getWallet = ZIO.serviceWithZIO[ZIORepository](_.userOperations.getWallet),
          isFirstLoginToday = ZIO.serviceWithZIO[ZIORepository](_.userOperations.isFirstLoginToday)
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
          friend = userId => GameService.friend(userId),
          unfriend = userId => GameService.unfriend(userId),
          play = playArgs =>
            for {
              event  <- ZIO.fromEither(playArgs.gameEvent.as[PlayEvent]).mapError(GameError.apply)
              played <- ZIO.serviceWithZIO[GameService](_.playSilently(playArgs.gameId, event))
            } yield played,
          changePassword =
            newPassword => ZIO.serviceWithZIO[ZIORepository](_.userOperations.changePassword(newPassword))
        ),
        Subscriptions(
          gameStream = gameStreamArgs =>
            ZStream.unwrap(
              for {
                authServer <- ZIO.service[AuthServer[User, Option[UserId], ConnectionId]]
                sessionLayer <- authServer
                  .sessionLayerFromToken(
                    gameStreamArgs.token,
                    Some(gameStreamArgs.connectionId)
                  ).mapError(e => GameError(e.getMessage))
              } yield ZStream
                .serviceWithStream[GameService](
                  _.gameStream(gameStreamArgs.gameId, gameStreamArgs.connectionId)
                    .flatMap(event => ZStream.fromZIOOption(ZIO.fromOption(event.toJsonAST.toOption)))
                ).provideSomeLayer[GameService & ZIORepository](sessionLayer)
            ),
          userStream = userStreamArgs =>
            ZStream.unwrap(
              for {
                authServer <- ZIO.service[AuthServer[User, Option[UserId], ConnectionId]]
                sessionLayer <- authServer
                  .sessionLayerFromToken(
                    userStreamArgs.token,
                    Some(userStreamArgs.connectionId)
                  ).mapError(e => GameError(e.getMessage))
              } yield ZStream
                .serviceWithStream[GameService](
                  _.userStream(userStreamArgs.connectionId)
                ).provideSomeLayer[GameService & ZIORepository](sessionLayer)
            )
        )
      )
    ) @@ maxFields(50)
      @@ maxDepth(30)
      @@ printErrors
      @@ gameErrorHandler
      @@ timeout(3.seconds)

}
