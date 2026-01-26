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
import caliban.*
import caliban.CalibanError.ExecutionError
import caliban.interop.zio.*
import caliban.interop.zio.json.*
import caliban.introspection.adt.__Type
import caliban.schema.*
import caliban.schema.ArgBuilder.auto.*
import caliban.schema.Schema.auto.*
import caliban.wrappers.Wrappers.*
import chuti.*
import chuti.ChannelId.*
import chuti.UserId.*
import dao.ZIORepository
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.logging.*
import zio.stream.ZStream

import java.util.Locale

object ChatApi {

  case class SayRequest(
    msg:       String,
    channelId: ChannelId,
    toUser:    Option[User] = None
  ) derives ArgBuilder

  case class ChatStreamArgs(
    channelId:    ChannelId,
    connectionId: ConnectionId
  )

  case class Queries(
    getRecentMessages: ChannelId => ZIO[ChatService & ChutiSession, GameError, Seq[
      ChatMessage
    ]]
  )

  case class Mutations(
    say: SayRequest => URIO[ChatService & ZIORepository & ChutiSession, Boolean]
  )

  case class Subscriptions(
    chatStream: ChatStreamArgs => ZStream[
      ChatService & ZIORepository & ChutiSession,
      GameError,
      ChatMessage
    ]
  )

  private given Schema[Any, UserId] = Schema.longSchema.contramap(_.value)
  private given Schema[Any, ChannelId] = Schema.longSchema.contramap(_.value)
  private given Schema[Any, ConnectionId] = Schema.stringSchema.contramap(_.value)
  private given Schema[Any, Locale] = Schema.stringSchema.contramap(_.toString)
  private given Schema[Any, User] = Schema.gen[Any, User]
  private given Schema[Any, ChatMessage] = Schema.gen[Any, ChatMessage]

  private given ArgBuilder[UserId] = ArgBuilder.int.map(UserId.apply)
  private given ArgBuilder[ChannelId] = ArgBuilder.int.map(ChannelId.apply)
  private given ArgBuilder[ConnectionId] = ArgBuilder.string.map(ConnectionId.apply)
  private given ArgBuilder[Locale] =
    ArgBuilder.string.flatMap(s =>
      Locale.forLanguageTag(s) match {
        case l: Locale => Right(l)
        case null => Left(ExecutionError(s"invalid locale $s"))
      }
    )
  private given ArgBuilder[User] = ArgBuilder.derived[User]

  lazy val api: GraphQL[ChatService & ZIORepository & ChutiSession] =
    graphQL[
      ChatService & ZIORepository & ChutiSession,
      Queries,
      Mutations,
      Subscriptions
    ](
      RootResolver(
        Queries(getRecentMessages = channelId => ChatService.getRecentMessages(channelId)),
        Mutations(
          say = sayRequest => ChatService.say(sayRequest).as(true)
        ),
        Subscriptions(
          chatStream = chatStreamArgs => ChatService.chatStream(chatStreamArgs.channelId, chatStreamArgs.connectionId)
        )
      )
    ) @@ maxFields(20)
      @@ maxDepth(30)
      @@ printErrors
      @@ timeout(3.seconds)

}
