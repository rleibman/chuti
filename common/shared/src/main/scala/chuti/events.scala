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

//////////////////////////////////////////////////////////////////////////////////////
// General stuff and parents
sealed trait EventInfo[T <: GameEvent] {
  def canDo(
    jugador: Jugador,
    game:    Game
  ): Boolean
  val values: Seq[EventInfo[_]] = Seq(NoOp)
}

sealed trait GameEvent {
  val gameId: Option[GameId]
  val userId: Option[UserId]
  val index:  Option[Int]
  def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent)
}

sealed trait PreGameEvent extends GameEvent
sealed trait PlayEvent extends GameEvent

object NoOp extends EventInfo[NoOp] {
  override def canDo(
    jugador: Jugador,
    game:    Game
  ): Boolean = true //always true
}

case class NoOp(
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) =
    (
      game,
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = userOpt.flatMap(_.id))
    )
}

case class BorloteEvent(
  borlote: Borlote,
  gameId:  Option[GameId] = None,
  userId:  Option[UserId] = None,
  index:   Option[Int] = None
) extends GameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = ??? //Write this
}

//This event ends the game and shuts down the server... it can only be called by god
case class PoisonPill(
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    (
      game,
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = userOpt.flatMap(_.id))
    )
  }
}

//////////////////////////////////////////////////////////////////////////////////////
// Start game and invitations
case class AbandonGame(
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None
) extends PreGameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    val user = userOpt.getOrElse(throw GameException("User required for this move"))

    (
      game.copy(
        gameStatus =
          if ((game.gameStatus != GameStatus.esperandoJugadoresInvitados && game.gameStatus != GameStatus.esperandoJugadoresAzar) ||
              game.jugadores.size == 1) //if it's the last user in the game the game is finished
            {
              GameStatus.abandonado
            } else {
            game.gameStatus
          },
        jugadores = game.jugadores.filter(_.user.id != user.id)
      ),
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = userOpt.flatMap(_.id))
    )
  }
}

case class DeclineInvite(
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None
) extends PreGameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    val user = userOpt.getOrElse(throw GameException("User required for this move"))
    (
      game.copy(jugadores = game.jugadores.filter(_.user.id != user.id)),
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = userOpt.flatMap(_.id))
    )
  }
}

case class InviteToGame(
  invited: User,
  gameId:  Option[GameId] = None,
  userId:  Option[UserId] = None,
  index:   Option[Int] = None
) extends PreGameEvent {
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
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = userOpt.flatMap(_.id))
    )
  }
}

case class JoinGame(
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None
) extends PreGameEvent {
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
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = userOpt.flatMap(_.id))
    )
  }
}

case class NuevoPartido(
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None
) extends PreGameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    (
      game.copy(
        gameStatus = GameStatus.comienzo,
        jugadores = game.jugadores.map(
          _.copy(
            mano = false,
            turno = false,
            cantante = false,
            cuantasCantas = None,
            cuenta = Seq.empty,
            fichas = List.empty,
            filas = List.empty
          )
        )
      ),
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = userOpt.flatMap(_.id))
    )
  }
}

case class Sopa(
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None,
  turno:  Option[User] = None
) extends PreGameEvent {
  import Game._
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
          jugador.copy(
            user = jugador.user,
            fichas = fichas,
            filas = List.empty,
            cantante = turno.fold(fichas.contains(laMulota))(_ == jugador.user),
            turno = turno.fold(fichas.contains(laMulota))(_ == jugador.user),
            mano = false,
            cuantasCantas = None
          )
      }
      .toList

    (
      game.copy(
        jugadores = newJugadores,
        gameStatus = GameStatus.cantando
      ),
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = userOpt.flatMap(_.id))
    )
  }

}

//////////////////////////////////////////////////////////////////////////////////////
// Cantando

object CuantasCantas {
  sealed abstract class CuantasCantas(
    val numFilas:  Int,
    val prioridad: Int
  ) {
    def <=(cuantasCanto: CuantasCantas): Boolean = prioridad <= cuantasCanto.prioridad
    def >(cuantasCanto:  CuantasCantas): Boolean = prioridad > cuantasCanto.prioridad
  }

  case object Casa extends CuantasCantas(4, 4)

  case object Canto5 extends CuantasCantas(5, 5)

  case object Canto6 extends CuantasCantas(6, 6)

  case object Canto7 extends CuantasCantas(7, 7)

  case object CantoTodas extends CuantasCantas(7, 8)

  case object Buenas extends CuantasCantas(-1, -1)
}
import chuti.CuantasCantas._

case class Canta(
  cuantasCantas: CuantasCantas,
  gameId:        Option[GameId] = None,
  userId:        Option[UserId] = None,
  index:         Option[Int] = None
) extends PlayEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    if (game.gameStatus != GameStatus.cantando)
      throw GameException("No es el momento de cantar, que onda?")
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
    val cantanteActual = game.jugadores.find(_.cantante).getOrElse(jugador)
    val nuevoCantante = cantanteActual.cuantasCantas.fold {
      //primer jugador cantando
      jugador.copy(cantante = true, mano = true, cuantasCantas = Option(cuantasCantas))
    } { cuantasCanto =>
      if (cuantasCantas <= cuantasCanto || cuantasCanto == CuantasCantas.CantoTodas) {
        //No es suficiente para salvarlo, dejalo como esta
        cantanteActual
      } else {
        //Lo salvaste, te llevas la mano
        jugador.copy(cantante = true, mano = true, cuantasCantas = Option(cuantasCantas))
      }
    }

    //El turno no cambia,
    val jugadoresNuevos =
      game.modifiedPlayers(
        _.user.id == nuevoCantante.user.id,
        _ => nuevoCantante,
        _.copy(cantante = false, mano = false, cuantasCantas = None)
      )

    val newGameStatus =
      if (nextPlayer.turno || cuantasCantas == CuantasCantas.CantoTodas) {
        //jugador es el ultimo en cantar, juego listo para empezar
        GameStatus.jugando
      } else {
        GameStatus.cantando
      }

    val modified = game.copy(jugadores = jugadoresNuevos, gameStatus = newGameStatus)
    (
      modified,
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = userOpt.flatMap(_.id))
    )
  }
}

//////////////////////////////////////////////////////////////////////////////////////
// Jugando

case class PideInicial(
  ficha:           Ficha,
  triunfo:         Triunfo,
  estrictaDerecha: Boolean,
  gameId:          Option[GameId] = None,
  userId:          Option[UserId] = None,
  index:           Option[Int] = None
) extends PlayEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    //Restricciones
    if (game.gameStatus != GameStatus.jugando)
      throw GameException(s"No es el momento de ${game.gameStatus}, que onda?")
    val user = userOpt.getOrElse(throw GameException("User required for this move"))

    val jugadorOpt = game.jugadores.find(_.user.id == user.id)
    if (jugadorOpt.isEmpty) {
      throw GameException("Este jugador no esta jugando aqui!")
    }
    val jugador = jugadorOpt.get
    if (!jugador.mano) {
      throw GameException("No te adelantes")
    }
    if (!jugador.fichas.contains(ficha)) {
      throw GameException("No puedes jugar una ficha que no tienes!")
    }

    (
      //transfiere la ficha al centro
      game
        .copy(
          triunfo = Option(triunfo),
          enJuego = List((user.id.get, ficha)),
          estrictaDerecha = estrictaDerecha,
          jugadores = game.modifiedPlayers(_.user.id == user.id, { j =>
            j.copy(fichas = j.dropFicha(ficha))
          })
        ),
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = userOpt.flatMap(_.id))
    )
  }
}

case class Pide(
  ficha:           Ficha,
  estrictaDerecha: Boolean,
  gameId:          Option[GameId] = None,
  userId:          Option[UserId] = None,
  index:           Option[Int] = None
) extends PlayEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    //TODO Checar que no sea hoyo tecnico, si pides, pero ya estabas hecho to, o el hoyo ya es obvio, es hoyo tecniro
    //Restricciones
    if (game.gameStatus != GameStatus.jugando)
      throw GameException(s"No es el momento de ${game.gameStatus}, que onda?")
    val user = userOpt.getOrElse(throw GameException("User required for this move"))

    val jugadorOpt = game.jugadores.find(_.user.id == user.id)
    if (jugadorOpt.isEmpty) {
      throw GameException("Este jugador no esta jugando aqui!")
    }
    val jugador = jugadorOpt.get
    if (!jugador.mano) {
      throw GameException("No te adelantes")
    }
    if (!jugador.fichas.contains(ficha)) {
      throw GameException("No puedes jugar una ficha que no tienes!")
    }

    (
      //transfiere la ficha al centro
      game
        .copy(
          enJuego = List((user.id.get, ficha)),
          estrictaDerecha = estrictaDerecha,
          jugadores = game.modifiedPlayers(_.user.id == user.id, { j =>
            j.copy(fichas = j.dropFicha(ficha))
          })
        ),
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = userOpt.flatMap(_.id))
    )
  }
}

case class Da(
  ficha:  Ficha,
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None
) extends PlayEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    //Restricciones
    if (game.gameStatus != GameStatus.jugando)
      throw GameException(s"No es el momento de ${game.gameStatus}, que onda?")
    val user = userOpt.getOrElse(throw GameException("User required for this move"))

    val jugadorOpt = game.jugadores.find(_.user.id == user.id)
    if (jugadorOpt.isEmpty) {
      throw GameException("Este jugador no esta jugando aqui!")
    }
    val jugador = jugadorOpt.get
    if (game.estrictaDerecha && !jugador.mano) {
      throw GameException("No te adelantes")
    }
    if (!jugador.fichas.contains(ficha)) {
      throw GameException("Este jugador no tiene esta ficha!")
    }

    //TODO Checar hoyo tecnico (el jugador dio una ficha que no fue pedida, o teniendo triunfo no lo solto)

    val enJuego = game.enJuego :+ (user.id.get, ficha)
    //Si es el cuarto jugador dando la ficha
    val modifiedGame: Game = if (enJuego.size == 4) {
      //Al ganador le damos las cuatro fichas, le damos también la mano, empezamos mano nueva
      //Nota, la primera fila se queda abierta, las demas se esconden y ya no importan.
      val ganador = Game.calculaJugadorGanador(enJuego, enJuego.head._2, game.triunfo.get)
      game.copy(jugadores = game.modifiedPlayers(
        _.user.id == Option(ganador._1), { j =>
          j.copy(
            mano = true,
            fichas = j.dropFicha(ficha),
            filas = j.filas :+ Fila(
              enJuego.map(_._2),
              abierta = game.jugadores.flatMap(_.filas).isEmpty
            )
          )
        }, { j =>
          j.copy(mano = false, fichas = j.dropFicha(ficha))
        }
      )
      )
    } else {
      //transfiere la ficha al centro
      game
        .copy(
          enJuego = enJuego,
          jugadores = game.modifiedPlayers(_.user.id == user.id, { j =>
            j.copy(fichas = j.dropFicha(ficha))
          })
        )
      //Checar como va el juego.
      //Ya fue hoyo?
      //Ya se hizo?
      //Fue hoyo tecnico?
      //Si se acabo el juego, checar como va el partido
      //Anunciar las cuentas
      //Ya se fue alguien?
    }
    (
      modifiedGame,
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = userOpt.flatMap(_.id))
    )
  }

}

//Acuerdate de los regalos
case class DeCaida(
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None
) extends PlayEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    //Restricciones
    if (game.gameStatus != GameStatus.jugando)
      throw GameException(s"No es el momento de ${game.gameStatus}, que onda?")
    val user = userOpt.getOrElse(throw GameException("User required for this move"))

    val jugadorOpt = game.jugadores.find(_.user.id == user.id)
    if (jugadorOpt.isEmpty) {
      throw GameException("Este jugador no esta jugando aqui!")
    }
    val jugador = jugadorOpt.get
    if (game.estrictaDerecha && !jugador.mano) {
      throw GameException("No te adelantes")
    }

    if ((game.quienCanta.filas.size + game.quienCanta.fichas.size) < game.quienCanta.cuantasCantas
          .fold(0)(_.numFilas)) {
      //Ya fue hoyo!
    } else if (game.quienCanta.filas.size >= game.quienCanta.cuantasCantas.fold(0)(_.numFilas)) {
      //Ya se hizo
    }

    //Checar si de caida es posible. Se anotan los puntos, regalos? termina juego.
    ()
    ??? //TODO write this
  }
}

case class MeRindo(
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None
) extends PlayEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = ??? //TODO write this
  //Nada mas te puedes rendir hasta correr la primera.
  //Se apunta un hoyo, juego nuevo
}

//////////////////////////////////////////////////////////////////////////////////////
// Game End
case class HoyoTecnico(
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = ??? //TODO write this
  //Se apunta un hoyo, juego nuevo.
}

case class TerminaJuego(
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None,
  index:  Option[Int] = None
) extends GameEvent {
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = ??? //TODO write this
  //Apuntar las cuentas...
  // Si nadie a llegado a 21, juego nuevo.
  // si ya llego alguien a 21, se hacen las cuentas.
}
