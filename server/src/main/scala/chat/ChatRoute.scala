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

package chat

import akka.http.scaladsl.server.{Directives, Route}
import api.{ChutiSession, Config, HasActorSystem}
import caliban.interop.circe.AkkaHttpCirceAdapter
import dao.{Repository, SessionProvider}
import zio.duration._

import scala.concurrent.ExecutionContextExecutor

trait ChatRoute
    extends Directives with AkkaHttpCirceAdapter with Repository with HasActorSystem with Config {
  implicit lazy val executionContext: ExecutionContextExecutor = actorSystem.dispatcher
  import ChatService._
  val staticContentDir: String = config.getString("chuti.staticContentDir")

  def route(session: ChutiSession): Route = pathPrefix("chat") {
    pathEndOrSingleSlash {
      implicit val runtime: zio.Runtime[zio.ZEnv] = zio.Runtime.default
      adapter.makeHttpService(
        interpreter.provideCustomLayer(SessionProvider.layer(session))
      )
    } ~
      path("schema") {
        get(complete(ChatApi.api.render))
      } ~
      path("ws") {
        adapter.makeWebSocketService(
          interpreter.provideCustomLayer(SessionProvider.layer(session)),
          skipValidation = false,
          keepAliveTime = Option(2500.milliseconds)
        )
      } ~ path("graphiql") {
      getFromFile(s"$staticContentDir/graphiql.html")
    }
  }
  val schema =
    "schema {\n  query: Queries\n  mutation: Mutations\n  subscription: Subscriptions\n}\n\nscalar Long\n\ntype ChatMessage {\n  user: User!\n  msg: String!\n  date: Long!\n}\n\ntype Mutations {\n  say(msg: String!): Boolean!\n}\n\ntype Queries {\n  \n}\n\ntype Subscriptions {\n  chatStream: ChatMessage!\n}\n\ntype User {\n  id: UserId\n  email: String!\n  name: String!\n  created: Long!\n  lastUpdated: Long!\n  lastLoggedIn: Long\n  wallet: Float!\n  deleted: Boolean!\n}\n\ntype UserId {\n  value: Int!\n}"
}
