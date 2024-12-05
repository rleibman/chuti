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

package caliban.client.scalajs

import caliban.client.FieldBuilder._
import caliban.client._
import caliban.client.__Value._

object ChatClient {

  type Instant = String

  type ChatMessage
  object ChatMessage {

    final case class ChatMessageView[FromUserSelection, ToUserSelection](
      fromUser:  FromUserSelection,
      msg:       String,
      channelId: Int,
      date:      Instant,
      toUser:    scala.Option[ToUserSelection]
    )

    type ViewSelection[FromUserSelection, ToUserSelection] =
      SelectionBuilder[ChatMessage, ChatMessageView[FromUserSelection, ToUserSelection]]

    def view[FromUserSelection, ToUserSelection](
      fromUserSelection: SelectionBuilder[User, FromUserSelection],
      toUserSelection:   SelectionBuilder[User, ToUserSelection]
    ): ViewSelection[FromUserSelection, ToUserSelection] =
      (fromUser(fromUserSelection) ~ msg ~ channelId ~ date ~ toUser(toUserSelection)).map {
        case (fromUser, msg, channelId, date, toUser) =>
          ChatMessageView(fromUser, msg, channelId, date, toUser)
      }

    def fromUser[A](innerSelection: SelectionBuilder[User, A]): SelectionBuilder[ChatMessage, A] =
      _root_.caliban.client.SelectionBuilder.Field("fromUser", Obj(innerSelection))
    def msg: SelectionBuilder[ChatMessage, String] = _root_.caliban.client.SelectionBuilder.Field("msg", Scalar())
    def channelId: SelectionBuilder[ChatMessage, Int] =
      _root_.caliban.client.SelectionBuilder.Field("channelId", Scalar())
    def date: SelectionBuilder[ChatMessage, Instant] = _root_.caliban.client.SelectionBuilder.Field("date", Scalar())
    def toUser[A](innerSelection: SelectionBuilder[User, A]): SelectionBuilder[ChatMessage, scala.Option[A]] =
      _root_.caliban.client.SelectionBuilder.Field("toUser", OptionOf(Obj(innerSelection)))

  }

  type User
  object User {

    final case class UserView(
      id:          scala.Option[Int],
      email:       String,
      name:        String,
      created:     Instant,
      lastUpdated: Instant,
      active:      Boolean,
      deleted:     Boolean,
      isAdmin:     Boolean
    )

    type ViewSelection = SelectionBuilder[User, UserView]

    def view: ViewSelection =
      (id ~ email ~ name ~ created ~ lastUpdated ~ active ~ deleted ~ isAdmin).map {
        case (id, email, name, created, lastUpdated, active, deleted, isAdmin) =>
          UserView(id, email, name, created, lastUpdated, active, deleted, isAdmin)
      }

    def id: SelectionBuilder[User, scala.Option[Int]] =
      _root_.caliban.client.SelectionBuilder.Field("id", OptionOf(Scalar()))
    def email:   SelectionBuilder[User, String] = _root_.caliban.client.SelectionBuilder.Field("email", Scalar())
    def name:    SelectionBuilder[User, String] = _root_.caliban.client.SelectionBuilder.Field("name", Scalar())
    def created: SelectionBuilder[User, Instant] = _root_.caliban.client.SelectionBuilder.Field("created", Scalar())
    def lastUpdated: SelectionBuilder[User, Instant] =
      _root_.caliban.client.SelectionBuilder.Field("lastUpdated", Scalar())
    def active:  SelectionBuilder[User, Boolean] = _root_.caliban.client.SelectionBuilder.Field("active", Scalar())
    def deleted: SelectionBuilder[User, Boolean] = _root_.caliban.client.SelectionBuilder.Field("deleted", Scalar())
    def isAdmin: SelectionBuilder[User, Boolean] = _root_.caliban.client.SelectionBuilder.Field("isAdmin", Scalar())

  }

  final case class UserInput(
    id:          scala.Option[Int] = None,
    email:       String,
    name:        String,
    created:     Instant,
    lastUpdated: Instant,
    active:      Boolean,
    deleted:     Boolean,
    isAdmin:     Boolean
  )
  object UserInput {

    implicit val encoder: ArgEncoder[UserInput] = new ArgEncoder[UserInput] {

      override def encode(value: UserInput): __Value =
        __ObjectValue(
          List(
            "id"          -> value.id.fold(__NullValue: __Value)(value => implicitly[ArgEncoder[Int]].encode(value)),
            "email"       -> implicitly[ArgEncoder[String]].encode(value.email),
            "name"        -> implicitly[ArgEncoder[String]].encode(value.name),
            "created"     -> implicitly[ArgEncoder[Instant]].encode(value.created),
            "lastUpdated" -> implicitly[ArgEncoder[Instant]].encode(value.lastUpdated),
            "active"      -> implicitly[ArgEncoder[Boolean]].encode(value.active),
            "deleted"     -> implicitly[ArgEncoder[Boolean]].encode(value.deleted),
            "isAdmin"     -> implicitly[ArgEncoder[Boolean]].encode(value.isAdmin)
          )
        )

    }

  }
  type Queries = _root_.caliban.client.Operations.RootQuery
  object Queries {

    def getRecentMessages[A](
      value: Int
    )(
      innerSelection:    SelectionBuilder[ChatMessage, A]
    )(implicit encoder0: ArgEncoder[Int]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootQuery, scala.Option[List[A]]] =
      _root_.caliban.client.SelectionBuilder.Field(
        "getRecentMessages",
        OptionOf(ListOf(Obj(innerSelection))),
        arguments = List(Argument("value", value, "Int!")(encoder0))
      )

  }

  type Mutations = _root_.caliban.client.Operations.RootMutation
  object Mutations {

    def say(
      msg:       String,
      channelId: Int,
      toUser:    scala.Option[UserInput] = None
    )(implicit
      encoder0: ArgEncoder[String],
      encoder1: ArgEncoder[Int],
      encoder2: ArgEncoder[scala.Option[UserInput]]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootMutation, Boolean] =
      _root_.caliban.client.SelectionBuilder.Field(
        "say",
        Scalar(),
        arguments = List(
          Argument("msg", msg, "String!")(encoder0),
          Argument("channelId", channelId, "Int!")(encoder1),
          Argument("toUser", toUser, "UserInput")(encoder2)
        )
      )

  }

  type Subscriptions = _root_.caliban.client.Operations.RootSubscription
  object Subscriptions {

    def chatStream[A](
      channelId:    Int,
      connectionId: String
    )(
      innerSelection: SelectionBuilder[ChatMessage, A]
    )(implicit
      encoder0: ArgEncoder[Int],
      encoder1: ArgEncoder[String]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootSubscription, scala.Option[A]] =
      _root_.caliban.client.SelectionBuilder.Field(
        "chatStream",
        OptionOf(Obj(innerSelection)),
        arguments = List(
          Argument("channelId", channelId, "Int!")(encoder0),
          Argument("connectionId", connectionId, "String!")(encoder1)
        )
      )

  }

}
