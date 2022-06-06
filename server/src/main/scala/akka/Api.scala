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

package akka

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.http.scaladsl.server.{Directives, Route, RouteConcatenation}
import akka.routes.{ModelRoutes, StaticHTMLRoute}
import api.config.Config
import api.token.TokenHolder
import chat.ChatService.ChatService
import dao.{Repository, SessionProvider}
import game.GameService.GameService
import mail.Postman.Postman
import zio.ZIO
import zio.clock.Clock
import zio.console.Console
import zio.logging.{Logging, log}

/** This class puts all of the live services together with all of the routes
  *
  * @author
  *   rleibman
  */
trait Api extends RouteConcatenation with Directives with StaticHTMLRoute with ModelRoutes with SessionUtils with HasActorSystem with ZIODirectives {
//  this: CoreActors & Core =>

  val routes: ZIO[
    Console & Clock & GameService & ChatService & Logging & Config & Repository & Postman & TokenHolder,
    Throwable,
    Route
  ] = ZIO
    .environment[
      Console & Clock & GameService & ChatService & Logging & Config & Repository & Postman & TokenHolder
    ].flatMap { r: Console with Clock with GameService with ChatService with Logging with Config with Repository with Postman with TokenHolder =>
      {
        for {
          _      <- log.info("Started routes")
          unauth <- unauthRoute
        } yield {
          DebuggingDirectives.logRequest("Request") {
//              randomTokenCsrfProtection(checkHeader) //Need to Add some stuff in the client if you want to make this work
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
                          apiRoute
                            .provideSomeLayer[
                              Console & Clock & GameService & ChatService & Logging & Config & Repository & Postman & TokenHolder
                            ](SessionProvider.layer(session)).provide(r)
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
