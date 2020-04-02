/*
 * Copyright 2020 Roberto Leibman
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package chat

import akka.actor.ActorSystem
import akka.http.scaladsl.server.Directives
import caliban.GraphQL.graphQL
import caliban.interop.circe.AkkaHttpCirceAdapter
import caliban.schema.GenericSchema
import caliban.wrappers.ApolloTracing.apolloTracing
import caliban.wrappers.Wrappers.{maxDepth, maxFields, printSlowQueries, timeout}
import caliban.{CalibanError, GraphQL, GraphQLInterpreter, RootResolver}
import chat.ChatService.ChatService
import chat.SessionProvider.SessionProvider
import zio._
import zio.clock.Clock
import zio.console.Console
import zio.duration._
import zio.stream.ZStream

/**
@rleibman ChatApi.api.interpreter should be done only once (for performance reasons)
you can unsafeRun once to get the interpreter (doesn't require any env)
and then for each request you can provide your layer
here you recreate an interpreter at every call, which works but it not efficient at all
ChatService.live too seems to be recreated every time
in summary, do an unsafeRun in ChatRoute to get the interpreter and the live layer,
then for each user you can do another unsafeRun  with provideCustomLayer
  */
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
  def layer(u: User): Layer[Nothing, SessionProvider] = ZLayer.succeed(live(u))
}

case class ChatMessage(
  user: User,
  msg:  String,
  date: Long
)

object ChatService {
  import SessionProvider._
  implicit val runtime: zio.Runtime[zio.ZEnv] = zio.Runtime.default

  type ChatService = Has[Service]
  trait Service {
    def say(msg: String): ZIO[SessionProvider, Nothing, ChatMessage]
    def chatStream: ZStream[SessionProvider, Nothing, ChatMessage]
  }

  val interpreter: GraphQLInterpreter[ZEnv with SessionProvider, CalibanError] = runtime.unsafeRun(
    ChatService
      .make()
      .memoize
      .use(layer =>
        ChatApi.api.interpreter.map(_.provideSomeLayer[ZEnv with SessionProvider](layer))
      )
  )

  def say(msg: String): URIO[ChatService with SessionProvider, ChatMessage] =
    URIO.accessM(_.get.say(msg))

  def chatStream: ZStream[ChatService with SessionProvider, Nothing, ChatMessage] =
    ZStream.accessStream(_.get.chatStream)

  case class UserQueue(
    user:  User,
    queue: Queue[ChatMessage]
  )

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

object ChatApi extends GenericSchema[ChatService with SessionProvider] {
  case class Queries()
  case class Mutations(say: String => URIO[SessionProvider with ChatService, Boolean])
  case class Subscriptions(
    chatStream: ZStream[SessionProvider with ChatService, Nothing, ChatMessage]
  )

  implicit val userSchema:        Typeclass[User] = gen[User]
  implicit val chatMessageSchema: Typeclass[ChatMessage] = gen[ChatMessage]

  val api: GraphQL[Console with Clock with ChatService with SessionProvider] =
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
      maxFields(200) @@ // query analyzer that limit query fields
      maxDepth(30) @@ // query analyzer that limit query depth
      timeout(3.seconds) @@ // wrapper that fails slow queries
      printSlowQueries(500.millis) @@ // wrapper that logs slow queries
      apolloTracing // wrapper for https://github.com/apollographql/apollo-tracing

}

trait ChatRoute extends Directives with AkkaHttpCirceAdapter {
  implicit val system:           ActorSystem = ActorSystem()
  implicit val executionContext: ExecutionContextExecutor = system.dispatcher
  import ChatService._
  implicit val runtime: zio.Runtime[zio.ZEnv] = zio.Runtime.default

  val route =
    path("api" / "graphql") {
      val user = User("joe") //this will come from the session
      adapter.makeHttpService(interpreter.provideCustomLayer(SessionProvider.layer(user)))
    } ~ path("ws" / "graphql") {
      val user = User("joe") //this will come from the session
      adapter.makeWebSocketService(interpreter.provideCustomLayer(SessionProvider.layer(user)))
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
