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

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

import caliban.{CalibanError, GraphQLInterpreter}
import chuti._
import dao.{DatabaseProvider, Repository, SessionProvider}
import zio._
import zio.clock.Clock
import zio.console.Console
import zio.duration._
import zio.logging.{Logging, log}
import zio.stream.ZStream

object ChatService {
  private lazy val ttl: Duration = 15.minutes

  type ChatService = Has[Service]
  trait Service {
    def getRecentMessages(
      channelId: ChannelId
    ): ZIO[SessionProvider, GameException, Seq[ChatMessage]]
    def say(
      msg: SayRequest
    ): URIO[Repository with DatabaseProvider with SessionProvider with Logging, ChatMessage]
    def chatStream(
      channelId:    ChannelId,
      connectionId: ConnectionId
    ): ZStream[
      Repository with DatabaseProvider with SessionProvider with Logging,
      GameException,
      ChatMessage
    ]
  }

  implicit val runtime: zio.Runtime[zio.ZEnv] = zio.Runtime.default

  lazy val interpreter: GraphQLInterpreter[
    Console with Clock with ChatService with Repository with DatabaseProvider with SessionProvider with Logging,
    CalibanError
  ] =
    runtime.unsafeRun(ChatApi.api.interpreter)

  def sendMessage(
    msg:       String,
    channelId: ChannelId,
    toUser:    Option[User]
  ): ZIO[
    ChatService with Repository with DatabaseProvider with SessionProvider with Logging,
    Nothing,
    ChatMessage
  ] =
    for {
      chat <- ZIO.service[Service]
      sent <- chat.say(SayRequest(msg, channelId, toUser))
    } yield sent

  def say(request: SayRequest): URIO[
    ChatService with Repository with DatabaseProvider with SessionProvider with Logging,
    ChatMessage
  ] =
    URIO.accessM(_.get.say(request))

  def chatStream(
    channelId:    ChannelId,
    connectionId: ConnectionId
  ): ZStream[
    ChatService with Repository with DatabaseProvider with SessionProvider with Logging,
    GameException,
    ChatMessage
  ] =
    ZStream.accessStream(_.get.chatStream(channelId, connectionId))

  def getRecentMessages(
    channelId: ChannelId
  ): ZIO[ChatService with SessionProvider, GameException, Seq[ChatMessage]] =
    URIO.accessM(_.get.getRecentMessages(channelId))

  case class MessageQueue(
    user:         User,
    connectionId: ConnectionId,
    queue:        Queue[ChatMessage]
  )

  def dateFilter(
    timeAgo: LocalDateTime,
    msg:     ChatMessage
  ): Boolean = msg.date.isAfter(timeAgo)

  def make(): ZLayer[Logging, Nothing, ChatService] = ZLayer.fromEffect {
    for {
      chatMessageQueue <- Ref.make(List.empty[MessageQueue])
      recentMessages   <- Ref.make(List.empty[ChatMessage])
      _                <- log.info("========================== This should only ever be seen once.")
    } yield new Service {

      def getRecentMessages(
        channelId: ChannelId
      ): ZIO[SessionProvider, GameException, Seq[ChatMessage]] = {
        recentMessages.get.map { seq =>
          val timeAgo = LocalDateTime.now.minus(ttl.toMillis, ChronoUnit.MILLIS)
          seq.filter(dateFilter(timeAgo, _))
        }
      }

      override def say(request: SayRequest): ZIO[
        Repository with DatabaseProvider with SessionProvider with Logging,
        Nothing,
        ChatMessage
      ] =
        for {
          allSubscriptions <- chatMessageQueue.get
          user             <- ZIO.access[SessionProvider](_.get.session.user)
          _                <- log.info(s"Sending ${request.msg}")
          sent <- {
            //TODO make sure the user has rights to send messages on the channel,
            // basically if the channel is lobby, or the user is the game channel for that game,
            // or it's a direct message.
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
          _ <- {
            val timeAgo = LocalDateTime.now.minus(ttl.toMillis, ChronoUnit.MILLIS)
            recentMessages.update(old => old.filter(dateFilter(timeAgo, _)) :+ sent)
          }
        } yield sent

      override def chatStream(
        channelId:    ChannelId,
        connectionId: ConnectionId
      ): ZStream[
        Repository with DatabaseProvider with SessionProvider with Logging,
        GameException,
        ChatMessage
      ] =
        ZStream.unwrap {
          for {
            user    <- ZIO.access[SessionProvider with Logging](_.get.session.user)
            gameOps <- ZIO.access[Repository](_.get.gameOperations)
            // Make sure the user has rights to listen in on the channel,
            // basically if the channel is lobby, or the user is in the game channel for that game
            // admins can listen in on games.
            gameForUser <- gameOps.getGameForUser.mapError(GameException.apply)
            _ <- if (channelId == ChannelId.directChannel) {
              throw GameException("You can't subscribe to the direct channel!")
            } else if (!user.isAdmin && channelId != ChannelId.lobbyChannel && !gameForUser
                         .fold(false)(_.channelId.fold(false)(_ == channelId))) {
              throw GameException("The user is not in this channel")
            } else {
              ZIO.succeed(true)
            }
            queue <- Queue.sliding[ChatMessage](requestedCapacity = 100)
            _     <- chatMessageQueue.update(MessageQueue(user, connectionId, queue) :: _)
            after <- chatMessageQueue.get
            _     <- log.info(s"Chat stream started, queues have ${after.length} entries")
          } yield ZStream
            .fromQueue(queue)
            .ensuring(
              log.info(s"Chat queue for user ${user.id} shut down") *>
                queue.shutdown *> chatMessageQueue.update(
                _.filterNot(_.connectionId == connectionId)
              )
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

}
