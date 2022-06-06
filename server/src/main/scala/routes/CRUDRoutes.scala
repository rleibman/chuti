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

package routes

import api.Auth.RequestWithSession
import api.Chuti.Environment
import api.ChutiSession
import chuti.Search
import dao.{CRUDOperations, RepositoryError, RepositoryIO, SessionProvider}
import io.circe.{Decoder, Encoder}
import zhttp.http.*
import zio.*
import zio.clock.Clock
import zio.logging.Logging
import io.circe.syntax.*
import io.circe.parser.*
import zio.magic.*
import util.*

import scala.util.matching.Regex

abstract class CRUDRoutes[E: Tag: Encoder: Decoder, PK: Tag: Decoder, SEARCH <: Search: Tag: Decoder] {
  self =>

  type OpsService = Has[CRUDOperations[E, PK, SEARCH]]

  val url: String

  val pkRegex: Regex = "^[0-9]*$".r

  val defaultSoftDelete: Boolean = false

  /** Override this to add other authenticated (i.e. with session) routes
    *
    * @return
    */
  def authOther: Http[Environment & OpsService, Throwable, RequestWithSession[ChutiSession], Response] = Http.empty

  /** Override this to add routes that don't require a session
    *
    * @return
    */
  def unauthRoute: Http[Environment & OpsService, Throwable, Request, Response] = Http.empty

  /** Override this to support children routes (e.g. /api/student/classroom)
    *
    * @param obj
    *   A Task that will contain the "parent" object
    * @return
    */
  def authChildrenRoutes(
    pk:  PK,
    obj: Option[E]
  ): Http[Environment, Throwable, RequestWithSession[ChutiSession], Response] = Http.empty

  /** You need to override this method so that the architecture knows how to get a primary key from an object
    *
    * @param obj
    * @return
    */
  def getPK(obj: E): PK

  def getOperation(id: PK): ZIO[
    SessionProvider & Logging & Clock & OpsService,
    RepositoryError,
    Option[E]
  ] =
    for {
      ops <- ZIO.service[CRUDOperations[E, PK, SEARCH]]
      ret <- ops.get(id)
    } yield ret

  def deleteOperation(
    objOpt: Option[E]
  ): ZIO[SessionProvider & Logging & Clock & OpsService, Throwable, Boolean] =
    for {
      ops <- ZIO.service[CRUDOperations[E, PK, SEARCH]]
      ret <- objOpt.fold(ZIO.succeed(false): RepositoryIO[Boolean])(obj => ops.delete(getPK(obj), defaultSoftDelete))
    } yield ret

  def upsertOperation(obj: E): ZIO[
    SessionProvider & Logging & Clock & OpsService,
    RepositoryError,
    E
  ] = {
    for {
      ops <- ZIO.service[CRUDOperations[E, PK, SEARCH]]
      ret <- ops.upsert(obj)
    } yield ret
  }

  def countOperation(search: Option[SEARCH]): ZIO[
    SessionProvider & Logging & Clock & OpsService,
    RepositoryError,
    Long
  ] =
    for {
      ops <- ZIO.service[CRUDOperations[E, PK, SEARCH]]
      ret <- ops.count(search)
    } yield ret

  def searchOperation(search: Option[SEARCH]): ZIO[
    SessionProvider & Logging & Clock & OpsService,
    RepositoryError,
    Seq[E]
  ] =
    for {
      ops <- ZIO.service[CRUDOperations[E, PK, SEARCH]]
      ret <- ops.search(search)
    } yield ret

  val authRoute: Http[Environment & OpsService, Throwable, RequestWithSession[ChutiSession], Response] =
    Http.collectZIO[RequestWithSession[ChutiSession]] { req =>
      (req match {
        case (Method.POST | Method.PUT) -> !! / "api" / self.url =>
          for {
            obj <- req.bodyAs[E]
            _   <- Logging.info(s"Upserting $url with $obj")
            ret <- upsertOperation(obj)
          } yield Response.json(ret.asJson.noSpaces)
        case (Method.POST) -> !! / "api" / self.url / "search" =>
          for {
            search <- req.bodyAs[SEARCH]
            res    <- searchOperation(Some(search))
          } yield Response.json(res.asJson.noSpaces)
        case Method.POST -> !! / s"api" / self.url / "count" =>
          for {
            search <- req.bodyAs[SEARCH]
            res    <- countOperation(Some(search))
          } yield Response.json(res.asJson.noSpaces)
        case Method.GET -> !! / "api" / self.url / pk =>
          for {
            pk  <- ZIO.fromEither(parse(pk).flatMap(_.as[PK])).mapError(e => HttpError.BadRequest(e.getMessage))
            res <- getOperation(pk)
          } yield Response.json(res.asJson.noSpaces)
        case Method.DELETE -> !! / "api" / self.url / pk =>
          for {
            pk     <- ZIO.fromEither(parse(pk).flatMap(_.as[PK])).mapError(e => HttpError.BadRequest(e.getMessage))
            getted <- getOperation(pk)
            res    <- deleteOperation(getted)
            _      <- Logging.info(s"Deleted ${pk.toString}")
          } yield Response.json(res.asJson.noSpaces)
      }).injectSome[Environment & OpsService](SessionProvider.layer(req.session))
    }

}
