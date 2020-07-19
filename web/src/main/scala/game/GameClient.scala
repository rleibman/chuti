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

package game

import caliban.client.CalibanClientError.DecodingError
import caliban.client.FieldBuilder._
import caliban.client.SelectionBuilder._
import caliban.client._
import caliban.client.Operations._
import caliban.client.Value._

object GameClient {

  type Json = io.circe.Json
  type LocalDateTime = String

  sealed trait UserEventType extends scala.Product with scala.Serializable
  object UserEventType {
    case object AbandonedGame extends UserEventType
    case object Connected extends UserEventType
    case object Disconnected extends UserEventType
    case object JoinedGame extends UserEventType
    case object Modified extends UserEventType

    implicit val decoder: ScalarDecoder[UserEventType] = {
      case StringValue("AbandonedGame") => Right(UserEventType.AbandonedGame)
      case StringValue("Connected")     => Right(UserEventType.Connected)
      case StringValue("Disconnected")  => Right(UserEventType.Disconnected)
      case StringValue("JoinedGame")    => Right(UserEventType.JoinedGame)
      case StringValue("Modified")      => Right(UserEventType.Modified)
      case other                        => Left(DecodingError(s"Can't build UserEventType from input $other"))
    }
    implicit val encoder: ArgEncoder[UserEventType] = new ArgEncoder[UserEventType] {
      override def encode(value: UserEventType): Value =
        value match {
          case UserEventType.AbandonedGame => EnumValue("AbandonedGame")
          case UserEventType.Connected     => EnumValue("Connected")
          case UserEventType.Disconnected  => EnumValue("Disconnected")
          case UserEventType.JoinedGame    => EnumValue("JoinedGame")
          case UserEventType.Modified      => EnumValue("Modified")
        }
      override def typeName: String = "UserEventType"
    }
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

  type UserEvent
  object UserEvent {
    def user[A](innerSelection: SelectionBuilder[User, A]): SelectionBuilder[UserEvent, A] =
      Field("user", Obj(innerSelection))
    def userEventType: SelectionBuilder[UserEvent, UserEventType] = Field("userEventType", Scalar())
    def gameId:        SelectionBuilder[UserEvent, Option[Int]] = Field("gameId", OptionOf(Scalar()))
  }

  type Queries = RootQuery
  object Queries {
    def getGame(value: Int): SelectionBuilder[RootQuery, Option[Json]] =
      Field("getGame", OptionOf(Scalar()), arguments = List(Argument("value", value)))
    def getGameForUser: SelectionBuilder[RootQuery, Option[Json]] =
      Field("getGameForUser", OptionOf(Scalar()))
    def getFriends[A](
      innerSelection: SelectionBuilder[User, A]
    ): SelectionBuilder[RootQuery, Option[List[A]]] =
      Field("getFriends", OptionOf(ListOf(Obj(innerSelection))))
    def getGameInvites: SelectionBuilder[RootQuery, Option[List[Json]]] =
      Field("getGameInvites", OptionOf(ListOf(Scalar())))
    def getLoggedInUsers[A](
      innerSelection: SelectionBuilder[User, A]
    ): SelectionBuilder[RootQuery, Option[List[A]]] =
      Field("getLoggedInUsers", OptionOf(ListOf(Obj(innerSelection))))
  }

  type Mutations = RootMutation
  object Mutations {
    def newGame(satoshiPerPoint: Int): SelectionBuilder[RootMutation, Option[Json]] =
      Field(
        "newGame",
        OptionOf(Scalar()),
        arguments = List(Argument("satoshiPerPoint", satoshiPerPoint))
      )
    def newGameSameUsers(value: Int): SelectionBuilder[RootMutation, Option[Json]] =
      Field("newGameSameUsers", OptionOf(Scalar()), arguments = List(Argument("value", value)))
    def joinRandomGame: SelectionBuilder[RootMutation, Option[Json]] =
      Field("joinRandomGame", OptionOf(Scalar()))
    def abandonGame(value: Int): SelectionBuilder[RootMutation, Option[Boolean]] =
      Field("abandonGame", OptionOf(Scalar()), arguments = List(Argument("value", value)))
    def inviteByEmail(
      name:   String,
      email:  String,
      gameId: Int
    ): SelectionBuilder[RootMutation, Option[Boolean]] =
      Field(
        "inviteByEmail",
        OptionOf(Scalar()),
        arguments =
          List(Argument("name", name), Argument("email", email), Argument("gameId", gameId))
      )
    def inviteToGame(
      userId: Int,
      gameId: Int
    ): SelectionBuilder[RootMutation, Option[Boolean]] =
      Field(
        "inviteToGame",
        OptionOf(Scalar()),
        arguments = List(Argument("userId", userId), Argument("gameId", gameId))
      )
    def acceptGameInvitation(value: Int): SelectionBuilder[RootMutation, Option[Json]] =
      Field("acceptGameInvitation", OptionOf(Scalar()), arguments = List(Argument("value", value)))
    def declineGameInvitation(value: Int): SelectionBuilder[RootMutation, Option[Boolean]] =
      Field("declineGameInvitation", OptionOf(Scalar()), arguments = List(Argument("value", value)))
    def cancelUnacceptedInvitations(value: Int): SelectionBuilder[RootMutation, Option[Boolean]] =
      Field(
        "cancelUnacceptedInvitations",
        OptionOf(Scalar()),
        arguments = List(Argument("value", value))
      )
    def friend(value: Int): SelectionBuilder[RootMutation, Option[Boolean]] =
      Field("friend", OptionOf(Scalar()), arguments = List(Argument("value", value)))
    def unfriend(value: Int): SelectionBuilder[RootMutation, Option[Boolean]] =
      Field("unfriend", OptionOf(Scalar()), arguments = List(Argument("value", value)))
    def play(
      gameId:    Int,
      gameEvent: Json
    ): SelectionBuilder[RootMutation, Option[Boolean]] =
      Field(
        "play",
        OptionOf(Scalar()),
        arguments = List(Argument("gameId", gameId), Argument("gameEvent", gameEvent))
      )
  }

  type Subscriptions = RootSubscription
  object Subscriptions {
    def gameStream(
      gameId:       Int,
      connectionId: String
    ): SelectionBuilder[RootSubscription, Option[Json]] =
      Field(
        "gameStream",
        OptionOf(Scalar()),
        arguments = List(Argument("gameId", gameId), Argument("connectionId", connectionId))
      )
    def userStream[A](
      value: String
    )(
      innerSelection: SelectionBuilder[UserEvent, A]
    ): SelectionBuilder[RootSubscription, Option[A]] =
      Field("userStream", OptionOf(Obj(innerSelection)), arguments = List(Argument("value", value)))
  }

}
