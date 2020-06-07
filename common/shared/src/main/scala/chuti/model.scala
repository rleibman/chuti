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

import scala.annotation.tailrec

object GameException {
  def apply(cause: Throwable): GameException = GameException(cause = Option(cause))
}

case class GameException(
  msg:   String = "",
  cause: Option[Throwable] = None
) extends Exception(msg, cause.orNull) {
  this.printStackTrace()
}

sealed class Numero(val value: Int) {
  override def toString: String = value.toString
}

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

object CuantasCantas {
  def byNum(cuantas: Int): CuantasCantas = cuantas match {
    case 5 => Canto5
    case 6 => Canto6
    case 7 => CantoTodas
    case _ => Casa
  }

  sealed abstract class CuantasCantas protected (
    val numFilas:  Int,
    val score:     Int,
    val prioridad: Int
  ) {}

  case object Casa extends CuantasCantas(4, 4, 4) {
    override def toString: String = "Casa"
  }
  case object Canto5 extends CuantasCantas(5, 5, 5) {
    override def toString: String = "Cinco"
  }
  case object Canto6 extends CuantasCantas(6, 6, 6) {
    override def toString: String = "Seis"
  }
  case object Canto7 extends CuantasCantas(7, 7, 7) {
    override def toString: String = "Siete (porque le voy al America)"
  }
  case object CantoTodas extends CuantasCantas(7, 21, 8) {
    override def toString: String = "Todas!"
  }
  case object Buenas extends CuantasCantas(-1, 0, -1) {
    override def toString: String = "Buenas"
  }
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

object Fila {
  def apply(
    abierta: Boolean,
    fichas:  Ficha*
  ): Fila = new Fila(fichas.toSeq, abierta)
  def apply(fichas: Ficha*): Fila = new Fila(fichas.toSeq, true)
}

case class Fila private[chuti] (
  fichas:  Seq[Ficha],
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
  lazy val yaSeHizo: Boolean = {
    if (!cantante) {
      throw GameException("Si no canta no se puede hacer!!")
    }
    filas.size >= cuantasCantas.fold(throw GameException("Si no canta no se puede hacer!!"))(
      _.numFilas
    )
  }

  def dropFicha(ficha: Ficha): List[Ficha] = {
    fichas.headOption match {
      case None              => fichas
      case Some(FichaTapada) => fichas.drop(1)
      case _                 => fichas.filter(_ != ficha)
    }
  }

  lazy val id: Option[UserId] = user.id

}

sealed trait Triunfo extends Product with Serializable

object Triunfo {
  case object SinTriunfos extends Triunfo
  case class TriunfoNumero(num: Numero) extends Triunfo
}

sealed trait GameStatus {
  def value: String
  def enJuego: Boolean = false
  def acabado: Boolean = false
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
  case object requiereSopa extends GameStatus {
    override def value:   String = "requiereSopa"
    override def enJuego: Boolean = true
  }
  object abandonado extends GameStatus {
    override def value: String = "abandonado"
    override def acabado: Boolean = true
  }
  object partidoTerminado extends GameStatus {
    override def value: String = "partidoTerminado"
    override def acabado: Boolean = true
  }
  def withName(str: String): GameStatus = str match {
    case "esperandoJugadoresInvitados" => esperandoJugadoresInvitados
    case "esperandoJugadoresAzar"      => esperandoJugadoresAzar
    case "comienzo"                    => comienzo
    case "cantando"                    => cantando
    case "jugando"                     => jugando
    case "requiereSopa"                => requiereSopa
    case "partidoTerminado"            => partidoTerminado
    case "abandonado"                  => abandonado
    case other                         => throw GameException(s"Unknown game state $other")
  }

}

import chuti.GameStatus._

sealed trait Borlote
object Borlote {
  case object Hoyo extends Borlote
  case object HoyoTecnico extends Borlote
  case object ConTuHoyoMeFui extends Borlote // TODO
  case object ElNiñoDelCumpleaños extends Borlote
  case object SantaClaus extends Borlote
  case object CampanitaSeJuega extends Borlote
  case object Helecho extends Borlote
}

case class GameId(value: Int) extends AnyVal

object Game {
  val laMulota:  Ficha = Ficha(Numero6, Numero6)
  val campanita: Ficha = Ficha(Numero0, Numero1)

  lazy val todaLaFicha: List[Ficha] = (0 to 6)
    .combinations(2).toList.map(seq => Ficha(Numero(seq(0)), Numero(seq(1)))) ++ (0 to 6).map(i =>
    Ficha(Numero(i), Numero(i))
  )

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
  satoshiPerPoint: Double = 100.0
) {

  @transient
  lazy val channelId: Option[ChannelId] = id.map(i => ChannelId(i.value))
  @transient
  lazy val mano: Jugador = jugadores.find(_.mano).get
  @transient
  lazy val quienCanta: Jugador = jugadores.find(_.cantante).get
  val abandonedPenalty = 10 //Times the satoshiPerPoint of the game
  val numPlayers = 4



  //Dado el triunfo, cuantas filas se pueden hacer con la siguiente mano
  def cuantasDeCaida(
    fichas:    Seq[Ficha],
    remainder: Seq[Ficha]
  ): Seq[Fila] = {
    @tailrec def loop(
      fichas:    Seq[Ficha],
      remainder: Seq[Ficha],
      filas:     Seq[Fila]
    ): Seq[Fila] = {
      val pideOpt = maxByTriunfo(fichas)
      pideOpt match {
        case None => filas
        case Some(pide) =>
          val enJuego = remainder
            .map(da => (da, score(pide, da)))
            .filter(_._2 > 0)
            .sortBy(-_._2)
          if (enJuego.headOption.fold(false)(_._2 >= 1000)) {
            filas
          } else {
            loop(
              fichas.filter(_ != pide),
              remainder.filter(f => enJuego.lastOption.fold(true)(_._1 != f)),
              filas :+ Fila(Seq(pide) ++ enJuego.lastOption.map(_._1).toSeq)
            )
          }

      }

    }
    loop(fichas, remainder, Seq.empty)
  }

  /**
    *
    * @return
    *         1100 + valor si pides otra cosa y te ganan con triunfo
    *         1000 + valor si te gana
    *         100 + valor si no te gana, pero por lo menos te dio lo que pediste
    *         0 si ni siquiera es lo que pediste
    */
  def score(
    pide: Ficha,
    da:   Ficha
  ): Int = {
    triunfo match {
      case None => throw GameException("Not happening!")
      case Some(SinTriunfos) =>
        if (!da.es(pide.arriba))
          0 //Ni siquiera es lo que pediste.
        else if (pide.esMula || pide.abajo.value > da.other(pide.arriba).value)
          100 + da.other(pide.arriba).value //Ganas por valor
        else
          1000 + da.other(pide.arriba).value //Te ganaron
      case Some(TriunfoNumero(triunfo)) =>
        if (pide.es(triunfo) && !da.es(triunfo))
          0 //Ni siquiera es lo que pediste.
        else if (!pide.es(triunfo) && da.es(triunfo))
          1100 + da.other(triunfo).value //Te ganaron con triunfo.
        else if (pide.es(triunfo) && (pide.esMula || pide
                   .other(triunfo).value > da.other(triunfo).value))
          100 + da.other(triunfo).value //Mula de triunfos, o ambos triunfos, pero tu ganas
        else if (pide.es(triunfo) && pide.other(triunfo).value < da.other(triunfo).value)
          1000 + da.other(triunfo).value //Ambos triunfos, pero te ganaron
        else if (!da.es(pide.arriba))
          0 //Ni siquiera es lo que pediste.
        else if (pide.esMula || pide.abajo.value > da.other(pide.arriba).value)
          100 + da.other(pide.arriba).value //Ganas por valor
        else
          1000 + da.other(pide.arriba).value //Te ganaron
    }
  }

  def fichaGanadora(
    pide:  Ficha,
    juego: Seq[Ficha]
  ): Ficha =
    juego
      .filter(_ != pide)
      .map(da => (da, score(pide, da)))
      .filter(_._2 >= 1000)
      .sortBy(-_._2)
      .headOption
      .map(_._1)
      .getOrElse(pide)

  def fichaGanadora(
    pide: Ficha,
    da:   Ficha
  ): Ficha = if (score(pide, da) > 1000) da else pide

  def puedesCaerte(jugador: Jugador): Boolean = {
    if (jugador.fichas.isEmpty) {
      true
    } else {
      val cuantas = cuantasDeCaida(jugador.fichas, jugadores.filter(_ != jugador).flatMap(_.fichas))
      val cantante = quienCanta
      if (jugador.cantante && (jugador.filas.size + cuantas.size) >= jugador.cuantasCantas
            .fold(0)(
              _.numFilas
            )) {
        true //Eres el cantante y ya estas hecho, caete!
      } else if (jugador.id != cantante.id &&
                 (jugador.fichas.size + cantante.filas.size - cuantas.size) < cantante.cuantasCantas
                   .fold(0)(_.numFilas)) {
        true //Ya es hoyo, deten esa masacre
      } else {
        false //No sabemos mas alla de las que cantaste.
      }
    }
  }

  def maxByTriunfo(fichas: Seq[Ficha]): Option[Ficha] =
    triunfo match {
      case None              => throw GameException("Should never happen!")
      case Some(SinTriunfos) => fichas.maxByOption(f => if (f.esMula) 100 else f.arriba.value)
      case Some(TriunfoNumero(num)) =>
        fichas.maxByOption(f =>
          if (f.es(num) && f.esMula)
            300
          else if (f.es(num))
            200 + f.other(num).value
          else if (f.esMula) 100
          else
            f.arriba.value
        )
    }

  def partidoOver: Boolean = {
    jugadores.exists(_.cuenta.map(_.puntos).sum >= 21)
  }

  def jugador(id: Option[UserId]): Jugador =
    jugadores
      .find(_.user.id == id).getOrElse(
        throw GameException("Este usuario no esta jugando en este juego")
      )

  def modifiedJugadores(
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

  def canTransitionTo(transitionState: GameStatus): Boolean = {
    //TODO fill this up and follow every place state changes
    transitionState match {
      case GameStatus.requiereSopa =>
        jugadores.length == numPlayers &&
          (gameStatus == GameStatus.jugando ||
            gameStatus == GameStatus.esperandoJugadoresAzar ||
            gameStatus == GameStatus.esperandoJugadoresInvitados) &&
          jugadores.forall(_.invited == false) //Not everyone has accepted
      case _ => false
    }
  }

  //Returns the newly modified state, and any changes to the event
  def applyEvent(
    userOpt: Option[User],
    event:   GameEvent
  ): (Game, GameEvent) = {
    //It is the responsibility of each event to make sure the event *can* be applied "legally"
    val processed = event.doEvent(userOpt, game = this)
    if (processed._2.index.getOrElse(-1) != currentEventIndex) {
      throw GameException("Error! the event did not gather the correct index")
    }
    (processed._1.copy(currentEventIndex = nextIndex), processed._2)
  }

  //very similar to apply event, but for the client, this re-applies an event that the server has already processed
  //No new events are generated
  def reapplyEvent(event: GameEvent): Game = {
    //TODO: Write this
    // if you're removing tiles from people's hand and their tiles are hidden, then just drop 1.
    val user = jugadores.find(_.user.id == event.userId).map(_.user)
    val processed = event.doEvent(user, game = this)
    if (processed._2.index.getOrElse(-1) != currentEventIndex) {
      throw GameException("Error! the event did not gather the correct index")
    }
    if (processed._2 != event) {
      //For all events, test that reapplying the event to the same original game doesn't change the event, Sopa in particular
      throw GameException(s"Should not have modified event on reapply!\n$event\n${processed._2}")
    }
    processed._1.copy(currentEventIndex = nextIndex)
  }

  override def toString: String = {
    def jugadorStr(jugador: Jugador): String =
      s"""
         |${jugador.user.name}: ${if (jugador.mano) "Me toca cantar" else ""}
         |${jugador.fichas.map(_.toString).mkString(" ")}""".stripMargin

    s"""
       |id          = $id
       |gameStatus  = $gameStatus
       |triunfo     = ${triunfo.getOrElse("")}
       |quien canta = ${quienCanta.user.name}
       |mano        = ${mano.user.name}
       |${jugadores.map { jugadorStr }.mkString}
       |""".stripMargin

  }

}

case class Cuenta(
  puntos: Int,
  esHoyo: Boolean = false
)
