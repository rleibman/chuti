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
import chuti.Search
import dao.{CRUDOperations, RepositoryError, RepositoryIO, SessionContext}
import util.*
import zio.http.*
import zio.*
import zio.logging.*
import zio.json.*

import scala.util.matching.Regex

abstract class CRUDRoutes[E: Tag: JsonEncoder: JsonDecoder, PK: Tag: JsonDecoder, SEARCH <: Search: Tag: JsonDecoder] {
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
  
  lazy private val authCRUD: Http[ChutiEnvironment & OpsService, Throwable, RequestWithSession[ChutiSession], Response] =
    Http.collectHttp[RequestWithSession[ChutiSession]] {
      case req @ (Method.POST | Method.PUT) -> !! / "api" / `url` if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          (for {
            obj <- req.bodyAs[E]
            _   <- ZIO.logInfo(s"Upserting $url with $obj")
            ret <- upsertOperation(obj)
          } yield Response.json(ret.toJson))
            .provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
        )
      case req @ (Method.POST) -> !! / "api" / `url` / "search" if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          (for {
            search <- req.bodyAs[SEARCH]
            res    <- searchOperation(Some(search))
          } yield Response.json(res.toJson)).provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
        )
      case req @ Method.POST -> !! / s"api" / `url` / "count" if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          (for {
            search <- req.bodyAs[SEARCH]
            res    <- countOperation(Some(search))
          } yield Response.json(res.toJson)).provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
        )
      case req @ Method.GET -> !! / "api" / `url` / pk if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          (for {
            pk  <- ZIO.fromEither(pk.fromJson[PK]).mapError(e => HttpError.BadRequest(e))
            res <- getOperation(pk)
          } yield Response.json(res.toJson)).provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
        )
      case req @ Method.DELETE -> !! / "api" / `url` / pk if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          (for {
            pk     <- ZIO.fromEither(pk.fromJson[PK]).mapError(e => HttpError.BadRequest(e))
            getted <- getOperation(pk)
            res    <- deleteOperation(getted)
            _      <- ZIO.logInfo(s"Deleted ${pk.toString}")
          } yield Response.json(res.toJson)).provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
        )
    }

  lazy val authRoute: Http[ChutiEnvironment & OpsService, Throwable, RequestWithSession[ChutiSession], Response] =
    authOther ++ authCRUD

}
