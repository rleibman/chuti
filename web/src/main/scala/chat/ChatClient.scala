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

import caliban.client.FieldBuilder.*
import caliban.client.*
import caliban.client.__Value.*

object ChatClient {

  type Instant = String

  type ChatMessage
  object ChatMessage {

    def fromUser[A](innerSelection: SelectionBuilder[User, A]): SelectionBuilder[ChatMessage, A] =
      _root_.caliban.client.SelectionBuilder.Field("fromUser", Obj(innerSelection))
    def msg:       SelectionBuilder[ChatMessage, String] = _root_.caliban.client.SelectionBuilder.Field("msg", Scalar())
    def channelId: SelectionBuilder[ChatMessage, Int] = _root_.caliban.client.SelectionBuilder.Field("channelId", Scalar())
    def toUser[A](innerSelection: SelectionBuilder[User, A]): SelectionBuilder[ChatMessage, Option[A]] =
      _root_.caliban.client.SelectionBuilder.Field("toUser", OptionOf(Obj(innerSelection)))
    def date: SelectionBuilder[ChatMessage, Instant] = _root_.caliban.client.SelectionBuilder.Field("date", Scalar())

  }

  type User
  object User {

    def id:      SelectionBuilder[User, Option[Int]] = _root_.caliban.client.SelectionBuilder.Field("id", OptionOf(Scalar()))
    def email:   SelectionBuilder[User, String] = _root_.caliban.client.SelectionBuilder.Field("email", Scalar())
    def name:    SelectionBuilder[User, String] = _root_.caliban.client.SelectionBuilder.Field("name", Scalar())
    def created: SelectionBuilder[User, Instant] = _root_.caliban.client.SelectionBuilder.Field("created", Scalar())
    def active:  SelectionBuilder[User, Boolean] = _root_.caliban.client.SelectionBuilder.Field("active", Scalar())
    def deleted: SelectionBuilder[User, Boolean] = _root_.caliban.client.SelectionBuilder.Field("deleted", Scalar())
    def isAdmin: SelectionBuilder[User, Boolean] = _root_.caliban.client.SelectionBuilder.Field("isAdmin", Scalar())

  }

  final case class UserInput(
    id:      Option[Int] = None,
    email:   String,
    name:    String,
    created: Instant,
    active:  Boolean,
    deleted: Boolean,
    isAdmin: Boolean
  )
  object UserInput {

    given ArgEncoder[UserInput] =
      (value: UserInput) =>
        __ObjectValue(
          List(
            "id"      -> value.id.fold(__NullValue: __Value)(value => summon[ArgEncoder[Int]].encode(value)),
            "email"   -> summon[ArgEncoder[String]].encode(value.email),
            "name"    -> summon[ArgEncoder[String]].encode(value.name),
            "created" -> summon[ArgEncoder[Instant]].encode(value.created),
            "active"  -> summon[ArgEncoder[Boolean]].encode(value.active),
            "deleted" -> summon[ArgEncoder[Boolean]].encode(value.deleted),
            "isAdmin" -> summon[ArgEncoder[Boolean]].encode(value.isAdmin)
          )
        )

  }
  type Queries = _root_.caliban.client.Operations.RootQuery
  object Queries {

    def getRecentMessages[A](
      value: Int
    )(
      innerSelection: SelectionBuilder[ChatMessage, A]
    )(using encoder0: ArgEncoder[Int]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootQuery, Option[List[A]]] =
      _root_.caliban.client.SelectionBuilder.Field(
        "getRecentMessages",
        OptionOf(ListOf(Obj(innerSelection))),
        arguments = List(Argument("value", value, "Int!")(encoder0))
      )

  }

  type Mutations = _root_.caliban.client.Operations.RootMutation
  object Mutations {

    def say(
      msg:            String,
      channelId:      Int,
      toUser:         Option[UserInput] = None
    )(using encoder0: ArgEncoder[String],
      encoder1:       ArgEncoder[Int],
      encoder2:       ArgEncoder[Option[UserInput]]
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
    )(using encoder0: ArgEncoder[Int],
      encoder1:       ArgEncoder[String]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootSubscription, Option[A]] =
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
