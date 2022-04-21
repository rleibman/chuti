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
import api.{SessionUtils, ZIODirectives}
import api.token.TokenHolder
import chuti.Search
import dao.*
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import io.circe.parser.decode
import io.circe.{Decoder, Encoder, Json}
import mail.Postman.Postman
import zio.*
import zio.logging.Logging
import zioslick.RepositoryException

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
  abstract class Service[E: Tag, PK: Tag, SEARCH <: Search: Tag]
      extends Directives with ZIODirectives with ErrorAccumulatingCirceSupport {

    type OpsService = Has[CRUDOperations[E, PK, SEARCH]]

    val url: String

    val pkRegex: Regex = "^[0-9]*$".r

    val defaultSoftDelete: Boolean = false

    /**
      * Override this to add other authenticated (i.e. with session) routes
      *
      * @return
      */
    def other: RIO[SessionProvider & Logging & OpsService, Route] =
      ZIO.succeed(reject)

    /**
      * Override this to add routes that don't require a session
      *
      * @return
      */
    def unauthRoute: URIO[Postman & TokenHolder & Logging & OpsService, Route] =
      ZIO.succeed(reject)

    /**
      * Override this to support children routes (e.g. /api/student/classroom)
      *
      * @param obj A Task that will contain the "parent" object
      * @return
      */
    def childrenRoutes(
      pk:  PK,
      obj: Option[E]
    ): RIO[SessionProvider & Logging & OpsService, Seq[Route]] =
      ZIO.succeed(Seq.empty)

    /**
      * You need to override this method so that the architecture knows how to get a primary key from an object
      *
      * @param obj
      * @return
      */
    def getPK(obj: E): PK

//    def fullLayer(
//      session: ChutiSession
//    ): ULayer[SessionProvider  & Logging with TokenHolder] =
//      SessionProvider.layer(session) ++ ZLayer.succeed(databaseProvider) ++ Slf4jLogger.make(
//        (_, b) => b
//      ) ++ ZLayer.succeed(TokenHolder.live)

    def getOperation(id: PK): ZIO[
      SessionProvider & Logging & OpsService,
      RepositoryException,
      Option[E]
    ] = {
      for {
        ops <- ZIO.service[CRUDOperations[E, PK, SEARCH]]
        ret <- ops.get(id)
      } yield ret
    }

    def deleteOperation(
      objOpt: Option[E]
    ): ZIO[SessionProvider & Logging & OpsService, Throwable, Boolean] =
      for {
        ops <- ZIO.service[CRUDOperations[E, PK, SEARCH]]
        ret <- objOpt.fold(ZIO.succeed(false): RepositoryIO[Boolean])(obj =>
          ops.delete(getPK(obj), defaultSoftDelete)
        )
      } yield ret

    def upsertOperation(obj: E): ZIO[
      SessionProvider & Logging & OpsService,
      RepositoryException,
      E
    ] = {
      for {
        ops <- ZIO.service[CRUDOperations[E, PK, SEARCH]]
        ret <- ops.upsert(obj)
      } yield ret
    }

    def countOperation(search: Option[SEARCH]): ZIO[
      SessionProvider & Logging & OpsService,
      RepositoryException,
      Long
    ] =
      for {
        ops <- ZIO.service[CRUDOperations[E, PK, SEARCH]]
        ret <- ops.count(search)
      } yield ret

    def searchOperation(search: Option[SEARCH]): ZIO[
      SessionProvider & Logging & OpsService,
      RepositoryException,
      Seq[E]
    ] =
      for {
        ops <- ZIO.service[CRUDOperations[E, PK, SEARCH]]
        ret <- ops.search(search)
      } yield ret

    /**
      * The main route. Note that it takes a pair of upickle ReaderWriter implicits that we need to be able to
      * marshall the objects in-to json.
      * In scala3 we may move these to parameters of the trait instead.
      *
      * @return
      */
    def route(
      implicit
      objDecoder:    Decoder[E],
      searchDecoder: Decoder[SEARCH],
      pkDecoder:     Decoder[PK],
      objEncoder:    Encoder[E],
      searchEncoder: Encoder[SEARCH],
      pkEncoder:     Encoder[PK]
    ): ZIO[
      Repository & SessionProvider & Logging & OpsService,
      Throwable,
      Route
    ] =
      for {
        other <- other
        runtime <-
          ZIO
            .environment[
              Repository & SessionProvider & Logging & OpsService
            ]
      } yield {
        pathPrefix(url) {
          other ~
            pathEndOrSingleSlash {
              (post | put) {
                entity(as[E])(obj => complete(upsertOperation(obj).provide(runtime)))
              }
            } ~
            path("search") {
              post {
                entity(as[Option[SEARCH]]) { search =>
                  complete(searchOperation(search).provide(runtime))
                }
              }
            } ~
            path("count") {
              post {
                entity(as[Option[SEARCH]]) { search =>
                  complete(
                    countOperation(search).map(a => Json.fromDouble(a.toDouble)).provide(runtime)
                  )
                }
              }
            } ~
            pathPrefix(pkRegex) { id =>
              zioRoute(
                (for {
                  gotten <- getOperation(decode[PK](id).toOption.get)
                  routes <- childrenRoutes(decode[PK](id).toOption.get, gotten)
                } yield routes.reduceOption(_ ~ _).getOrElse(reject)).provide(runtime)
              ) ~
                pathEndOrSingleSlash {
                  get {
                    complete(
                      getOperation(decode[PK](id).toOption.get)
                        .provide(runtime)
                        .map(_.toSeq) //The #!@#!@# akka optionMarshaller gets in our way and converts an option to null/object before it ships it, so we convert it to seq
                    )
                  } ~
                    delete {
                      complete(
                        (for {
                          getted  <- getOperation(decode[PK](id).toOption.get)
                          deleted <- deleteOperation(getted)
                        } yield deleted).provide(runtime)
                      )
                    }
                }
            }
        }
      }
  }

}
