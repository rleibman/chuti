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

package game

import caliban.client.CalibanClientError.DecodingError
import caliban.client.FieldBuilder._
import caliban.client._
import caliban.client.__Value._
import zio.json.*

object GameClient {

  type Instant = String

  type GameAsJson = String // TODO switch to opaque type

  type PlayEventAsJson = String // TODO switch to opaque type

  type GameEventAsJson = String // TODO switch to opaque type

  sealed trait UserEventType extends scala.Product with scala.Serializable { def value: String }

  object UserEventType {

    given JsonCodec[UserEventType] = DeriveJsonCodec.gen[UserEventType]

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
    ): SelectionBuilder[_root_.caliban.client.Operations.RootQuery, scala.Option[GameAsJson]] =
      _root_.caliban.client.SelectionBuilder.Field("getGame", OptionOf(Scalar()), arguments = List(Argument("value", value, "Int!")(encoder0)))
    def getGameForUser: SelectionBuilder[_root_.caliban.client.Operations.RootQuery, scala.Option[GameAsJson]] =
      _root_.caliban.client.SelectionBuilder.Field("getGameForUser", OptionOf(Scalar()))
    def getFriends[A](
      innerSelection: SelectionBuilder[User, A]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootQuery, scala.Option[List[A]]] =
      _root_.caliban.client.SelectionBuilder.Field("getFriends", OptionOf(ListOf(Obj(innerSelection))))
    def getGameInvites: SelectionBuilder[_root_.caliban.client.Operations.RootQuery, scala.Option[List[GameAsJson]]] =
      _root_.caliban.client.SelectionBuilder.Field("getGameInvites", OptionOf(ListOf(Scalar())))
    def getLoggedInUsers[A](
      innerSelection: SelectionBuilder[User, A]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootQuery, scala.Option[List[A]]] =
      _root_.caliban.client.SelectionBuilder.Field("getLoggedInUsers", OptionOf(ListOf(Obj(innerSelection))))
    def getHistoricalUserGames: SelectionBuilder[_root_.caliban.client.Operations.RootQuery, scala.Option[List[GameAsJson]]] =
      _root_.caliban.client.SelectionBuilder.Field("getHistoricalUserGames", OptionOf(ListOf(Scalar())))

  }

  type Mutations = _root_.caliban.client.Operations.RootMutation
  object Mutations {

    def newGame(
      satoshiPerPoint:   Int
    )(implicit encoder0: ArgEncoder[Int]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootMutation, scala.Option[GameAsJson]] =
      _root_.caliban.client.SelectionBuilder.Field(
        "newGame",
        OptionOf(Scalar()),
        arguments = List(Argument("satoshiPerPoint", satoshiPerPoint, "Int!")(encoder0))
      )
    def newGameSameUsers(
      value:             Int
    )(implicit encoder0: ArgEncoder[Int]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootMutation, scala.Option[GameAsJson]] =
      _root_.caliban.client.SelectionBuilder
        .Field("newGameSameUsers", OptionOf(Scalar()), arguments = List(Argument("value", value, "Int!")(encoder0)))
    def joinRandomGame: SelectionBuilder[_root_.caliban.client.Operations.RootMutation, scala.Option[GameAsJson]] =
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
    ): SelectionBuilder[_root_.caliban.client.Operations.RootMutation, scala.Option[GameAsJson]] =
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
      playEvent:         PlayEventAsJson
    )(implicit encoder0: ArgEncoder[Int],
      encoder1:          ArgEncoder[PlayEventAsJson]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootMutation, scala.Option[Boolean]] =
      _root_.caliban.client.SelectionBuilder.Field(
        "play",
        OptionOf(Scalar()),
        arguments = List(
          Argument("gameId", gameId, "Int!")(encoder0),
          Argument("playEvent", playEvent, "PlayEventAsJson!")(encoder1)
        )
      )

  }

  type Subscriptions = _root_.caliban.client.Operations.RootSubscription
  object Subscriptions {

    def gameStream(
      gameId:            Int,
      connectionId:      Int
    )(implicit encoder0: ArgEncoder[Int],
      encoder1:          ArgEncoder[Int]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootSubscription, scala.Option[GameEventAsJson]] =
      _root_.caliban.client.SelectionBuilder.Field(
        "gameStream",
        OptionOf(Scalar()),
        arguments = List(Argument("gameId", gameId, "Int!")(encoder0), Argument("connectionId", connectionId, "Int!")(encoder1))
      )
    def userStream[A](
      value: Int
    )(
      innerSelection:    SelectionBuilder[UserEvent, A]
    )(implicit encoder0: ArgEncoder[Int]
    ): SelectionBuilder[_root_.caliban.client.Operations.RootSubscription, scala.Option[A]] =
      _root_.caliban.client.SelectionBuilder
        .Field("userStream", OptionOf(Obj(innerSelection)), arguments = List(Argument("value", value, "Int!")(encoder0)))

  }

}
