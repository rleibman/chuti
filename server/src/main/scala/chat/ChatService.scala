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
import chuti.{ChannelId, ChatMessage, ConnectionId, User}
import dao.SessionProvider
import zio._
import zio.clock.Clock
import zio.console.Console
import zio.logging.{Logging, log}
import zio.stream.ZStream

object ChatService {

  type ChatService = Has[Service]
  trait Service {
    def say(msg: SayRequest): URIO[SessionProvider with Logging, ChatMessage]
    def chatStream(
      channelId:    ChannelId,
      connectionId: ConnectionId
    ): ZStream[SessionProvider with Logging, Nothing, ChatMessage]
  }

  implicit val runtime: zio.Runtime[zio.ZEnv] = zio.Runtime.default

  lazy val interpreter: GraphQLInterpreter[
    Console with Clock with ChatService with SessionProvider with Logging,
    CalibanError
  ] =
    runtime.unsafeRun(ChatApi.api.interpreter)

  def sendMessage(
    msg:       String,
    channelId: ChannelId,
    toUser:    Option[User]
  ): ZIO[ChatService with SessionProvider with Logging, Nothing, ChatMessage] =
    for {
      chat <- ZIO.service[Service]
      sent <- chat.say(SayRequest(msg, channelId, toUser))
    } yield sent

  def say(request: SayRequest): URIO[ChatService with SessionProvider with Logging, ChatMessage] =
    URIO.accessM(_.get.say(request))

  def chatStream(
    channelId:    ChannelId,
    connectionId: ConnectionId
  ): ZStream[ChatService with SessionProvider with Logging, Nothing, ChatMessage] =
    ZStream.accessStream(_.get.chatStream(channelId, connectionId))

  case class MessageQueue(
    user:         User,
    connectionId: ConnectionId,
    queue:        Queue[ChatMessage]
  )

  def make(): ZLayer[Logging, Nothing, ChatService] = ZLayer.fromEffect {
    for {
      chatMessageQueue <- Ref.make(List.empty[MessageQueue])
      _                <- log.info("========================== This should only ever be seen once.")
    } yield new Service {
      override def say(
        request: SayRequest
      ): ZIO[SessionProvider with Logging, Nothing, ChatMessage] =
        for {
          allSubscriptions <- chatMessageQueue.get
          user             <- ZIO.access[SessionProvider](_.get.session.user)
          _                <- log.info(s"Sending ${request.msg}")
          sent <- {
            //TODO make sure the user has rights to listen in on the channel,
            // basically if the channel is lobby, or the user is the game channel for that game
            //TODO validate that the message is not longer than MAX_MESSAGE_SIZE (1024?)
            val sendMe =
              ChatMessage(
                fromUser = user,
                msg = request.msg,
                channelId = request.channelId,
                toUser = request.toUser
              )

            UIO
              .foreach(
                allSubscriptions.filter(subs => request.toUser.fold(true)(_.id == subs.user.id))
              )(_.queue.offer(sendMe))
              .as(sendMe)
          }
        } yield sent

      override def chatStream(
        channelId:    ChannelId,
        connectionId: ConnectionId
      ): ZStream[SessionProvider with Logging, Nothing, ChatMessage] = ZStream.unwrap {
        for {
          user <- ZIO.access[SessionProvider with Logging](_.get.session.user)
          //TODO make sure the user has rights to listen in on the channel,
          // basically if the channel is lobby, or the user is the game channel for that game
          queue <- Queue.sliding[ChatMessage](requestedCapacity = 100)
          _     <- chatMessageQueue.update(MessageQueue(user, connectionId, queue) :: _)
          after <- chatMessageQueue.get
          _     <- log.info(s"Chat stream started, queues have ${after.length} entries")
        } yield ZStream
          .fromQueue(queue)
          .ensuring(
            log.info(s"Chat queue for user ${user.id} shut down") *>
              queue.shutdown *> chatMessageQueue.update(_.filterNot(_.connectionId == connectionId))
          )
          .filter(m =>
            m.channelId == channelId ||
              (m.channelId == ChannelId.directChannel && m.toUser.nonEmpty)
          ).catchAllCause { c =>
            c.prettyPrint
            ZStream.halt(c)
          }

      }
    }
  }

  //TODO keep the last 15 minutes of conversation.
}
