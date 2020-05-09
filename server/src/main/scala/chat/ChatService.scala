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
import zio.logging.slf4j.Slf4jLogger
import zio.logging.{Logging, log}
import zio.stream.ZStream

object ChatService {
  implicit val runtime: zio.Runtime[zio.ZEnv] = zio.Runtime.default

  type ChatService = Has[Service]
  trait Service {
    def say(msg: SayRequest): URIO[SessionProvider with Logging, ChatMessage]
    def chatStream(
      channelId: ChannelId
    ): ZStream[SessionProvider with Logging, Nothing, ChatMessage]
  }

//  dapperwareYesterday at 8:39 PM
//    What I tend to do (since I use play a lot and it’s difficult to live in the effect) is to shove shared services into my runtime, especially if they are supposed to last the lifetime of the service.
//  ghostdogprYesterday at 9:55 PM
//    yeah that's good too, there's Runtime.unsafeFromLayer that makes it convenient
  lazy val TheChatService: ZManaged[Any, Nothing, ZLayer[Any, Nothing, ChatService]] = {
    println("You should only see this message once")
    ChatService.make().memoize
  }

  private val logLayer: ZLayer[Any, Nothing, Logging] = Slf4jLogger.make((_, str) => str)

  lazy val interpreter: GraphQLInterpreter[ZEnv with SessionProvider with Logging, CalibanError] =
    runtime.unsafeRun(
      TheChatService
        .use(layer =>
          ChatApi.api.interpreter
            .map(_.provideSomeLayer[ZEnv with SessionProvider with Logging](layer))
        )
    )

  def sendMessage(
    msg:    String,
    toUser: Option[User]
  ): ZIO[SessionProvider, Nothing, ChatMessage] = {
    TheChatService.use(layer =>
      (
        for {
          chat <- ZIO.access[ChatService](_.get)
          sent <- chat.say(SayRequest(msg, toUser))
        } yield sent
      ).provideSomeLayer[SessionProvider](layer ++ logLayer)
    )
  }

  def say(request: SayRequest): URIO[ChatService with SessionProvider with Logging, ChatMessage] =
    URIO.accessM(_.get.say(request))

  def chatStream(
    channelId: ChannelId
  ): ZStream[ChatService with SessionProvider with Logging, Nothing, ChatMessage] =
    ZStream.accessStream(_.get.chatStream(channelId))

  case class MessageQueue(
    user:  User,
    queue: Queue[ChatMessage]
  )

  def make(): ZLayer[Any, Nothing, ChatService] = ZLayer.fromEffect {
    for {
      chatMessageQueue <- Ref.make(List.empty[MessageQueue])
    } yield new Service {
      override def say(
        request: SayRequest
      ): ZIO[SessionProvider with Logging, Nothing, ChatMessage] =
        for {
          allSubscriptions <- chatMessageQueue.get
          user             <- ZIO.access[SessionProvider](_.get.session.user)
          _                <- log.debug(s"Sending ${request.msg}")
          sent <- {
            val sendMe = ChatMessage(user, request.msg)
            UIO
              .foreach(allSubscriptions)(userQueue =>
                userQueue.queue
                  .offer(sendMe)
                  .onInterrupt(
                    //If it was shutdown, remove from subscribers
                    chatMessageQueue.update(_.filterNot(_ == userQueue))
                  )
              )
              .as(sendMe)
          }
        } yield {
          sent
        }

      override def chatStream(
        channelId: ChannelId
      ): ZStream[SessionProvider with Logging, Nothing, ChatMessage] = ZStream.unwrap {
        for {
          user  <- ZIO.access[SessionProvider with Logging](_.get.session.user)
          queue <- Queue.sliding[ChatMessage](requestedCapacity = 100)
          _     <- chatMessageQueue.update(MessageQueue(user, queue) :: _)
        } yield ZStream.fromQueue(queue).filter(_.fromUser.chatChannel == Option(channelId))
      }
    }
  }

}
