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

import chuti.Numero.*
import chuti.Triunfo.{SinTriunfos, TriunfoNumero}
import io.circe.{Decoder, Encoder}

import java.time.Instant
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

enum Numero(val value: Int) {

  case Numero0 extends Numero(0)
  case Numero1 extends Numero(1)
  case Numero2 extends Numero(2)
  case Numero3 extends Numero(3)
  case Numero4 extends Numero(4)
  case Numero5 extends Numero(5)
  case Numero6 extends Numero(6)

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

  def apply(num: Int): Numero = values(num)

  given Decoder[Numero] = Decoder.forProduct1("value")(apply)

  given Encoder[Numero] = Encoder.forProduct1("value")(_.value)

}

object CuantasCantas {

  def posibilidades(min: CuantasCantas): Seq[CuantasCantas] = {
    values.filter(_.prioridad >= min.prioridad)
  }

  def byNum(cuantas: Int): CuantasCantas =
    cuantas match {
      case 5 => Canto5
      case 6 => Canto6
      case 7 => CantoTodas
      case _ => Casa
    }

  def byPriority(prioridad: Int): CuantasCantas = (Buenas +: values).find(_.prioridad == prioridad).get

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

    override def toString: String = "Siete (?!?)"

  }
  case object CantoTodas extends CuantasCantas(7, 21, 8) {

    override def toString: String = "Chuti!"

  }
  case object Buenas extends CuantasCantas(-1, 0, -1) {

    override def toString: String = "Buenas"

  }

  val values: Seq[CuantasCantas] = Seq(Casa, Canto5, Canto6, Canto7, CantoTodas)

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
    val splitted = str.split(":").nn.map {
      case s: String => Numero(s.toInt)
      case null => throw GameException(s"Invalid ficha string: $str")
    }
    Ficha(splitted(0), splitted(1))
  }

  given Decoder[Ficha] =
    Decoder.forProduct3[Ficha, String, Numero, Numero]("type", "arriba", "abajo") {
      case ("tapada", _, _)                   => FichaTapada
      case (_, arriba: Numero, abajo: Numero) => apply(arriba, abajo)
    }

  given Encoder[Ficha] =
    Encoder.forProduct3[Ficha, String, Numero, Numero]("type", "arriba", "abajo") {
      case FichaTapada                  => ("tapada", Numero0, Numero0)
      case FichaConocida(arriba, abajo) => ("conocida", arriba, abajo)
    }

}

sealed trait Ficha extends Product with Serializable {

  def arriba: Numero
  def abajo:  Numero
  def esMula: Boolean
  def value:  Int
  def es(num:    Numero): Boolean
  def other(num: Numero): Numero

}

case object FichaTapada extends Ficha {

  override def arriba:             Numero = throw GameException("No puedes hacer esto con una ficha tapada")
  override def abajo:              Numero = throw GameException("No puedes hacer esto con una ficha tapada")
  override def esMula:             Boolean = throw GameException("No puedes hacer esto con una ficha tapada")
  override def value:              Int = throw GameException("No puedes hacer esto con una ficha tapada")
  override def es(num: Numero):    Boolean = throw GameException("No puedes hacer esto con una ficha tapada")
  override def other(num: Numero): Numero = throw GameException("No puedes hacer esto con una ficha tapada")
  override def toString:           String = "?:?"

}

case class FichaConocida private[chuti] (
  arriba: Numero,
  abajo:  Numero
) extends Ficha {

  lazy override val esMula:        Boolean = arriba == abajo
  lazy override val value:         Int = arriba.value + abajo.value
  override def es(num: Numero):    Boolean = arriba == num || abajo == num
  override def other(num: Numero): Numero = { if (arriba == num) abajo else arriba }
  override def toString:           String = s"${arriba.value}:${abajo.value}"

}

object Fila {

  def apply(
    index:  Int,
    fichas: Ficha*
  ): Fila = new Fila(fichas.toSeq, index)

}

case class Fila(
  fichas: Seq[Ficha],
  index:  Int = 0
)

import chuti.CuantasCantas.*

enum JugadorState {

  case dando, cantando, esperandoCanto, pidiendoInicial, pidiendo, esperando, haciendoSopa, partidoTerminado, invitedNotAnswered,
    waitingOthersAcceptance

}

object JugadorState {

  def description(state: JugadorState): String =
    state match {
      case JugadorState.dando                   => "Dando ficha"
      case JugadorState.cantando                => "Cantando"
      case JugadorState.esperandoCanto          => "Esperando Canto"
      case JugadorState.pidiendoInicial         => "Pidiendo (inicial)"
      case JugadorState.pidiendo                => "Pidiendo"
      case JugadorState.esperando               => "Esperando"
      case JugadorState.haciendoSopa            => "Haciendo Sopa"
      case JugadorState.partidoTerminado        => "-"
      case JugadorState.invitedNotAnswered      => "Esperando a que acepte"
      case JugadorState.waitingOthersAcceptance => "Esperando a que los demas acepten"
    }

}
import chuti.JugadorState.*

case class Jugador(
  user:             User,
  invited:          Boolean = false, // Should really reverse this and call it "accepted"
  fichas:           List[Ficha] = List.empty,
  filas:            List[Fila] = List.empty,
  turno:            Boolean = false, // A quien le tocaba cantar este juego
  cantante:         Boolean = false, // Quien esta cantando este juego
  mano:             Boolean = false, // Quien tiene la mano en este momento
  cuantasCantas:    Option[CuantasCantas] = None,
  statusString:     String = "",
  ganadorDePartido: Boolean = false,
  cuenta:           Seq[Cuenta] = Seq.empty
) {

  lazy val yaSeHizo: Boolean = {
    if (!cantante)
      throw GameException("Si no canta no se puede hacer!!")
    filas.size >= cuantasCantas.fold(throw GameException("Si no canta no se puede hacer!!"))(
      _.numFilas
    )
  }

  def filterFichas(dropMe: Seq[Fila]): List[Ficha] = {
    fichas.headOption match {
      case None              => fichas
      case Some(FichaTapada) => fichas.drop(dropMe.size)
      case _                 => fichas.diff(dropMe.flatMap(_.fichas))
    }
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

  case object SinTriunfos extends Triunfo {

    override def toString: String = "SinTriunfos"

  }
  case class TriunfoNumero(num: Numero) extends Triunfo {

    override def toString: String = num.value.toString

  }

  lazy val posibilidades: Seq[Triunfo] = Seq(SinTriunfos) ++ Numero.values.map(TriunfoNumero.apply)

  def apply(str: String): Triunfo =
    str match {
      case "SinTriunfos" => SinTriunfos
      case str           => TriunfoNumero(Numero(str.toInt))
    }

}

enum GameStatus(
  val value:   String,
  val enJuego: Boolean = false,
  val acabado: Boolean = false
) {

  case esperandoJugadoresInvitados extends GameStatus(value = "esperandoJugadoresInvitados")
  case esperandoJugadoresAzar extends GameStatus(value = "esperandoJugadoresAzar")
  case comienzo extends GameStatus(value = "comienzo", enJuego = true)
  case cantando extends GameStatus(value = "cantando", enJuego = true)
  case jugando extends GameStatus(value = "jugando", enJuego = true)
  case requiereSopa extends GameStatus(value = "requiereSopa", enJuego = true)
  case abandonado extends GameStatus(value = "abandonado", acabado = true)
  case partidoTerminado extends GameStatus(value = "partidoTerminado", acabado = true)

}
//  def withName(str: String): GameStatus =
//    str match {
//      case "esperandoJugadoresInvitados" => esperandoJugadoresInvitados
//      case "esperandoJugadoresAzar"      => esperandoJugadoresAzar
//      case "comienzo"                    => comienzo
//      case "cantando"                    => cantando
//      case "jugando"                     => jugando
//      case "requiereSopa"                => requiereSopa
//      case "partidoTerminado"            => partidoTerminado
//      case "abandonado"                  => abandonado
//      case other                         => throw GameException(s"Estado de juego desconocido: $other")
//    }

import chuti.GameStatus.*

enum Borlote(override val toString: String) {

  case Hoyo extends Borlote(toString = "Hoyo!")
  case HoyoTecnico extends Borlote(toString = "Hoyo Tecnico!")
  case ElNiñoDelCumpleaños extends Borlote(toString = "El Niño del Cumpleaños!")
  case SantaClaus extends Borlote(toString = "Santa Claus!")
  case Campanita extends Borlote(toString = "Campanita!")
  case Helecho extends Borlote(toString = "Helecho!")
  case TodoConDos extends Borlote(toString = "Todo con dos!!!!")

}

object Game {

  val laMulota:  Ficha = Ficha(Numero6, Numero6)
  val campanita: Ficha = Ficha(Numero0, Numero1)

  lazy val todaLaFicha: List[Ficha] = (0 to 6)
    .combinations(2).toList.map(seq => Ficha(Numero(seq(0)), Numero(seq(1)))) ++ (0 to 6).map(i => Ficha(Numero(i), Numero(i)))

  def calculaJugadorGanador(
    fichas:   Seq[(UserId, Ficha)],
    pidiendo: Ficha,
    triunfo:  Triunfo
  ): (UserId, Ficha) = {
    // could use fichaGanadora instead
    triunfo match {
      case SinTriunfos =>
        if (pidiendo.esMula) fichas.find(_._2 == pidiendo).get
        else fichas.filter(_._2.es(pidiendo.arriba)).maxBy(_._2.abajo.value)
      case TriunfoNumero(triunfoVal) =>
        val triunfos = fichas.filter(_._2.es(triunfoVal))
        if (triunfos.isEmpty)
          if (pidiendo.esMula) fichas.find(_._2 == pidiendo).get
          else fichas.filter(_._2.es(pidiendo.arriba)).maxBy(_._2.abajo.value)
        else {
          if (pidiendo.esMula && pidiendo.es(triunfoVal)) fichas.find(_._2 == pidiendo).get
          else
            triunfos.maxBy(f => if (f._2.arriba == triunfoVal) f._2.abajo.value else f._2.arriba.value)
        }
    }
  }

}

case class Game(
  id:                Option[GameId],
  created:           Instant,
  gameStatus:        GameStatus = comienzo,
  currentEventIndex: Int = 0,
  // Game State
  triunfo:         Option[Triunfo] = None,
  enJuego:         List[(UserId, Ficha)] = List.empty,
  estrictaDerecha: Boolean = false,
  jugadores:       List[Jugador] = List.empty,
  statusString:    String = "",
  satoshiPerPoint: Double = 100.0
) {

  def jugadorState(jugador: Jugador): JugadorState = {
    gameStatus match {
      case GameStatus.comienzo =>
        throw GameException("Porque estas preguntando el estatus del jugador si no ha pasado nada?")
      case GameStatus.abandonado =>
        JugadorState.partidoTerminado
      case GameStatus.esperandoJugadoresInvitados | GameStatus.esperandoJugadoresAzar =>
        if (jugador.invited)
          JugadorState.invitedNotAnswered
        else
          JugadorState.waitingOthersAcceptance
      case GameStatus.cantando =>
        if (jugador.mano)
          JugadorState.cantando
        else
          JugadorState.esperandoCanto
      case GameStatus.jugando =>
        if (jugador.mano && jugador.cantante && jugador.filas.isEmpty && enJuego.isEmpty)
          JugadorState.pidiendoInicial
        else if (jugador.mano && enJuego.isEmpty)
          JugadorState.pidiendo
        else if (
          enJuego.nonEmpty && !enJuego
            .exists(_._1 == jugador.id.get)
        )
          JugadorState.dando
        else
          JugadorState.esperando
      case GameStatus.requiereSopa =>
        if (jugador.turno)
          JugadorState.haciendoSopa
        else
          JugadorState.esperando
      case GameStatus.partidoTerminado =>
        JugadorState.partidoTerminado
    }
  }

  @transient
  lazy val ganadorDePartido: Option[Jugador] = jugadores.find(_.ganadorDePartido)
  @transient
  lazy val channelId: Option[ChannelId] = id.map(i => ChannelId(i.gameId))
  @transient
  lazy val mano: Option[Jugador] = jugadores.find(_.mano)
  @transient
  lazy val turno: Option[Jugador] = jugadores.find(_.turno)
  @transient
  lazy val quienCanta:  Option[Jugador] = jugadores.find(_.cantante)
  val abandonedPenalty: Int = 10 // Times the satoshiPerPoint of the game
  val numPlayers:       Int = 4

  @transient
  lazy val cuentasCalculadas: Seq[(Jugador, Int, Double)] = {
    val conPuntos = jugadores.map(j => (j, j.cuenta.map(_.puntos).sum))
    conPuntos.map { case (j, puntos) =>
      val total =
        (if (j.ganadorDePartido) 3 else -1) + // Si ganas cada uno te da 1 (o sea, recibes 3)
          (if (puntos < 0) -2
           else
             0) + // Si alguien quedó en negativos por tener varios hoyos, producto de estar cante y cante, le da dos puntos al ganador.
          (if (puntos == 0) -1
           else
             0) + // Si alguien quedó en cero, le da un punt al ganador.
          (if (j.ganadorDePartido) conPuntos.count(_._2 < 0) * 2
           else
             0) + // Si alguien quedó en negativos por tener varios hoyos, producto de estar cante y cante, le da dos puntos al ganador.
          (if (j.ganadorDePartido) conPuntos.count(_._2 == 0) * 1
           else
             0) + // Si alguien quedó en cero, le da un punt al ganador.
          (if (j.ganadorDePartido && j.cuenta.lastOption.fold(false)(_.puntos == 21)) 3
           else if (ganadorDePartido.fold(false)(_.cuenta.lastOption.fold(false)(_.puntos == 21)))
             -1
           else 0) + // Si se va con chuti, recibe 6 o dos de cada jugador.
          conPuntos.filter(_._1.id != j.id).map(_._1.cuenta.count(_.esHoyo)).sum + // Hoyos ajenos
          (-(j.cuenta.count(_.esHoyo) * 3)) // Hoyos propios

      (j, puntos, total * satoshiPerPoint)
    }
  }

  // Dado el triunfo, cuantas filas se pueden hacer con la siguiente mano
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
          if (enJuego.headOption.fold(false)(_._2 >= 1000))
            filas
          else {
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

  /** @return
    *   1100 + valor si pides otra cosa y te ganan con triunfo 1000 + valor si te gana 100 + valor si no te gana, pero por lo menos te dio lo que
    *   pediste 0 si ni siquiera es lo que pediste
    */
  def score(
    pide: Ficha,
    da:   Ficha
  ): Int = {
    triunfo match {
      case None => throw GameException("Nuncamente!")
      case Some(SinTriunfos) =>
        if (!da.es(pide.arriba))
          0 // Ni siquiera es lo que pediste.
        else if (pide.esMula || pide.abajo.value > da.other(pide.arriba).value)
          100 + da.other(pide.arriba).value // Ganas por valor
        else
          1000 + da.other(pide.arriba).value // Te ganaron
      case Some(TriunfoNumero(triunfo)) =>
        if (pide.es(triunfo) && !da.es(triunfo))
          0 // Ni siquiera es lo que pediste.
        else if (!pide.es(triunfo) && da.es(triunfo))
          1100 + da.other(triunfo).value // Te ganaron con triunfo.
        else if (
          pide.es(triunfo) && (pide.esMula || pide
            .other(triunfo).value > da.other(triunfo).value)
        )
          100 + da.other(triunfo).value // Mula de triunfos, o ambos triunfos, pero tu ganas
        else if (pide.es(triunfo) && pide.other(triunfo).value < da.other(triunfo).value)
          1000 + da.other(triunfo).value // Ambos triunfos, pero te ganaron
        else if (!da.es(pide.arriba))
          0 // Ni siquiera es lo que pediste.
        else if (pide.esMula || pide.abajo.value > da.other(pide.arriba).value)
          100 + da.other(pide.arriba).value // Ganas por valor
        else
          1000 + da.other(pide.arriba).value // Te ganaron
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
    if (jugador.fichas.isEmpty) true
    else {
      // Calcula cuantas puedes hacer de caida, dadas las fichas que tienes y las fichas que ya se jugaron,
      // primero calcula las fichas que quedan
      val resto = Game.todaLaFicha.diff(
        jugador.fichas ++ jugadores.flatMap(_.filas.flatMap(_.fichas))
      )
      val cuantas = cuantasDeCaida(jugador.fichas, resto)

      quienCanta.fold(false) { cantante =>
        if (
          jugador.cantante && (jugador.filas.size + cuantas.size) >= jugador.cuantasCantas
            .fold(0)(
              _.numFilas
            )
        )
          true // Eres el cantante y ya estas hecho, caete!
        else if (
          jugador.id != cantante.id &&
          (jugador.fichas.size + cantante.filas.size - cuantas.size) < cantante.cuantasCantas
            .fold(0)(_.numFilas)
        )
          true // Ya es hoyo, deten esa masacre
        else
          false // No sabemos mas alla de las que cantaste.
      }
    }
  }

  def maxByTriunfo(fichas: Seq[Ficha]): Option[Ficha] =
    triunfo match {
      case None              => throw GameException("Nuncamente!")
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

  def partidoTerminado: Boolean = {
    jugadores.exists(_.ganadorDePartido)
  }

  def setStatusStrings(
    gameStatusString:     Option[String],
    jugadorStatusStrings: Seq[(UserId, String)]
  ): Game = {
    val a = jugadorStatusStrings.headOption.fold(this) { _ =>
      val map = jugadorStatusStrings.toMap
      val newJugadores = jugadores.map { j =>
        val strOpt = map.get(j.id.get)
        strOpt.fold(j)(str => j.copy(statusString = str))
      }
      copy(jugadores = newJugadores)
    }
    gameStatusString.fold(a)(str => a.copy(statusString = str))
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
    if (index == 0)
      jugadores.last
    else
      jugadores(index - 1)
  }
  def prevPlayer(user: User): Jugador = {
    val index = jugadores.indexWhere(_.user.id == user.id)
    if (index == 0)
      jugadores.last
    else
      jugadores(index - 1)
  }
  def nextPlayer(jugador: Jugador): Jugador = {
    val index = jugadores.indexOf(jugador)
    if (index == numPlayers - 1)
      jugadores.head
    else
      jugadores(index + 1)
  }
  def nextPlayer(user: User): Jugador = {
    val index = jugadores.indexWhere(_.user.id == user.id)
    if (index == numPlayers - 1)
      jugadores.head
    else
      jugadores(index + 1)
  }

  def nextIndex: Int = currentEventIndex + 1

  def canTransitionTo(transitionState: GameStatus): Boolean = {
    // NOTE fill this up and follow every place state changes
    transitionState match {
      case GameStatus.requiereSopa =>
        jugadores.length == numPlayers &&
        (gameStatus == GameStatus.jugando ||
          gameStatus == GameStatus.esperandoJugadoresAzar ||
          gameStatus == GameStatus.esperandoJugadoresInvitados) &&
        jugadores.forall(!_.invited) // Not everyone has accepted
      case _ => false
    }
  }

  // Returns the newly modified state, and any changes to the event
  def applyEvent(
    userOpt: Option[User],
    event:   GameEvent
  ): (Game, GameEvent) = {
    // It is the responsibility of each event to make sure the event *can* be applied "legally"
    val processed = event.doEvent(userOpt, game = this)
    if (processed._2.index.getOrElse(-1) != currentEventIndex)
      throw GameException("Error! El evento no tenia el indice correcto")
    (processed._1.copy(currentEventIndex = nextIndex), processed._2)
  }

  // very similar to apply event, but for the client, this re-applies an event that the server has already processed
  // No new events are generated
  def reapplyEvent(event: GameEvent): Game = {
    // if you're removing tiles from people's hand and their tiles are hidden, then just drop 1.
    val user = jugadores.find(_.user.id == event.userId).map(_.user)
    val processed = event.redoEvent(user, game = this)
    if (event.index.getOrElse(-1) != currentEventIndex)
      throw GameException("Error! No puedes re-aplicar este evento al juego!")
    processed.copy(currentEventIndex = nextIndex)
  }

  override def toString: String = {
    def jugadorStr(jugador: Jugador): String =
      s"""
         |${jugador.user.name}: ${
          if (jugador.mano) "Me toca cantar"
          else ""
        } ${jugador.cuantasCantas
          .fold("")(c => s"canto: $c")}
         |${jugador.fichas.map(_.toString).mkString(" ")}""".stripMargin

    s"""
       |id          = $id
       |gameStatus  = $gameStatus
       |triunfo     = ${triunfo.getOrElse("")}
       |quien canta = ${quienCanta.map(_.user.name)}
       |mano        = ${mano.map(_.user.name)}
       |${jugadores.map(jugadorStr).mkString}
       |""".stripMargin

  }

}

case class Cuenta(
  puntos: Int,
  esHoyo: Boolean = false
)
