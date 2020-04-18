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

import caliban.client.FieldBuilder._
import caliban.client.SelectionBuilder._
import caliban.client._
import caliban.client.Operations._

object ChatClient {

  type ChatMessage
  object ChatMessage {
    def user[A](innerSelection: SelectionBuilder[User, A]): SelectionBuilder[ChatMessage, A] =
      Field("user", Obj(innerSelection))
    def msg:  SelectionBuilder[ChatMessage, String] = Field("msg", Scalar())
    def date: SelectionBuilder[ChatMessage, LocalDateTime] = Field("date", Scalar())
  }

  type User
  object User {
    def id[A](innerSelection: SelectionBuilder[UserId, A]): SelectionBuilder[User, Option[A]] =
      Field("id", OptionOf(Obj(innerSelection)))
    def email:       SelectionBuilder[User, String] = Field("email", Scalar())
    def name:        SelectionBuilder[User, String] = Field("name", Scalar())
    def created:     SelectionBuilder[User, LocalDateTime] = Field("created", Scalar())
    def lastUpdated: SelectionBuilder[User, LocalDateTime] = Field("lastUpdated", Scalar())
    def lastLoggedIn: SelectionBuilder[User, Option[LocalDateTime]] =
      Field("lastLoggedIn", OptionOf(Scalar()))
    def wallet:  SelectionBuilder[User, Double] = Field("wallet", Scalar())
    def deleted: SelectionBuilder[User, Boolean] = Field("deleted", Scalar())
  }

  type UserId
  object UserId {
    def value: SelectionBuilder[UserId, Int] = Field("value", Scalar())
  }

  type Queries = RootQuery
  object Queries {}

  type Mutations = RootMutation
  object Mutations {
    def say(msg: String): SelectionBuilder[RootMutation, Boolean] =
      Field("say", Scalar(), arguments = List(Argument("msg", msg)))
  }

  type Subscriptions = RootSubscription
  object Subscriptions {
    def chatStream[A](
      innerSelection: SelectionBuilder[ChatMessage, A]
    ): SelectionBuilder[RootSubscription, A] = Field("chatStream", Obj(innerSelection))
  }

}
