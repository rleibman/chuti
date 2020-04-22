package game

import caliban.client.CalibanClientError.DecodingError
import caliban.client.FieldBuilder._
import caliban.client.Operations._
import caliban.client.SelectionBuilder._
import caliban.client.Value._
import caliban.client._

object GameClient {

  type Json = String

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
      override def encode(value: UserEventType): Value = value match {
        case UserEventType.AbandonedGame => EnumValue("AbandonedGame")
        case UserEventType.Connected     => EnumValue("Connected")
        case UserEventType.Disconnected  => EnumValue("Disconnected")
        case UserEventType.JoinedGame    => EnumValue("JoinedGame")
        case UserEventType.Modified      => EnumValue("Modified")
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
    def gameId[A](innerSelection: SelectionBuilder[GameId, A]): SelectionBuilder[Canta, Option[A]] =
      Field("gameId", OptionOf(Obj(innerSelection)))
    def index:   SelectionBuilder[Canta, Option[Int]] = Field("index", OptionOf(Scalar()))
    def cuantas: SelectionBuilder[Canta, Int] = Field("cuantas", Scalar())
  }

  type ChannelId
  object ChannelId {
    def value: SelectionBuilder[ChannelId, Int] = Field("value", Scalar())
  }

  type Da
  object Da {
    def gameId[A](innerSelection: SelectionBuilder[GameId, A]): SelectionBuilder[Da, Option[A]] =
      Field("gameId", OptionOf(Obj(innerSelection)))
    def index: SelectionBuilder[Da, Option[Int]] = Field("index", OptionOf(Scalar()))
    def ficha[A](innerSelection: SelectionBuilder[Ficha, A]): SelectionBuilder[Da, A] =
      Field("ficha", Obj(innerSelection))
  }

  type DeCaida
  object DeCaida {
    def gameId[A](
      innerSelection: SelectionBuilder[GameId, A]
    ):         SelectionBuilder[DeCaida, Option[A]] = Field("gameId", OptionOf(Obj(innerSelection)))
    def index: SelectionBuilder[DeCaida, Option[Int]] = Field("index", OptionOf(Scalar()))
  }

  type EmpiezaJuego
  object EmpiezaJuego {
    def gameId[A](
      innerSelection: SelectionBuilder[GameId, A]
    ):         SelectionBuilder[EmpiezaJuego, Option[A]] = Field("gameId", OptionOf(Obj(innerSelection)))
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

  type GameId
  object GameId {
    def value: SelectionBuilder[GameId, Int] = Field("value", Scalar())
  }

  type HoyoTecnico
  object HoyoTecnico {
    def gameId[A](
      innerSelection: SelectionBuilder[GameId, A]
    ):         SelectionBuilder[HoyoTecnico, Option[A]] = Field("gameId", OptionOf(Obj(innerSelection)))
    def index: SelectionBuilder[HoyoTecnico, Option[Int]] = Field("index", OptionOf(Scalar()))
  }

  type InviteFriend
  object InviteFriend {
    def gameId[A](
      innerSelection: SelectionBuilder[GameId, A]
    ):         SelectionBuilder[InviteFriend, Option[A]] = Field("gameId", OptionOf(Obj(innerSelection)))
    def index: SelectionBuilder[InviteFriend, Option[Int]] = Field("index", OptionOf(Scalar()))
    def user[A](innerSelection: SelectionBuilder[User, A]): SelectionBuilder[InviteFriend, A] =
      Field("user", Obj(innerSelection))
  }

  type JoinGame
  object JoinGame {
    def gameId[A](
      innerSelection: SelectionBuilder[GameId, A]
    ):         SelectionBuilder[JoinGame, Option[A]] = Field("gameId", OptionOf(Obj(innerSelection)))
    def index: SelectionBuilder[JoinGame, Option[Int]] = Field("index", OptionOf(Scalar()))
    def user[A](innerSelection: SelectionBuilder[User, A]): SelectionBuilder[JoinGame, A] =
      Field("user", Obj(innerSelection))
  }

  type MeRindo
  object MeRindo {
    def gameId[A](
      innerSelection: SelectionBuilder[GameId, A]
    ):         SelectionBuilder[MeRindo, Option[A]] = Field("gameId", OptionOf(Obj(innerSelection)))
    def index: SelectionBuilder[MeRindo, Option[Int]] = Field("index", OptionOf(Scalar()))
  }

  type NoOp
  object NoOp {
    def gameId[A](innerSelection: SelectionBuilder[GameId, A]): SelectionBuilder[NoOp, Option[A]] =
      Field("gameId", OptionOf(Obj(innerSelection)))
    def index: SelectionBuilder[NoOp, Option[Int]] = Field("index", OptionOf(Scalar()))
  }

  type Pide
  object Pide {
    def gameId[A](innerSelection: SelectionBuilder[GameId, A]): SelectionBuilder[Pide, Option[A]] =
      Field("gameId", OptionOf(Obj(innerSelection)))
    def index: SelectionBuilder[Pide, Option[Int]] = Field("index", OptionOf(Scalar()))
    def ficha[A](innerSelection: SelectionBuilder[Ficha, A]): SelectionBuilder[Pide, A] =
      Field("ficha", Obj(innerSelection))
    def estrictaDerecha: SelectionBuilder[Pide, Boolean] = Field("estrictaDerecha", Scalar())
  }

  type PideInicial
  object PideInicial {
    def gameId[A](
      innerSelection: SelectionBuilder[GameId, A]
    ):         SelectionBuilder[PideInicial, Option[A]] = Field("gameId", OptionOf(Obj(innerSelection)))
    def index: SelectionBuilder[PideInicial, Option[Int]] = Field("index", OptionOf(Scalar()))
    def ficha[A](innerSelection: SelectionBuilder[Ficha, A]): SelectionBuilder[PideInicial, A] =
      Field("ficha", Obj(innerSelection))
    def triunfan:        SelectionBuilder[PideInicial, Numero] = Field("triunfan", Scalar())
    def estrictaDerecha: SelectionBuilder[PideInicial, Boolean] = Field("estrictaDerecha", Scalar())
  }

  type PoisonPill
  object PoisonPill {
    def gameId[A](
      innerSelection: SelectionBuilder[GameId, A]
    ):         SelectionBuilder[PoisonPill, Option[A]] = Field("gameId", OptionOf(Obj(innerSelection)))
    def index: SelectionBuilder[PoisonPill, Option[Int]] = Field("index", OptionOf(Scalar()))
  }

  type TerminaJuego
  object TerminaJuego {
    def gameId[A](
      innerSelection: SelectionBuilder[GameId, A]
    ):         SelectionBuilder[TerminaJuego, Option[A]] = Field("gameId", OptionOf(Obj(innerSelection)))
    def index: SelectionBuilder[TerminaJuego, Option[Int]] = Field("index", OptionOf(Scalar()))
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
    def userEventType: SelectionBuilder[UserEvent, UserEventType] = Field("userEventType", Scalar())
  }

  type UserId
  object UserId {
    def value: SelectionBuilder[UserId, Int] = Field("value", Scalar())
  }

  type Queries = RootQuery
  object Queries {
    def getGame(value: Int): SelectionBuilder[RootQuery, Option[Json]] =
      Field("getGame", OptionOf(Scalar()), arguments = List(Argument("value", value)))
    def getGameForUser: SelectionBuilder[RootQuery, Option[Json]] =
      Field("getGameForUser", OptionOf(Scalar()))
  }

  type Mutations = RootMutation
  object Mutations {
    def newGame: SelectionBuilder[RootMutation, Option[Json]] = Field("newGame", OptionOf(Scalar()))
    def joinRandomGame: SelectionBuilder[RootMutation, Option[Json]] =
      Field("joinRandomGame", OptionOf(Scalar()))
    def abandonGame(value: Int): SelectionBuilder[RootMutation, Option[Boolean]] =
      Field("abandonGame", OptionOf(Scalar()), arguments = List(Argument("value", value)))
    def play(gameEvent: Json): SelectionBuilder[RootMutation, Option[Boolean]] =
      Field("play", OptionOf(Scalar()), arguments = List(Argument("gameEvent", gameEvent)))
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
      innerSelection: SelectionBuilder[Json, A]
    ): SelectionBuilder[RootSubscription, A] =
      Field(
        name = "gameStream",
        builder = Obj(innerSelection),
        arguments = List(Argument("gameId", gameId.value))
      )
  }

}
