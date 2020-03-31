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

import caliban.GraphQL.graphQL
import caliban.{ GraphQL, RootResolver }
import caliban.schema.GenericSchema
import caliban.wrappers.ApolloTracing.apolloTracing
import caliban.wrappers.Wrappers.{ maxDepth, maxFields, printSlowQueries, timeout }
import chat.SessionProvider.SessionProvider
import zio._
import zio.clock.Clock
import zio.console.Console
import zio.duration._
import zio.stream.ZStream

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

trait ChatServer extends GenericSchema[SessionProvider] {
  import SessionProvider._

  case class ChatMessage(user: User, msg: String, date: Long)

  case class Queries()
  case class Mutations(say: String => URIO[SessionProvider, Boolean])
  case class Subscriptions(chatStream: ZStream[SessionProvider, Nothing, ChatMessage])

  implicit val userSchema: Typeclass[User]               = gen[User]
  implicit val chatMessageSchema: Typeclass[ChatMessage] = gen[ChatMessage]

  val api: GraphQL[Console with Clock with SessionProvider] =
    graphQL(
      RootResolver(
        Queries(
          ),
        Mutations(
          say = msg => say(msg).as(true)
        ),
        Subscriptions(
          chatStream = chatStream()
        )
      )
    ) @@
    maxFields(200) @@               // query analyzer that limit query fields
    maxDepth(30) @@                 // query analyzer that limit query depth
    timeout(3.seconds) @@           // wrapper that fails slow queries
    printSlowQueries(500.millis) @@ // wrapper that logs slow queries
    apolloTracing                   // wrapper for https://github.com/apollographql/apollo-tracing

  private lazy val chatQueue: ZIO[SessionProvider, Nothing, Queue[ChatMessage]] = Queue.unbounded[ChatMessage]

  def say(msg: String): ZIO[SessionProvider, Nothing, ChatMessage] =
    for {
      q           <- chatQueue
      user        <- ZIO.access[SessionProvider](_.get.user)
      chatMessage <- Task.succeed(ChatMessage(user, msg, System.currentTimeMillis()))
      _           <- q.offer(chatMessage)
    } yield chatMessage

  def chatStream(): ZStream[SessionProvider, Nothing, ChatMessage] =
    ZStream.unwrap(for {
      q <- chatQueue
    } yield ZStream.fromQueue(q))

}
