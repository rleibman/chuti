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
import chuti.GameEvent

object GameClient {

  type Json = String

  sealed trait Estado extends scala.Product with scala.Serializable
  object Estado {
    case object cantando extends Estado
    case object comienzo extends Estado
    case object jugando extends Estado
    case object terminado extends Estado

    implicit val decoder: ScalarDecoder[Estado] = {
      case StringValue("cantando")  => Right(Estado.cantando)
      case StringValue("comienzo")  => Right(Estado.comienzo)
      case StringValue("jugando")   => Right(Estado.jugando)
      case StringValue("terminado") => Right(Estado.terminado)
      case other                    => Left(DecodingError(s"Can't build Estado from input $other"))
    }
    implicit val encoder: ArgEncoder[Estado] = new ArgEncoder[Estado] {
      override def encode(value: Estado): Value = value match {
        case Estado.cantando  => EnumValue("cantando")
        case Estado.comienzo  => EnumValue("comienzo")
        case Estado.jugando   => EnumValue("jugando")
        case Estado.terminado => EnumValue("terminado")
      }
      override def typeName: String = "Estado"
    }
  }

  sealed trait Numero extends scala.Product with scala.Serializable
  object Numero {
    case object Numero0 extends Numero
    case object Numero1 extends Numero
    case object Numero2 extends Numero
    case object Numero3 extends Numero
    case object Numero4 extends Numero
    case object Numero5 extends Numero
    case object Numero6 extends Numero

    implicit val decoder: ScalarDecoder[Numero] = {
      case StringValue("Numero0") => Right(Numero.Numero0)
      case StringValue("Numero1") => Right(Numero.Numero1)
      case StringValue("Numero2") => Right(Numero.Numero2)
      case StringValue("Numero3") => Right(Numero.Numero3)
      case StringValue("Numero4") => Right(Numero.Numero4)
      case StringValue("Numero5") => Right(Numero.Numero5)
      case StringValue("Numero6") => Right(Numero.Numero6)
      case other                  => Left(DecodingError(s"Can't build Numero from input $other"))
    }
    implicit val encoder: ArgEncoder[Numero] = new ArgEncoder[Numero] {
      override def encode(value: Numero): Value = value match {
        case Numero.Numero0 => EnumValue("Numero0")
        case Numero.Numero1 => EnumValue("Numero1")
        case Numero.Numero2 => EnumValue("Numero2")
        case Numero.Numero3 => EnumValue("Numero3")
        case Numero.Numero4 => EnumValue("Numero4")
        case Numero.Numero5 => EnumValue("Numero5")
        case Numero.Numero6 => EnumValue("Numero6")
      }
      override def typeName: String = "Numero"
    }
  }

  sealed trait UserEventType extends scala.Product with scala.Serializable
  object UserEventType {
    case object Connected extends UserEventType
    case object Disconnected extends UserEventType
    case object Modified extends UserEventType

    implicit val decoder: ScalarDecoder[UserEventType] = {
      case StringValue("Connected")    => Right(UserEventType.Connected)
      case StringValue("Disconnected") => Right(UserEventType.Disconnected)
      case StringValue("Modified")     => Right(UserEventType.Modified)
      case other                       => Left(DecodingError(s"Can't build UserEventType from input $other"))
    }
    implicit val encoder: ArgEncoder[UserEventType] = new ArgEncoder[UserEventType] {
      override def encode(value: UserEventType): Value = value match {
        case UserEventType.Connected    => EnumValue("Connected")
        case UserEventType.Disconnected => EnumValue("Disconnected")
        case UserEventType.Modified     => EnumValue("Modified")
      }
      override def typeName: String = "UserEventType"
    }
  }

  sealed trait UserStatus extends scala.Product with scala.Serializable
  object UserStatus {
    case object InLobby extends UserStatus
    case object Offline extends UserStatus
    case object Playing extends UserStatus

    implicit val decoder: ScalarDecoder[UserStatus] = {
      case StringValue("InLobby") => Right(UserStatus.InLobby)
      case StringValue("Offline") => Right(UserStatus.Offline)
      case StringValue("Playing") => Right(UserStatus.Playing)
      case other                  => Left(DecodingError(s"Can't build UserStatus from input $other"))
    }
    implicit val encoder: ArgEncoder[UserStatus] = new ArgEncoder[UserStatus] {
      override def encode(value: UserStatus): Value = value match {
        case UserStatus.InLobby => EnumValue("InLobby")
        case UserStatus.Offline => EnumValue("Offline")
        case UserStatus.Playing => EnumValue("Playing")
      }
      override def typeName: String = "UserStatus"
    }
  }

  type Canta
  object Canta {
    def gameId[A](innerSelection: SelectionBuilder[GameId, A]): SelectionBuilder[Canta, A] =
      Field("gameId", Obj(innerSelection))
    def index:   SelectionBuilder[Canta, Option[Int]] = Field("index", OptionOf(Scalar()))
    def cuantas: SelectionBuilder[Canta, Int] = Field("cuantas", Scalar())
  }

  type ChannelId
  object ChannelId {
    def value: SelectionBuilder[ChannelId, Int] = Field("value", Scalar())
  }

  type Da
  object Da {
    def gameId[A](innerSelection: SelectionBuilder[GameId, A]): SelectionBuilder[Da, A] =
      Field("gameId", Obj(innerSelection))
    def index: SelectionBuilder[Da, Option[Int]] = Field("index", OptionOf(Scalar()))
    def ficha[A](innerSelection: SelectionBuilder[Ficha, A]): SelectionBuilder[Da, A] =
      Field("ficha", Obj(innerSelection))
  }

  type DeCaida
  object DeCaida {
    def gameId[A](innerSelection: SelectionBuilder[GameId, A]): SelectionBuilder[DeCaida, A] =
      Field("gameId", Obj(innerSelection))
    def index: SelectionBuilder[DeCaida, Option[Int]] = Field("index", OptionOf(Scalar()))
  }

  type EmpiezaJuego
  object EmpiezaJuego {
    def gameId[A](innerSelection: SelectionBuilder[GameId, A]): SelectionBuilder[EmpiezaJuego, A] =
      Field("gameId", Obj(innerSelection))
    def index: SelectionBuilder[EmpiezaJuego, Option[Int]] = Field("index", OptionOf(Scalar()))
    def turno[A](
      innerSelection: SelectionBuilder[User, A]
    ): SelectionBuilder[EmpiezaJuego, Option[A]] = Field("turno", OptionOf(Obj(innerSelection)))
  }

  type Ficha
  object Ficha {
    def arriba: SelectionBuilder[Ficha, Numero] = Field("arriba", Scalar())
    def abajo:  SelectionBuilder[Ficha, Numero] = Field("abajo", Scalar())
  }

  type Fila
  object Fila {
    def fichas[A](innerSelection: SelectionBuilder[Ficha, A]): SelectionBuilder[Fila, List[A]] =
      Field("fichas", ListOf(Obj(innerSelection)))
  }

  type GameId
  object GameId {
    def value: SelectionBuilder[GameId, Int] = Field("value", Scalar())
  }

  type GameState
  object GameState {
    def id[A](innerSelection: SelectionBuilder[GameId, A]): SelectionBuilder[GameState, Option[A]] =
      Field("id", OptionOf(Obj(innerSelection)))
    def jugadores[A](
      innerSelection: SelectionBuilder[Jugador, A]
    ): SelectionBuilder[GameState, List[A]] = Field("jugadores", ListOf(Obj(innerSelection)))
    def enJuego[A](
      innerSelection: SelectionBuilder[Ficha, A]
    ): SelectionBuilder[GameState, List[A]] = Field("enJuego", ListOf(Obj(innerSelection)))
    def triunfo[A](
      onSinTriunfos:   SelectionBuilder[SinTriunfos, A],
      onTriunfanMulas: SelectionBuilder[TriunfanMulas, A],
      onTriunfoNumero: SelectionBuilder[TriunfoNumero, A]
    ): SelectionBuilder[GameState, Option[A]] =
      Field(
        "triunfo",
        OptionOf(
          ChoiceOf(
            Map(
              "SinTriunfos"   -> Obj(onSinTriunfos),
              "TriunfanMulas" -> Obj(onTriunfanMulas),
              "TriunfoNumero" -> Obj(onTriunfoNumero)
            )
          )
        )
      )
    def estado:       SelectionBuilder[GameState, Estado] = Field("estado", Scalar())
    def currentIndex: SelectionBuilder[GameState, Int] = Field("currentIndex", Scalar())
  }

  type HoyoTecnico
  object HoyoTecnico {
    def gameId[A](innerSelection: SelectionBuilder[GameId, A]): SelectionBuilder[HoyoTecnico, A] =
      Field("gameId", Obj(innerSelection))
    def index: SelectionBuilder[HoyoTecnico, Option[Int]] = Field("index", OptionOf(Scalar()))
  }

  type InviteFriend
  object InviteFriend {
    def gameId[A](innerSelection: SelectionBuilder[GameId, A]): SelectionBuilder[InviteFriend, A] =
      Field("gameId", Obj(innerSelection))
    def index: SelectionBuilder[InviteFriend, Option[Int]] = Field("index", OptionOf(Scalar()))
    def user[A](innerSelection: SelectionBuilder[User, A]): SelectionBuilder[InviteFriend, A] =
      Field("user", Obj(innerSelection))
  }

  type JoinGame
  object JoinGame {
    def gameId[A](innerSelection: SelectionBuilder[GameId, A]): SelectionBuilder[JoinGame, A] =
      Field("gameId", Obj(innerSelection))
    def index: SelectionBuilder[JoinGame, Option[Int]] = Field("index", OptionOf(Scalar()))
    def user[A](innerSelection: SelectionBuilder[User, A]): SelectionBuilder[JoinGame, A] =
      Field("user", Obj(innerSelection))
  }

  type Jugador
  object Jugador {
    def user[A](innerSelection: SelectionBuilder[User, A]): SelectionBuilder[Jugador, A] =
      Field("user", Obj(innerSelection))
    def fichas[A](innerSelection: SelectionBuilder[Ficha, A]): SelectionBuilder[Jugador, List[A]] =
      Field("fichas", ListOf(Obj(innerSelection)))
    def casas[A](innerSelection: SelectionBuilder[Fila, A]): SelectionBuilder[Jugador, List[A]] =
      Field("casas", ListOf(Obj(innerSelection)))
    def cantador: SelectionBuilder[Jugador, Boolean] = Field("cantador", Scalar())
    def mano:     SelectionBuilder[Jugador, Boolean] = Field("mano", Scalar())
  }

  type MeRindo
  object MeRindo {
    def gameId[A](innerSelection: SelectionBuilder[GameId, A]): SelectionBuilder[MeRindo, A] =
      Field("gameId", Obj(innerSelection))
    def index: SelectionBuilder[MeRindo, Option[Int]] = Field("index", OptionOf(Scalar()))
  }

  type NoOp
  object NoOp {
    def gameId[A](innerSelection: SelectionBuilder[GameId, A]): SelectionBuilder[NoOp, A] =
      Field("gameId", Obj(innerSelection))
    def index: SelectionBuilder[NoOp, Option[Int]] = Field("index", OptionOf(Scalar()))
  }

  type Pide
  object Pide {
    def gameId[A](innerSelection: SelectionBuilder[GameId, A]): SelectionBuilder[Pide, A] =
      Field("gameId", Obj(innerSelection))
    def index: SelectionBuilder[Pide, Option[Int]] = Field("index", OptionOf(Scalar()))
    def ficha[A](innerSelection: SelectionBuilder[Ficha, A]): SelectionBuilder[Pide, A] =
      Field("ficha", Obj(innerSelection))
    def estrictaDerecha: SelectionBuilder[Pide, Boolean] = Field("estrictaDerecha", Scalar())
  }

  type PideInicial
  object PideInicial {
    def gameId[A](innerSelection: SelectionBuilder[GameId, A]): SelectionBuilder[PideInicial, A] =
      Field("gameId", Obj(innerSelection))
    def index: SelectionBuilder[PideInicial, Option[Int]] = Field("index", OptionOf(Scalar()))
    def ficha[A](innerSelection: SelectionBuilder[Ficha, A]): SelectionBuilder[PideInicial, A] =
      Field("ficha", Obj(innerSelection))
    def triunfan:        SelectionBuilder[PideInicial, Numero] = Field("triunfan", Scalar())
    def estrictaDerecha: SelectionBuilder[PideInicial, Boolean] = Field("estrictaDerecha", Scalar())
  }

  type PoisonPill
  object PoisonPill {
    def gameId[A](innerSelection: SelectionBuilder[GameId, A]): SelectionBuilder[PoisonPill, A] =
      Field("gameId", Obj(innerSelection))
    def index: SelectionBuilder[PoisonPill, Option[Int]] = Field("index", OptionOf(Scalar()))
  }

  type SinTriunfos
  object SinTriunfos {
    def a: SelectionBuilder[SinTriunfos, Option[Boolean]] = Field("_", OptionOf(Scalar()))
  }

  type TerminaJuego
  object TerminaJuego {
    def gameId[A](innerSelection: SelectionBuilder[GameId, A]): SelectionBuilder[TerminaJuego, A] =
      Field("gameId", Obj(innerSelection))
    def index: SelectionBuilder[TerminaJuego, Option[Int]] = Field("index", OptionOf(Scalar()))
  }

  type TriunfanMulas
  object TriunfanMulas {
    def a: SelectionBuilder[TriunfanMulas, Option[Boolean]] = Field("_", OptionOf(Scalar()))
  }

  type TriunfoNumero
  object TriunfoNumero {
    def num: SelectionBuilder[TriunfoNumero, Numero] = Field("num", Scalar())
  }

  type User
  object User {
    def id[A](innerSelection: SelectionBuilder[UserId, A]): SelectionBuilder[User, Option[A]] =
      Field("id", OptionOf(Obj(innerSelection)))
    def email:      SelectionBuilder[User, String] = Field("email", Scalar())
    def name:       SelectionBuilder[User, String] = Field("name", Scalar())
    def userStatus: SelectionBuilder[User, UserStatus] = Field("userStatus", Scalar())
    def currentChannelId[A](
      innerSelection: SelectionBuilder[ChannelId, A]
    ):               SelectionBuilder[User, Option[A]] = Field("currentChannelId", OptionOf(Obj(innerSelection)))
    def created:     SelectionBuilder[User, Long] = Field("created", Scalar())
    def lastUpdated: SelectionBuilder[User, Long] = Field("lastUpdated", Scalar())
    def lastLoggedIn: SelectionBuilder[User, Option[Long]] =
      Field("lastLoggedIn", OptionOf(Scalar()))
    def wallet:  SelectionBuilder[User, Double] = Field("wallet", Scalar())
    def deleted: SelectionBuilder[User, Boolean] = Field("deleted", Scalar())
  }

  type UserEvent
  object UserEvent {
    def user[A](innerSelection: SelectionBuilder[User, A]): SelectionBuilder[UserEvent, A] =
      Field("user", Obj(innerSelection))
    def isFriendOfCurrent: SelectionBuilder[UserEvent, Boolean] =
      Field("isFriendOfCurrent", Scalar())
    def userEventType: SelectionBuilder[UserEvent, UserEventType] = Field("userEventType", Scalar())
  }

  type UserId
  object UserId {
    def value: SelectionBuilder[UserId, Int] = Field("value", Scalar())
  }

  type Queries = RootQuery
  object Queries {
    def getGame[A](
      value: Int
    )(
      innerSelection: SelectionBuilder[GameState, A]
    ): SelectionBuilder[RootQuery, Option[A]] =
      Field("getGame", OptionOf(Obj(innerSelection)), arguments = List(Argument("value", value)))
  }

  type Mutations = RootMutation
  object Mutations {
    def newGame[A](
      innerSelection: SelectionBuilder[GameState, A]
    ): SelectionBuilder[RootMutation, Option[A]] = Field("newGame", OptionOf(Obj(innerSelection)))
    def joinRandomGame[A](
      innerSelection: SelectionBuilder[GameState, A]
    ): SelectionBuilder[RootMutation, Option[A]] =
      Field("joinRandomGame", OptionOf(Obj(innerSelection)))
    def abandonGame(value: Int): SelectionBuilder[RootMutation, Option[Boolean]] =
      Field("abandonGame", OptionOf(Scalar()), arguments = List(Argument("value", value)))
    def play[A](
      json: Json
    )(
      innerSelection: SelectionBuilder[GameState, A]
    ): SelectionBuilder[RootMutation, Option[A]] =
      Field("play", OptionOf(Obj(innerSelection)), arguments = List(Argument("json", json)))
  }

  type Subscriptions = RootSubscription
  object Subscriptions {
    def userStream[A](
      innerSelection: SelectionBuilder[UserEvent, A]
    ): SelectionBuilder[RootSubscription, A] =
      Field(
        name = "userStream",
        builder = Obj(innerSelection)
      )
    def gameStream[A](
      gameId: chuti.GameId
    )(
      innerSelection: SelectionBuilder[GameEvent, A]
    ): SelectionBuilder[RootSubscription, A] =
      Field(
        name = "gameStream",
        builder = Obj(innerSelection),
        arguments = List(Argument("gameId", gameId.value))
      )

  }

}
