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

import zio.http.*
import zio.json.*
import zio.json.ast.Json
import zio.{IO, ZIO}
import chuti.GameError

trait AppRoutes[-R, -SessionType, +E] {

  final protected def seeOther(location: String): IO[GameError, Response] =
    for {
      url <- ZIO.fromEither(URL.decode(location)).mapError(e => GameError(e))
    } yield Response(Status.SeeOther, Headers(Header.Location(url)))

  final protected def json(value: Json): Response = Response.json(value.toString)

  final protected def json[A: JsonEncoder](value: A): Response = Response.json(value.toJson)

  /** These routes represent the api, the are intended to be used thorough ajax-type calls they require a session
    */
  def api: ZIO[R, E, Routes[R & SessionType, E]] = ZIO.succeed(Routes.empty)

  def unauth: ZIO[R, E, Routes[R, E]] = ZIO.succeed(Routes.empty)

}
