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

import java.time.{Instant, LocalDateTime, ZoneOffset}

import caliban.CalibanError.ExecutionError
import caliban.GraphQL.graphQL
import caliban.Value.IntValue
import caliban.schema.{ArgBuilder, GenericSchema, Schema}
import caliban.wrappers.ApolloTracing.apolloTracing
import caliban.wrappers.Wrappers.{maxDepth, maxFields, printSlowQueries, timeout}
import caliban.{GraphQL, RootResolver}
import chat.ChatService.ChatService
import chuti.{ConnectionId, GameId, UserId, _}
import dao.SessionProvider
import game.GameService.{GameLayer, GameService}
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import zio.ZIO
import zio.clock.Clock
import zio.console.Console
import zio.duration._
import zio.stream.ZStream

object GameApi extends GenericSchema[GameService with GameLayer with ChatService] {
  import caliban.interop.circe.json._

  case class PlayArgs(
    gameId:    GameId,
    gameEvent: Json
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
    getGame:          GameId => ZIO[GameService with GameLayer, GameException, Option[Json]],
    getGameForUser:   ZIO[GameService with GameLayer, GameException, Option[Json]],
    getFriends:       ZIO[GameService with GameLayer, GameException, Seq[User]],
    getGameInvites:   ZIO[GameService with GameLayer, GameException, Seq[Json]],
    getLoggedInUsers: ZIO[GameService with GameLayer, GameException, Seq[User]]
  )
  case class Mutations(
    newGame:        NewGameArgs => ZIO[GameService with GameLayer, GameException, Json],
    joinRandomGame: ZIO[GameService with GameLayer, GameException, Json],
    abandonGame:    GameId => ZIO[GameService with GameLayer, GameException, Boolean],
    inviteByEmail: InviteByEmailArgs => ZIO[
      GameService with GameLayer with ChatService,
      GameException,
      Boolean
    ],
    inviteToGame: GameInviteArgs => ZIO[
      GameService with GameLayer with ChatService,
      GameException,
      Boolean
    ],
    acceptGameInvitation: GameId => ZIO[GameService with GameLayer, GameException, Json],
    declineGameInvitation: GameId => ZIO[
      GameService with GameLayer with ChatService,
      GameException,
      Boolean
    ],
    cancelUnacceptedInvitations: GameId => ZIO[
      GameService with GameLayer with ChatService,
      GameException,
      Boolean
    ],
    friend:   UserId => ZIO[GameService with GameLayer with ChatService, GameException, Boolean],
    unfriend: UserId => ZIO[GameService with GameLayer with ChatService, GameException, Boolean],
    play:     PlayArgs => ZIO[GameService with GameLayer, GameException, Boolean]
  )
  case class Subscriptions(
    gameStream: GameStreamArgs => ZStream[GameService with GameLayer, GameException, Json],
    userStream: ConnectionId => ZStream[GameService with GameLayer, GameException, UserEvent]
  )

  implicit val localDateTimeSchema: Typeclass[LocalDateTime] =
    Schema.longSchema.contramap(_.toInstant(ZoneOffset.UTC).toEpochMilli)
  implicit val localDateTimeArgBuilder: ArgBuilder[LocalDateTime] = {
    case value: IntValue =>
      Right(LocalDateTime.ofInstant(Instant.ofEpochMilli(value.toLong), ZoneOffset.UTC))
    case other => Left(ExecutionError(s"Can't build a LocalDateTime from input $other"))
  }
  implicit private val userSchema: GameApi.Typeclass[User] = gen[User]
//  implicit private val numeroTypeSchema: GameApi.Typeclass[Numero] = gen[Numero]
//  implicit private val estadoSchema:     GameApi.Typeclass[Estado] = gen[Estado]
//  implicit private val triunfoSchema:    GameApi.Typeclass[Triunfo] = gen[Triunfo]
//  implicit private val fichaSchema:      GameApi.Typeclass[Ficha] = gen[Ficha]
//  implicit private val filaSchema:       GameApi.Typeclass[Fila] = gen[Fila]
//  implicit private val jugadorSchema:    GameApi.Typeclass[Jugador] = gen[Jugador]
//  implicit private val gameStateSchema:  GameApi.Typeclass[GameState] = gen[GameState]
  //  implicit private val jugadaSchema:     GameApi.Typeclass[GameEvent] = gen[GameEvent]

  private def filterSecretKnowledge(game: Game) =
    for {
      user <- ZIO.access[SessionProvider](_.get.session.user)
    } yield game.copy(
      jugadores = game.modifiedJugadores(
        _.user.id == user.id,
        identity,
        j =>
          j.copy(
            fichas = j.fichas.map(_ => FichaTapada),
            filas = j.filas.map(f => f.copy(fichas = f.fichas.map(_ => FichaTapada)))
          )
      )
    )

  val api: GraphQL[Console with Clock with GameService with GameLayer with ChatService] =
    graphQL(
      RootResolver(
        Queries(
          getGame = gameId =>
            for {
              gameOpt  <- GameService.getGame(gameId)
              filtered <- ZIO.foreach(gameOpt)(filterSecretKnowledge)
            } yield filtered.map(_.asJson),
          getGameForUser = for {
            gameOpt  <- GameService.getGameForUser
            filtered <- ZIO.foreach(gameOpt)(filterSecretKnowledge)
          } yield filtered.map(_.asJson),
          getFriends = GameService.getFriends,
          getGameInvites = for {
            gameSeq  <- GameService.getGameInvites
            filtered <- ZIO.foreach(gameSeq)(filterSecretKnowledge)
          } yield filtered.map(_.asJson),
          getLoggedInUsers = GameService.getLoggedInUsers
        ),
        Mutations(
          newGame = newGameArgs =>
            for {
              game     <- GameService.newGame(satoshiPerPoint = newGameArgs.satoshiPerPoint)
              filtered <- filterSecretKnowledge(game)
            } yield filtered.asJson,
          joinRandomGame = for {
            game     <- GameService.joinRandomGame()
            filtered <- filterSecretKnowledge(game)
          } yield filtered.asJson,
          abandonGame = gameId => GameService.abandonGame(gameId),
          inviteToGame = userInviteArgs =>
            GameService.inviteToGame(userInviteArgs.userId, userInviteArgs.gameId),
          inviteByEmail = inviteByEmailArgs =>
            GameService.inviteByEmail(
              inviteByEmailArgs.name,
              inviteByEmailArgs.email,
              inviteByEmailArgs.gameId
            ),
          acceptGameInvitation = gameId =>
            for {
              game     <- GameService.acceptGameInvitation(gameId)
              filtered <- filterSecretKnowledge(game)
            } yield filtered.asJson,
          declineGameInvitation = gameId => GameService.declineGameInvitation(gameId),
          cancelUnacceptedInvitations = gameId => GameService.cancelUnacceptedInvitations(gameId),
          friend = userId => GameService.friend(userId),
          unfriend = userId => GameService.unfriend(userId),
          play = playArgs => GameService.play(playArgs.gameId, playArgs.gameEvent.asJson)
        ),
        Subscriptions(
          gameStream = gameStreamArgs =>
            GameService
              .gameStream(gameStreamArgs.gameId, gameStreamArgs.connectionId).map(_.asJson),
          userStream = connectionId => GameService.userStream(connectionId)
        )
      )
    ) @@
      maxFields(200) @@ // query analyzer that limit query fields
      maxDepth(30) @@ // query analyzer that limit query depth
      timeout(15.seconds) @@ // wrapper that fails slow queries
      printSlowQueries(3.seconds) @@ // wrapper that logs slow queries
      apolloTracing // wrapper for https://github.com/apollographql/apollo-tracing
  val schema =
    "schema {\n  query: Queries\n  mutation: Mutations\n  subscription: Subscriptions\n}\n\nscalar Json\n\nscalar Long\n\nenum UserEventType {\n  AbandonedGame\n  Connected\n  Disconnected\n  JoinedGame\n  Modified\n}\n\nenum UserStatus {\n  Idle\n  Invited\n  Offline\n  Playing\n}\n\ntype Mutations {\n  newGame(satoshiPerPoint: Int!): Json\n  joinRandomGame: Json\n  abandonGame(value: Int!): Boolean\n  inviteByEmail(name: String!, email: String!, gameId: Int!): Boolean\n  inviteToGame(userId: Int!, gameId: Int!): Boolean\n  acceptGameInvitation(value: Int!): Json\n  declineGameInvitation(value: Int!): Boolean\n  cancelUnacceptedInvitations(value: Int!): Boolean\n  friend(value: Int!): Boolean\n  unfriend(value: Int!): Boolean\n  play(gameId: Int!, gameEvent: Json!): Boolean\n}\n\ntype Queries {\n  getGame(value: Int!): Json\n  getGameForUser: Json\n  getFriends: [User!]\n  getGameInvites: [Json!]\n  getLoggedInUsers: [User!]\n}\n\ntype Subscriptions {\n  gameStream(gameId: Int!, connectionId: String!): Json!\n  userStream(value: String!): UserEvent!\n}\n\ntype User {\n  id: Int\n  email: String!\n  name: String!\n  userStatus: UserStatus!\n  created: Long!\n  lastUpdated: Long!\n  lastLoggedIn: Long\n  active: Boolean!\n  deleted: Boolean!\n}\n\ntype UserEvent {\n  user: User!\n  userEventType: UserEventType!\n}"

  //Generate client with
  // calibanGenClient /Volumes/Personal/projects/chuti/server/src/main/graphql/game.schema /Volumes/Personal/projects/chuti/web/src/main/scala/game/GameClient.scala
}
