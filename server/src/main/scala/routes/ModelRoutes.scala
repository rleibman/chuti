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

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directives, Route}
import api.{ChutiSession, HasActorSystem, LiveEnvironment}
import chat.ChatRoute
import chuti.Game
import dao.{DatabaseProvider, Repository}
import game.GameRoute
import io.circe.generic.auto._
import mail.Postman
import slick.basic.BasicBackend
import zio.UIO

/**
  * For convenience, this trait aggregates all of the model routes.
  */
trait ModelRoutes extends Directives {
  this: LiveEnvironment with HasActorSystem with Repository.Service with DatabaseProvider.Service =>

  private val gameRoute: GameRoute = new GameRoute
    with DatabaseProvider.Service with Repository.Service {
    override def db:          UIO[BasicBackend#DatabaseDef] = ModelRoutes.this.db
    override val actorSystem: ActorSystem = ModelRoutes.this.actorSystem
    override val gameOperations: Repository.GameOperations =
      ModelRoutes.this.gameOperations
    override val userOperations: Repository.UserOperations = ModelRoutes.this.userOperations
  }

  private val authRoute: AuthRoute = new AuthRoute
    with DatabaseProvider.Service with Repository.Service {
    override def db: UIO[BasicBackend#DatabaseDef] = ModelRoutes.this.db
    override val gameOperations: Repository.GameOperations =
      ModelRoutes.this.gameOperations
    override val userOperations:       Repository.UserOperations = ModelRoutes.this.userOperations
    implicit override val actorSystem: ActorSystem = ModelRoutes.this.actorSystem
    override val postman:              Postman.Service[Any] = ModelRoutes.this.postman
  }

  private val chatRoute: ChatRoute = new ChatRoute
    with DatabaseProvider.Service with Repository.Service {
    override def db: UIO[BasicBackend#DatabaseDef] = ModelRoutes.this.db
    override val gameOperations: Repository.GameOperations =
      ModelRoutes.this.gameOperations
    override val userOperations:       Repository.UserOperations = ModelRoutes.this.userOperations
    implicit override val actorSystem: ActorSystem = ModelRoutes.this.actorSystem
  }

  private val crudRoutes: List[CRUDRoute[_, _, _]] = List(
    authRoute
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
    gameRoute.route(session) ~
      authRoute.crudRoute.route(session) ~
      chatRoute.route(session)
//    sampleModelObjectRouteRoute.crudRoute.route(session)
  }
}
