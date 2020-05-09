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

import akka.http.scaladsl.server.{Directives, Route}
import api.token.TokenHolder
import api.{ChutiSession, ZIODirectives}
import chuti.Search
import dao.{CRUDOperations, DatabaseProvider, SessionProvider}
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import io.circe.parser.decode
import io.circe.{Decoder, Encoder, Json}
import zio._
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger

import scala.util.matching.Regex

/**
  * A crud route avoids boilerplate by definining a simple route for crud operations of an object
  *
  * @tparam E       The model object that is the base of the route
  * @tparam PK      The type of the object's primary key (used for gets/deletes)
  * @tparam SEARCH  A search object (extends Search)
  */
trait CRUDRoute[E, PK, SEARCH <: Search] {
  def crudRoute: CRUDRoute.Service[E, PK, SEARCH]
}

object CRUDRoute {
  abstract class Service[E, PK, SEARCH <: Search]
      extends Directives with ZIODirectives with ErrorAccumulatingCirceSupport {

    val url: String

    val ops: CRUDOperations[E, PK, SEARCH]

    val databaseProvider: DatabaseProvider.Service

    val pkRegex: Regex = "^[0-9]*$".r

    val defaultSoftDelete: Boolean = false

    /**
      * Override this to add other authenticated (i.e. with session) routes
      *
      * @param session
      * @return
      */
    def other(session: ChutiSession): Route = reject

    /**
      * Override this to add routes that don't require a session
      *
      * @return
      */
    def unauthRoute: Route = reject

    /**
      * Override this to support children routes (e.g. /api/student/classroom)
      *
      * @param obj A Task that will contain the "parent" object
      * @param session
      * @return
      */
    def childrenRoutes(
      pk:      PK,
      obj:     Task[Option[E]],
      session: ChutiSession
    ): Seq[Route] = Seq.empty

    /**
      * You need to override this method so that the architecture knows how to get a primary key from an object
      *
      * @param obj
      * @return
      */
    def getPK(obj: E): PK

    def fullLayer(
      session: ChutiSession
    ): ULayer[SessionProvider with DatabaseProvider with Logging with TokenHolder] =
      SessionProvider.layer(session) ++ ZLayer.succeed(databaseProvider) ++ Slf4jLogger.make(
        (_, b) => b
      ) ++ ZLayer.succeed(TokenHolder.live)

    def getOperation(
      id:      PK,
      session: ChutiSession
    ): Task[Option[E]] = {
      ops.get(id).provideLayer(fullLayer(session))
    }

    def deleteOperation(
      objTask: Task[Option[E]],
      session: ChutiSession
    ): Task[Boolean] =
      for {
        objOpt <- objTask
        deleted <- objOpt.fold(Task.succeed(false): Task[Boolean])(obj =>
          ops.delete(getPK(obj), defaultSoftDelete).provideLayer(fullLayer(session))
        )
      } yield deleted

    def upsertOperation(
      obj:     E,
      session: ChutiSession
    ): Task[E] =
      ops.upsert(obj).provideLayer(fullLayer(session))

    def countOperation(
      search:  Option[SEARCH],
      session: ChutiSession
    ): Task[Long] =
      ops.count(search).provideLayer(fullLayer(session))

    def searchOperation(
      search:  Option[SEARCH],
      session: ChutiSession
    ): Task[Seq[E]] =
      ops.search(search).provideLayer(fullLayer(session))

    /**
      * The main route. Note that it takes a pair of upickle ReaderWriter implicits that we need to be able to
      * marshall the objects in-to json.
      * In scala3 we may move these to parameters of the trait instead.
      *
      * @param session
      * @return
      */
    def route(
      session: ChutiSession
    )(
      implicit
      objDecoder:    Decoder[E],
      searchDecoder: Decoder[SEARCH],
      pkDecoder:     Decoder[PK],
      objEncoder:    Encoder[E],
      searchEncoder: Encoder[SEARCH],
      pkEncoder:     Encoder[PK]
    ): Route =
      pathPrefix(url) {
        other(session) ~
          pathEndOrSingleSlash {
            (post | put) {
              entity(as[E]) { obj =>
                complete(upsertOperation(obj, session))
              }
            }
          } ~
          path("search") {
            post {
              entity(as[Option[SEARCH]]) { search =>
                complete(searchOperation(search, session))
              }
            }
          } ~
          path("count") {
            post {
              entity(as[Option[SEARCH]]) { search =>
                complete(countOperation(search, session).map(a => Json.fromDouble(a.toDouble)))
              }
            }
          } ~
          pathPrefix(pkRegex) { id =>
            childrenRoutes(
              decode[PK](id).toOption.get,
              getOperation(decode[PK](id).toOption.get, session),
              session
            ).reduceOption(_ ~ _)
              .getOrElse(reject) ~
              pathEndOrSingleSlash {
                get {
                  complete(
                    getOperation(decode[PK](id).toOption.get, session)
                      .map(_.toSeq) //The #!@#!@# akka optionMarshaller gets in our way and converts an option to null/object before it ships it, so we convert it to seq
                  )
                } ~
                  delete {
                    complete(
                      deleteOperation(getOperation(decode[PK](id).toOption.get, session), session)
                    )
                  }
              }
          }
      }
  }

}
