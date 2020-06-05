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

package chat

import akka.http.scaladsl.server.{Directives, Route}
import api.HasActorSystem
import api.config.Config
import caliban.interop.circe.AkkaHttpCirceAdapter
import dao.{DatabaseProvider, Repository, SessionProvider}
import zio.clock.Clock
import zio.console.Console
import zio.duration._
import zio.logging.Logging
import zio.{RIO, ZIO}

import scala.concurrent.ExecutionContextExecutor

trait ChatRoute extends Directives with AkkaHttpCirceAdapter with HasActorSystem {
  implicit lazy val executionContext: ExecutionContextExecutor = actorSystem.dispatcher
  import ChatService._

  def route: RIO[
    Console with Clock with ChatService with Repository with DatabaseProvider with SessionProvider with Logging with Config,
    Route
  ] = {
    for {
      config <- ZIO.service[Config.Service]
      runtime <- ZIO
        .runtime[Console with Clock with ChatService with Repository with DatabaseProvider with SessionProvider with Logging with Config]
    } yield {
      val staticContentDir =
        config.config.getString(s"${config.configKey}.staticContentDir")

      implicit val r = runtime

      pathPrefix("chat") {
        pathEndOrSingleSlash {
          adapter.makeHttpService(
            interpreter
          )
        } ~
          path("schema") {
            get(complete(ChatApi.api.render))
          } ~
          path("ws") {
            adapter.makeWebSocketService(
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
}
