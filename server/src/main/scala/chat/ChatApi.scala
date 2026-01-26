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
import dao.Repository
import zio.*
import zio.logging.*
import zio.stream.ZStream

case class SayRequest(
  msg:       String,
  channelId: ChannelId,
  toUser:    Option[User] = None
)

object ChatApi extends GenericSchema[ChatService & Repository & ChutiSession] {

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
    say: SayRequest => URIO[ChatService & Repository & ChutiSession, Boolean]
  )

  case class Subscriptions(
    chatStream: ChatStreamArgs => ZStream[
      ChatService & Repository & ChutiSession,
      GameError,
      ChatMessage
    ]
  )

  private given Schema[Any, UserId] = Schema.longSchema.contramap(_.value)
  private given Schema[Any, ChannelId] = Schema.longSchema.contramap(_.value)
  private given Schema[Any, ConnectionId] = Schema.stringSchema.contramap(_.value)
  private given Schema[Any, User] = gen[Any, User]
  private given Schema[Any, ChatMessage] = gen[Any, ChatMessage]
  private given Schema[ChatService & ChutiSession, Queries] = Schema.gen[ChatService & ChutiSession, Queries]
  private given Schema[ChatService & Repository & ChutiSession, Mutations] =
    Schema.gen[ChatService & Repository & ChutiSession, Mutations]
  private given Schema[ChatService & Repository & ChutiSession, Subscriptions] =
    Schema.gen[ChatService & Repository & ChutiSession, Subscriptions]

  private given ArgBuilder[UserId] = ArgBuilder.int.map(UserId.apply)
  private given ArgBuilder[ChannelId] = ArgBuilder.int.map(ChannelId.apply)
  private given ArgBuilder[ConnectionId] = ArgBuilder.string.map(ConnectionId.apply)

  lazy val api: GraphQL[ChatService & Repository & ChutiSession] =
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
      maxDepth(30)
//      @@ // query analyzer that limit query depth
//      timeout(3.seconds) @@ // wrapper that fails slow queries
//      printSlowQueries(3.seconds)

  //  @@ // wrapper that logs slow queries
//      apolloTracing // wrapper for https://github.com/apollographql/apollo-tracing
  val schema =
    """schema {\n  query: Queries\n  mutation: Mutations\n  subscription: Subscriptions\n}\n\nscalar Instant\n\ninput UserInput {\n  id: Int\n  email: String!\n  name: String!\n  created: Instant!\n  active: Boolean!\n  deleted: Boolean!\n  isAdmin: Boolean!\n}\n\ntype ChatMessage {\n  fromUser: User!\n  msg: String!\n  channelId: Int!\n  toUser: User\n  date: Insant!\n}\n\ntype Mutations {\n  say(msg: String!, channelId: Int!, toUser: UserInput): Boolean!\n}\n\ntype Queries {\n  getRecentMessages(value: Int!): [ChatMessage!]\n}\n\ntype Subscriptions {\n  chatStream(channelId: Int!, connectionId: String!): ChatMessage\n}\n\ntype User {\n  id: Int\n  email: String!\n  name: String!\n  created: Instant!\n  active: Boolean!\n  deleted: Boolean!\n  isAdmin: Boolean!\n}"""
  // Generate client with
  // calibanGenClient /Volumes/Personal/projects/chuti/server/src/main/graphql/chat.schema /Volumes/Personal/projects/chuti/web/src/main/scala/chat/ChatClient.scala

}
