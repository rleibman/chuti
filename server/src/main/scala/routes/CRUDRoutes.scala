package routes

import api.Auth.RequestWithSession
import api.Chuti.Environment
import api.ChutiSession
import chuti.Search
import dao.{CRUDOperations, RepositoryError, RepositoryIO, SessionProvider}
import zhttp.http.{Http, Request, Response}
import zio.*
import zio.clock.Clock
import zio.logging.Logging

import scala.util.matching.Regex

abstract class CRUDRoutes[E: Tag, PK: Tag, SEARCH <: Search : Tag] {

  type OpsService = Has[CRUDOperations[E, PK, SEARCH]]

  val url: String

  val pkRegex: Regex = "^[0-9]*$".r

  val defaultSoftDelete: Boolean = false

  /** Override this to add other authenticated (i.e. with session) routes
    *
    * @return
    */
  def other: Http[Environment & SessionProvider, Throwable, Request, Response] = Http.empty

  /** Override this to add routes that don't require a session
    *
    * @return
    */
  def unauthRoute: Http[Environment, Throwable, Request, Response] = Http.empty

  /** Override this to support children routes (e.g. /api/student/classroom)
    *
    * @param obj
    * A Task that will contain the "parent" object
    * @return
    */
  def authChildrenRoutes(
                      pk: PK,
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
  ] = {
    for {
      ops <- ZIO.service[CRUDOperations[E, PK, SEARCH]]
      ret <- ops.get(id)
    } yield ret
  }

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

  val authRoute: Http[Environment, Throwable, RequestWithSession[ChutiSession], Response] = ???
}

