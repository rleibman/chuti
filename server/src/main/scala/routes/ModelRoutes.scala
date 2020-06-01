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
import dao.{DatabaseProvider, Repository, SessionProvider}
import game.GameRoute
import game.UserConnectionRepo.UserConnectionRepo
import io.circe.generic.auto._
import mail.Postman.Postman
import zio.clock.Clock
import zio.console.Console
import zio.logging.Logging
import zio.{Layer, RIO, ZIO, ZLayer}

/**
  * For convenience, this trait aggregates all of the model routes.
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

  def unauthRoute
    : RIO[Repository with Postman with TokenHolder with Logging with DatabaseProvider, Route] =
    for {
      repo <- ZIO.access[Repository](_.get)
      auth <- {
        val opsLayer
          : Layer[Nothing, CRUDRoute.Service[User, UserId, PagedStringSearch]#OpsService] =
          ZLayer.succeed(repo.userOperations)
        authRoute.crudRoute.unauthRoute.provideSomeLayer[
          Repository with Postman with TokenHolder with Logging with DatabaseProvider
        ](opsLayer)
      }
    } yield auth

//    ZIO //Collect all
//      .foreach(
//        crudRoutes.map(r => r.crudRoute.unauthRoute)
//      )(identity).map(_.reduceOption(_ ~ _).getOrElse(reject))

  //it would be nice to be able to do this, but it's hard to define the readers and writers for marshalling
  //  def apiRoute(session: Any): Route =
  //    pathPrefix("api") {
  //    crudRoutes.map(_.crudRoute.route(session)).reduceOption(_ ~ _).getOrElse(reject)
  //  }

  def apiRoute: ZIO[
    Console with Clock with ChatService with SessionProvider with Logging with Config with DatabaseProvider with Repository with UserConnectionRepo with Postman with TokenHolder,
    Throwable,
    Route
  ] = {
    for {
      repo <- ZIO.service[Repository.Service]
      game <- gameRoute.route
      auth <- {
        val opsLayer
          : Layer[Nothing, CRUDRoute.Service[User, UserId, PagedStringSearch]#OpsService] =
          ZLayer.succeed(repo.userOperations)
        authRoute.crudRoute.route
          .provideSomeLayer[Repository with DatabaseProvider with SessionProvider with Logging](
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
