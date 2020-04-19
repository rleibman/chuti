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

package routes

import java.time.{Instant, LocalDateTime, ZoneOffset}

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.Materializer
import api.{ChutiSession, ZIODirectives}
import caliban.CalibanError.ExecutionError
import caliban.GraphQL.graphQL
import caliban.Value.IntValue
import caliban.interop.circe.AkkaHttpCirceAdapter
import caliban.schema._
import caliban.wrappers.ApolloTracing.apolloTracing
import caliban.wrappers.Wrappers.{maxDepth, maxFields, printSlowQueries, timeout}
import caliban.{AkkaHttpAdapter, GraphQL, RootResolver}
import chuti._
import routes.GameService.GameService
import zio.clock.Clock
import zio.console.Console
import zio.duration._
import zio.stream.ZStream
import zio.{Has, URIO, ZLayer}

import scala.util.Try

object GameService {
  type GameService = Has[Service]
  trait Service {}

  def make(juego: GameState): ZLayer[Any, Nothing, GameService] = ???
}

case class GameArgs()

object GameApi extends GenericSchema[GameService] {
  case class Queries(getGame:       Int => URIO[GameService, GameState])
  case class Mutations(jugada:      Event => URIO[GameService, GameState])
  case class Subscriptions(jugadas: ZStream[GameService, Nothing, Event])

  implicit val localDateTimeSchema: Typeclass[LocalDateTime] =
    Schema.longSchema.contramap(_.toInstant(ZoneOffset.UTC).toEpochMilli)
  implicit val localDateTimeArgBuilder: ArgBuilder[LocalDateTime] = {
    case value: IntValue =>
      Right(LocalDateTime.ofInstant(Instant.ofEpochMilli(value.toLong), ZoneOffset.UTC))
    case other => Left(ExecutionError(s"Can't build a LocalDateTime from input $other"))
  }
  implicit private val usuarioSchema = gen[User]
  implicit private val numeroTypeSchema = gen[Numero]
  implicit private val estadoSchema = gen[Estado]
  implicit private val triunfoSchema = gen[Triunfo]
  implicit private val fichaSchema = gen[Ficha]
  implicit private val filaSchema = gen[Fila]
  implicit private val jugadorSchema = gen[Jugador]
  implicit private val estadoDeJuegoSchema = gen[GameState]
  implicit private val jugadaSchema = gen[Event]

  val api: GraphQL[Console with Clock with GameService] =
    graphQL(
      RootResolver(
        Queries(
          getGame = juegoId => ???
        ),
        Mutations(
          jugada = jugada => ???
        ),
        Subscriptions(
          jugadas = ???
        )
      )
    ) @@
      maxFields(200) @@ // query analyzer that limit query fields
      maxDepth(30) @@ // query analyzer that limit query depth
      timeout(3.seconds) @@ // wrapper that fails slow queries
      printSlowQueries(500.millis) @@ // wrapper that logs slow queries
      apolloTracing // wrapper for https://github.com/apollographql/apollo-tracing
}

trait GameRoute extends ZIODirectives with Directives with AkkaHttpCirceAdapter {
  def juego:       GameState
  def actorSystem: ActorSystem

  implicit def materializer: Materializer = Materializer.matFromSystem(actorSystem)

  lazy private val interpreter = runtime.unsafeRun(
    GameService
      .make(juego)
      .memoize
      .use(layer => GameApi.api.interpreter.map(_.provideCustomLayer(layer)))
  )

  def route(session: ChutiSession): Route = reject

//  def route(session: ChutiSession): Route = {
//    val sys = actorSystem
//    import sys.dispatcher
//    path("api" / "graphql") {
//      adapter.makeHttpService(interpreter)
//    } ~ path("ws" / "graphql") {
//      adapter.makeWebSocketService(interpreter)
//    } ~ path("graphiql") {
//      getFromResource("graphiql.html")
//    }
//  }

}
