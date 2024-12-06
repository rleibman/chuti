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

import api.token.TokenHolder
import api.{ChutiEnvironment, ChutiSession}
import caliban.interop.tapir.{HttpInterpreter, WebSocketInterpreter}
import caliban.{CalibanError, GraphiQLHandler, QuickAdapter}
import chat.{ChatApi, ChatService}
import dao.Repository
import mail.Postman
import zio.*
import zio.http.*

object ChatRoutes {

  lazy val authRoute: IO[CalibanError.ValidationError, Routes[ChatService & ChutiEnvironment & ChutiSession, Nothing]] =
    for {
      interpreter <- ChatApi.api.interpreter
    } yield {
      Routes(
        Method.ANY / "api" / "chat" ->
          QuickAdapter(interpreter).handlers.api,
        Method.ANY / "api" / "chat" / "ws" ->
          QuickAdapter(interpreter).handlers.webSocket,
        Method.ANY / "api" / "chat" / "graphiql" ->
          GraphiQLHandler.handler(apiPath = "/api/chat"),
        Method.GET / "api" / "chat" / "schema" ->
          Handler.fromBody(Body.fromCharSequence(ChatApi.api.render)),
        Method.POST / "api" / "chat" / "upload" ->
          QuickAdapter(interpreter).handlers.upload
      )
    }

}
