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

package chuti

import java.time.LocalDateTime

import chuti.Numero._
import chuti.Triunfo.{SinTriunfos, TriunfoNumero}
import io.circe.{Decoder, Encoder}

object GameException {
  def apply(cause: Throwable): GameException = GameException(cause = Option(cause))
}

case class GameException(
  msg:   String = "",
  cause: Option[Throwable] = None
) extends Exception(msg, cause.orNull)

sealed class Numero(val value: Int)

object Numero {
  def max(
    arriba: Numero,
    abajo:  Numero
  ): Numero = Numero(Math.max(arriba.value, abajo.value))
  def min(
    arriba: Numero,
    abajo:  Numero
  ): Numero = Numero(Math.min(arriba.value, abajo.value))

  case object Numero0 extends Numero(0)

  case object Numero1 extends Numero(1)

  case object Numero2 extends Numero(2)

  case object Numero3 extends Numero(3)

  case object Numero4 extends Numero(4)

  case object Numero5 extends Numero(5)

  case object Numero6 extends Numero(6)

  def values = Seq(Numero0, Numero1, Numero2, Numero3, Numero4, Numero5, Numero6)

  def apply(num: Int): Numero = values(num)

  implicit val decodeNumero: Decoder[Numero] =
    Decoder.forProduct1("value")(apply)

  implicit val encodeNumero: Encoder[Numero] =
    Encoder.forProduct1("value")(_.value)

}

object Ficha {
  def apply(
    a: Numero,
    b: Numero
  ): Ficha = {
    val max = Numero.max(a, b)
    val min = Numero.min(a, b)
    FichaConocida(max, min)
  }

  def fromString(str: String): Ficha = {
    val splitted = str.split(":").map(s => Numero(s.toInt))
    Ficha(splitted(0), splitted(1))
  }

  implicit val decodeFicha: Decoder[Ficha] =
    Decoder.forProduct3[Ficha, String, Numero, Numero]("type", "arriba", "abajo") {
      case ("tapada", _, _) => FichaTapada
      case (_, arriba: Numero, abajo: Numero) => apply(arriba, abajo)
    }

  implicit val encodeFicha: Encoder[Ficha] =
    Encoder.forProduct3[Ficha, String, Numero, Numero]("type", "arriba", "abajo") {
      case FichaTapada                  => ("tapada", Numero0, Numero0)
      case FichaConocida(arriba, abajo) => ("conocida", arriba, abajo)
    }
}

sealed trait Ficha {
  def arriba: Numero
  def abajo:  Numero
  def esMula: Boolean
  def value:  Int
  def es(num:    Numero): Boolean
  def other(num: Numero): Numero
}

case object FichaTapada extends Ficha {
  override def arriba: Numero = throw GameException("No puedes hacer esto con una ficha tapada")
  override def abajo:  Numero = throw GameException("No puedes hacer esto con una ficha tapada")
  override def esMula: Boolean = throw GameException("No puedes hacer esto con una ficha tapada")
  override def value:  Int = throw GameException("No puedes hacer esto con una ficha tapada")
  override def es(num: Numero): Boolean =
    throw GameException("No puedes hacer esto con una ficha tapada")
  override def other(num: Numero): Numero =
    throw GameException("No puedes hacer esto con una ficha tapada")
  override def toString: String = "?:?"
}

case class FichaConocida private[chuti] (
  arriba: Numero,
  abajo:  Numero
) extends Ficha {
  lazy override val esMula: Boolean = arriba == abajo
  lazy override val value:  Int = arriba.value + abajo.value
  override def es(num:    Numero): Boolean = arriba == num || abajo == num
  override def other(num: Numero): Numero = { if (arriba == num) abajo else arriba }
  override def toString: String = s"${arriba.value}:${abajo.value}"
}

case class Fila(
  fichas:  List[Ficha],
  abierta: Boolean = false
)

import chuti.CuantasCantas._

case class Jugador(
  user:          User,
  invited:       Boolean = false,
  fichas:        List[Ficha] = List.empty,
  filas:         List[Fila] = List.empty,
  turno:         Boolean = false, //A quien le tocaba cantar este juego
  cantante:      Boolean = false, //Quien esta cantando este juego
  mano:          Boolean = false, //Quien tiene la mano en este momento
  cuantasCantas: Option[CuantasCantas] = None,
  cuenta:        Seq[Cuenta] = Seq.empty
) {
  def dropFicha(ficha: Ficha): List[Ficha] = {
    fichas.headOption match {
      case None              => fichas
      case Some(FichaTapada) => fichas.drop(1)
      case _                 => fichas.filter(_ != ficha)
    }
  }

}

sealed trait Triunfo extends Product with Serializable

object Triunfo {
  case object SinTriunfos extends Triunfo
  case class TriunfoNumero(num: Numero) extends Triunfo
}

sealed trait GameStatus {
  def value: String
  def enJuego: Boolean = false
}

object GameStatus {

  case object esperandoJugadoresInvitados extends GameStatus {
    override def value: String = "esperandoJugadoresInvitados"
  }
  case object esperandoJugadoresAzar extends GameStatus {
    override def value: String = "esperandoJugadoresAzar"
  }
  case object comienzo extends GameStatus {
    override def value:   String = "comienzo"
    override def enJuego: Boolean = true
  }
  case object cantando extends GameStatus {
    override def value:   String = "cantando"
    override def enJuego: Boolean = true
  }
  case object jugando extends GameStatus {
    override def value:   String = "jugando"
    override def enJuego: Boolean = true
  }
  case object terminado extends GameStatus {
    override def value: String = "terminado"
  }
  object abandonado extends GameStatus {
    override def value: String = "abandonado"
  }
  def withName(str: String): GameStatus = str match {
    case "esperandoJugadoresInvitados" => esperandoJugadoresInvitados
    case "esperandoJugadoresAzar"      => esperandoJugadoresAzar
    case "comienzo"                    => comienzo
    case "cantando"                    => cantando
    case "jugando"                     => jugando
    case "terminado"                   => terminado
    case "abandonado"                  => abandonado
    case other                         => throw GameException(s"Unknown game state $other")
  }

}

import chuti.GameStatus._

sealed trait Borlote
case object Hoyo extends Borlote
case object ElNiñoDelCumpleaños extends Borlote
case object SantaClaus extends Borlote
case object CampanitaSeJuega extends Borlote
case object Helecho extends Borlote

case class GameId(value: Int) extends AnyVal

object Game {
  val laMulota:  Ficha = Ficha(Numero6, Numero6)
  val campanita: Ficha = Ficha(Numero0, Numero1)

  lazy val todaLaFicha: List[Ficha] = (0 to 6)
    .combinations(2).toList.map(seq => Ficha(Numero(seq(0)), Numero(seq(1)))) ++ (0 to 6).map(i =>
    Ficha(Numero(i), Numero(i))
  )

  def calculaFichaGanadora(
    fichas:   Seq[Ficha],
    pidiendo: Ficha,
    triunfo:  Triunfo
  ): Ficha = {
    triunfo match {
      case SinTriunfos =>
        if (pidiendo.esMula) pidiendo
        else fichas.filter(_.es(pidiendo.arriba)).maxBy(_.arriba.value)
      case TriunfoNumero(triunfoVal) =>
        val triunfos = fichas.filter(_.es(triunfoVal))
        if (triunfos.isEmpty) {
          if (pidiendo.esMula) pidiendo
          else fichas.filter(_.es(pidiendo.arriba)).maxBy(_.arriba.value)
        } else {
          if (pidiendo.esMula && pidiendo.es(triunfoVal)) pidiendo
          else triunfos.maxBy(f => if (f.arriba == triunfoVal) f.abajo.value else f.arriba.value)
        }
    }
  }
  def calculaJugadorGanador(
    fichas:   Seq[(UserId, Ficha)],
    pidiendo: Ficha,
    triunfo:  Triunfo
  ): (UserId, Ficha) = {
    triunfo match {
      case SinTriunfos =>
        if (pidiendo.esMula) fichas.find(_._2 == pidiendo).get
        else fichas.filter(_._2.es(pidiendo.arriba)).maxBy(_._2.arriba.value)
      case TriunfoNumero(triunfoVal) =>
        val triunfos = fichas.filter(_._2.es(triunfoVal))
        if (triunfos.isEmpty) {
          if (pidiendo.esMula) fichas.find(_._2 == pidiendo).get
          else fichas.filter(_._2.es(pidiendo.arriba)).maxBy(_._2.arriba.value)
        } else {
          if (pidiendo.esMula && pidiendo.es(triunfoVal)) fichas.find(_._2 == pidiendo).get
          else
            triunfos.maxBy(f =>
              if (f._2.arriba == triunfoVal) f._2.abajo.value else f._2.arriba.value
            )
        }
    }
  }

}

case class Game(
  id:                Option[GameId],
  gameStatus:        GameStatus = comienzo,
  currentEventIndex: Int = 0,
  created:           LocalDateTime = LocalDateTime.now,
  //Game State
  triunfo:         Option[Triunfo] = None,
  enJuego:         List[(UserId, Ficha)] = List.empty,
  estrictaDerecha: Boolean = false,
  jugadores:       List[Jugador] = List.empty,
  pointsPerDollar: Double = 100.0
) {
  lazy val mano: Jugador = {
    jugadores.find(_.mano).get
  }

  lazy val quienCanta: Jugador = jugadores.find(_.cantante).get

  def modifiedPlayers(
    filter:       Jugador => Boolean,
    ifMatches:    Jugador => Jugador,
    ifNotMatches: Jugador => Jugador = identity
  ): List[Jugador] =
    jugadores.map { jugador =>
      if (filter(jugador))
        ifMatches(jugador)
      else
        ifNotMatches(jugador)
    }

  def prevPlayer(jugador: Jugador): Jugador = {
    val index = jugadores.indexOf(jugador)
    if (index == 0) {
      jugadores.last
    } else {
      jugadores(index - 1)
    }
  }
  def prevPlayer(user: User): Jugador = {
    val index = jugadores.indexWhere(_.user.id == user.id)
    if (index == 0) {
      jugadores.last
    } else {
      jugadores(index - 1)
    }
  }
  def nextPlayer(jugador: Jugador): Jugador = {
    val index = jugadores.indexOf(jugador)
    if (index == numPlayers - 1) {
      jugadores.head
    } else {
      jugadores(index + 1)
    }
  }
  def nextPlayer(user: User): Jugador = {
    val index = jugadores.indexWhere(_.user.id == user.id)
    if (index == numPlayers - 1) {
      jugadores.head
    } else {
      jugadores(index + 1)
    }
  }

  def nextIndex: Int = currentEventIndex + 1

  val abandonedPenalty = 10.0
  val numPlayers = 4

  def canTransition(estado: GameStatus): Boolean = {
    estado match {
      case GameStatus.cantando =>
        jugadores.length == numPlayers && (gameStatus == GameStatus.esperandoJugadoresAzar || gameStatus == GameStatus.esperandoJugadoresInvitados) &&
          jugadores.forall(_.invited == false) //Not everyone has accepted
      case _ => false
    }
  }

  //Returns the newly modified state, and any changes to the event
  def applyEvent(
    userOpt: Option[User],
    event:   GameEvent
  ): (Game, GameEvent) = {
    //TODO make sure the event *can* be applied "legally"
    val processed = event.doEvent(userOpt, game = this)
    if (processed._2.index.getOrElse(-1) != currentEventIndex) {
      throw GameException("Error! the event did not gather the correct index")
    }
    (processed._1.copy(currentEventIndex = nextIndex), processed._2)
  }

  //very similar to apply event, but for the client, this re-applies an event that the server has already processed
  def reapplyEvent(event: GameEvent): (Game, GameEvent) = {
    //TODO: Write this
    // if you're removing tiles from people's hand and their tiles are hidden, then just drop 1.
    ???
  }

  override def toString: String = {
    def jugadorStr(jugador: Jugador): String =
      s"""
         |${jugador.user.name}: ${if (jugador.mano) "Me toca cantar" else ""}
         |${jugador.fichas.map(_.toString).mkString(" ")}
         |""".stripMargin

    s"""
       |id         = $id
       |gameStatus = $gameStatus
       |${jugadores.map { jugadorStr }.mkString}
       |""".stripMargin

  }

}

case class Cuenta(
  puntos: Int,
  esHoyo: Boolean
)
