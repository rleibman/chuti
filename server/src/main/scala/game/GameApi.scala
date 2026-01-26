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
import caliban.interop.zio.*
import caliban.interop.zio.json.*
import caliban.introspection.adt.__Type
import caliban.schema.*
import caliban.schema.ArgBuilder.auto.*
import caliban.schema.Schema.auto.*
import caliban.wrappers.Wrappers.*
import chat.ChatService
import chuti.{*, given}
import dao.ZIORepository
import mail.Postman
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.logging.*
import zio.nio.file.Files
import zio.stream.ZStream

import java.net.URI
import java.nio.file.StandardOpenOption
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
    getHistoricalUserGames: ZIO[GameService & ChutiSession & ZIORepository, GameError, Json]
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
    friend:   UserId => ZIO[GameService & ChutiSession & ZIORepository & ChatService, GameError, Boolean],
    unfriend: UserId => ZIO[GameService & ChutiSession & ZIORepository & ChatService, GameError, Boolean],
    play:     PlayArgs => ZIO[GameService & ChutiSession & ZIORepository, GameError, Boolean]
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
  private given Schema[Any, Game] = Schema.gen[Any, Game]

  private given Schema[ChutiEnvironment & ChutiSession & GameService & ChatService, Queries] =
    Schema.gen[ChutiEnvironment & ChutiSession & GameService & ChatService, Queries]
  private given Schema[ChutiEnvironment & ChutiSession & GameService & ChatService, Mutations] =
    Schema.gen[ChutiEnvironment & ChutiSession & GameService & ChatService, Mutations]
  private given Schema[ChutiEnvironment & ChutiSession & GameService & ChatService, Subscriptions] =
    Schema.gen[ChutiEnvironment & ChutiSession & GameService & ChatService, Subscriptions]
  private given ArgBuilder[UserId] = ArgBuilder.long.map(UserId.apply)
  private given ArgBuilder[GameId] = ArgBuilder.long.map(GameId.apply)
  private given ArgBuilder[ConnectionId] = ArgBuilder.string.map(ConnectionId.apply)

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
      userOpt <- ZIO.serviceWith[ChutiSession](_.user)
      user    <- ZIO.fromOption(userOpt).orElseFail(GameError("Usuario no autenticado"))
    } yield sanitizeGame(game, user)

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
              gameOpt   <- GameService.getGame(gameId)
              sanitized <- ZIO.foreach(gameOpt)(sanitizeGame)
              jsonOpt   <- ZIO.fromEither(sanitized.toRight("").flatMap(_.toJsonAST)).mapError(GameError.apply)
            } yield jsonOpt,
          getGameForUser = for {
            gameOpt   <- GameService.getGameForUser
            sanitized <- ZIO.foreach(gameOpt)(sanitizeGame)
            jsonOpt   <- ZIO.fromEither(sanitized.toRight("").flatMap(_.toJsonAST)).mapError(GameError.apply)
          } yield jsonOpt,
          getFriends = GameService.getFriends,
          getGameInvites = for {
            gameSeq <- GameService.getGameInvites
            games   <- ZIO.foreach(gameSeq)(sanitizeGame)
            json    <- ZIO.fromEither(games.toJsonAST).mapError(GameError.apply)
          } yield json,
          getLoggedInUsers = GameService.getLoggedInUsers,
          getHistoricalUserGames = for {
            gameSeq <- GameService.getHistoricalUserGames
            games   <- ZIO.foreach(gameSeq)(sanitizeGame)
            json    <- ZIO.fromEither(games.toJsonAST).mapError(GameError.apply)
          } yield json
        ),
        Mutations(
          newGame = newGameArgs =>
            for {
              game      <- GameService.newGame(satoshiPerPoint = newGameArgs.satoshiPerPoint)
              sanitized <- sanitizeGame(game)
              jsonOpt   <- ZIO.fromEither(sanitized.toJsonAST).mapError(GameError.apply)
            } yield jsonOpt,
          newGameSameUsers = gameId =>
            for {
              game      <- GameService.newGameSameUsers(gameId)
              sanitized <- sanitizeGame(game)
              json      <- ZIO.fromEither(sanitized.toJsonAST).mapError(GameError.apply)
            } yield json,
          joinRandomGame = for {
            game      <- GameService.joinRandomGame()
            sanitized <- sanitizeGame(game)
            json      <- ZIO.fromEither(sanitized.toJsonAST).mapError(GameError.apply)
          } yield json,
          abandonGame = gameId => GameService.abandonGame(gameId),
          inviteToGame = userInviteArgs => GameService.inviteToGame(userInviteArgs.userId, userInviteArgs.gameId),
          inviteByEmail = inviteByEmailArgs =>
            GameService.inviteByEmail(
              inviteByEmailArgs.name,
              inviteByEmailArgs.email,
              inviteByEmailArgs.gameId
            ),
          startGame = gameId => GameService.startGame(gameId),
          acceptGameInvitation = gameId =>
            for {
              game      <- GameService.acceptGameInvitation(gameId)
              sanitized <- sanitizeGame(game)
              jsonOpt   <- ZIO.fromEither(sanitized.toJsonAST).mapError(GameError.apply)
            } yield jsonOpt,
          declineGameInvitation = gameId => GameService.declineGameInvitation(gameId),
          cancelUnacceptedInvitations = gameId => GameService.cancelUnacceptedInvitations(gameId),
          friend = userId => GameService.friend(userId),
          unfriend = userId => GameService.unfriend(userId),
          play = playArgs =>
            for {
              json   <- ZIO.fromEither(playArgs.gameEvent.toJsonAST).mapError(GameError.apply)
              played <- GameService.play(playArgs.gameId, json)
            } yield played
        ),
        Subscriptions(
          gameStream = gameStreamArgs =>
            GameService
              .gameStream(gameStreamArgs.gameId, gameStreamArgs.connectionId).flatMap(event =>
                ZStream.fromZIOOption(ZIO.fromOption(event.toJsonAST.toOption))
              ),
          userStream = connectionId => GameService.userStream(connectionId)
        )
      )
    ) @@ maxFields(20)
      @@ maxDepth(30)
      @@ printErrors
      @@ timeout(3.seconds)

}
