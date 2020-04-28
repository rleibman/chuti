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
    (game, copy(index = Option(game.currentIndex), gameId = game.id))
}

case class InviteFriend(
  gameId: Option[GameId] = None,
  index:  Option[Int] = None,
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
    (game, copy(index = Option(game.currentIndex), gameId = game.id))
}
case class TerminaJuego(
  gameId: Option[GameId] = None,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(game: Game): (Game, GameEvent) = ??? //TODO write this
}

sealed abstract class CuantasCantas(val num: Int) {
  def <=(cuantasCanto: CuantasCantas) = num <= cuantasCanto.num

}

object CuantasCantas {

  object Canto4 extends CuantasCantas(4)

  object Canto5 extends CuantasCantas(5)

  object Canto6 extends CuantasCantas(6)

  object CantoTodas extends CuantasCantas(7)

}
case class Canta(
  user:          User, //All events shold have an initiating Option[User]
  cuantasCantas: CuantasCantas,
  gameId:        Option[GameId] = None,
  index:         Option[Int] = None
) extends GameEvent {
  override def doEvent(game: Game): (Game, GameEvent) = {
    val jugadorOpt = game.jugadores.find(_.mano)
    if (jugadorOpt.isEmpty) {
      throw GameException("Este jugador no esta jugando aqui!")
    }
    val jugador = jugadorOpt.get
    if (jugador.user.id != user.id) {
      throw GameException("No te adelantes")
    }
    val nextPlayer = game.nextPlayer(jugador)
    val cantadorActual = game.jugadores.find(_.mano).getOrElse(jugador)
    val nuevaMano = cantadorActual.cuantasCantas.fold {
      //primer jugador cantando
      jugador
    } { cuantasCanto =>
      if (cuantasCantas <= cuantasCanto || cuantasCanto == CuantasCantas.CantoTodas) {
        //No es suficiente para salvarlo
        cantadorActual
      } else {
        //Lo salvaste, te llevas la mano
        jugador
      }

      //Nota: al final de este evento, el turno no cambia y el cantador y la mano son iguales
      //cantador = false,
      //mano = false,
    }

    val newGameStatus = if (nextPlayer.cuantasCantas.nonEmpty || cuantasCantas == CuantasCantas.CantoTodas) {
      //jugador es el ultimo en cantar, juego listo para empezar
      GameStatus.jugando
    } else {
      GameStatus.cantando
    }

    val modified = game
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
  index:  Option[Int] = None,
  user:   User
) extends GameEvent {
  override def doEvent(game: Game): (Game, GameEvent) = {
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
  override def doEvent(game: Game): (Game, GameEvent) = {
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
  index:  Option[Int] = None,
  user:   User
) extends GameEvent {
  override def doEvent(game: Game): (Game, GameEvent) = {
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
            cantador = false,
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
