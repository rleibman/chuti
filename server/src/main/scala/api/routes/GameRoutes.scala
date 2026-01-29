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

import api.{ChutiEnvironment, ChutiSession}
import auth.{AuthServer, Session}
import caliban.*
import chat.ChatService
import chuti.*
import dao.ZIORepository
import game.{GameApi, GameService}
import zio.http.*
import zio.json.*
import zio.{ZIO, ZLayer}

object GameRoutes extends AppRoutes[ChutiEnvironment, ChutiSession, GameError] {

  case class ChangePasswordRequest(
    currentPassword: String,
    newPassword:     String
  )

  object ChangePasswordRequest {

    given JsonDecoder[ChangePasswordRequest] = DeriveJsonDecoder.gen[ChangePasswordRequest]

  }

  override def api: ZIO[
    ChutiEnvironment,
    GameError,
    Routes[ChutiEnvironment & ChutiSession, GameError]
  ] =
    GameApi.api.interpreter.mapBoth(
      GameError(_),
      interpreter => {
        Routes(
          Method.ANY / "api" / "game" ->
            QuickAdapter(interpreter).handlers.api,
          Method.ANY / "api" / "game" / "ws" ->
            QuickAdapter(interpreter).handlers.webSocket
              .tapAllZIO(a => ZIO.logError(s"WebSocket error: ${a.toString}"), b => ZIO.logInfo("WebSocket closed")),
          Method.ANY / "api" / "game" / "graphiql" ->
            GraphiQLHandler.handler(apiPath = "/api/game", wsPath = None),
          Method.POST / "api" / "game" / "upload" ->
            QuickAdapter(interpreter).handlers.upload,
          Method.POST / "api" / "changePassword" ->
            Handler.fromFunctionZIO[Request] { request =>
              (for {
                session    <- ZIO.service[ChutiSession]
                authServer <- ZIO.service[AuthServer[User, UserId, ConnectionId]]
                user       <- ZIO.fromOption(session.user).orElseFail(GameError("Not logged in"))
                bodyStr    <- request.body.asString.mapError(e => GameError(e))
                changePasswordRequest <- ZIO
                  .fromEither(bodyStr.fromJson[ChangePasswordRequest]).mapError(e => GameError(e))
                // Verify current password by attempting to log in
                loginResult <- authServer
                  .login(user.email, changePasswordRequest.currentPassword, session.connectionId).mapError(e =>
                    GameError(e)
                  )
                _ <- ZIO.when(loginResult.isEmpty)(ZIO.fail(GameError("Current password is incorrect")))
                // Change the password
                _ <- authServer
                  .changePassword(user.id, changePasswordRequest.newPassword).mapError(e => GameError(e))
              } yield Response.ok).catchAll { error =>
                ZIO.succeed(Response.text(error.getMessage).status(Status.BadRequest))
              }
            }
        )
      }
    )

  override def unauth: ZIO[ChutiEnvironment, GameError, Routes[ChutiEnvironment, GameError]] =
    GameApi.api.interpreter
      .tapErrorCause(cause => ZIO.logCause(cause))
      .mapBoth(
        GameError(_),
        _ =>
          Routes(
            Method.GET / "unauth" / "chuti" / "schema" ->
              Handler.fromBody(Body.fromCharSequence(GameApi.api.render))
          )
      )

}
