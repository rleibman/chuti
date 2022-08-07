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

import caliban.GraphQL.graphQL
import caliban.schema.{ArgBuilder, GenericSchema, Schema}
import caliban.wrappers.Wrappers.{maxDepth, maxFields, printSlowQueries, timeout}
import caliban.{GraphQL, RootResolver}
import chat.ChatApi.{Mutations, Queries, Subscriptions}
import chat.ChatService
import chuti.*
import dao.{Repository, SessionContext}
import game.GameService
import game.GameService.GameLayer
import zio.logging.*
import zio.stream.ZStream
import zio.{Clock, Console, ZIO, *}
import zio.json.*

object GameApi extends GenericSchema[GameService & GameLayer & ChatService] {

  opaque type GameAsJson = String

  object GameAsJson {

    def fromGame(game: Game): GameAsJson = game.toJson

  }

  opaque type GameEventAsJson = String

  object GameEventAsJson {

    def fromGameEvent(gameEvent: GameEvent): GameEventAsJson = gameEvent.toJson

  }

  opaque type PlayEventAsJson = String

  extension (playEvent: PlayEvent) def toPlayEventAsJson: PlayEventAsJson = playEvent.toJson

  object PlayEventAsJson {

    given JsonDecoder[PlayEventAsJson] = JsonDecoder.string
    given JsonEncoder[PlayEventAsJson] = JsonEncoder.string

  }

  case class PlayArgs(
    gameId:    GameId,
    playEvent: PlayEventAsJson
  )
  case class GameInviteArgs(
    userId: UserId,
    gameId: GameId
  )

  case class GameStreamArgs(
    gameId:       GameId,
    connectionId: ConnectionId
  )

  case class NewGameArgs(satoshiPerPoint: Int)

  case class InviteByEmailArgs(
    name:   String,
    email:  String,
    gameId: GameId
  )

  case class Queries(
    getGame:                GameId => ZIO[GameService & GameLayer, GameError, Option[GameAsJson]],
    getGameForUser:         ZIO[GameService & GameLayer, GameError, Option[GameAsJson]],
    getFriends:             ZIO[GameService & GameLayer, GameError, Seq[User]],
    getGameInvites:         ZIO[GameService & GameLayer, GameError, Seq[GameAsJson]],
    getLoggedInUsers:       ZIO[GameService & GameLayer, GameError, Seq[User]],
    getHistoricalUserGames: ZIO[GameService & GameLayer, GameError, Seq[GameAsJson]]
  )
  case class Mutations(
    newGame: NewGameArgs => ZIO[GameService & GameLayer, GameError, GameAsJson],
    newGameSameUsers: GameId => ZIO[
      GameService & GameLayer & ChatService,
      GameError,
      GameAsJson
    ],
    joinRandomGame: ZIO[GameService & GameLayer, GameError, GameAsJson],
    abandonGame:    GameId => ZIO[GameService & GameLayer, GameError, Boolean],
    inviteByEmail: InviteByEmailArgs => ZIO[
      GameService & GameLayer & ChatService,
      GameError,
      Boolean
    ],
    startGame: GameId => ZIO[GameService & GameLayer, GameError, Boolean],
    inviteToGame: GameInviteArgs => ZIO[
      GameService & GameLayer & ChatService,
      GameError,
      Boolean
    ],
    acceptGameInvitation: GameId => ZIO[GameService & GameLayer, GameError, GameAsJson],
    declineGameInvitation: GameId => ZIO[
      GameService & GameLayer & ChatService,
      GameError,
      Boolean
    ],
    cancelUnacceptedInvitations: GameId => ZIO[
      GameService & GameLayer & ChatService,
      GameError,
      Boolean
    ],
    friend:   UserId => ZIO[GameService & GameLayer & ChatService, GameError, Boolean],
    unfriend: UserId => ZIO[GameService & GameLayer & ChatService, GameError, Boolean],
    play:     PlayArgs => ZIO[GameService & GameLayer, GameError, Boolean]
  )
  case class Subscriptions(
    gameStream: GameStreamArgs => ZStream[GameService & GameLayer, GameError, GameEventAsJson],
    userStream: ConnectionId => ZStream[GameService & GameLayer, GameError, UserEvent]
  )

  private given Schema[Any, GameId] = Schema.intSchema.contramap(_.gameId)
  private given Schema[Any, UserId] = Schema.intSchema.contramap(_.userId)
  private given Schema[Any, ConnectionId] = Schema.intSchema.contramap(_.connectionId)
  private given Schema[Any, User] = Schema.gen[Any, User]
  private given Schema[Any, Game] = Schema.gen[Any, Game]
  private given Schema[GameService & GameLayer, Queries] = Schema.gen[GameService & GameLayer, Queries]
  private given Schema[GameService & GameLayer & ChatService, Mutations] = Schema.gen[GameService & GameLayer & ChatService, Mutations]
  private given Schema[GameService & GameLayer & ChatService, Subscriptions] = Schema.gen[GameService & GameLayer & ChatService, Subscriptions]
  private given ArgBuilder[UserId] = ArgBuilder.int.map(UserId.apply)
  private given ArgBuilder[GameId] = ArgBuilder.int.map(GameId.apply)
  private given ArgBuilder[ConnectionId] = ArgBuilder.int.map(ConnectionId.apply)

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

  def sanitizeGame(game: Game): ZIO[SessionContext, Nothing, Game] =
    for {
      user <- ZIO.service[SessionContext].map(_.session.user)
    } yield sanitizeGame(game, user)

  lazy val api: GraphQL[GameService & GameLayer & ChatService] =
    graphQL(
      RootResolver(
        Queries(
          getGame = gameId =>
            for {
              gameOpt  <- GameService.getGame(gameId)
              filtered <- ZIO.foreach(gameOpt)(sanitizeGame)
            } yield filtered.map(GameAsJson.fromGame),
          getGameForUser = for {
            gameOpt  <- GameService.getGameForUser
            filtered <- ZIO.foreach(gameOpt)(sanitizeGame)
          } yield filtered.map(GameAsJson.fromGame),
          getFriends = GameService.getFriends,
          getGameInvites = for {
            gameSeq  <- GameService.getGameInvites
            filtered <- ZIO.foreach(gameSeq)(sanitizeGame)
          } yield filtered.map(GameAsJson.fromGame),
          getLoggedInUsers = GameService.getLoggedInUsers,
          getHistoricalUserGames = for {
            gameSeq  <- GameService.getHistoricalUserGames
            filtered <- ZIO.foreach(gameSeq)(sanitizeGame)
          } yield filtered.map(GameAsJson.fromGame)
        ),
        Mutations(
          newGame = newGameArgs =>
            for {
              game     <- GameService.newGame(satoshiPerPoint = newGameArgs.satoshiPerPoint)
              filtered <- sanitizeGame(game)
            } yield filtered.toJson,
          newGameSameUsers = gameId =>
            for {
              game     <- GameService.newGameSameUsers(gameId)
              filtered <- sanitizeGame(game)
            } yield filtered.toJson,
          joinRandomGame = for {
            game     <- GameService.joinRandomGame()
            filtered <- sanitizeGame(game)
          } yield filtered.toJson,
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
              game     <- GameService.acceptGameInvitation(gameId)
              filtered <- sanitizeGame(game)
            } yield filtered.toJson,
          declineGameInvitation = gameId => GameService.declineGameInvitation(gameId),
          cancelUnacceptedInvitations = gameId => GameService.cancelUnacceptedInvitations(gameId),
          friend = userId => GameService.friend(userId),
          unfriend = userId => GameService.unfriend(userId),
          play = playArgs =>
            for {
              playEvent <- ZIO.fromEither(playArgs.playEvent.fromJson[PlayEvent]).mapError(s => GameError(s))
              played    <- GameService.play(playArgs.gameId, playEvent)
            } yield played
        ),
        Subscriptions(
          gameStream = gameStreamArgs =>
            GameService
              .gameStream(gameStreamArgs.gameId, gameStreamArgs.connectionId).map(GameEventAsJson.fromGameEvent),
          userStream = connectionId => GameService.userStream(connectionId)
        )
      )
    ) @@
      maxFields(200) @@ // query analyzer that limit query fields
      maxDepth(30)
//      @@ // query analyzer that limit query depth
//      timeout(15.seconds) @@ // wrapper that fails slow queries
//      printSlowQueries(3.seconds)
//  @@ // wrapper that logs slow queries
//      apolloTracing // wrapper for https://github.com/apollographql/apollo-tracing
  val schema =
    "schema {\n  query: Queries\n  mutation: Mutations\n  subscription: Subscriptions\n}\n\nscalar Json\n\nscalar Instant\n\nenum UserEventType {\n  AbandonedGame\n  Connected\n  Disconnected\n  JoinedGame\n  Modified\n}\n\ntype Mutations {\n  newGame(satoshiPerPoint: Int!): Json\n  newGameSameUsers(value: Int!): Json\n  joinRandomGame: Json\n  abandonGame(value: Int!): Boolean\n  inviteByEmail(name: String!, email: String!, gameId: Int!): Boolean\n  startGame(value: Int!): Boolean\n  inviteToGame(userId: Int!, gameId: Int!): Boolean\n  acceptGameInvitation(value: Int!): Json\n  declineGameInvitation(value: Int!): Boolean\n  cancelUnacceptedInvitations(value: Int!): Boolean\n  friend(value: Int!): Boolean\n  unfriend(value: Int!): Boolean\n  play(gameId: Int!, gameEvent: Json!): Boolean\n}\n\ntype Queries {\n  getGame(value: Int!): Json\n  getGameForUser: Json\n  getFriends: [User!]\n  `: [Json!]\n  getLoggedInUsers: [User!]\n  getHistoricalUserGames: [Json!]\n}\n\ntype Subscriptions {\n  gameStream(gameId: Int!, connectionId: String!): Json\n  userStream(value: String!): UserEvent\n}\n\ntype User {\n  id: Int\n  email: String!\n  name: String!\n  created: Instant!\n  active: Boolean!\n  deleted: Boolean!\n  isAdmin: Boolean!\n}\n\ntype UserEvent {\n  user: User!\n  userEventType: UserEventType!\n  gameId: Int\n}"

  // Generate client with
  // calibanGenClient /Volumes/Personal/projects/chuti/server/src/main/graphql/game.schema /Volumes/Personal/projects/chuti/web/src/main/scala/game/GameClient.scala
}
