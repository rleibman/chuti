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
import chat.ChatService
import dao.Repository
import game.{GameApi, GameService}
import mail.Postman
import zio.*
import zio.http.*

object GameRoutes {

  lazy private val interpreter = GameApi.api.interpreter

  lazy val authRoute
    : IO[CalibanError.ValidationError, Routes[GameService & ChatService & ChutiEnvironment & ChutiSession, Nothing]] =
    for {
      interpreter <- interpreter
    } yield {
      Routes(
        Method.ANY / "api" / "game" ->
          QuickAdapter(interpreter).handlers.api,
        Method.ANY / "api" / "game" / "graphiql" ->
          GraphiQLHandler.handler(apiPath = "/api/game"),
        Method.GET / "api" / "game" / "schema" ->
          Handler.fromBody(Body.fromCharSequence(GameApi.api.render)),
        Method.POST / "api" / "game" / "upload" ->
          QuickAdapter(interpreter).handlers.upload
      )
    }

}
