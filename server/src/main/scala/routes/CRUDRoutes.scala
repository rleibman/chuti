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

import api.Chuti.ChutiEnvironment
import api.ChutiSession
import api.auth.Auth.RequestWithSession
import chuti.Search
import dao.{CRUDOperations, RepositoryError, RepositoryIO, SessionContext}
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.{Decoder, Encoder}
import util.*
import zhttp.http.*
import zio.*
import zio.logging.*

import scala.util.matching.Regex

abstract class CRUDRoutes[E: Tag: Encoder: Decoder, PK: Tag: Decoder, SEARCH <: Search: Tag: Decoder] {
  self =>

  type OpsService = CRUDOperations[E, PK, SEARCH]

  val url: String

  val pkRegex: Regex = "^[0-9]*$".r

  val defaultSoftDelete: Boolean = false

  /** Override this to add other authenticated (i.e. with session) routes
    *
    * @return
    */
  def authOther: Http[ChutiEnvironment & OpsService, Throwable, RequestWithSession[ChutiSession], Response] = Http.empty

  /** Override this to add routes that don't require a session
    *
    * @return
    */
  def unauthRoute: Http[ChutiEnvironment & OpsService, Throwable, Request, Response] = Http.empty

  /** Override this to support children routes (e.g. /api/student/classroom)
    *
    * @param obj
    *   A Task that will contain the "parent" object
    * @return
    */
  def authChildrenRoutes(
    pk:  PK,
    obj: Option[E]
  ): Http[ChutiEnvironment, Throwable, RequestWithSession[ChutiSession], Response] = Http.empty

  /** You need to override this method so that the architecture knows how to get a primary key from an object
    *
    * @param the
    *   primary key for object obj
    * @return
    */
  def getPK(obj: E): PK

  def getOperation(id: PK): ZIO[
    SessionContext & OpsService,
    RepositoryError,
    Option[E]
  ] =
    for {
      ops <- ZIO.service[CRUDOperations[E, PK, SEARCH]]
      ret <- ops.get(id)
    } yield ret

  def deleteOperation(
    objOpt: Option[E]
  ): ZIO[SessionContext & OpsService, Throwable, Boolean] =
    for {
      ops <- ZIO.service[CRUDOperations[E, PK, SEARCH]]
      ret <- objOpt.fold(ZIO.succeed(false): RepositoryIO[Boolean])(obj => ops.delete(getPK(obj), defaultSoftDelete))
    } yield ret

  def upsertOperation(obj: E): ZIO[
    SessionContext & OpsService,
    RepositoryError,
    E
  ] = {
    for {
      ops <- ZIO.service[CRUDOperations[E, PK, SEARCH]]
      ret <- ops.upsert(obj)
    } yield ret
  }

  def countOperation(search: Option[SEARCH]): ZIO[
    SessionContext & OpsService,
    RepositoryError,
    Long
  ] =
    for {
      ops <- ZIO.service[CRUDOperations[E, PK, SEARCH]]
      ret <- ops.count(search)
    } yield ret

  def searchOperation(search: Option[SEARCH]): ZIO[
    SessionContext & OpsService,
    RepositoryError,
    Seq[E]
  ] =
    for {
      ops <- ZIO.service[CRUDOperations[E, PK, SEARCH]]
      ret <- ops.search(search)
    } yield ret

  private val authCRUD: Http[ChutiEnvironment & OpsService, Throwable, RequestWithSession[ChutiSession], Response] =
    Http.collectHttp[RequestWithSession[ChutiSession]] {
      case req @ (Method.POST | Method.PUT) -> !! / "api" / self.url if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          (for {
            obj <- req.bodyAs[E]
            _   <- ZIO.logInfo(s"Upserting $url with $obj")
            ret <- upsertOperation(obj)
          } yield Response.json(ret.asJson.noSpaces))
            .provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
        )
      case req @ (Method.POST) -> !! / "api" / self.url / "search" if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          (for {
            search <- req.bodyAs[SEARCH]
            res    <- searchOperation(Some(search))
          } yield Response.json(res.asJson.noSpaces)).provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
        )
      case req @ Method.POST -> !! / s"api" / self.url / "count" if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          (for {
            search <- req.bodyAs[SEARCH]
            res    <- countOperation(Some(search))
          } yield Response.json(res.asJson.noSpaces)).provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
        )
      case req @ Method.GET -> !! / "api" / self.url / pk if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          (for {
            pk  <- ZIO.fromEither(parse(pk).flatMap(_.as[PK])).mapError(e => HttpError.BadRequest(e.getMessage.nn))
            res <- getOperation(pk)
          } yield Response.json(res.asJson.noSpaces)).provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
        )
      case req @ Method.DELETE -> !! / "api" / self.url / pk if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          (for {
            pk     <- ZIO.fromEither(parse(pk).flatMap(_.as[PK])).mapError(e => HttpError.BadRequest(e.getMessage.nn))
            getted <- getOperation(pk)
            res    <- deleteOperation(getted)
            _      <- ZIO.logInfo(s"Deleted ${pk.toString}")
          } yield Response.json(res.asJson.noSpaces)).provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
        )
    }

  val authRoute: Http[ChutiEnvironment & OpsService, Throwable, RequestWithSession[ChutiSession], Response] =
    authOther ++ authCRUD

}
