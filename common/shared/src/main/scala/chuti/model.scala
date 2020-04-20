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

sealed trait Numero {
  def value: Int = Numero.values.indexOf(this)
}

object Numero {

  case object Numero0 extends Numero

  case object Numero1 extends Numero

  case object Numero2 extends Numero

  case object Numero3 extends Numero

  case object Numero4 extends Numero

  case object Numero5 extends Numero

  case object Numero6 extends Numero

  def values = Seq(Numero0, Numero1, Numero2, Numero3, Numero4, Numero5, Numero6)

  def apply(num: Int): Numero = values(num)
}

import java.time.LocalDateTime

import caliban.schema.Annotations.GQLInterface
import chuti.Numero._

import scala.util.Random

case class Ficha(
  arriba: Numero,
  abajo:  Numero
) {
  lazy val esMula: Boolean = arriba == abajo

  override def toString: String = s"${arriba.value}:${abajo.value}"
}

case class Fila(fichas: List[Ficha])

case class Jugador(
  user:     User,
  fichas:   List[Ficha] = List.empty,
  casas:    List[Fila] = List.empty,
  cantador: Boolean = false,
  mano:     Boolean = false
)

sealed trait Triunfo

object Triunfo {
  case object TriunfanMulas extends Triunfo
  case object SinTriunfos extends Triunfo
  case class TriunfoNumero(num: Numero) extends Triunfo
}

sealed trait Estado

object Estado {
  case object comienzo extends Estado
  case object cantando extends Estado
  case object jugando extends Estado
  case object terminado extends Estado
}

import chuti.Estado._

sealed trait Borlote
case object ElNiñoDelCumpleanos extends Borlote
case object SantaClaus extends Borlote
case object CampanitaSeJuega extends Borlote

sealed trait EventInfo[T <: GameEvent] {
  def canDo(
    jugador:   Jugador,
    gameState: GameState
  ): Boolean
  val values: Seq[EventInfo[_]] = Seq(NoOp)
}

sealed trait GameEvent {
  val gameId: GameId
  val index:  Option[Int]
  def doEvent(gameState: GameState): (GameState, GameEvent)
}

object NoOp extends EventInfo[NoOp] {
  override def canDo(
    jugador:   Jugador,
    gameState: GameState
  ): Boolean = true //always true
}

case class NoOp(
  gameId: GameId,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(gameState: GameState): (GameState, GameEvent) =
    (gameState, copy(index = Option(gameState.currentIndex)))
}

case class InviteFriend(
  gameId: GameId,
  index:  Option[Int],
  user:   User
) extends GameEvent {
  override def doEvent(gameState: GameState): (GameState, GameEvent) = ???
}
case class JoinGame(
  gameId: GameId,
  index:  Option[Int],
  user:   User
) extends GameEvent {
  override def doEvent(gameState: GameState): (GameState, GameEvent) = ???
}
//This event ends the game and shuts down the server... it can only be called by god
case class PoisonPill(
  gameId: GameId,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(gameState: GameState): (GameState, GameEvent) =
    (gameState, copy(index = Option(gameState.currentIndex)))
}
case class TerminaJuego(
  gameId: GameId,
  index:  Option[Int]
) extends GameEvent {
  override def doEvent(gameState: GameState): (GameState, GameEvent) = ???
}
case class Canta(
  gameId:  GameId,
  index:   Option[Int] = None,
  cuantas: Int
) extends GameEvent {
  override def doEvent(gameState: GameState): (GameState, GameEvent) = ???
}
case class PideInicial(
  gameId:          GameId,
  index:           Option[Int] = None,
  ficha:           Ficha,
  triunfan:        Numero,
  estrictaDerecha: Boolean
) extends GameEvent {
  override def doEvent(gameState: GameState): (GameState, GameEvent) = ???
}
case class Pide(
  gameId:          GameId,
  index:           Option[Int] = None,
  ficha:           Ficha,
  estrictaDerecha: Boolean
) extends GameEvent {
  override def doEvent(gameState: GameState): (GameState, GameEvent) = ???
}
case class Da(
  gameId: GameId,
  index:  Option[Int] = None,
  ficha:  Ficha
) extends GameEvent {
  override def doEvent(gameState: GameState): (GameState, GameEvent) = ???
}
//Acuerdate de los regalos
case class DeCaida(
  gameId: GameId,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(gameState: GameState): (GameState, GameEvent) = ???
}
case class HoyoTecnico(
  gameId: GameId,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(gameState: GameState): (GameState, GameEvent) = ???
}
case class MeRindo(
  gameId: GameId,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(gameState: GameState): (GameState, GameEvent) = ???
}
case class EmpiezaJuego(
  gameId: GameId,
  index:  Option[Int] = None,
  turno:  Option[User]
) extends GameEvent {
  import Mesa._
  def sopa = Random.shuffle(todaLaFicha)
  override def doEvent(gameState: GameState): (GameState, GameEvent) = {
    val jugadores = gameState.jugadores
    val newJugadores = sopa
      .grouped(todaLaFicha.length / jugadores.length)
      .zip(jugadores)
      .map {
        case (fichas, jugador) =>
          Jugador(
            user = jugador.user,
            fichas = fichas,
            casas = List.empty,
            cantador = turno.fold(fichas.contains(laMulota))(_ == jugador.user),
            mano = turno.fold(fichas.contains(laMulota))(_ == jugador.user)
          )
      }
      .toList

    val newState = gameState.copy(
      jugadores = newJugadores
    )
    (newState, this)
  }

}

case class GameId(value: Int) extends AnyVal

case class GameState(
  id:           Option[GameId],
  jugadores:    List[Jugador],
  enJuego:      List[Ficha] = List.empty,
  triunfo:      Option[Triunfo] = None,
  estado:       Estado = comienzo,
  currentIndex: Int = 0
) {
  def resultado(): Option[List[Cuenta]] = ???
  //Returns the newly modified state, and any changes to the event
  def event(event: GameEvent): (GameState, GameEvent) = {
    val newIndex = currentIndex + 1
    val processed = event.doEvent(gameState = this)
    (processed._1.copy(currentIndex = newIndex), processed._2)
  }
}

case class Cuenta(
  user:   User,
  puntos: Int,
  esHoyo: Boolean
)

/**
  * Un partido consta de uno o mas juegos, todos los partidos tienen los mismos usuarios
  *
  * @param cuentas
  */
case class PartidoArchivo(cuentas: List[(User, Double)])

object Mesa {
  lazy val todaLaFicha: List[Ficha] = ((0 to 6)
    .combinations(2).toList.map(seq => Ficha(Numero(seq(0)), Numero(seq(1)))) ++ (0 to 6).map(i =>
    Ficha(Numero(i), Numero(i))
  ))

  val laMulota = Ficha(Numero6, Numero6)
  val campanita = Ficha(Numero0, Numero1)

}

/**
  * Cada mesa tiene una serie de usuarios, una serie de partidos y un juego a cada momento.
  * Tambien tiene un historial de juegos, guardado en disco.
  *
  * @param partidos
  */
case class Mesa(
  partidos: List[PartidoArchivo] = List.empty,
  users:    List[User] = List.empty
) {
  def total(costoPorPunto: Double): List[(User, Double)] =
    partidos.flatMap(_.cuentas).groupBy(_._1).map(g => (g._1, g._2.map(_._2).sum)).toList

  /**
    * Imprime nada mas el asunto que un jugador puede ver
    *
    * @return
    */
  def jugadorPrint(jugador: Jugador) = ???
}
