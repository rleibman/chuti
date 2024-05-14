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

import caliban.*
import caliban.schema.{ArgBuilder, GenericSchema, Schema}
import caliban.wrappers.Wrappers.{maxDepth, maxFields, printSlowQueries, timeout}
import chat.ChatService
import chuti.*
import chuti.ChannelId.*
import chuti.UserId.*
import dao.{Repository, SessionContext}
import zio.*
import zio.logging.*
import zio.stream.ZStream

case class SayRequest(
  msg:       String,
  channelId: ChannelId,
  toUser:    Option[User] = None
)

object ChatApi extends GenericSchema[ChatService & Repository & SessionContext] {

  case class ChatStreamArgs(
    channelId:    ChannelId,
    connectionId: ConnectionId
  )

  case class Queries(
    getRecentMessages: ChannelId => ZIO[ChatService & SessionContext, GameError, Seq[
      ChatMessage
    ]]
  )

  case class Mutations(
    say: SayRequest => URIO[ChatService & Repository & SessionContext, Boolean]
  )

  case class Subscriptions(
    chatStream: ChatStreamArgs => ZStream[
      ChatService & Repository & SessionContext,
      GameError,
      ChatMessage
    ]
  )

  private given Schema[Any, UserId] = Schema.intSchema.contramap(_.userId)
  private given Schema[Any, ChannelId] = Schema.intSchema.contramap(_.channelId)
  private given Schema[Any, ConnectionId] = Schema.intSchema.contramap(_.connectionId)
  private given Schema[Any, User] = gen[Any, User]
  private given Schema[Any, ChatMessage] = gen[Any, ChatMessage]
  private given Schema[ChatService & SessionContext, Queries] = Schema.gen[ChatService & SessionContext, Queries]
  private given Schema[ChatService & Repository & SessionContext, Mutations] = Schema.gen[ChatService & Repository & SessionContext, Mutations]
  private given Schema[ChatService & Repository & SessionContext, Subscriptions] =
    Schema.gen[ChatService & Repository & SessionContext, Subscriptions]

  private given ArgBuilder[UserId] = ArgBuilder.int.map(UserId.apply)
  private given ArgBuilder[ChannelId] = ArgBuilder.int.map(ChannelId.apply)
  private given ArgBuilder[ConnectionId] = ArgBuilder.int.map(ConnectionId.apply)

  lazy val api: GraphQL[ChatService & Repository & SessionContext] =
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
    )
    @@ maxFields (22) // query analyzer that limit query fields
    @@ maxDepth (30) // query analyzer that limit query depth
//      timeout(3.seconds) @@ // wrapper that fails slow queries
//      printSlowQueries(3.seconds)

  //  @@ // wrapper that logs slow queries
//      apolloTracing // wrapper for https://github.com/apollographql/apollo-tracing
  val schema =
    """schema {\n  query: Queries\n  mutation: Mutations\n  subscription: Subscriptions\n}\n\nscalar Instant\n\ninput UserInput {\n  id: Int\n  email: String!\n  name: String!\n  created: Instant!\n  active: Boolean!\n  deleted: Boolean!\n  isAdmin: Boolean!\n}\n\ntype ChatMessage {\n  fromUser: User!\n  msg: String!\n  channelId: Int!\n  toUser: User\n  date: Insant!\n}\n\ntype Mutations {\n  say(msg: String!, channelId: Int!, toUser: UserInput): Boolean!\n}\n\ntype Queries {\n  getRecentMessages(value: Int!): [ChatMessage!]\n}\n\ntype Subscriptions {\n  chatStream(channelId: Int!, connectionId: String!): ChatMessage\n}\n\ntype User {\n  id: Int\n  email: String!\n  name: String!\n  created: Instant!\n  active: Boolean!\n  deleted: Boolean!\n  isAdmin: Boolean!\n}"""
  // Generate client with
  // calibanGenClient /Volumes/Personal/projects/chuti/server/src/main/graphql/chat.schema /Volumes/Personal/projects/chuti/web/src/main/scala/chat/ChatClient.scala

}
