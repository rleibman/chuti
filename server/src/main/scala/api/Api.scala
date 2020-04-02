/*
 * Copyright 2020 Roberto Leibman
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package api

import akka.actor.ActorSystem
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.directives.DebuggingDirectives
import akka.http.scaladsl.server.{Directives, Route, RouteConcatenation}
import core.{Core, CoreActors}
import routes.{HTMLRoute, ModelRoutes}

/**
  * This class puts all of the live services together with all of the routes
  * @author rleibman
  */
trait Api
    extends RouteConcatenation with Directives with LiveEnvironment with HTMLRoute with ModelRoutes
    with SessionUtils with HasActorSystem with ZIODirectives {
  this: CoreActors with Core =>

  implicit private val _ = actorSystem.dispatcher

  val routes: Route = DebuggingDirectives.logRequest("Request") {
    extractLog { log =>
      unauthRoute ~ {
        ensureSession { sessionResult =>
          extractRequestContext { requestContext =>
            sessionResult.toOption match {
              case Some(session) =>
                apiRoute(session)
              case None =>
                log.info(
                  s"Unauthorized request of ${requestContext.unmatchedPath}, redirecting to login"
                )
                redirect("/loginForm", StatusCodes.Found)
            }
          }
        }
      } ~
        htmlRoute
    }
  }
}
