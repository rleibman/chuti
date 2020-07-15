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

object ChatClient {

  type LocalDateTime = String

  type ChatMessage
  object ChatMessage {
    def fromUser[A](innerSelection: SelectionBuilder[User, A]): SelectionBuilder[ChatMessage, A] =
      Field("fromUser", Obj(innerSelection))
    def msg:       SelectionBuilder[ChatMessage, String] = Field("msg", Scalar())
    def channelId: SelectionBuilder[ChatMessage, Int] = Field("channelId", Scalar())
    def toUser[A](
      innerSelection: SelectionBuilder[User, A]
    ):        SelectionBuilder[ChatMessage, Option[A]] = Field("toUser", OptionOf(Obj(innerSelection)))
    def date: SelectionBuilder[ChatMessage, LocalDateTime] = Field("date", Scalar())
  }

  type User
  object User {
    def id:      SelectionBuilder[User, Option[Int]] = Field("id", OptionOf(Scalar()))
    def email:   SelectionBuilder[User, String] = Field("email", Scalar())
    def name:    SelectionBuilder[User, String] = Field("name", Scalar())
    def created: SelectionBuilder[User, LocalDateTime] = Field("created", Scalar())
    def active:  SelectionBuilder[User, Boolean] = Field("active", Scalar())
    def deleted: SelectionBuilder[User, Boolean] = Field("deleted", Scalar())
    def isAdmin: SelectionBuilder[User, Boolean] = Field("isAdmin", Scalar())
  }

  case class UserInput(
    id:      Option[Int] = None,
    email:   String,
    name:    String,
    created: LocalDateTime,
    active:  Boolean,
    deleted: Boolean,
    isAdmin: Boolean
  )
  object UserInput {
    implicit val encoder: ArgEncoder[UserInput] = new ArgEncoder[UserInput] {
      override def encode(value: UserInput): Value =
        ObjectValue(
          List(
            "id" -> value.id.fold(NullValue: Value)(value =>
              implicitly[ArgEncoder[Int]].encode(value)
            ),
            "email"   -> implicitly[ArgEncoder[String]].encode(value.email),
            "name"    -> implicitly[ArgEncoder[String]].encode(value.name),
            "created" -> implicitly[ArgEncoder[LocalDateTime]].encode(value.created),
            "active"  -> implicitly[ArgEncoder[Boolean]].encode(value.active),
            "deleted" -> implicitly[ArgEncoder[Boolean]].encode(value.deleted),
            "isAdmin" -> implicitly[ArgEncoder[Boolean]].encode(value.isAdmin)
          )
        )
      override def typeName: String = "UserInput"
    }
  }
  type Queries = RootQuery
  object Queries {
    def getRecentMessages[A](
      value: Int
    )(
      innerSelection: SelectionBuilder[ChatMessage, A]
    ): SelectionBuilder[RootQuery, Option[List[A]]] =
      Field(
        "getRecentMessages",
        OptionOf(ListOf(Obj(innerSelection))),
        arguments = List(Argument("value", value))
      )
  }

  type Mutations = RootMutation
  object Mutations {
    def say(
      msg:       String,
      channelId: Int,
      toUser:    Option[UserInput] = None
    ): SelectionBuilder[RootMutation, Boolean] =
      Field(
        "say",
        Scalar(),
        arguments =
          List(Argument("msg", msg), Argument("channelId", channelId), Argument("toUser", toUser))
      )
  }

  type Subscriptions = RootSubscription
  object Subscriptions {
    def chatStream[A](
      channelId:    Int,
      connectionId: String
    )(
      innerSelection: SelectionBuilder[ChatMessage, A]
    ): SelectionBuilder[RootSubscription, Option[A]] =
      Field(
        "chatStream",
        OptionOf(Obj(innerSelection)),
        arguments = List(Argument("channelId", channelId), Argument("connectionId", connectionId))
      )
  }

}
