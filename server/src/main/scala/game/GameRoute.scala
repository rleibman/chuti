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

import akka.actor.ActorSystem
import akka.http.scaladsl.server.{Directives, Route}
import api.{ChutiSession, Config, HasActorSystem}
import caliban.interop.circe.AkkaHttpCirceAdapter
import chuti._
import dao.{DatabaseProvider, Repository, SessionProvider}
import game.GameService.GameLayer
import zio.{Layer, ZLayer}
import zio.duration._

import scala.concurrent.ExecutionContextExecutor

case class GameArgs()

trait GameRoute extends Directives with AkkaHttpCirceAdapter with HasActorSystem with Config {
  this: Repository.Service with DatabaseProvider.Service =>

  def repositoryLayer: Layer[Nothing, Repository] = ZLayer.succeed(this)
  def databaseProviderLayer: Layer[Nothing, DatabaseProvider] = ZLayer.succeed(this)

  def gameLayer(session: ChutiSession): Layer[Nothing, GameLayer] = SessionProvider.layer(session) ++ repositoryLayer ++ databaseProviderLayer

  implicit lazy val executionContext: ExecutionContextExecutor = actorSystem.dispatcher
  import GameService._
  val staticContentDir: String = config.getString("chuti.staticContentDir")

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
          keepAliveTime = Option(25.seconds)
        )
      } ~ path("graphiql") {
      getFromFile(s"$staticContentDir/graphiql.html")
    }
  }
}
