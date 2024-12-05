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

import api.{ChutiEnvironment, ChutiSession}
import chuti.{GameError, Search}
import dao.{CRUDOperations, Repository, RepositoryError, RepositoryIO}
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
  def authOther: Routes[ChutiEnvironment & ChutiSession, Throwable] = Routes.empty

  /** Override this to add routes that don't require a session
    *
    * @return
    */
  def unauthRoute: Routes[ChutiEnvironment, Throwable] = Routes.empty

  /** Override this to support children routes (e.g. /api/student/classroom)
    *
    * @param obj
    *   A Task that will contain the "parent" object
    * @return
    */
  def authChildrenRoutes(
    pk:  PK,
    obj: Option[E]
  ): Routes[ChutiEnvironment, Nothing] = Routes.empty

  /** You need to override this method so that the architecture knows how to get a primary key from an object
    *
    * @param the
    *   primary key for object obj
    * @return
    */
  def getPK(obj: E): PK

  def getOperation(id: PK): ZIO[
    ChutiSession & OpsService,
    RepositoryError,
    Option[E]
  ] =
    for {
      ops <- ZIO.service[CRUDOperations[E, PK, SEARCH]]
      ret <- ops.get(id)
    } yield ret

  def deleteOperation(
    objOpt: Option[E]
  ): ZIO[ChutiSession & OpsService, Throwable, Boolean] =
    for {
      ops <- ZIO.service[CRUDOperations[E, PK, SEARCH]]
      ret <- objOpt.fold(ZIO.succeed(false): RepositoryIO[Boolean])(obj => ops.delete(getPK(obj), defaultSoftDelete))
    } yield ret

  def upsertOperation(obj: E): ZIO[
    ChutiSession & OpsService,
    RepositoryError,
    E
  ] = {
    for {
      ops <- ZIO.service[CRUDOperations[E, PK, SEARCH]]
      ret <- ops.upsert(obj)
    } yield ret
  }

  def countOperation(search: Option[SEARCH]): ZIO[
    ChutiSession & OpsService,
    RepositoryError,
    Long
  ] =
    for {
      ops <- ZIO.service[CRUDOperations[E, PK, SEARCH]]
      ret <- ops.count(search)
    } yield ret

  def searchOperation(search: Option[SEARCH]): ZIO[
    ChutiSession & OpsService,
    RepositoryError,
    Seq[E]
  ] =
    for {
      ops <- ZIO.service[CRUDOperations[E, PK, SEARCH]]
      ret <- ops.search(search)
    } yield ret

  lazy private val authCRUD: Routes[ChutiEnvironment & OpsService & ChutiSession, Throwable] =
    Routes(
      Method.ANY / "api" / self.url -> handler { (req: Request) =>
        for {
          obj <- req.bodyAs[E]
          _   <- ZIO.logInfo(s"Upserting $url with $obj")
          ret <- upsertOperation(obj)
        } yield Response.json(ret.toJson)
      },
      Method.POST / "api" / self.url / "search" -> handler { (req: Request) =>
        for {
          search <- req.bodyAs[SEARCH]
          res    <- searchOperation(Some(search))
        } yield Response.json(res.toJson)
      },
      Method.POST / s"api" / self.url / "count" -> handler { (req: Request) =>
        for {
          search <- req.bodyAs[SEARCH]
          res    <- countOperation(Some(search))
        } yield Response.json(res.toJson)
      },
      Method.GET / "api" / self.url / trailing -> handler {
        (
          path: Path,
          req:  Request
        ) =>
          for {
            pk  <- ZIO.fromEither(path.toString.fromJson[PK]).mapError(GameError.apply)
            res <- getOperation(pk)
          } yield Response.json(res.toJson)
      },
      Method.DELETE / "api" / self.url / trailing -> handler {
        (
          path: Path,
          req:  Request
        ) =>
          for {
            pk     <- ZIO.fromEither(path.toString.fromJson[PK]).mapError(GameError.apply)
            getted <- getOperation(pk)
            res    <- deleteOperation(getted)
            _      <- ZIO.logInfo(s"Deleted ${pk.toString}")
          } yield Response.json(res.toJson)
      }
    )

  lazy val authRoute: Routes[ChutiSession & ChutiEnvironment & OpsService, Throwable] =
    authOther ++ authCRUD

}
