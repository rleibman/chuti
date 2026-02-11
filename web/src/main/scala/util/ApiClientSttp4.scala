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

package util

import caliban.client.CalibanClientError
import chuti.ConnectionId
import japgolly.scalajs.react.{AsyncCallback, Callback}
import org.scalajs.dom.window
import sttp.capabilities
import sttp.client4.*
import sttp.client4.fetch.FetchBackend
import sttp.model.*
import zio.json.*

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

object ApiClientSttp4 {

  val connectionId: ConnectionId = ConnectionId.random

  private def encodeConnectionId[ConnectionId: JsonEncoder](connectionId: ConnectionId): String = {
    java.util.Base64.getEncoder.encodeToString(connectionId.toJson.getBytes)
  }
  // All of this goes away once caliban supports sttp4

  import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

  private def asyncJwtToken: AsyncCallback[Option[String]] =
    AsyncCallback.pure(Option(window.localStorage.getItem("jwtToken")))

  val backend: WebSocketBackend[Future] = FetchBackend()

  case class ZioJsonException(error: String) extends Exception

  object JsonInput {

    def sanitize[T: IsOption]: String => String = { s =>
      if (implicitly[IsOption[T]].isOption && s.trim.isEmpty) "null" else s
    }

  }

  def asJson[B: {JsonDecoder, IsOption}]: ResponseAs[Either[ResponseException[String], B]] =
    asString.mapWithMetadata(ResponseAs.deserializeRightWithError(deserializeJson))

  def deserializeJson[B: {JsonDecoder, IsOption}]: String => Either[Exception, B] =
    JsonInput.sanitize[B].andThen(_.fromJson[B].left.map(ZioJsonException(_)))

  def withAuthOptional[A](
    request: Request[Either[CalibanClientError, A]]
  ): AsyncCallback[Either[CalibanClientError, A]] = {
    def doCall(tokOpt: Option[String]): AsyncCallback[Response[Either[CalibanClientError, A]]] = {
      AsyncCallback.fromFuture {
        val reqWithConnectionId = request.header("X-Connection-Id", encodeConnectionId(connectionId))
        val finalReq = tokOpt.fold(reqWithConnectionId)(tok => reqWithConnectionId.auth.bearer(tok))
        finalReq
          .readTimeout(2.minutes)
          .send(backend)
      }
    }

    for {
      tokOpt   <- asyncJwtToken
      response <- doCall(tokOpt)
    } yield response.body
  }

  def withAuth[A](
    request: Request[Either[CalibanClientError, A]],
    onAuthError: String => AsyncCallback[Any] = msg =>
      AsyncCallback.pure {
        window.console.log(msg)
        window.location.reload()
      }
  ): AsyncCallback[Either[CalibanClientError, A]] = {
    def doCall(tok: String): AsyncCallback[Response[Either[CalibanClientError, A]]] = {
      AsyncCallback.fromFuture {
        request
          .header("X-Connection-Id", encodeConnectionId(connectionId))
          .auth
          .bearer(tok)
          .readTimeout(2.minutes)
          .send(backend)
      }
    }

    for {
      tokOpt      <- asyncJwtToken
      responseOpt <- AsyncCallback.traverseOption(tokOpt)(doCall)
      withRefresh <- responseOpt match {
        case Some(response)
            if response.code == StatusCode.Unauthorized && response.body.left.exists(
              _.getMessage.contains("token_expired")
            ) =>
          Callback.log("Refreshing token").asAsyncCallback >>
            // Call refresh endpoint
            (for {
              refreshResponse <-
                AsyncCallback.fromFuture(
                  basicRequest
                    .get(uri"/refresh")
                    .response(asString)
                    .readTimeout(2.minutes)
                    .send(backend)
                )
              retried <- refreshResponse.code match {
                case c if c.isSuccess =>
                  refreshResponse.header(HeaderNames.Authorization) match {
                    case Some(authHeader) =>
                      val newToken = authHeader.stripPrefix("Bearer ")
                      window.localStorage.setItem("jwtToken", newToken)
                      doCall(newToken).map(_.body)
                    case None =>
                      val msg = "Server said refresh was ok, but didn't return a token"
                      onAuthError(msg) >> AsyncCallback.pure(
                        Left(CalibanClientError.CommunicationError(msg): CalibanClientError)
                      )
                  }
                case c =>
                  val msg = s"Trying to get Refresh token got $c"
                  onAuthError(msg) >> AsyncCallback.pure(Left(CalibanClientError.CommunicationError(msg)))
              }
            } yield retried)
        case None =>
          val msg = "No token set, please log in"
          onAuthError(msg) >> AsyncCallback.pure(Left(CalibanClientError.CommunicationError(msg)))
        case Some(other) if !other.code.isSuccess =>
          AsyncCallback.pure {
            // Clear the token
            window.localStorage.removeItem("jwtToken")
          } >>
            AsyncCallback.pure(other.body) // Success, or some other "normal" error, pass it along
        case Some(other) =>
          AsyncCallback.pure(other.body) // Success, or some other "normal" error, pass it along
      }
    } yield withRefresh
  }

}
