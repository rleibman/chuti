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

import java.time.{Instant, LocalDateTime, ZoneOffset}

import caliban.CalibanError.ExecutionError
import caliban.GraphQL.graphQL
import caliban.Value.IntValue
import caliban.schema.{ArgBuilder, GenericSchema, Schema}
import caliban.wrappers.ApolloTracing.apolloTracing
import caliban.wrappers.Wrappers.{maxDepth, maxFields, printSlowQueries, timeout}
import caliban.{GraphQL, RootResolver}
import chat.ChatService.ChatService
import chuti.User
import dao.SessionProvider
import routes.GameApi.Typeclass
import zio.URIO
import zio.clock.Clock
import zio.console.Console
import zio.duration._
import zio.stream.ZStream

case class SayRequest(msg: String)

object ChatApi extends GenericSchema[ChatService with SessionProvider] {
  case class Queries()
  case class Mutations(say: SayRequest => URIO[SessionProvider with ChatService, Boolean])
  case class Subscriptions(
    chatStream: ZStream[SessionProvider with ChatService, Nothing, ChatMessage]
  )

  implicit val localDateTimeSchema: Typeclass[LocalDateTime] =
    Schema.longSchema.contramap(_.toInstant(ZoneOffset.UTC).toEpochMilli)
  implicit val localDateTimeArgBuilder: ArgBuilder[LocalDateTime] = {
    case value: IntValue => Right(LocalDateTime.ofInstant(Instant.ofEpochMilli(value.toLong), ZoneOffset.UTC))
    case other => Left(ExecutionError(s"Can't build a LocalDateTime from input $other"))
  }
  implicit val userSchema:        Typeclass[User] = gen[User]
  implicit val chatMessageSchema: Typeclass[ChatMessage] = gen[ChatMessage]

  lazy val api: GraphQL[Console with Clock with ChatService with SessionProvider] =
    graphQL(
      RootResolver(
        Queries(
          ),
        Mutations(
          say = msg => ChatService.say(msg).as(true)
        ),
        Subscriptions(
          chatStream = ChatService.chatStream
        )
      )
    ) @@
      maxFields(200) @@ // query analyzer that limit query fields
      maxDepth(30) @@ // query analyzer that limit query depth
      timeout(3.seconds) @@ // wrapper that fails slow queries
      printSlowQueries(500.millis) @@ // wrapper that logs slow queries
      apolloTracing // wrapper for https://github.com/apollographql/apollo-tracing


  //Generate client with calibanGenClient /Volumes/Personal/projects/chuti/server/src/main/graphql/chat.schema /Volumes/Personal/projects/chuti/web/src/main/scala/chat/ChatClient.scala
}
