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
import caliban.schema.{ArgBuilder, GenericSchema, Schema}
import caliban.wrappers.Wrappers.{maxDepth, maxFields, printSlowQueries, timeout}
import caliban.{GraphQL, RootResolver}
import chat.ChatService.ChatService
import chuti.{ConnectionId, *}
import dao.{Repository, SessionContext}
import zio.clock.Clock
import zio.console.Console
import zio.duration.*
import zio.logging.Logging
import zio.stream.ZStream
import zio.{URIO, ZIO}
import chuti.UserId.*
import chuti.ChannelId.*

case class SayRequest(
  msg:       String,
  channelId: ChannelId,
  toUser:    Option[User] = None
)

object ChatApi extends GenericSchema[ChatService & Repository & SessionContext & Logging & Clock] {

  case class ChatStreamArgs(
    channelId:    ChannelId,
    connectionId: ConnectionId
  )

  case class Queries(
    getRecentMessages: ChannelId => ZIO[ChatService & SessionContext, GameException, Seq[
      ChatMessage
    ]]
  )

  case class Mutations(
    say: SayRequest => URIO[ChatService & Repository & SessionContext & Logging & Clock, Boolean]
  )

  case class Subscriptions(
    chatStream: ChatStreamArgs => ZStream[
      ChatService & Repository & SessionContext & Logging & Clock,
      GameException,
      ChatMessage
    ]
  )

  private given Schema[Any, UserId] = Schema.intSchema.contramap(_.userId)
  private given Schema[Any, ChannelId] = Schema.intSchema.contramap(_.channelId)
  private given Schema[Any, ConnectionId] = Schema.intSchema.contramap(_.connectionId)
  private given Schema[Any, User] = gen[Any, User]
  private given Schema[Any, ChatMessage] = gen[Any, ChatMessage]
  private given Schema[ChatService & SessionContext, Queries] = Schema.gen[ChatService & SessionContext, Queries]
  private given Schema[ChatService & Repository & SessionContext & Logging & Clock, Mutations] =
    Schema.gen[ChatService & Repository & SessionContext & Logging & Clock, Mutations]
  private given Schema[ChatService & Repository & SessionContext & Logging & Clock, Subscriptions] =
    Schema.gen[ChatService & Repository & SessionContext & Logging & Clock, Subscriptions]

  private given ArgBuilder[UserId] = ArgBuilder.int.map(UserId.apply)
  private given ArgBuilder[ChannelId] = ArgBuilder.int.map(ChannelId.apply)
  private given ArgBuilder[ConnectionId] = ArgBuilder.int.map(ConnectionId.apply)

  lazy val api: GraphQL[Console & Clock & ChatService & Repository & SessionContext & Logging] =
    graphQL(
      RootResolver(
        Queries(getRecentMessages = channelId => ChatService.getRecentMessages(channelId)),
        Mutations(
          say = sayRequest => ChatService.say(sayRequest).as(true)
        ),
        Subscriptions(
          chatStream = chatStreamArgs => ChatService.chatStream(chatStreamArgs.channelId, chatStreamArgs.connectionId)
        )
      )
    ) @@
      maxFields(22) @@ // query analyzer that limit query fields
      maxDepth(30) @@ // query analyzer that limit query depth
      timeout(3.seconds) @@ // wrapper that fails slow queries
      printSlowQueries(3.seconds)
//  @@ // wrapper that logs slow queries
//      apolloTracing // wrapper for https://github.com/apollographql/apollo-tracing
  val schema =
    """schema {\n  query: Queries\n  mutation: Mutations\n  subscription: Subscriptions\n}\n\nscalar Instant\n\ninput UserInput {\n  id: Int\n  email: String!\n  name: String!\n  created: Instant!\n  active: Boolean!\n  deleted: Boolean!\n  isAdmin: Boolean!\n}\n\ntype ChatMessage {\n  fromUser: User!\n  msg: String!\n  channelId: Int!\n  toUser: User\n  date: Insant!\n}\n\ntype Mutations {\n  say(msg: String!, channelId: Int!, toUser: UserInput): Boolean!\n}\n\ntype Queries {\n  getRecentMessages(value: Int!): [ChatMessage!]\n}\n\ntype Subscriptions {\n  chatStream(channelId: Int!, connectionId: String!): ChatMessage\n}\n\ntype User {\n  id: Int\n  email: String!\n  name: String!\n  created: Instant!\n  active: Boolean!\n  deleted: Boolean!\n  isAdmin: Boolean!\n}"""
  // Generate client with
  // calibanGenClient /Volumes/Personal/projects/chuti/server/src/main/graphql/chat.schema /Volumes/Personal/projects/chuti/web/src/main/scala/chat/ChatClient.scala

}
