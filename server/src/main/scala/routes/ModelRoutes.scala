/*
 * Copyright 2020 Roberto Leibman
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package routes

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directives, Route}
import api.{ChutiSession, LiveEnvironment}
import chuti.GameState

/**
  * For convenience, this trait aggregates all of the model routes.
  */
trait ModelRoutes extends Directives {

  private val gameRoute = new GameRoute with LiveEnvironment {
    override val juego:       GameState = ???
    override val actorSystem: ActorSystem = ???
  }

  private val crudRoutes: List[CRUDRoute[_, _, _]] = List(
//    sampleModelObjectRouteRoute
  )

  def unauthRoute: Route =
    crudRoutes.map(_.crudRoute.unauthRoute).reduceOption(_ ~ _).getOrElse(reject)

  //TODO: it would be nice to be able to do this, but it's hard to define the readers and writers for marshalling
  //  def apiRoute(session: Any): Route =
  //    pathPrefix("api") {
  //    crudRoutes.map(_.crudRoute.route(session)).reduceOption(_ ~ _).getOrElse(reject)
  //  }

  def apiRoute(session: ChutiSession): Route = pathPrefix("api") {
    gameRoute.route(session)
//    sampleModelObjectRouteRoute.crudRoute.route(session)
    reject
  }
}
