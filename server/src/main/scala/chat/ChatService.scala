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
import chuti.{ChannelId, ChatMessage, User}
import dao.SessionProvider
import zio._
import zio.stream.ZStream

object ChatService {
  implicit val runtime: zio.Runtime[zio.ZEnv] = zio.Runtime.default

  type ChatService = Has[Service]
  trait Service {
    def say(msg:              SayRequest): URIO[SessionProvider, ChatMessage]
    def chatStream(channelId: ChannelId):  ZStream[SessionProvider, Nothing, ChatMessage]
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

  def chatStream(
    channelId: ChannelId
  ): ZStream[ChatService with SessionProvider, Nothing, ChatMessage] =
    ZStream.accessStream(_.get.chatStream(channelId))

  case class MessageQueue(
    user:  User,
    queue: Queue[ChatMessage]
  )

  def make(): ZLayer[Any, Nothing, ChatService] = ZLayer.fromEffect {
    for {
      chatMessageQueue <- Ref.make(List.empty[MessageQueue])
    } yield new Service {
      override def say(request: SayRequest): ZIO[SessionProvider, Nothing, ChatMessage] =
        for {
          sub  <- chatMessageQueue.get
          user <- ZIO.access[SessionProvider](_.get.session.user)
          sent <- {
            val addMe = ChatMessage(user, request.msg)
            println(addMe) //TODO move to log
            UIO
              .foreach(sub)(userQueue =>
                userQueue.queue
                  .offer(addMe)
                  .onInterrupt(
                    //If it was shutdown, remove from subscribers
                    chatMessageQueue.update(_.filterNot(_ == userQueue))
                  )
              )
              .as(addMe)
          }
        } yield {
          sent
        }

      override def chatStream(
        channelId: ChannelId
      ): ZStream[SessionProvider, Nothing, ChatMessage] = ZStream.unwrap {
        for {
          user  <- ZIO.access[SessionProvider](_.get.session.user)
          queue <- Queue.sliding[ChatMessage](requestedCapacity = 100)
          _     <- chatMessageQueue.update(MessageQueue(user, queue) :: _)
        } yield ZStream.fromQueue(queue).filter(_.fromUser.chatChannel == Option(channelId))
      }
    }
  }

}
