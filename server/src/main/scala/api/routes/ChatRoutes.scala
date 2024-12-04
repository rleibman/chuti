/*
 * Copyright (c) 2024 Roberto Leibman
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
