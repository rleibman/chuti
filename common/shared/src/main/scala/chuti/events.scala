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

import monocle.{Lens, Traversal}
import monocle.macros.GenLens

import scala.util.Random

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
  def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent)
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
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) =
    (game, copy(index = Option(game.currentIndex), gameId = game.id))
}

//This event ends the game and shuts down the server... it can only be called by god
case class PoisonPill(
  gameId: Option[GameId] = None,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    (game, copy(index = Option(game.currentIndex), gameId = game.id))
  }
}
case class TerminaJuego(
  gameId: Option[GameId] = None,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = ??? //TODO write this
}

object CuantasCantas {
  sealed abstract class CuantasCantas(val num: Int) {
    def <=(cuantasCanto: CuantasCantas) = num <= cuantasCanto.num
  }

  object Canto4 extends CuantasCantas(4)

  object Canto5 extends CuantasCantas(5)

  object Canto6 extends CuantasCantas(6)

  object CantoTodas extends CuantasCantas(7)
}
import CuantasCantas._

case class Canta(
  cuantasCantas: CuantasCantas,
  gameId:        Option[GameId] = None,
  index:         Option[Int] = None
) extends GameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    val user = userOpt.getOrElse(throw GameException("User required for this move"))

    val jugadorOpt = game.jugadores.find(_.user.id == user.id)
    if (jugadorOpt.isEmpty) {
      throw GameException("Este jugador no esta jugando aqui!")
    }
    val jugador = jugadorOpt.get
    if (jugador.mano) {
      throw GameException("No te adelantes")
    }
    val nextPlayer = game.nextPlayer(jugador)
    val cantanteActual = game.jugadores.find(_.mano).getOrElse(jugador)
    val nuevoCantante = cantanteActual.cuantasCantas.fold {
      //primer jugador cantando
      jugador
    } { cuantasCanto =>
      if (cuantasCantas <= cuantasCanto || cuantasCanto == CuantasCantas.CantoTodas) {
        //No es suficiente para salvarlo
        cantanteActual
      } else {
        //Lo salvaste, te llevas la mano
        jugador
      }
    }

    //El turno no cambia,
    val jugadoresNuevos = game.modifyPlayers(_.user.id == nuevoCantante.user.id,
      _.copy(cantante = true, mano = true, cuantasCantas = Option(cuantasCantas)),
      _.copy(cantante = false, mano = false, cuantasCantas = None)
    )

    val (newGameStatus) =
      if (nextPlayer.turno || cuantasCantas == CuantasCantas.CantoTodas) {
        //jugador es el ultimo en cantar, juego listo para empezar
        GameStatus.jugando
      } else {
        GameStatus.cantando
      }

    val modified = game.copy(jugadores = jugadoresNuevos, gameStatus = newGameStatus)
    (modified, copy(index = Option(game.currentIndex), gameId = game.id))
  }
}
case class PideInicial(
  gameId:          Option[GameId] = None,
  index:           Option[Int] = None,
  ficha:           Ficha,
  triunfan:        Numero,
  estrictaDerecha: Boolean
) extends GameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = ??? //TODO write this
}
case class Pide(
  gameId:          Option[GameId] = None,
  index:           Option[Int] = None,
  ficha:           Ficha,
  estrictaDerecha: Boolean
) extends GameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = ??? //TODO write this
}
case class Da(
  gameId: Option[GameId] = None,
  index:  Option[Int] = None,
  ficha:  Ficha
) extends GameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = ??? //TODO write this
}
//Acuerdate de los regalos
case class DeCaida(
  gameId: Option[GameId] = None,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = ??? //TODO write this
}
case class HoyoTecnico(
  gameId: Option[GameId] = None,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = ??? //TODO write this
}
case class MeRindo(
  gameId: Option[GameId] = None,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = ??? //TODO write this
}
case class AbandonGame(
  gameId: Option[GameId] = None,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    val user = userOpt.getOrElse(throw GameException("User required for this move"))
    (
      //TODO if it's the last user in the game the game is finished
      game.copy(
        gameStatus =
          if (game.gameStatus != GameStatus.esperandoJugadoresInvitados && game.gameStatus != GameStatus.esperandoJugadoresAzar)
            GameStatus.abandonado
          else game.gameStatus,
        jugadores = game.jugadores.filter(_.user.id != user.id)
      ),
      copy(index = Option(game.currentIndex), gameId = game.id)
    )
  }
}

case class DeclineInvite(
  gameId: Option[GameId] = None,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    val user = userOpt.getOrElse(throw GameException("User required for this move"))
    (
      game.copy(jugadores = game.jugadores.filter(_.user.id != user.id)),
      copy(index = Option(game.currentIndex), gameId = game.id)
    )
  }
}

case class InviteToGame(
  gameId:  Option[GameId] = None,
  index:   Option[Int] = None,
  invited: User
) extends GameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    if (game.jugadores.exists(j => j.user.id == invited.id))
      throw GameException("A player can't join a game twice")
    if (game.jugadores.exists(_.user.id == invited.id))
      throw GameException(s"User ${invited.id} is already in game")
    if (game.jugadores.length == game.numPlayers)
      throw GameException("The game is already full")
    (
      game.copy(jugadores = game.jugadores :+ Jugador(invited, invited = true)),
      copy(index = Option(game.currentIndex), gameId = game.id)
    )
  }
}

case class JoinGame(
  gameId: Option[GameId] = None,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    val user = userOpt.getOrElse(throw GameException("User required for this move"))
    if (game.jugadores.exists(j => j.user.id == user.id && !j.invited)) {
      throw GameException("A player can't join a game twice")
    }

    val newPlayer = game.jugadores
      .find(_.user.id == user.id).fold(Jugador(user.copy(userStatus = UserStatus.Playing))) { j =>
        j.copy(invited = false, user = j.user.copy(userStatus = UserStatus.Playing))
      }

    (
      game.copy(jugadores = game.jugadores.filter(_.user.id != user.id) :+ newPlayer),
      copy(index = Option(game.currentIndex), gameId = game.id)
    )
  }
}

case class EmpiezaJuego(
  gameId: Option[GameId] = None,
  index:  Option[Int] = None,
  turno:  Option[User] = None
) extends GameEvent {
  import Mesa._
  def sopa: List[Ficha] = Random.shuffle(todaLaFicha)
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
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
            cantante = false,
            turno = turno.fold(fichas.contains(laMulota))(_ == jugador.user),
            mano = false,
            invited = false
          )
      }
      .toList

    val newState = game.copy(
      jugadores = newJugadores,
      gameStatus = GameStatus.cantando
    )
    (newState, copy(index = Option(game.currentIndex), gameId = game.id))
  }

}
