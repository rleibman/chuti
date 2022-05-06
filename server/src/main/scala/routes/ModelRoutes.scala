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
import api.HasActorSystem
import api.config.Config
import api.token.TokenHolder
import chat.ChatRoute
import chat.ChatService.ChatService
import chuti.{PagedStringSearch, User, UserId}
import dao.{CRUDOperations, Repository, SessionProvider}
import game.GameRoute
import game.GameService.{GameLayer, GameService}
import io.circe.generic.auto.*
import mail.Postman.Postman
import zio.clock.Clock
import zio.console.Console
import zio.logging.Logging
import zio.*

/** For convenience, this trait aggregates all of the model routes.
  */
trait ModelRoutes extends Directives {
  this: HasActorSystem =>

  private val gameRoute: GameRoute = new GameRoute {

    implicit override val actorSystem: ActorSystem = ModelRoutes.this.actorSystem

  }

  private val authRoute: AuthRoute = new AuthRoute {

    implicit override val actorSystem: ActorSystem = ModelRoutes.this.actorSystem

  }

  private val chatRoute: ChatRoute = new ChatRoute {

    implicit override val actorSystem: ActorSystem = ModelRoutes.this.actorSystem

  }

  def unauthRoute: RIO[Repository & Postman & TokenHolder & Logging & Clock, Route] =
    for {
      repo <- ZIO.access[Repository](_.get)
      auth <- {
        val opsLayer: ULayer[Has[CRUDOperations[User, UserId, PagedStringSearch]]] =
          ZLayer.succeed(repo.userOperations)
        authRoute.crudRoute.unauthRoute.provideSomeLayer[
          Repository & Postman & TokenHolder & Logging & Clock
        ](opsLayer)
      }
    } yield auth

//    ZIO //Collect all
//      .foreach(
//        crudRoutes.map(r => r.crudRoute.unauthRoute)
//      )(identity).map(_.reduceOption(_ ~ _).getOrElse(reject))

  // it would be nice to be able to do this, but it's hard to define the readers and writers for marshalling
  //  def apiRoute(session: Any): Route =
  //    pathPrefix("api") {
  //    crudRoutes.map(_.crudRoute.route(session)).reduceOption(_ ~ _).getOrElse(reject)
  //  }

  def apiRoute: ZIO[
    Console & Clock & ChatService & Repository & SessionProvider & Logging & Config & GameService & GameLayer,
    Throwable,
    Route
  ] = {
    for {
      repo <- ZIO.service[Repository.Service]
      game <- gameRoute.route
      auth <- {

        val opsLayer: ULayer[Has[CRUDOperations[chuti.User, chuti.UserId, chuti.PagedStringSearch]]] =
          ZLayer.succeed(repo.userOperations)
        authRoute.crudRoute.route
          .provideSomeLayer[Repository & SessionProvider & Logging & Clock](
            opsLayer
          )
      }
      chat <- chatRoute.route
    } yield {
      pathPrefix("api") {
        game ~ auth ~ chat
      }
    }
  }

}
