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

import caliban.client.CalibanClientError.DecodingError
import caliban.client.FieldBuilder._
import caliban.client._
import caliban.client.__Value._

object GameClient {

  type Instant = String

  sealed trait UserEventType extends scala.Product with scala.Serializable { def value: String }
  object UserEventType {

    case object AbandonedGame extends UserEventType { val value: String = "AbandonedGame" }
    case object Connected extends UserEventType { val value: String = "Connected" }
    case object Disconnected extends UserEventType { val value: String = "Disconnected" }
    case object JoinedGame extends UserEventType { val value: String = "JoinedGame" }
    case object Modified extends UserEventType { val value: String = "Modified" }

    implicit val decoder: ScalarDecoder[UserEventType] = {
      case __StringValue("AbandonedGame") => Right(UserEventType.AbandonedGame)
      case __StringValue("Connected")     => Right(UserEventType.Connected)
      case __StringValue("Disconnected")  => Right(UserEventType.Disconnected)
      case __StringValue("JoinedGame")    => Right(UserEventType.JoinedGame)
      case __StringValue("Modified")      => Right(UserEventType.Modified)
      case other                          => Left(DecodingError(s"Can't build UserEventType from input $other"))
    }
    implicit val encoder: ArgEncoder[UserEventType] = {
      case UserEventType.AbandonedGame => __EnumValue("AbandonedGame")
      case UserEventType.Connected     => __EnumValue("Connected")
      case UserEventType.Disconnected  => __EnumValue("Disconnected")
      case UserEventType.JoinedGame    => __EnumValue("JoinedGame")
      case UserEventType.Modified      => __EnumValue("Modified")
    }

    val values: scala.collection.immutable.Vector[UserEventType] =
      scala.collection.immutable.Vector(AbandonedGame, Connected, Disconnected, JoinedGame, Modified)

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

    def id:          SelectionBuilder[User, scala.Option[Int]] = _root_.caliban.client.SelectionBuilder.Field("id", OptionOf(Scalar()))
    def email:       SelectionBuilder[User, String] = _root_.caliban.client.SelectionBuilder.Field("email", Scalar())
    def name:        SelectionBuilder[User, String] = _root_.caliban.client.SelectionBuilder.Field("name", Scalar())
    def created:     SelectionBuilder[User, Instant] = _root_.caliban.client.SelectionBuilder.Field("created", Scalar())
    def lastUpdated: SelectionBuilder[User, Instant] = _root_.caliban.client.SelectionBuilder.Field("lastUpdated", Scalar())
    def active:      SelectionBuilder[User, Boolean] = _root_.caliban.client.SelectionBuilder.Field("active", Scalar())
    def deleted:     SelectionBuilder[User, Boolean] = _root_.caliban.client.SelectionBuilder.Field("deleted", Scalar())
    def isAdmin:     SelectionBuilder[User, Boolean] = _root_.caliban.client.SelectionBuilder.Field("isAdmin", Scalar())

  }

  type UserEvent
  object UserEvent {

    final case class UserEventView[UserSelection](
      user:          UserSelection,
      userEventType: UserEventType,
      gameId:        scala.Option[Int]
    )

    type ViewSelection[UserSelection] = SelectionBuilder[UserEvent, UserEventView[UserSelection]]

    def view[UserSelection](userSelection: SelectionBuilder[User, UserSelection]): ViewSelection[UserSelection] =
      (user(userSelection) ~ userEventType ~ gameId).map { case (user, userEventType, gameId) => UserEventView(user, userEventType, gameId) }

    def user[A](innerSelection: SelectionBuilder[User, A]): SelectionBuilder[UserEvent, A] =
      _root_.caliban.client.SelectionBuilder.Field("user", Obj(innerSelection))
    def userEventType: SelectionBuilder[UserEvent, UserEventType] = _root_.caliban.client.SelectionBuilder.Field("userEventType", Scalar())
    def gameId:        SelectionBuilder[UserEvent, scala.Option[Int]] = _root_.caliban.client.SelectionBuilder.Field("gameId", OptionOf(Scalar()))

  }

  type Queries = _root_.caliban.client.Operations.RootQuery
  object Queries {

    def getGame(
      value:             Int
    )(implicit encoder0: ArgEncoder[Int]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootQuery, scala.Option[zio.json.ast.Json]] =
      _root_.caliban.client.SelectionBuilder.Field("getGame", OptionOf(Scalar()), arguments = List(Argument("value", value, "Int!")(encoder0)))
    def getGameForUser: SelectionBuilder[_root_.caliban.client.Operations.RootQuery, scala.Option[zio.json.ast.Json]] =
      _root_.caliban.client.SelectionBuilder.Field("getGameForUser", OptionOf(Scalar()))
    def getFriends[A](
      innerSelection: SelectionBuilder[User, A]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootQuery, scala.Option[List[A]]] =
      _root_.caliban.client.SelectionBuilder.Field("getFriends", OptionOf(ListOf(Obj(innerSelection))))
    def getGameInvites: SelectionBuilder[_root_.caliban.client.Operations.RootQuery, scala.Option[List[zio.json.ast.Json]]] =
      _root_.caliban.client.SelectionBuilder.Field("getGameInvites", OptionOf(ListOf(Scalar())))
    def getLoggedInUsers[A](
      innerSelection: SelectionBuilder[User, A]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootQuery, scala.Option[List[A]]] =
      _root_.caliban.client.SelectionBuilder.Field("getLoggedInUsers", OptionOf(ListOf(Obj(innerSelection))))
    def getHistoricalUserGames: SelectionBuilder[_root_.caliban.client.Operations.RootQuery, scala.Option[List[zio.json.ast.Json]]] =
      _root_.caliban.client.SelectionBuilder.Field("getHistoricalUserGames", OptionOf(ListOf(Scalar())))

  }

  type Mutations = _root_.caliban.client.Operations.RootMutation
  object Mutations {

    def newGame(
      satoshiPerPoint:   Int
    )(implicit encoder0: ArgEncoder[Int]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootMutation, scala.Option[zio.json.ast.Json]] =
      _root_.caliban.client.SelectionBuilder.Field(
        "newGame",
        OptionOf(Scalar()),
        arguments = List(Argument("satoshiPerPoint", satoshiPerPoint, "Int!")(encoder0))
      )
    def newGameSameUsers(
      value:             Int
    )(implicit encoder0: ArgEncoder[Int]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootMutation, scala.Option[zio.json.ast.Json]] =
      _root_.caliban.client.SelectionBuilder
        .Field("newGameSameUsers", OptionOf(Scalar()), arguments = List(Argument("value", value, "Int!")(encoder0)))
    def joinRandomGame: SelectionBuilder[_root_.caliban.client.Operations.RootMutation, scala.Option[zio.json.ast.Json]] =
      _root_.caliban.client.SelectionBuilder.Field("joinRandomGame", OptionOf(Scalar()))
    def abandonGame(
      value:             Int
    )(implicit encoder0: ArgEncoder[Int]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootMutation, scala.Option[Boolean]] =
      _root_.caliban.client.SelectionBuilder.Field("abandonGame", OptionOf(Scalar()), arguments = List(Argument("value", value, "Int!")(encoder0)))
    def inviteByEmail(
      name:              String,
      email:             String,
      gameId:            Int
    )(implicit encoder0: ArgEncoder[String],
      encoder1:          ArgEncoder[String],
      encoder2:          ArgEncoder[Int]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootMutation, scala.Option[Boolean]] =
      _root_.caliban.client.SelectionBuilder.Field(
        "inviteByEmail",
        OptionOf(Scalar()),
        arguments = List(
          Argument("name", name, "String!")(encoder0),
          Argument("email", email, "String!")(encoder1),
          Argument("gameId", gameId, "Int!")(encoder2)
        )
      )
    def startGame(
      value:             Int
    )(implicit encoder0: ArgEncoder[Int]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootMutation, scala.Option[Boolean]] =
      _root_.caliban.client.SelectionBuilder.Field("startGame", OptionOf(Scalar()), arguments = List(Argument("value", value, "Int!")(encoder0)))
    def inviteToGame(
      userId:            Int,
      gameId:            Int
    )(implicit encoder0: ArgEncoder[Int],
      encoder1:          ArgEncoder[Int]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootMutation, scala.Option[Boolean]] =
      _root_.caliban.client.SelectionBuilder.Field(
        "inviteToGame",
        OptionOf(Scalar()),
        arguments = List(Argument("userId", userId, "Int!")(encoder0), Argument("gameId", gameId, "Int!")(encoder1))
      )
    def acceptGameInvitation(
      value:             Int
    )(implicit encoder0: ArgEncoder[Int]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootMutation, scala.Option[zio.json.ast.Json]] =
      _root_.caliban.client.SelectionBuilder
        .Field("acceptGameInvitation", OptionOf(Scalar()), arguments = List(Argument("value", value, "Int!")(encoder0)))
    def declineGameInvitation(
      value:             Int
    )(implicit encoder0: ArgEncoder[Int]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootMutation, scala.Option[Boolean]] =
      _root_.caliban.client.SelectionBuilder
        .Field("declineGameInvitation", OptionOf(Scalar()), arguments = List(Argument("value", value, "Int!")(encoder0)))
    def cancelUnacceptedInvitations(
      value:             Int
    )(implicit encoder0: ArgEncoder[Int]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootMutation, scala.Option[Boolean]] =
      _root_.caliban.client.SelectionBuilder.Field(
        "cancelUnacceptedInvitations",
        OptionOf(Scalar()),
        arguments = List(Argument("value", value, "Int!")(encoder0))
      )
    def friend(
      value:             Int
    )(implicit encoder0: ArgEncoder[Int]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootMutation, scala.Option[Boolean]] =
      _root_.caliban.client.SelectionBuilder.Field("friend", OptionOf(Scalar()), arguments = List(Argument("value", value, "Int!")(encoder0)))
    def unfriend(
      value:             Int
    )(implicit encoder0: ArgEncoder[Int]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootMutation, scala.Option[Boolean]] =
      _root_.caliban.client.SelectionBuilder.Field("unfriend", OptionOf(Scalar()), arguments = List(Argument("value", value, "Int!")(encoder0)))
    def play(
      gameId:            Int,
      gameEvent:         zio.json.ast.Json
    )(implicit encoder0: ArgEncoder[Int],
      encoder1:          ArgEncoder[zio.json.ast.Json]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootMutation, scala.Option[Boolean]] =
      _root_.caliban.client.SelectionBuilder.Field(
        "play",
        OptionOf(Scalar()),
        arguments = List(Argument("gameId", gameId, "Int!")(encoder0), Argument("gameEvent", gameEvent, "Json!")(encoder1))
      )

  }

  type Subscriptions = _root_.caliban.client.Operations.RootSubscription
  object Subscriptions {

    def gameStream(
      gameId:            Int,
      connectionId:      String
    )(implicit encoder0: ArgEncoder[Int],
      encoder1:          ArgEncoder[String]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootSubscription, scala.Option[zio.json.ast.Json]] =
      _root_.caliban.client.SelectionBuilder.Field(
        "gameStream",
        OptionOf(Scalar()),
        arguments = List(
          Argument("gameId", gameId, "Int!")(encoder0),
          Argument("connectionId", connectionId, "String!")(encoder1)
        )
      )
    def userStream[A](
      value: String
    )(
      innerSelection:    SelectionBuilder[UserEvent, A]
    )(implicit encoder0: ArgEncoder[String]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootSubscription, scala.Option[A]] =
      _root_.caliban.client.SelectionBuilder.Field(
        "userStream",
        OptionOf(Obj(innerSelection)),
        arguments = List(Argument("value", value, "String!")(encoder0))
      )

  }

}
