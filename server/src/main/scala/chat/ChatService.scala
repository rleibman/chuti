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

import api.ChutiSession
import caliban.{CalibanError, GraphQLInterpreter}
import chuti.*
import chuti.ChannelId.*
import dao.Repository
import zio.logging.*
import zio.stream.ZStream
import zio.{Clock, Console, *}

import java.time.Instant
import java.time.temporal.ChronoUnit

trait ChatService {

  def getRecentMessages(
    channelId: ChannelId
  ): ZIO[ChutiSession, GameError, Seq[ChatMessage]]

  def say(msg: SayRequest): URIO[Repository & ChutiSession, ChatMessage]

  def chatStream(
    channelId:    ChannelId,
    connectionId: ConnectionId
  ): ZStream[
    Repository & ChutiSession,
    GameError,
    ChatMessage
  ]

}

object ChatService {

  import GameId.*

  lazy private val ttl: Duration = 15.minutes

  def sendMessage(
    msg:       String,
    channelId: ChannelId,
    toUser:    Option[User]
  ): ZIO[
    ChatService & Repository & ChutiSession,
    Nothing,
    ChatMessage
  ] =
    for {
      chat <- ZIO.service[ChatService]
      sent <- chat.say(SayRequest(msg, channelId, toUser))
    } yield sent

  def say(request: SayRequest): URIO[
    ChatService & Repository & ChutiSession,
    ChatMessage
  ] = ZIO.service[ChatService].flatMap(_.say(request))

  def chatStream(
    channelId:    ChannelId,
    connectionId: ConnectionId
  ): ZStream[
    ChatService & Repository & ChutiSession,
    GameError,
    ChatMessage
  ] = ZStream.service[ChatService].flatMap(_.chatStream(channelId, connectionId))

  def getRecentMessages(
    channelId: ChannelId
  ): ZIO[ChatService & ChutiSession, GameError, Seq[ChatMessage]] =
    ZIO.service[ChatService].flatMap(_.getRecentMessages(channelId))

  case class MessageQueue(
    user:         User,
    connectionId: ConnectionId,
    queue:        Queue[ChatMessage]
  )

  def dateFilter(
    timeAgo: Instant,
    msg:     ChatMessage
  ): Boolean = msg.date.isAfter(timeAgo)

  def make(): URLayer[Any, ChatService] =
    ZLayer.fromZIO {
      for {
        chatMessageQueue <- Ref.make(List.empty[MessageQueue])
        recentMessages   <- Ref.make(List.empty[ChatMessage])
        _                <- ZIO.logInfo("========================== This should only ever be seen once.")
      } yield new ChatService {

        def getRecentMessages(
          channelId: ChannelId
        ): ZIO[ChutiSession, GameError, Seq[ChatMessage]] = {
          for {
            now <- Clock.instant
            res <- recentMessages.get.map { seq =>
              val timeAgo = now.minus(ttl.toMillis, ChronoUnit.MILLIS).nn
              seq.filter(msg => msg.channelId == channelId && dateFilter(timeAgo, msg))
            }
          } yield res
        }

        override def say(request: SayRequest): ZIO[
          Repository & ChutiSession,
          Nothing,
          ChatMessage
        ] =
          for {
            allSubscriptions <- chatMessageQueue.get
            now              <- Clock.instant
            user             <- ZIO.serviceWith[ChutiSession](_.user)
            _                <- ZIO.logInfo(s"Sending ${request.msg}")
            sent <- {
              // TODO make sure the user has rights to send messages on the channel,
              // basically if the channel is lobby, or the user is the game channel for that game,
              // or it's a direct message.
              // TODO validate that the message is not longer than MAX_MESSAGE_SIZE (1024?)
              val sendMe =
                ChatMessage(
                  fromUser = user,
                  msg = request.msg,
                  channelId = request.channelId,
                  toUser = request.toUser,
                  date = now
                )

              ZIO
                .foreach(
                  allSubscriptions.filter(subs => request.toUser.fold(true)(_.id == subs.user.id))
                )(_.queue.offer(sendMe))
                .as(sendMe)
            }
            _ <- {
              val timeAgo = now.minus(ttl.toMillis, ChronoUnit.MILLIS).nn
              recentMessages.update(old => old.filter(dateFilter(timeAgo, _)) :+ sent)
            }
          } yield sent

        override def chatStream(
          channelId:    ChannelId,
          connectionId: ConnectionId
        ): ZStream[
          Repository & ChutiSession,
          GameError,
          ChatMessage
        ] =
          ZStream.unwrap {
            for {
              user    <- ZIO.serviceWith[ChutiSession](_.user)
              gameOps <- ZIO.service[Repository].map(_.gameOperations)
              // Make sure the user has rights to listen in on the channel,
              // basically if the channel is lobby, or the user is in the game channel for that game
              // admins can listen in on games.
              userInGame <- gameOps.userInGame(GameId(channelId.channelId)).mapError(GameError.apply)
              _ <-
                if (channelId == ChannelId.directChannel)
                  throw GameError("No te puedes subscribir a un canal directo!")
                else if (!user.isAdmin && channelId != ChannelId.lobbyChannel && !userInGame)
                  throw GameError("El usuario no esta en este canal")
                else
                  ZIO.succeed(true)
              queue <- Queue.sliding[ChatMessage](requestedCapacity = 100)
              _     <- chatMessageQueue.update(MessageQueue(user, connectionId, queue) :: _)
              after <- chatMessageQueue.get
              _     <- ZIO.logInfo(s"Chat stream started, queues have ${after.length} entries")
            } yield ZStream
              .fromQueue(queue)
              .ensuring(
                ZIO.logInfo(s"Chat queue for user ${user.id} shut down") *>
                  queue.shutdown *> chatMessageQueue.update(
                    _.filterNot(_.connectionId == connectionId)
                  )
              )
              .filter(m =>
                m.channelId == channelId ||
                  (m.channelId == ChannelId.directChannel && m.toUser.nonEmpty)
              ).catchAllCause { c =>
                c.prettyPrint
              ZStream.failCause(c)
              }

          }
      }
    }

}
