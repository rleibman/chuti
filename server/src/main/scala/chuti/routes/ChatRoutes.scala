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

package chuti.routes

import caliban.{CalibanError, GraphQLInterpreter, GraphiQLHandler, QuickAdapter}
import chuti.GameError
import chuti.api.{ChutiEnvironment, ChutiSession}
import chuti.chat.{ChatApi, ChatService}
import chuti.db.ZIORepository
import zio.*
import zio.http.*

object ChatRoutes extends AppRoutes[ChutiEnvironment, ChutiSession, GameError] {

  override def api: ZIO[ChutiEnvironment, GameError, Routes[ChutiEnvironment & ChutiSession, GameError]] =
    ChatApi.api.interpreter.mapBoth(
      GameError(_),
      interpreter =>
        Routes(
          Method.ANY / "api" / "chat" ->
            QuickAdapter(interpreter).handlers.api,
          Method.ANY / "api" / "chat" / "ws" ->
            QuickAdapter(interpreter).handlers.webSocket
              .tapAllZIO(a => ZIO.logError(s"WebSocket error: ${a.toString}"), b => ZIO.logInfo("WebSocket closed")),
          Method.ANY / "api" / "chat" / "graphiql" ->
            GraphiQLHandler.handler(apiPath = "/api/chat", wsPath = None),
          Method.POST / "api" / "chat" / "upload" -> // TODO, I really don't know what this does.
            QuickAdapter(interpreter).handlers.upload
        )
    )

  /** These do not require a session
    */
  override def unauth: ZIO[ChutiEnvironment, GameError, Routes[ChutiEnvironment, GameError]] =
    ChatApi.api.interpreter.mapBoth(
      GameError(_),
      _ =>
        Routes(
          Method.GET / "unauth" / "chat" / "schema" ->
            Handler.fromBody(Body.fromCharSequence(ChatApi.api.render))
        )
    )

}
