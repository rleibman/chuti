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

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives
import api.ZIODirectives
import caliban.GraphQL.graphQL
import caliban.interop.circe.AkkaHttpCirceAdapter
import caliban.schema.GenericSchema
import caliban.wrappers.ApolloTracing.apolloTracing
import caliban.wrappers.Wrappers.{maxDepth, maxFields, printSlowQueries, timeout}
import caliban.{GraphQL, GraphQLInterpreter, RootResolver}
import chat.ChatService.ChatService
import chat.SessionProvider.SessionProvider
import zio._
import zio.clock.Clock
import zio.console.Console
import zio.duration._
import zio.stream.ZStream

import scala.concurrent.ExecutionContextExecutor

case class User(name: String)

object SessionProvider {

  type SessionProvider = Has[SessionProvider.Session]
  trait Session {
    def user: User
  }
  def live(u: User): Session = new Session {
    val user: User = u
  }
}

case class ChatMessage(user: User, msg: String, date: Long)

object ChatService {
  import SessionProvider._
  type ChatService = Has[Service]
  trait Service {
    def say(msg: String): ZIO[SessionProvider, Nothing, ChatMessage]
    def chatStream: ZStream[SessionProvider, Nothing, ChatMessage]
  }

  val live: ZManaged[Any, Nothing, ZLayer[Any, Nothing, ChatService]] = ChatService.make().memoize

  def say(msg: String): URIO[ChatService with SessionProvider, ChatMessage] = URIO.accessM(_.get.say(msg))

  def chatStream: ZStream[ChatService with SessionProvider, Nothing, ChatMessage] =
    ZStream.accessStream(_.get.chatStream)

  case class UserQueue(user: User, queue: Queue[ChatMessage])

  def make(): ZLayer[Any, Nothing, ChatService] = ZLayer.fromEffect {
    for {
      subscribers <- Ref.make(List.empty[UserQueue])
    } yield new Service {
      override def say(msg: String): ZIO[SessionProvider, Nothing, ChatMessage] =
        for {
          sub  <- subscribers.get
          user <- ZIO.access[SessionProvider](_.get.user)
          sent <- {
            val addMe = ChatMessage(user, msg, System.currentTimeMillis())
            UIO
              .foreach(sub)(userQueue =>
                userQueue.queue
                  .offer(addMe)
                  .onInterrupt(
                    //If it was shutdown, remove from subscribers
                    subscribers.update(_.filterNot(_ == userQueue))
                  )
              )
              .as(addMe)
          }
        } yield {
          sent
        }

      override def chatStream: ZStream[SessionProvider, Nothing, ChatMessage] = ZStream.unwrap {
        for {
          user  <- ZIO.access[SessionProvider](_.get.user)
          queue <- Queue.sliding[ChatMessage](requestedCapacity = 100)
          _     <- subscribers.update(UserQueue(user, queue) :: _)
        } yield ZStream.fromQueue(queue)
      }
    }
  }
}

object ChatApi extends GenericSchema[SessionProvider with ChatService] {
  case class Queries()
  case class Mutations(say: String => URIO[SessionProvider with ChatService, Boolean])
  case class Subscriptions(chatStream: ZStream[SessionProvider with ChatService, Nothing, ChatMessage])

  implicit val userSchema: Typeclass[User]               = gen[User]
  implicit val chatMessageSchema: Typeclass[ChatMessage] = gen[ChatMessage]

  val api: GraphQL[Console with Clock with SessionProvider with ChatService] =
    graphQL(
      RootResolver(
        Queries(
          ),
        Mutations(
          say = msg => ChatService.say(msg).as(true)
        ),
        Subscriptions(
          chatStream = ChatService.chatStream
        )
      )
    ) @@
    maxFields(200) @@               // query analyzer that limit query fields
    maxDepth(30) @@                 // query analyzer that limit query depth
    timeout(3.seconds) @@           // wrapper that fails slow queries
    printSlowQueries(500.millis) @@ // wrapper that logs slow queries
    apolloTracing                   // wrapper for https://github.com/apollographql/apollo-tracing

}

trait GameRoute extends ZIODirectives with Directives with AkkaHttpCirceAdapter {
  implicit val system: ActorSystem                        = ActorSystem()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher

  def interpreter(user: User): GraphQLInterpreter[zio.ZEnv, Any] = runtime.unsafeRun(
    ChatService.live.use { layer =>
      ChatApi.api.interpreter
        .map(_.provideCustomLayer(layer ++ ZLayer.succeed(SessionProvider.live(user))))
    }
  )

  val route =
    path("api" / "graphql") {
      val user = User("joe") //this will come from the session
      adapter.makeHttpService(interpreter(user))
    } ~ path("ws" / "graphql") {
      val user = User("joe") //this will come from the session
      adapter.makeWebSocketService(interpreter(user))
    } ~ path("graphiql") {
      getFromResource("graphiql.html")
    }
}
/*
For the client side we'll need:
https://github.com/rleibman/scalajs-reconnecting-websocket/blob/master/src/main/scala/net/leibman/reconnecting/ReconnectingWebSocket.scala
and
https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md
 */
