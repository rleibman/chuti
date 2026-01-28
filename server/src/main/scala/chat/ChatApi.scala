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
import auth.{AuthConfig, AuthServer}
import caliban.*
import caliban.CalibanError.ExecutionError
import caliban.schema.*
import caliban.schema.ArgBuilder.auto.*
import caliban.schema.Schema.auto.*
import caliban.wrappers.Wrappers.*
import chat.ChannelId.*
import chuti.*
import chuti.UserId.*
import dao.ZIORepository
import zio.*
import zio.stream.ZStream

import java.util.Locale

object ChatApi {

  case class ChatStreamArgs(
    channelId:    ChannelId,
    connectionId: ConnectionId,
    token:        String
  ) derives ArgBuilder

  case class Queries(
    getRecentMessages: ChannelId => ZIO[ChatService & ZIORepository & ChutiSession, GameError, Seq[ChatMessage]]
  )

  case class Mutations(
    say: SayRequest => ZIO[ChatService & ZIORepository & ChutiSession, GameError, Boolean]
  )

  case class Subscriptions(
    chatStream: ChatStreamArgs => ZStream[
      ChatService & ZIORepository & AuthConfig & AuthServer[User, Option[UserId], ConnectionId],
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

  private given ArgBuilder[UserId] = ArgBuilder.long.map(UserId.apply)
  private given ArgBuilder[ChannelId] = ArgBuilder.long.map(ChannelId.apply)
  private given ArgBuilder[ConnectionId] = ArgBuilder.string.map(ConnectionId.apply)
  private given ArgBuilder[Locale] =
    ArgBuilder.string.flatMap(s =>
      Locale.forLanguageTag(s) match {
        case l: Locale => Right(l)
        case null => Left(ExecutionError(s"invalid locale $s"))
      }
    )
  private given ArgBuilder[User] = ArgBuilder.derived[User]
  private given ArgBuilder[SayRequest] = ArgBuilder.derived[SayRequest]

  lazy val api: GraphQL[ChatService & ZIORepository & ChutiSession & AuthConfig & AuthServer[User, Option[UserId], ConnectionId]] =
    graphQL[
      ChatService & ZIORepository & ChutiSession & AuthConfig & AuthServer[User, Option[UserId], ConnectionId],
      Queries,
      Mutations,
      Subscriptions
    ](
      RootResolver(
        Queries(getRecentMessages = channelId => ZIO.serviceWithZIO[ChatService](_.getRecentMessages(channelId))),
        Mutations(say = sayRequest => ZIO.serviceWithZIO[ChatService](_.say(sayRequest)).as(true)),
        Subscriptions(
          chatStream = chatStreamArgs =>
            ZStream.unwrap(
              for {
                authServer <- ZIO.service[AuthServer[User, Option[UserId], ConnectionId]]
                sessionLayer <- authServer
                  .sessionLayerFromToken(
                    chatStreamArgs.token,
                    Some(chatStreamArgs.connectionId)
                  ).mapError(e => GameError(e.getMessage))
              } yield ZStream
                .serviceWithStream[ChatService](
                  _.chatStream(chatStreamArgs.channelId, chatStreamArgs.connectionId)
                ).provideSomeLayer[ChatService & ZIORepository](sessionLayer)
            )
        )
      )
    ) @@ maxFields(50)
      @@ maxDepth(30)
      @@ printErrors
      @@ timeout(3.seconds)

}
