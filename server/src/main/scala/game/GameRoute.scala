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

package game

import akka.http.scaladsl.server.{Directives, Route}
import api.token.TokenHolder
import api.{ChutiSession, HasActorSystem, config}
import caliban.interop.circe.AkkaHttpCirceAdapter
import dao.{DatabaseProvider, Repository, SessionProvider}
import game.GameService.GameLayer
import mail.CourierPostman
import mail.Postman.Postman
import zio.duration._
import zio.logging.slf4j.Slf4jLogger
import zio.{Layer, ULayer, ZLayer}

import scala.concurrent.ExecutionContextExecutor

case class GameArgs()

object GameRoute {
  private def postman: ULayer[Postman] = ZLayer.succeed(CourierPostman.live(config.live))
}

trait GameRoute extends Directives with AkkaHttpCirceAdapter with HasActorSystem {
  this: Repository.Service with DatabaseProvider.Service =>

  def repositoryLayer:       ULayer[Repository] = ZLayer.succeed(this)
  def databaseProviderLayer: ULayer[DatabaseProvider] = ZLayer.succeed(this)
  def postmanLayer:          ULayer[Postman] = GameRoute.postman

  def gameLayer(session: ChutiSession): Layer[Nothing, GameLayer] =
    zio.console.Console.live ++
      SessionProvider.layer(session) ++
      databaseProviderLayer ++
      repositoryLayer ++
      postmanLayer ++
      Slf4jLogger.make((_, b) => b) ++
      ZLayer.succeed(TokenHolder.live) ++
      ZLayer.succeed(LoggedInUserRepo.live)

  implicit lazy val executionContext: ExecutionContextExecutor = actorSystem.dispatcher
  import GameService._
  val staticContentDir: String =
    config.live.config.getString(s"${config.live.configKey}.staticContentDir")

  def route(session: ChutiSession): Route = pathPrefix("game") {
    pathEndOrSingleSlash {
      implicit val runtime: zio.Runtime[zio.ZEnv] = zio.Runtime.default
      adapter.makeHttpService(
        interpreter.provideCustomLayer(gameLayer(session))
      )
    } ~
      path("schema") {
        get(complete(GameApi.api.render))
      } ~
      path("ws") {
        adapter.makeWebSocketService(
          interpreter.provideCustomLayer(gameLayer(session)),
          skipValidation = false,
          keepAliveTime = Option(58.seconds)
        )
      } ~ path("graphiql") {
      getFromFile(s"$staticContentDir/graphiql.html")
    }
  }
}
