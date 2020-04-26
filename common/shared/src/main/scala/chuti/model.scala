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

object GameException {
  def apply(cause: Throwable): GameException = GameException(cause = Option(cause))
}

case class GameException(
  msg:   String = "",
  cause: Option[Throwable] = None
) extends Exception(msg, cause.orNull)

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
  invited:  Boolean = false,
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

sealed trait Estado {
  def value: String
  def enJuego: Boolean = false
}

object Estado {

  case object esperandoJugadoresInvitados extends Estado {
    override def value: String = "esperandoJugadoresInvitados"
  }
  case object esperandoJugadoresAzar extends Estado {
    override def value: String = "esperandoJugadoresAzar"
  }
  case object comienzo extends Estado {
    override def value:   String = "comienzo"
    override def enJuego: Boolean = true
  }
  case object cantando extends Estado {
    override def value:   String = "cantando"
    override def enJuego: Boolean = true
  }
  case object jugando extends Estado {
    override def value:   String = "jugando"
    override def enJuego: Boolean = true
  }
  case object terminado extends Estado {
    override def value: String = "terminado"
  }
  object abandonado extends Estado {
    override def value: String = "abandonado"
  }
  def withName(str: String): Estado = str match {
    case "esperandoJugadoresInvitados" => esperandoJugadoresInvitados
    case "esperandoJugadoresAzar"      => esperandoJugadoresAzar
    case "comienzo"                    => comienzo
    case "cantando"                    => cantando
    case "jugando"                     => jugando
    case "terminado"                   => terminado
    case "abandonado"                  => abandonado
    case other                         => throw GameException(s"Unknown game exception e")
  }

}

import chuti.Estado._

sealed trait Borlote
case object ElNiñoDelCumpleaños extends Borlote
case object SantaClaus extends Borlote
case object CampanitaSeJuega extends Borlote

sealed trait EventInfo[T <: GameEvent] {
  def canDo(
    jugador: Jugador,
    game:    Game
  ): Boolean
  val values: Seq[EventInfo[_]] = Seq(NoOp)
}

sealed trait GameEvent {
  val gameId: Option[GameId]
  val index:  Option[Int]
  def doEvent(game: Game): (Game, GameEvent)
}

object NoOp extends EventInfo[NoOp] {
  override def canDo(
    jugador: Jugador,
    game:    Game
  ): Boolean = true //always true
}

case class NoOp(
  gameId: Option[GameId] = None,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(game: Game): (Game, GameEvent) =
    (game, copy(index = Option(game.currentIndex)))
}

case class InviteFriend(
  gameId: Option[GameId] = None,
  index:  Option[Int],
  user:   User
) extends GameEvent {
  override def doEvent(game: Game): (Game, GameEvent) = ??? //TODO write this
}
//This event ends the game and shuts down the server... it can only be called by god
case class PoisonPill(
  gameId: Option[GameId] = None,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(game: Game): (Game, GameEvent) =
    (game, copy(index = Option(game.currentIndex)))
}
case class TerminaJuego(
  gameId: Option[GameId] = None,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(game: Game): (Game, GameEvent) = ??? //TODO write this
}
case class Canta(
  gameId:  Option[GameId] = None,
  index:   Option[Int] = None,
  cuantas: Int
) extends GameEvent {
  override def doEvent(game: Game): (Game, GameEvent) = ??? //TODO write this
}
case class PideInicial(
  gameId:          Option[GameId] = None,
  index:           Option[Int] = None,
  ficha:           Ficha,
  triunfan:        Numero,
  estrictaDerecha: Boolean
) extends GameEvent {
  override def doEvent(game: Game): (Game, GameEvent) = ??? //TODO write this
}
case class Pide(
  gameId:          Option[GameId] = None,
  index:           Option[Int] = None,
  ficha:           Ficha,
  estrictaDerecha: Boolean
) extends GameEvent {
  override def doEvent(game: Game): (Game, GameEvent) = ??? //TODO write this
}
case class Da(
  gameId: Option[GameId] = None,
  index:  Option[Int] = None,
  ficha:  Ficha
) extends GameEvent {
  override def doEvent(game: Game): (Game, GameEvent) = ??? //TODO write this
}
//Acuerdate de los regalos
case class DeCaida(
  gameId: Option[GameId] = None,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(game: Game): (Game, GameEvent) = ??? //TODO write this
}
case class HoyoTecnico(
  gameId: Option[GameId] = None,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(game: Game): (Game, GameEvent) = ??? //TODO write this
}
case class MeRindo(
  gameId: Option[GameId] = None,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(game: Game): (Game, GameEvent) = ??? //TODO write this
}
case class AbandonGame(
  user:   User,
  gameId: Option[GameId] = None,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(game: Game): (Game, GameEvent) = {
    (game.copy(gameStatus = Estado.abandonado), this)
  }
}
case class JoinGame(
  gameId: Option[GameId] = None,
  index:  Option[Int] = None,
  user:   User
) extends GameEvent {
  override def doEvent(game: Game): (Game, GameEvent) = {
    (game.copy(jugadores = (game.jugadores :+ Jugador(user))), this)
  }
}
case class EmpiezaJuego(
  gameId: Option[GameId] = None,
  index:  Option[Int] = None,
  turno:  Option[User] = None
) extends GameEvent {
  import Mesa._
  def sopa = Random.shuffle(todaLaFicha)
  override def doEvent(game: Game): (Game, GameEvent) = {
    val jugadores = game.jugadores
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
            mano = turno.fold(fichas.contains(laMulota))(_ == jugador.user),
            invited = false
          )
      }
      .toList

    val newState = game.copy(
      jugadores = newJugadores,
      gameStatus = Estado.jugando
    )
    (newState, this)
  }

}

case class GameId(value: Int) extends AnyVal

case class Game(
  id:           Option[GameId],
  jugadores:    List[Jugador] = List.empty,
  enJuego:      List[Ficha] = List.empty,
  triunfo:      Option[Triunfo] = None,
  gameStatus:   Estado = comienzo,
  currentIndex: Int = 0,
  created:      LocalDateTime = LocalDateTime.now
) {
  val size = 4

  def canTransition(estado: Estado): Boolean = {
    estado match {
      case Estado.jugando =>
        jugadores.length == size && (gameStatus == Estado.esperandoJugadoresAzar || gameStatus == Estado.esperandoJugadoresInvitados)
      case _ => false
    }
  }

  def resultado(): Option[List[Cuenta]] = ??? //TODO write this

  //Returns the newly modified state, and any changes to the event
  def applyEvent(event: GameEvent): (Game, GameEvent) = {
    //TODO make sure the event index matches the next event we need
    //TODO make sure the event *can* be applied "legally"
    val newIndex = currentIndex + 1
    val processed = event.doEvent(game = this)
    (processed._1.copy(currentIndex = newIndex), processed._2)
  }

  //very similar to apply event, but for the client, this re-applies an event that the server has already processed
  def reapplyEvent(event: GameEvent): (Game, GameEvent) = {
    //TODO write
    ??? //TODO write this
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
  def jugadorPrint(jugador: Jugador) = ??? //TODO write this
}
