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
import api.token.TokenHolder
import caliban.{CalibanError, ZHttpAdapter}
import caliban.interop.tapir.{HttpInterpreter, WebSocketInterpreter}
import chat.ChatService
import game.{GameApi, GameService}
import dao.{Repository, SessionContext}
import mail.Postman
import zio.http.*
import zio.*

object GameRoutes {

  import sttp.tapir.CodecFormat.*
  import sttp.tapir.json.circe.*

  lazy val route =
    Routes(
      Method.ANY / "api" / "game" -> handler { (req: RequestWithSession[ChutiSession]) =>
            for {
              interpreter <- GameService.interpreter
            } yield ZHttpAdapter
          .makeHttpService(HttpInterpreter(interpreter))
          .provideSomeLayer[GameService & Repository & Postman & TokenHolder & ChatService, SessionContext, Throwable](SessionContext.live(req.session.get))
          },
      Method.ANY / "api" / "game" / "ws" -> handler { (req: RequestWithSession[ChutiSession]) =>
            for {
              interpreter <- GameService.interpreter
            } yield ZHttpAdapter
              .makeWebSocketService(WebSocketInterpreter(interpreter, keepAliveTime = Option(5.minutes)))
              .provideSomeLayer[GameService & Repository & Postman & TokenHolder & ChatService, SessionContext, Throwable](SessionContext.live(req.session.get))
          },
      Method.ANY / "api" / "game" / "schema" -> handler {
        Response.text(GameApi.api.render)
      },
      Method.ANY / "api" / "game" / "graphiql" ->
        Handler.fromFileZIO(
          for {
            config <- ZIO.service[ConfigurationService]
            staticContentDir = config.config.getString(s"${config.configKey}.staticContentDir").nn
          } yield new java.io.File(s"$staticContentDir/graphiql.html")
        )
)

}
