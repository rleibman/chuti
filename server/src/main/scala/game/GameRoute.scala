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

import akka.http.scaladsl.server.{Directives, Route}
import api.{HasActorSystem, config}
import caliban.AkkaHttpAdapter
import chat.ChatService.ChatService
import zio._
import zio.clock.Clock
import zio.console.Console
import zio.duration._
import sttp.tapir.json.circe._

import scala.concurrent.ExecutionContextExecutor

case class GameArgs()

trait GameRoute extends Directives with HasActorSystem {
  implicit lazy val executionContext: ExecutionContextExecutor = actorSystem.dispatcher
  import GameService._
  val staticContentDir: String =
    config.live.config.getString(s"${config.live.configKey}.staticContentDir")

  def route: URIO[Console with Clock with GameService with GameLayer with ChatService, Route] =
    for {
      runtime <- ZIO.runtime[Console with Clock with GameService with GameLayer with ChatService]
    } yield {
      implicit val r: Runtime[Console with Clock with GameService with GameLayer with ChatService] = runtime

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
