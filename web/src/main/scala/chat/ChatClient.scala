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

import caliban.client.FieldBuilder._
import caliban.client.SelectionBuilder._
import caliban.client._
import caliban.client.Operations._
import caliban.client.Value._
import chuti.ChannelId

object ChatClient {

  type ChatMessage
  object ChatMessage {
    def fromUser[A](innerSelection: SelectionBuilder[User, A]): SelectionBuilder[ChatMessage, A] =
      Field("fromUser", Obj(innerSelection))
    def msg: SelectionBuilder[ChatMessage, String] = Field("msg", Scalar())
    def toUser[A](
      innerSelection: SelectionBuilder[User, A]
    ):        SelectionBuilder[ChatMessage, Option[A]] = Field("toUser", OptionOf(Obj(innerSelection)))
    def date: SelectionBuilder[ChatMessage, Long] = Field("date", Scalar())
  }

  type User
  object User {
    def id[A](innerSelection: SelectionBuilder[UserId, A]): SelectionBuilder[User, Option[A]] =
      Field("id", OptionOf(Obj(innerSelection)))
    def email:       SelectionBuilder[User, String] = Field("email", Scalar())
    def name:        SelectionBuilder[User, String] = Field("name", Scalar())
    def created:     SelectionBuilder[User, Long] = Field("created", Scalar())
    def lastUpdated: SelectionBuilder[User, Long] = Field("lastUpdated", Scalar())
    def lastLoggedIn: SelectionBuilder[User, Option[Long]] =
      Field("lastLoggedIn", OptionOf(Scalar()))
    def wallet:  SelectionBuilder[User, Double] = Field("wallet", Scalar())
    def deleted: SelectionBuilder[User, Boolean] = Field("deleted", Scalar())
  }

  type UserId
  object UserId {
    def value: SelectionBuilder[UserId, Int] = Field("value", Scalar())
  }

  case class UserIdInput(value: Int)
  object UserIdInput {
    implicit val encoder: ArgEncoder[UserIdInput] = new ArgEncoder[UserIdInput] {
      override def encode(value: UserIdInput): Value =
        ObjectValue(List("value" -> implicitly[ArgEncoder[Int]].encode(value.value)))
      override def typeName: String = "UserIdInput"
    }
  }
  case class UserInput(
    id:           Option[UserIdInput] = None,
    email:        String,
    name:         String,
    created:      Long,
    lastUpdated:  Long,
    lastLoggedIn: Option[Long] = None,
    wallet:       Double,
    deleted:      Boolean
  )
  object UserInput {
    implicit val encoder: ArgEncoder[UserInput] = new ArgEncoder[UserInput] {
      override def encode(value: UserInput): Value =
        ObjectValue(
          List(
            "id" -> value.id.fold(NullValue: Value)(value =>
              implicitly[ArgEncoder[UserIdInput]].encode(value)
            ),
            "email"       -> implicitly[ArgEncoder[String]].encode(value.email),
            "name"        -> implicitly[ArgEncoder[String]].encode(value.name),
            "created"     -> implicitly[ArgEncoder[Long]].encode(value.created),
            "lastUpdated" -> implicitly[ArgEncoder[Long]].encode(value.lastUpdated),
            "lastLoggedIn" -> value.lastLoggedIn.fold(NullValue: Value)(value =>
              implicitly[ArgEncoder[Long]].encode(value)
            ),
            "wallet"  -> implicitly[ArgEncoder[Double]].encode(value.wallet),
            "deleted" -> implicitly[ArgEncoder[Boolean]].encode(value.deleted)
          )
        )
      override def typeName: String = "UserInput"
    }
  }
  type Queries = RootQuery
  object Queries {}

  type Mutations = RootMutation
  object Mutations {
    def say(
      msg:    String,
      toUser: Option[UserInput] = None
    ): SelectionBuilder[RootMutation, Boolean] =
      Field("say", Scalar(), arguments = List(Argument("msg", msg), Argument("toUser", toUser)))
  }

  type Subscriptions = RootSubscription
  object Subscriptions {
    def chatStream[A](
      channelId: ChannelId
    )(
      innerSelection: SelectionBuilder[ChatMessage, A]
    ): SelectionBuilder[RootSubscription, A] =
      Field(
        name = "chatStream",
        builder = Obj(innerSelection),
        arguments = List(Argument("channelId", channelId.value))
      )
  }

}
