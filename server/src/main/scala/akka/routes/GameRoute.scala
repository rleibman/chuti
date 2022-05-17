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

package akka.routes

import akka.HasActorSystem
import akka.http.scaladsl.server.{Directives, Route}
import api.config
import caliban.AkkaHttpAdapter
import chat.ChatService.ChatService
import game.{GameApi, GameService}
import sttp.tapir.json.circe.*
import zio.*
import zio.clock.Clock
import zio.console.Console
import zio.duration.*

import scala.concurrent.ExecutionContextExecutor

case class GameArgs()

trait GameRoute extends Directives with HasActorSystem {

  implicit lazy val executionContext: ExecutionContextExecutor = actorSystem.dispatcher
  import GameService.*
  val staticContentDir: String =
    config.live.config.getString(s"${config.live.configKey}.staticContentDir")

  def route: URIO[Console & Clock & GameService & GameLayer & ChatService, Route] =
    for {
      runtime <- ZIO.runtime[Console & Clock & GameService & GameLayer & ChatService]
    } yield {
      implicit val r: Runtime[Console & Clock & GameService & GameLayer & ChatService] = runtime

      pathPrefix("game") {
        pathEndOrSingleSlash {
          AkkaHttpAdapter.makeHttpService(
            interpreter
          )
        } ~
          path("schema") {
            get(complete(GameApi.api.render))
          } ~
          path("ws") {
            AkkaHttpAdapter.makeWebSocketService(
              interpreter,
              skipValidation = false,
              keepAliveTime = Option(5.minutes)
            )
          } ~ path("graphiql") {
            getFromFile(s"$staticContentDir/graphiql.html")
          }
      }
    }

}
