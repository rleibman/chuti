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

import caliban.{CalibanError, GraphQLInterpreter}
import chuti.User
import dao.SessionProvider
import zio._
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

//case class User(name: String)
//
//object SessionProvider {
//
//  type SessionProvider = Has[SessionProvider.Session]
//  trait Session {
//    def user: User
//  }
//  def live(u: User): Session = new Session {
//    val user: User = u
//  }
//  def layer(u: User): Layer[Nothing, SessionProvider] = ZLayer.succeed(live(u))
//}

case class ChatMessage(
  user: User,
  msg:  String,
  date: Long
)

object ChatService {
  import dao.SessionProvider._
  implicit val runtime: zio.Runtime[zio.ZEnv] = zio.Runtime.default

  type ChatService = Has[Service]
  trait Service {
    def say(msg: SayRequest): ZIO[SessionProvider, Nothing, ChatMessage]
    def chatStream: ZStream[SessionProvider, Nothing, ChatMessage]
  }

  lazy val interpreter: GraphQLInterpreter[ZEnv with SessionProvider, CalibanError] =
    runtime.unsafeRun(
      ChatService
        .make()
        .memoize
        .use(layer =>
          ChatApi.api.interpreter.map(_.provideSomeLayer[ZEnv with SessionProvider](layer))
        )
    )

  def say(request: SayRequest): URIO[ChatService with SessionProvider, ChatMessage] =
    URIO.accessM(_.get.say(request))

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
      override def say(request: SayRequest): ZIO[SessionProvider, Nothing, ChatMessage] =
        for {
          sub  <- subscribers.get
          user <- ZIO.access[SessionProvider](_.get.session.user)
          sent <- {
            val addMe = ChatMessage(user, request.msg, System.currentTimeMillis())
            println(addMe)
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
          user  <- ZIO.access[SessionProvider](_.get.session.user)
          queue <- Queue.sliding[ChatMessage](requestedCapacity = 100)
          _     <- subscribers.update(UserQueue(user, queue) :: _)
        } yield ZStream.fromQueue(queue)
      }
    }
  }

}




/*
For the client side we'll need:
https://github.com/rleibman/scalajs-reconnecting-websocket/blob/master/src/main/scala/net/leibman/reconnecting/ReconnectingWebSocket.scala
and
https://github.com/apollographql/subscriptions-transport-ws/blob/master/PROTOCOL.md
 */
