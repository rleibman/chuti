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

package api.routes

import api.Chuti.ChutiEnvironment
import api.ChutiSession
import api.auth.Auth.RequestWithSession
import api.config.ConfigurationService
import caliban.{CalibanError, ZHttpAdapter}
import caliban.interop.tapir.{HttpInterpreter, WebSocketInterpreter}
import chat.{ChatApi, ChatService}
import dao.{Repository, SessionContext}
import zio.http.*
import zio.*

object ChatRoutes {

  import sttp.tapir.CodecFormat.*
  import sttp.tapir.json.circe.*

  extension (req: Request) {

    def session: Option[ChutiSession] = ???

  }

  lazy val route =
    Routes(
      Method.ANY / "api" / "chat" -> handler { (req: Request) =>
        for {
          interpreter <- ChatService.interpreter
        } yield ZHttpAdapter
          .makeHttpService(HttpInterpreter(interpreter))
          .provideSomeLayer[ChatService & Repository, SessionContext, Throwable](SessionContext.live(req.session.get))
      }.flatten,
      Method.ANY / "api" / "chat" / "ws" -> handler { (req: Request) =>
        for {
          interpreter <- ChatService.interpreter
        } yield ZHttpAdapter
          .makeWebSocketService(WebSocketInterpreter(interpreter, keepAliveTime = Option(5.minutes)))
          .provideSomeLayer[ChatService & Repository, SessionContext, Throwable](SessionContext.live(req.session.get))
      }.flatten,
      Method.ANY / "api" / "chat" / "schema" -> handler {
        Response.text(ChatApi.api.render)
      },
      Method.ANY / "api" / "chat" / "graphiql" ->
        Handler.fromFileZIO(
          for {
            config <- ZIO.service[ConfigurationService]
            staticContentDir = config.config.getString(s"${config.configKey}.staticContentDir").nn
          } yield new java.io.File(s"$staticContentDir/graphiql.html")
        )
    )

}
