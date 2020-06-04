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

import caliban.client.CalibanClientError.DecodingError
import caliban.client.FieldBuilder._
import caliban.client.SelectionBuilder._
import caliban.client._
import caliban.client.Operations._
import caliban.client.Value._

object ChatClient {

  sealed trait UserStatus extends scala.Product with scala.Serializable
  object UserStatus {
    case object Idle extends UserStatus
    case object Invited extends UserStatus
    case object Offline extends UserStatus
    case object Playing extends UserStatus

    implicit val decoder: ScalarDecoder[UserStatus] = {
      case StringValue("Idle")    => Right(UserStatus.Idle)
      case StringValue("Invited") => Right(UserStatus.Invited)
      case StringValue("Offline") => Right(UserStatus.Offline)
      case StringValue("Playing") => Right(UserStatus.Playing)
      case other                  => Left(DecodingError(s"Can't build UserStatus from input $other"))
    }
    implicit val encoder: ArgEncoder[UserStatus] = new ArgEncoder[UserStatus] {
      override def encode(value: UserStatus): Value = value match {
        case UserStatus.Idle    => EnumValue("Idle")
        case UserStatus.Invited => EnumValue("Invited")
        case UserStatus.Offline => EnumValue("Offline")
        case UserStatus.Playing => EnumValue("Playing")
      }
      override def typeName: String = "UserStatus"
    }
  }

  type ChatMessage
  object ChatMessage {
    def fromUser[A](innerSelection: SelectionBuilder[User, A]): SelectionBuilder[ChatMessage, A] =
      Field("fromUser", Obj(innerSelection))
    def msg:       SelectionBuilder[ChatMessage, String] = Field("msg", Scalar())
    def channelId: SelectionBuilder[ChatMessage, Int] = Field("channelId", Scalar())
    def toUser[A](
      innerSelection: SelectionBuilder[User, A]
    ):        SelectionBuilder[ChatMessage, Option[A]] = Field("toUser", OptionOf(Obj(innerSelection)))
    def date: SelectionBuilder[ChatMessage, Long] = Field("date", Scalar())
  }

  type User
  object User {
    def id:          SelectionBuilder[User, Option[Int]] = Field("id", OptionOf(Scalar()))
    def email:       SelectionBuilder[User, String] = Field("email", Scalar())
    def name:        SelectionBuilder[User, String] = Field("name", Scalar())
    def userStatus:  SelectionBuilder[User, UserStatus] = Field("userStatus", Scalar())
    def created:     SelectionBuilder[User, Long] = Field("created", Scalar())
    def lastUpdated: SelectionBuilder[User, Long] = Field("lastUpdated", Scalar())
    def lastLoggedIn: SelectionBuilder[User, Option[Long]] =
      Field("lastLoggedIn", OptionOf(Scalar()))
    def active:  SelectionBuilder[User, Boolean] = Field("active", Scalar())
    def deleted: SelectionBuilder[User, Boolean] = Field("deleted", Scalar())
  }

  case class UserInput(
    id:           Option[Int] = None,
    email:        String,
    name:         String,
    userStatus:   UserStatus,
    created:      Long,
    lastUpdated:  Long,
    lastLoggedIn: Option[Long] = None,
    active:       Boolean,
    deleted:      Boolean
  )
  object UserInput {
    implicit val encoder: ArgEncoder[UserInput] = new ArgEncoder[UserInput] {
      override def encode(value: UserInput): Value =
        ObjectValue(
          List(
            "id" -> value.id.fold(NullValue: Value)(value =>
              implicitly[ArgEncoder[Int]].encode(value)
            ),
            "email"       -> implicitly[ArgEncoder[String]].encode(value.email),
            "name"        -> implicitly[ArgEncoder[String]].encode(value.name),
            "userStatus"  -> implicitly[ArgEncoder[UserStatus]].encode(value.userStatus),
            "created"     -> implicitly[ArgEncoder[Long]].encode(value.created),
            "lastUpdated" -> implicitly[ArgEncoder[Long]].encode(value.lastUpdated),
            "lastLoggedIn" -> value.lastLoggedIn.fold(NullValue: Value)(value =>
              implicitly[ArgEncoder[Long]].encode(value)
            ),
            "active"  -> implicitly[ArgEncoder[Boolean]].encode(value.active),
            "deleted" -> implicitly[ArgEncoder[Boolean]].encode(value.deleted)
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
    ): SelectionBuilder[RootSubscription, A] =
      Field(
        "chatStream",
        Obj(innerSelection),
        arguments = List(Argument("channelId", channelId), Argument("connectionId", connectionId))
      )
  }

}
