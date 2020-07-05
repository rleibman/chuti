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

package api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.http.scaladsl.server.{Directives, Route, RouteConcatenation}
import api.config.Config
import api.token.TokenHolder
import chat.ChatService.ChatService
import core.{Core, CoreActors}
import dao.{Repository, SessionProvider}
import game.GameService.GameService
import game.UserConnectionRepo.UserConnectionRepo
import mail.Postman.Postman
import routes.{ModelRoutes, StaticHTMLRoute}
import zio.ZIO
import zio.clock.Clock
import zio.console.Console
import zio.logging.{Logging, log}

/**
  * This class puts all of the live services together with all of the routes
  * @author rleibman
  */
trait Api
    extends RouteConcatenation with Directives with StaticHTMLRoute with ModelRoutes
    with SessionUtils with HasActorSystem with ZIODirectives {
  this: CoreActors with Core =>

  implicit private val _ = actorSystem.dispatcher

  val routes: ZIO[
    Console with Clock with GameService with ChatService with Logging with Config with Repository with UserConnectionRepo with Postman with TokenHolder,
    Throwable,
    Route
  ] = ZIO
    .environment[
      Console with Clock with GameService with ChatService with Logging with Config with Repository with UserConnectionRepo with Postman with TokenHolder
    ].flatMap {
      r: Console
        with Clock with GameService with ChatService with Logging with Config with Repository
        with UserConnectionRepo with Postman with TokenHolder =>
        {
          for {
            _      <- log.info("Started routes")
            unauth <- unauthRoute
          } yield {
            DebuggingDirectives.logRequest("Request") {
              path("helloworld") {
                get {
                  complete {
                    "Hello World"
                  }
                }
              } ~
                extractLog { log =>
                  unauth ~ {
                    ensureSession { sessionResult =>
                      extractRequestContext { requestContext =>
                        sessionResult.toOption match {
                          case Some(session) =>
                            val me: ZIO[
                              Console with Clock with GameService with ChatService with Logging with Config with Repository with UserConnectionRepo with Postman with TokenHolder,
                              Throwable,
                              Route
                            ] = apiRoute
                              .provideSomeLayer[
                                Console with Clock with GameService with ChatService with Logging with Config with Repository with UserConnectionRepo with Postman with TokenHolder
                              ](SessionProvider.layer(session))
                            val meme = me.provide(r)

                            meme
                          case None =>
                            log.info(
                              s"Unauthorized ${requestContext.request.method.value} request of ${requestContext.unmatchedPath}, redirecting to login"
                            )
                            redirect("/loginForm", StatusCodes.SeeOther)
                        }
                      }
                    }
                  } ~
                    htmlRoute
                }
            }
          }
        }
    }
}
