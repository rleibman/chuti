/*
 * Copyright (c) 2024 Roberto Leibman
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package chat

import caliban.{CalibanError, GraphQLInterpreter}
import chuti.*
import chuti.ChannelId.*
import dao.{Repository, SessionContext}
import game.GameService.runtime
import zio.{Clock, Console, *}
import zio.logging.*
import zio.stream.ZStream

import java.time.Instant
import java.time.temporal.ChronoUnit

trait ChatService {

  def getRecentMessages(
    channelId: ChannelId
  ): ZIO[SessionContext, GameError, Seq[ChatMessage]]

  def say(msg: SayRequest): URIO[Repository & SessionContext, ChatMessage]

  def chatStream(
    channelId:    ChannelId,
    connectionId: ConnectionId
  ): ZStream[
    Repository & SessionContext,
    GameError,
    ChatMessage
  ]

}

object ChatService {

  import GameId.*

  lazy private val ttl: Duration = 15.minutes

  given runtime: zio.Runtime[Any] = zio.Runtime.default

  lazy val interpreter: IO[CalibanError.ValidationError, GraphQLInterpreter[ChatService & Repository & SessionContext, CalibanError]] = ChatApi.api.interpreter

  def sendMessage(
    msg:       String,
    channelId: ChannelId,
    toUser:    Option[User]
  ): ZIO[
    ChatService & Repository & SessionContext,
    Nothing,
    ChatMessage
  ] =
    for {
      chat <- ZIO.service[ChatService]
      sent <- chat.say(SayRequest(msg, channelId, toUser))
    } yield sent

  def say(request: SayRequest): URIO[
    ChatService & Repository & SessionContext,
    ChatMessage
  ] = ZIO.service[ChatService].flatMap(_.say(request))

  def chatStream(
    channelId:    ChannelId,
    connectionId: ConnectionId
  ): ZStream[
    ChatService & Repository & SessionContext,
    GameError,
    ChatMessage
  ] = ZStream.service[ChatService].flatMap(_.chatStream(channelId, connectionId))

  def getRecentMessages(
    channelId: ChannelId
  ): ZIO[ChatService & SessionContext, GameError, Seq[ChatMessage]] = ZIO.service[ChatService].flatMap(_.getRecentMessages(channelId))

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
        _                <- ZIO.logInfo("Making ChatService")
        chatMessageQueue <- Ref.make(List.empty[MessageQueue])
        recentMessages   <- Ref.make(List.empty[ChatMessage])
      } yield new ChatService {

        def getRecentMessages(
          channelId: ChannelId
        ): ZIO[SessionContext, GameError, Seq[ChatMessage]] = {
          for {
            now <- Clock.instant
            res <- recentMessages.get.map { seq =>
              val timeAgo = now.minus(ttl.toMillis, ChronoUnit.MILLIS).nn
              seq.filter(msg => msg.channelId == channelId && dateFilter(timeAgo, msg))
            }
          } yield res
        }

        override def say(request: SayRequest): ZIO[
          Repository & SessionContext,
          Nothing,
          ChatMessage
        ] =
          for {
            allSubscriptions <- chatMessageQueue.get
            now              <- Clock.instant
            user             <- ZIO.service[SessionContext].map(_.session.user)
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
          Repository & SessionContext,
          GameError,
          ChatMessage
        ] =
          ZStream.unwrap {
            for {
              user    <- ZIO.service[SessionContext].map(_.session.user)
              gameOps <- ZIO.service[Repository].map(_.gameOperations)
              // Make sure the user has rights to listen in on the channel,
              // basically if the channel is lobby, or the user is in the game channel for that game
              // admins can listen in on games.
              userInGame <- gameOps.userInGame(GameId(channelId.channelId)).mapError(GameError.apply)
              _ <-
                if (channelId == ChannelId.directChannel)
                  ZIO.fail(GameError("No te puedes subscribir a un canal directo!"))
                else if (!user.isAdmin && channelId != ChannelId.lobbyChannel && !userInGame)
                  ZIO.fail(GameError("El usuario no esta en este canal"))
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
