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

import chuti.Triunfo.{SinTriunfos, TriunfoNumero}

import scala.annotation.tailrec
import scala.util.Random

//////////////////////////////////////////////////////////////////////////////////////
// General stuff and parents
sealed trait EventInfo[T <: GameEvent] {

  def canDo(
    jugador: Jugador,
    game:    Game
  ): Boolean
  val values: Seq[EventInfo[?]] = Seq(NoOp)

}

object ReapplyMode extends Enumeration {

  type ReapplyMode = Value
  val none, fullRefresh, reapply = Value

}

import chuti.ReapplyMode.*

sealed trait GameEvent {

  val gameId:           Option[GameId]
  val userId:           Option[UserId]
  val index:            Option[Int]
  val gameStatusString: Option[String]
  def soundUrl: Option[String]
  val jugadorStatusString: Seq[(UserId, String)]
  val reapplyMode: ReapplyMode = reapply
  def expectedStatus: Option[GameStatus]
  def redoEvent(
    userOpt: Option[User],
    game:    Game
  ): Game
  def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent)

  def processStatusMessages(game: Game): Game = {
    game.setStatusStrings(gameStatusString, jugadorStatusString)
  }

}

sealed trait PreGameEvent extends GameEvent
sealed trait PlayEvent extends GameEvent {

  def doEvent(
    jugador: Jugador,
    game:    Game
  ): (Game, GameEvent)
  def redoEvent(
    jugador: Jugador,
    game:    Game
  ): Game

  override def expectedStatus: Option[GameStatus] = Option(GameStatus.jugando)

  final def redoEvent(
    userOpt: Option[User],
    game:    Game
  ): Game = {
    userOpt.fold(throw GameException("Eventos de juego requieren un usuario")) { user =>
      if (expectedStatus.fold(false)(_ != game.gameStatus))
        throw GameException(s"No es el momento de ${game.gameStatus}, que onda? Event = $this")

      processStatusMessages(redoEvent(game.jugador(user.id), game))
    }
  }

  final def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    userOpt.fold(throw GameException("Eventos de juego requieren un usuario")) { user =>
      if (expectedStatus.fold(false)(_ != game.gameStatus))
        throw GameException(s"No es el momento de ${game.gameStatus}, que onda? Event = $this")

      doEvent(game.jugador(user.id), game)
    }
  }

}

object NoOp extends EventInfo[NoOp] {

  override def canDo(
    jugador: Jugador,
    game:    Game
  ): Boolean = true // always true

}

case class NoOp(
  gameId:              Option[GameId] = None,
  userId:              Option[UserId] = None,
  index:               Option[Int] = None,
  gameStatusString:    Option[String] = None,
  soundUrl:            Option[String] = None,
  jugadorStatusString: Seq[(UserId, String)] = Seq.empty
) extends GameEvent {

  override val reapplyMode:    ReapplyMode = none
  override def expectedStatus: Option[GameStatus] = None
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) =
    (
      game,
      copy(
        index = Option(game.currentEventIndex),
        gameId = game.id,
        userId = userOpt.flatMap(_.id),
        gameStatusString = None
      )
    )

  override def redoEvent(
    userOpt: Option[User],
    game:    Game
  ): Game = game

}

case class NoOpPlay(
  gameId:              Option[GameId] = None,
  userId:              Option[UserId] = None,
  index:               Option[Int] = None,
  gameStatusString:    Option[String] = None,
  soundUrl:            Option[String] = None,
  jugadorStatusString: Seq[(UserId, String)] = Seq.empty
) extends PlayEvent {

  override val reapplyMode:    ReapplyMode = none
  override def expectedStatus: Option[GameStatus] = Option(GameStatus.jugando)

  override def doEvent(
    jugador: Jugador,
    game:    Game
  ): (Game, GameEvent) =
    (
      game,
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = jugador.id)
    )

  override def redoEvent(
    jugador: Jugador,
    game:    Game
  ): Game = game

}

case class BorloteEvent(
  borlote:             Borlote,
  gameId:              Option[GameId] = None,
  userId:              Option[UserId] = None,
  index:               Option[Int] = None,
  gameStatusString:    Option[String] = None,
  jugadorStatusString: Seq[(UserId, String)] = Seq.empty
) extends GameEvent {

  override def soundUrl: Option[String] =
    borlote match {
      case Borlote.Hoyo                => Some("sounds/hoyo.mp3")
      case Borlote.HoyoTecnico         => Some("sounds/hoyoTecnico.mp3")
      case Borlote.ElNiñoDelCumpleaños => Some("sounds/cumpleanos.mp3")
      case Borlote.Campanita           => Some("sounds/campanita.mp3")
      case Borlote.SantaClaus          => Some("sounds/santaclaus.mp3")
      case _                           => None
    }

  override def expectedStatus: Option[GameStatus] = None
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) =
    (
      game,
      copy(index = Option(game.currentEventIndex), gameId = game.id, userId = userOpt.flatMap(_.id))
    )

  override def redoEvent(
    userOpt: Option[User],
    game:    Game
  ): Game = processStatusMessages(game)

}

//This event ends the game and shuts down the server... it can only be called by god
case class PoisonPill(
  gameId: Option[GameId] = None,
  userId: Option[UserId] = None
) extends GameEvent {

  override val index:               Option[Int] = None
  override val gameStatusString:    Option[String] = None
  override val jugadorStatusString: Seq[(UserId, String)] = Seq.empty
  override def expectedStatus:      Option[GameStatus] = None
  override val soundUrl:            Option[String] = None

  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    if (userOpt.fold(false)(_.id != Option(godUserId)))
      throw GameException("Solo dios puede administrar veneno")
    (
      game,
      copy(gameId = game.id, userId = userOpt.flatMap(_.id))
    )
  }

  override def redoEvent(
    userOpt: Option[User],
    game:    Game
  ): Game = throw GameException("No puedes volver a hacer este evento")

}

//////////////////////////////////////////////////////////////////////////////////////
// Start game and invitations
case class AbandonGame(
  gameId:              Option[GameId] = None,
  userId:              Option[UserId] = None,
  index:               Option[Int] = None,
  gameStatusString:    Option[String] = None,
  soundUrl:            Option[String] = None,
  jugadorStatusString: Seq[(UserId, String)] = Seq.empty
) extends PreGameEvent {

  override def expectedStatus: Option[GameStatus] = None
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    val user = userOpt.getOrElse(throw GameException("Se necesita un usuario para esta movida"))

    (
      game.copy(
        gameStatus =
          if (
            (game.gameStatus != GameStatus.esperandoJugadoresInvitados && game.gameStatus != GameStatus.esperandoJugadoresAzar) ||
            game.jugadores.size == 1
          ) // if it's the last user in the game the game is finished
            GameStatus.abandonado
          else
            game.gameStatus,
        jugadores = game.jugadores.filter(_.id != user.id)
      ),
      copy(
        index = Option(game.currentEventIndex),
        gameId = game.id,
        userId = userOpt.flatMap(_.id),
        gameStatusString = Option(s"${user.name} abandono el juego")
      )
    )
  }
  override def redoEvent(
    userOpt: Option[User],
    game:    Game
  ): Game = processStatusMessages(doEvent(userOpt, game)._1)

}

case class DeclineInvite(
  gameId:              Option[GameId] = None,
  userId:              Option[UserId] = None,
  index:               Option[Int] = None,
  gameStatusString:    Option[String] = None,
  soundUrl:            Option[String] = None,
  jugadorStatusString: Seq[(UserId, String)] = Seq.empty
) extends PreGameEvent {

  override def expectedStatus: Option[GameStatus] = Option(GameStatus.esperandoJugadoresInvitados)
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    val user = userOpt.getOrElse(throw GameException("Se necesita un usuario para esta movida"))
    (
      game.copy(jugadores = game.jugadores.filter(_.id != user.id)),
      copy(
        index = Option(game.currentEventIndex),
        gameId = game.id,
        userId = userOpt.flatMap(_.id),
        gameStatusString = Option(s"${user.name} no quizo jugar")
      )
    )
  }

  override def redoEvent(
    userOpt: Option[User],
    game:    Game
  ): Game = processStatusMessages(doEvent(userOpt, game)._1)

}

case class InviteToGame(
  invited:             User,
  gameId:              Option[GameId] = None,
  userId:              Option[UserId] = None,
  index:               Option[Int] = None,
  gameStatusString:    Option[String] = None,
  soundUrl:            Option[String] = None,
  jugadorStatusString: Seq[(UserId, String)] = Seq.empty
) extends PreGameEvent {

  override def expectedStatus: Option[GameStatus] = Option(GameStatus.esperandoJugadoresInvitados)
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    if (game.jugadores.exists(j => j.id == invited.id))
      throw GameException("Un jugador no puede estar dos veces en el mismo juego")
    if (game.jugadores.exists(_.id == invited.id))
      throw GameException(s"Usuario ${invited.id} ya esta en el juego")
    if (game.jugadores.length == game.numPlayers)
      throw GameException("El juego ya esta completo")
    (
      game.copy(jugadores = game.jugadores :+ Jugador(invited, invited = true)),
      copy(
        index = Option(game.currentEventIndex),
        gameId = game.id,
        userId = userOpt.flatMap(_.id),
        gameStatusString = Option(s"invitamos a ${invited.name} a jugar")
      )
    )
  }
  override def redoEvent(
    userOpt: Option[User],
    game:    Game
  ): Game = processStatusMessages(doEvent(userOpt, game)._1)

}

case class JoinGame(
  joinedUser:          Option[User] = None,
  gameId:              Option[GameId] = None,
  userId:              Option[UserId] = None,
  index:               Option[Int] = None,
  gameStatusString:    Option[String] = None,
  soundUrl:            Option[String] = None,
  jugadorStatusString: Seq[(UserId, String)] = Seq.empty
) extends PreGameEvent {

  override def expectedStatus: Option[GameStatus] = Option(GameStatus.esperandoJugadoresInvitados)
  override def doEvent(
    userOpt: Option[User],
    game:    Game
  ): (Game, GameEvent) = {
    val user =
      joinedUser.getOrElse(
        userOpt.getOrElse(throw GameException("Se necesita un usuario para esta movida"))
      )
    if (game.jugadores.exists(j => j.id == user.id && !j.invited))
      throw GameException("Un jugador no puede estar dos veces en el mismo juego")

    val newPlayer = game.jugadores
      .find(_.id == user.id).fold(Jugador(user))(j => j.copy(invited = false))

    (
      game.copy(
        jugadores = game.jugadores.filter(_.id != user.id) :+ newPlayer
      ),
      copy(
        joinedUser = Option(user),
        index = Option(game.currentEventIndex),
        gameId = game.id,
        userId = user.id,
        gameStatusString = Option(s"${user.name} entro al juego")
      )
    )
  }

  override def redoEvent(
    userOpt: Option[User],
    game:    Game
  ): Game =
    processStatusMessages {
      val done = doEvent(userOpt, game)._1
      if (done.canTransitionTo(GameStatus.requiereSopa)) {
        done.copy(
          jugadores = done.jugadores.map(_.copy(invited = false)),
          gameStatus = GameStatus.requiereSopa
        )
      } else
        done
    }

}

case class NuevoPartido(
  gameId:              Option[GameId] = None,
  userId:              Option[UserId] = None,
  index:               Option[Int] = None,
  gameStatusString:    Option[String] = None,
  soundUrl:            Option[String] = None,
  jugadorStatusString: Seq[(UserId, String)] = Seq.empty
) extends PreGameEvent {

  override def expectedStatus: Option[GameStatus] = Option(GameStatus.comienzo)
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
      copy(
        index = Option(game.currentEventIndex),
        gameId = game.id,
        userId = userOpt.flatMap(_.id),
        gameStatusString = Option(s"Nuevo partido con los mismos jugadores")
      )
    )
  }
  override def redoEvent(
    userOpt: Option[User],
    game:    Game
  ): Game = processStatusMessages(doEvent(userOpt, game)._1)

}

//If you want to be able to store and replay events, Sopa needs to carry the whole new game after it's done.
case class Sopa(
  gameId:              Option[GameId] = None,
  userId:              Option[UserId] = None,
  index:               Option[Int] = None,
  gameStatusString:    Option[String] = None,
  soundUrl:            Option[String] = Option("sounds/sopa.mp3"),
  jugadorStatusString: Seq[(UserId, String)] = Seq.empty,
  firstSopa:           Boolean = false
) extends PlayEvent {

  override val reapplyMode: ReapplyMode = fullRefresh
  import Game.*
  def sopa:                    List[Ficha] = Random.shuffle(todaLaFicha)
  override def expectedStatus: Option[GameStatus] = Option(GameStatus.requiereSopa)
  override def doEvent(
    jugador: Jugador,
    game:    Game
  ): (Game, GameEvent) = {
    // La sopa siempre la debe de hacer el jugador anterior al turno
    // Realmente no importa, pero hay que mantener la tradición :)
    if (!firstSopa && !jugador.turno)
      throw GameException("Quien canto la ultima vez tiene que hacer la sopa")
    val nextTurno: Jugador = game.nextPlayer(jugador)

    val jugadores = game.jugadores
    val newJugadores = sopa
      .grouped(todaLaFicha.length / jugadores.length)
      .zip(jugadores)
      .map { case (fichas, jugador) =>
        jugador.copy(
          user = jugador.user,
          fichas = fichas,
          filas = List.empty,
          cantante = (firstSopa && fichas
            .contains(laMulota)) || (!firstSopa && nextTurno.id == jugador.id),
          turno = (firstSopa && fichas
            .contains(laMulota)) || (!firstSopa && nextTurno.id == jugador.id),
          mano = (firstSopa && fichas
            .contains(laMulota)) || (!firstSopa && nextTurno.id == jugador.id),
          cuantasCantas = None
        )
      }
      .toList

    val newGame = game.copy(
      jugadores = newJugadores,
      gameStatus = GameStatus.cantando,
      enJuego = List.empty,
      triunfo = None
    )

    (
      newGame,
      copy(
        index = Option(newGame.currentEventIndex),
        gameId = game.id,
        userId = jugador.id,
        gameStatusString = Option(
          s"${jugador.user.name} hizo la sopa${newGame.quienCanta.fold("")(j => s", ${j.user.name} canta.")}"
        )
      )
    )
  }

  override def redoEvent(
    jugador: Jugador,
    game:    Game
  ): Game = {
    // necesitas básicamente el juego completo, sopa should call for a reload of the game
    throw GameException("No puedes hacer la sopa, tienes que empezar el juego desde el principio")
  }

}

//////////////////////////////////////////////////////////////////////////////////////
// Cantando

import chuti.CuantasCantas.*

case class Canta(
  cuantasCantas:       CuantasCantas,
  gameId:              Option[GameId] = None,
  userId:              Option[UserId] = None,
  index:               Option[Int] = None,
  gameStatusString:    Option[String] = None,
  soundUrl:            Option[String] = Option("sounds/canta.mp3"),
  jugadorStatusString: Seq[(UserId, String)] = Seq.empty
) extends PlayEvent {

  override def expectedStatus: Option[GameStatus] = Option(GameStatus.cantando)

  override def doEvent(
    jugador: Jugador,
    game:    Game
  ): (Game, GameEvent) = {
    if (game.gameStatus != GameStatus.cantando)
      throw GameException("No es el momento de cantar, que onda?")

    val nextPlayer = game.nextPlayer(jugador)
    val cantanteActual = game.jugadores.find(_.cantante).getOrElse(jugador)
    val nuevoCantante = cantanteActual.cuantasCantas.fold {
      // primer jugador cantando
      jugador.copy(cantante = true, mano = false, cuantasCantas = Option(cuantasCantas))
    } { cuantasCanto =>
      if (cuantasCantas.prioridad <= cuantasCanto.prioridad || cuantasCanto == CuantasCantas.CantoTodas)
        // No es suficiente para salvarlo, dejalo como esta
        cantanteActual
      else
        // Lo salvaste, ahora eres el cantante
        jugador.copy(cantante = true, mano = false, cuantasCantas = Option(cuantasCantas))
    }

    val nextJugador = game.nextPlayer(jugador)
    // El turno no cambia, pero la mano si
    val jugadoresNuevos = game.jugadores.map { other =>
      val a =
        if (other.id == nuevoCantante.id)
          nuevoCantante
        else
          other.copy(cantante = false, mano = false)
      if (a.id == jugador.id && cuantasCantas == CuantasCantas.Buenas)
        // Si canto buenas márcalo asi
        a.copy(cuantasCantas = Option(CuantasCantas.Buenas))
      else if (a.id == nextJugador.id && !(nextPlayer.turno || cuantasCantas == CuantasCantas.CantoTodas))
        // Pásale la mano al siguiente jugador, a menos que sea el ultimo jugador
        a.copy(mano = true)
      else
        a
    }

    val salvoString = if (nuevoCantante.user.id != cantanteActual.user.id) {
      Option(
        s"${nuevoCantante.user.name} salvo a ${cantanteActual.user.name}, cantando $cuantasCantas"
      )
    } else
      Option(s"${cantanteActual.user.name} canto ${if (cuantasCantas == CuantasCantas.Buenas) CuantasCantas.Casa
        else cuantasCantas} ")

    val newGameStatus =
      if (nextPlayer.turno || cuantasCantas == CuantasCantas.CantoTodas)
        // jugador es el ultimo en cantar, juego listo para empezar
        GameStatus.jugando
      else
        GameStatus.cantando

    val modified = game.copy(jugadores = jugadoresNuevos, gameStatus = newGameStatus)
    (
      modified.copy(jugadores = modified.jugadores.map { j =>
        if (j.cantante && newGameStatus == GameStatus.jugando)
          j.copy(mano = true)
        else if (newGameStatus == GameStatus.jugando)
          j.copy(cuantasCantas = Option(CuantasCantas.Buenas))
        else j
      }),
      copy(
        index = Option(game.currentEventIndex),
        gameId = game.id,
        userId = jugador.id,
        gameStatusString = salvoString
      )
    )
  }

  override def redoEvent(
    jugador: Jugador,
    game:    Game
  ): Game = doEvent(jugador, game)._1

}

//////////////////////////////////////////////////////////////////////////////////////
// Jugando

case class Pide(
  ficha:               Ficha,
  estrictaDerecha:     Boolean,
  triunfo:             Option[Triunfo] = None,
  hoyoTecnico:         Option[String] = None,
  gameId:              Option[GameId] = None,
  userId:              Option[UserId] = None,
  index:               Option[Int] = None,
  gameStatusString:    Option[String] = None,
  soundUrl:            Option[String] = Option("sounds/ficha.mp3"),
  jugadorStatusString: Seq[(UserId, String)] = Seq.empty
) extends PlayEvent {

  private def pideInicial(
    ficha:           Ficha,
    triunfo:         Triunfo,
    estrictaDerecha: Boolean,
    jugador:         Jugador,
    game:            Game
  ): (Game, Pide) = {
    if (game.triunfo.fold(false)(_ != triunfo))
      throw GameException("No puedes cambiar triunfos cuando se te antoje.")
    val modified = game
      .copy(
        triunfo = Option(triunfo),
        enJuego = List((jugador.id.get, ficha)),
        estrictaDerecha = estrictaDerecha,
        jugadores = game.modifiedJugadores(
          _.id == jugador.id,
          { j =>
            j.copy(
              fichas = j.dropFicha(ficha),
              cuantasCantas = if (j.cantante) j.cuantasCantas else None
            )
          },
          j => j.copy(cuantasCantas = if (j.cantante) j.cuantasCantas else None)
        ) // Ya no importa quien canto que
      )
    (
      // transfiere la ficha al centro
      modified,
      copy(
        index = Option(game.currentEventIndex),
        gameId = game.id,
        userId = jugador.id,
        // En este caso, el hoyo tecnico se calcula despues de haber declarado triunfo
        hoyoTecnico =
          if (modified.puedesCaerte(jugador)) Option("Podías haberte caído, no lo hiciste")
          else None,
        gameStatusString = Option(
          s"${jugador.user.name} salio con $ficha ${if (estrictaDerecha) ". Estricta derecha!"
            else ""}"
        )
      )
    )

  }
  private def pide(
    ficha:           Ficha,
    estrictaDerecha: Boolean,
    jugador:         Jugador,
    game:            Game
  ): (Game, Pide) = {
    val modified = game
      .copy(
        enJuego = List((jugador.id.get, ficha)),
        estrictaDerecha = estrictaDerecha,
        jugadores = game.modifiedJugadores(_.id == jugador.id, j => j.copy(fichas = j.dropFicha(ficha)))
      )
    (
      // transfiere la ficha al centro
      modified,
      copy(
        index = Option(game.currentEventIndex),
        gameId = game.id,
        userId = jugador.id,
        hoyoTecnico =
          if (game.puedesCaerte(jugador)) Option("Podías haberte caído, no lo hiciste")
          else None
      )
    )
  }

  override def doEvent(
    jugador: Jugador,
    game:    Game
  ): (Game, GameEvent) = {
    // Restricciones
    if (!jugador.fichas.contains(ficha))
      throw GameException("No puedes jugar una ficha que no tienes!")
    if (game.enJuego.nonEmpty)
      throw GameException("No puedes pedir si hay fichas en la mesa")
    triunfo.fold(pide(ficha, estrictaDerecha, jugador, game))(triunfo => pideInicial(ficha, triunfo, estrictaDerecha, jugador, game))
  }

  def redoPide(
    ficha:           Ficha,
    estrictaDerecha: Boolean,
    jugador:         Jugador,
    game:            Game
  ): Game =
    processStatusMessages(
      game
        .copy(
          enJuego = List((jugador.id.get, ficha)),
          estrictaDerecha = estrictaDerecha,
          jugadores = game.modifiedJugadores(_.id == jugador.id, j => j.copy(fichas = j.dropFicha(ficha)))
        )
    )

  def redoPideInicial(
    ficha:           Ficha,
    triunfo:         Triunfo,
    estrictaDerecha: Boolean,
    jugador:         Jugador,
    game:            Game
  ): Game =
    processStatusMessages(
      game.copy(
        triunfo = Option(triunfo),
        enJuego = List((jugador.id.get, ficha)),
        estrictaDerecha = estrictaDerecha,
        jugadores = game.modifiedJugadores(
          _.id == jugador.id,
          { j =>
            j.copy(
              fichas = j.dropFicha(ficha),
              cuantasCantas = if (j.cantante) j.cuantasCantas else None
            )
          },
          j => j.copy(cuantasCantas = if (j.cantante) j.cuantasCantas else None)
        ) // Ya no importa quien canto que
      )
    )

  override def redoEvent(
    jugador: Jugador,
    game:    Game
  ): Game = {
    triunfo.fold(redoPide(ficha, estrictaDerecha, jugador, game))(triunfo => redoPideInicial(ficha, triunfo, estrictaDerecha, jugador, game))
  }

}

case class Da(
  ficha:               Ficha,
  ganador:             Option[UserId] = None,
  hoyoTecnico:         Option[String] = None,
  gameId:              Option[GameId] = None,
  userId:              Option[UserId] = None,
  index:               Option[Int] = None,
  gameStatusString:    Option[String] = None,
  soundUrl:            Option[String] = Option("sounds/ficha.mp3"),
  jugadorStatusString: Seq[(UserId, String)] = Seq.empty
) extends PlayEvent {

  override def doEvent(
    jugador: Jugador,
    game:    Game
  ): (Game, GameEvent) = {
    // Restricciones
    if (!jugador.fichas.contains(ficha))
      throw GameException("Este jugador no tiene esta ficha!")
    if (game.estrictaDerecha && !game.enJuego.exists(_._1 == game.prevPlayer(jugador).id.get))
      throw GameException("Estricta derecha, no te adelantes!")
    val enJuego = game.enJuego :+ (jugador.id.get, ficha)
    // Si es el cuarto jugador dando la ficha
    val (modifiedGame, ganador, gameStatusString): (Game, Option[UserId], Option[String]) =
      if (enJuego.size == game.numPlayers) {
        // Al ganador le damos las cuatro fichas, le damos también la mano, empezamos mano nueva
        // Nota, la primera fila se queda abierta, las demas se esconden y ya no importan.
        // could rewrite this using game.fichaGanadora(
        val (ganador, fichaGanadora @ _): (UserId, Ficha) =
          Game.calculaJugadorGanador(enJuego, enJuego.head._2, game.triunfo.get)
        val totalFilas = game.jugadores.flatMap(_.filas).size
        // I'm not sure who has it, but the code b
        val a = game.copy(
          enJuego = List.empty,
          estrictaDerecha = false,
          jugadores = game.modifiedJugadores(
            _.id == Option(ganador),
            { j =>
              j.copy(
                mano = true,
                filas = j.filas :+ Fila(
                  enJuego.map(_._2),
                  totalFilas
                )
              )
            },
            _.copy(mano = false)
          )
        )
        (
          a.copy(
            jugadores = a.modifiedJugadores(_.id == jugador.id, j => j.copy(fichas = j.dropFicha(ficha)))
          ),
          Option(ganador),
          Option(s"${game.jugador(Option(ganador)).user.name} gano la ultima mano")
        )
      } else {
        // transfiere la ficha al centro
        (
          game
            .copy(
              enJuego = enJuego,
              jugadores = game.modifiedJugadores(_.id == jugador.id, j => j.copy(fichas = j.dropFicha(ficha)))
            ),
          None,
          None
        )
      }

    // Checar hoyo tecnico (el jugador dio una ficha que no fue pedida, o teniendo triunfo no lo solto)
    val pidióFicha = enJuego.head._2
    val pidioNum = game.triunfo match {
      case None                     => throw GameException("Nuncamente!")
      case Some(TriunfoNumero(num)) => if (pidióFicha.es(num)) num else pidióFicha.arriba
      case Some(SinTriunfos)        => pidióFicha.arriba
    }

    val hoyoTecnico =
      if (
        !ficha.es(pidioNum) &&
        modifiedGame.jugador(jugador.id).fichas.exists(_.es(pidioNum))
      )
        Option(s"Pidieron $pidioNum, diste $ficha pero si tienes $pidioNum.... que tonto!")
      else if (
        !ficha.es(pidioNum) &&
        (game.triunfo match {
          case None => throw GameException("Nuncamente!")
          case Some(TriunfoNumero(num)) =>
            !ficha.es(num) &&
            modifiedGame.jugador(jugador.id).fichas.exists(_.es(num))
          case Some(SinTriunfos) => false
        })
      )
        Option(s"Pidieron $pidioNum, diste $ficha pero si tienes triunfos")
      else
        None

    (
      modifiedGame,
      copy(
        ganador = ganador,
        hoyoTecnico = hoyoTecnico,
        index = Option(game.currentEventIndex),
        gameId = game.id,
        userId = jugador.id,
        gameStatusString = gameStatusString
      )
    )
  }

  override def redoEvent(
    jugador: Jugador,
    game:    Game
  ): Game = {
    val enJuego = game.enJuego :+ (jugador.id.get, ficha)
    if (enJuego.size == game.numPlayers) {
      // Al ganador le damos las cuatro fichas, le damos también la mano, empezamos mano nueva
      // Nota, la primera fila se queda abierta, las demas se esconden y ya no importan.
      val totalFilas = game.jugadores.flatMap(_.filas).size
      val a = game.copy(
        enJuego = List.empty,
        estrictaDerecha = false,
        jugadores = game.modifiedJugadores(
          _.id == ganador,
          { j =>
            j.copy(
              mano = true,
              filas = j.filas :+ Fila(
                enJuego.map(_._2),
                index = totalFilas
              )
            )
          },
          _.copy(mano = false)
        )
      )
      a.copy(
        jugadores = a.modifiedJugadores(_.id == jugador.id, j => j.copy(fichas = j.dropFicha(ficha)))
      )
    } else {
      // transfiere la ficha al centro
      game
        .copy(
          enJuego = enJuego,
          jugadores = game.modifiedJugadores(_.id == jugador.id, j => j.copy(fichas = j.dropFicha(ficha)))
        )
    }
  }

}

//Acuerdate de los regalos
case class Caete(
  regalos:             Seq[(UserId, Seq[Fila])] = Seq.empty,
  deCaida:             Seq[Fila] = Seq.empty,
  triunfo:             Option[Triunfo] = None,
  gameId:              Option[GameId] = None,
  userId:              Option[UserId] = None,
  index:               Option[Int] = None,
  gameStatusString:    Option[String] = None,
  soundUrl:            Option[String] = None,
  jugadorStatusString: Seq[(UserId, String)] = Seq.empty,
  ganadorDePartido:    Option[UserId] = None
) extends PlayEvent {

  override def doEvent(
    jugador: Jugador,
    game:    Game
  ): (Game, GameEvent) = {
    // Restricciones
    if (!jugador.mano)
      throw GameException("No te adelantes")
    // Te puedes caer antes de pedir la inicial, en cuyo caso hay que establecer los triunfos
    val conTriunfos = game.copy(triunfo = game.triunfo.fold(triunfo)(Option(_)))

    val deCaída: Seq[Fila] = conTriunfos
      .cuantasDeCaida(
        jugador.fichas,
        conTriunfos.jugadores.filter(_.id != jugador.id).flatMap(_.fichas)
      )

    val puntosNuevos = jugador.cuantasCantas.fold(deCaída.size)(c =>
      if (jugador.cantante && c == CuantasCantas.CantoTodas && (jugador.filas.size + deCaída.size) == 7) 21
      else deCaída.size + jugador.filas.size
    )

    // Regalos, en orden de mayor a menor.
    val losRegalos: Seq[Ficha] =
      jugador.fichas.diff(deCaída.map(_.fichas.head)).sortBy(-_.arriba.value)

    val jugadores =
      conTriunfos.modifiedJugadores(
        _.id == jugador.id,
        j => {
          val ganadorDePartido = (j.cuenta.map(_.puntos).sum + puntosNuevos) >= 21
          j.copy(
            filas = j.filas ++ deCaída,
            fichas = List.empty,
            ganadorDePartido = ganadorDePartido
          )
        },
        j => j.copy(fichas = j.fichas.diff(deCaída.flatMap(_.fichas)))
      )

    // Nota que los regalos pueden ocasionar que alguien gane el partido.
    // Los regalos están en orden de tamaño, por lo que el primero que llege a 21 es el ganador.
    @tailrec def regaloLoop(
      regalos:   Seq[Ficha],
      jugadores: Map[Jugador, Seq[Fila]]
    ): Map[Jugador, Seq[Fila]] = {
      if (regalos.isEmpty)
        jugadores
      else {

        val yaHayGanador = jugadores.exists(_._1.ganadorDePartido)
        val regalo = regalos.head
        val fichaMerecedora =
          conTriunfos.fichaGanadora(regalo, jugadores.flatMap(_._1.fichas).toSeq)

        regaloLoop(
          regalos.tail,
          jugadores = jugadores.map(j =>
            if (j._1.fichas.contains(fichaMerecedora)) {
              (
                j._1.copy(
                  filas = j._1.filas :+ Fila(-1, fichaMerecedora, regalo),
                  fichas = j._1.fichas.filter(_ != fichaMerecedora),
                  ganadorDePartido = !yaHayGanador && ((j._1.cuenta
                    .map(_.puntos).sum + j._1.filas.size + 1) >= 21)
                ),
                j._2 :+ Fila(-1, fichaMerecedora, regalo)
              )
              // Nota que es posible auto-regalarse si nadie mas tiene otra de esas
            } else if (j._1.id == jugador.id && regalo == fichaMerecedora) {
              (
                j._1.copy(
                  filas = j._1.filas :+ Fila(-1, fichaMerecedora),
                  fichas = j._1.fichas.filter(_ != fichaMerecedora),
                  ganadorDePartido = !yaHayGanador && ((j._1.cuenta
                    .map(_.puntos).sum + j._1.filas.size + 1) >= 21)
                ),
                j._2 :+ Fila(-1, fichaMerecedora)
              )
            } else
              j
          )
        )
      }
    }

    val regalosRegalados: Map[Jugador, Seq[Fila]] =
      regaloLoop(losRegalos, jugadores.map(j => (j, Seq.empty[Fila])).toMap)

    val returnEvent = copy(
      triunfo = conTriunfos.triunfo,
      deCaida = deCaída,
      regalos = regalosRegalados.map(r => r._1.id.get -> r._2).toSeq,
      index = Option(game.currentEventIndex),
      gameId = game.id,
      userId = jugador.id,
      gameStatusString = Option(s"${jugador.user.name} acabo. ${if (regalosRegalados.nonEmpty)
          s"Hubieron regalos para ${regalosRegalados.map(_._1.user.name).mkString(",")}."
        else
          ""}"),
      ganadorDePartido = regalosRegalados.find(_._1.ganadorDePartido).flatMap(_._1.id)
    )

    (
      returnEvent.redoEvent(jugador, game),
      returnEvent.copy(soundUrl = Option(s"sounds/caete${jugador.fichas.size - regalos.size}.mp3"))
    )
  }

  override def redoEvent(
    jugador: Jugador,
    game:    Game
  ): Game = {
    val regalosMap = regalos.toMap
    game.copy(
      triunfo = triunfo,
      jugadores = game.modifiedJugadores(
        _.id == jugador.id,
        j =>
          j.copy(
            filas = j.filas ++ deCaida ++ regalosMap.getOrElse(j.id.get, Seq.empty),
            fichas = List.empty,
            ganadorDePartido = ganadorDePartido == j.id
          ),
        j =>
          j.copy(
            filas = j.filas ++ regalosMap.getOrElse(j.id.get, Seq.empty),
            fichas = List.empty,
            ganadorDePartido = ganadorDePartido == j.id
          )
      )
    )
  }

}

case class MeRindo(
  gameId:              Option[GameId] = None,
  userId:              Option[UserId] = None,
  index:               Option[Int] = None,
  gameStatusString:    Option[String] = None,
  soundUrl:            Option[String] = Option("sounds/merindo.mp3"),
  jugadorStatusString: Seq[(UserId, String)] = Seq.empty
) extends PlayEvent {

  override def doEvent(
    jugador: Jugador,
    game:    Game
  ): (Game, GameEvent) = {
    // Nada mas te puedes rendir hasta correr la primera.
    // Se apunta un hoyo, juego nuevo
    if (jugador.filas.size > 1)
      throw GameException("No te puedes rendir después de la primera")
    (
      game.copy(
        gameStatus = GameStatus.requiereSopa,
        jugadores = game.modifiedJugadores(
          _.id == jugador.id,
          guey =>
            guey.copy(cuenta =
              guey.cuenta :+ Cuenta(
                -guey.cuantasCantas.fold(0)(_.score),
                esHoyo = true
              )
            )
        )
      ),
      copy(
        index = Option(game.currentEventIndex),
        gameId = game.id,
        userId = jugador.id,
        gameStatusString = Option(s"${jugador.user.name} se rindió...con su respectivo hoyo.")
      )
    )
  }

  override def redoEvent(
    jugador: Jugador,
    game:    Game
  ): Game = doEvent(jugador, game)._1

}

case class HoyoTecnico(
  razon:               String,
  gameId:              Option[GameId] = None,
  userId:              Option[UserId] = None,
  index:               Option[Int] = None,
  gameStatusString:    Option[String] = None,
  soundUrl:            Option[String] = None,
  jugadorStatusString: Seq[(UserId, String)] = Seq.empty
) extends PlayEvent {

  override def doEvent(
    jugador: Jugador,
    game:    Game
  ): (Game, GameEvent) = {
    (
      // Se apunta un hoyo, juego nuevo.
      game.copy(
        gameStatus = GameStatus.requiereSopa,
        jugadores = game.modifiedJugadores(
          _.id == jugador.id,
          // El hoyo técnico no cuenta puntos negativos a menos que seas el que canta, porque hacerte un hoyo tecnico no te debe absolver de los numeros negativos
          guey =>
            guey.copy(cuenta =
              guey.cuenta :+ Cuenta(
                if (guey.cantante) guey.cuantasCantas.fold(0)(-_.numFilas) else 0,
                esHoyo = true
              )
            )
        )
      ),
      copy(
        index = Option(game.currentEventIndex),
        gameId = game.id,
        userId = jugador.id,
        gameStatusString = Option(s"Hoyo tecnico para ${jugador.user.name}!! $razon")
      )
    )
  }

  override def redoEvent(
    jugador: Jugador,
    game:    Game
  ): Game = doEvent(jugador, game)._1

}

//////////////////////////////////////////////////////////////////////////////////////
// Game End
case class TerminaJuego(
  gameId:              Option[GameId] = None,
  userId:              Option[UserId] = None,
  index:               Option[Int] = None,
  gameStatusString:    Option[String] = None,
  soundUrl:            Option[String] = None,
  jugadorStatusString: Seq[(UserId, String)] = Seq.empty,
  partidoTerminado:    Boolean = false
) extends PlayEvent {

  override val reapplyMode: ReapplyMode = fullRefresh
  override def doEvent(
    jugador: Jugador,
    game:    Game
  ): (Game, GameEvent) = {
    val conCuentas = game.copy(
      gameStatus = if (game.partidoTerminado) GameStatus.partidoTerminado else GameStatus.requiereSopa,
      jugadores = game.modifiedJugadores(
        _.cantante,
        { victima =>
          if (game.quienCanta.fold(false)(_.yaSeHizo)) {
            victima.copy(cuenta =
              victima.cuenta ++ victima.filas.headOption.map(_ => Cuenta(Math.max(victima.filas.size, victima.cuantasCantas.fold(0)(_.score))))
            )
          } else {
            // Ya fue hoyo!
            victima
              .copy(cuenta =
                victima.cuenta :+ Cuenta(
                  -victima.cuantasCantas.fold(0)(_.score),
                  esHoyo = true
                )
              )
          }
        },
        otro => otro.copy(cuenta = otro.cuenta ++ otro.filas.headOption.map(_ => Cuenta(otro.filas.size)))
      )
    )

    val (fueHoyo, statusStr) = game.quienCanta.fold((false, "")) { j =>
      val regalados =
        game.jugadores.filter(j => !j.cantante && j.filas.nonEmpty).map(_.user.name).mkString(",")

      if (j.yaSeHizo) {
        (
          false,
          s"${j.user.name} se hizo con ${j.filas.size} filas. ${if (regalados.nonEmpty) s"Regalos para $regalados."
            else ""}"
        )
      } else {
        (
          true,
          s"Fue hoyo de ${j.cuantasCantas.get} para ${j.user.name}. ${if (regalados.nonEmpty) s"Ayudaron $regalados."
            else ""}"
        )
      }
    }

    val leTocaLaSopa = game.jugadores.find(_.turno).map(j => game.nextPlayer(j))

    if (conCuentas.partidoTerminado) {
      (
        // el ganador de el partido no es necesariamente el que haya llegado a 21 (si hay mas que uno), sino
        // a) el que se fue
        // b) el que obtuvo el primer regalo que hizo que se fuera.
        conCuentas.copy(gameStatus = GameStatus.partidoTerminado, enJuego = List.empty),
        copy(
          index = Option(game.currentEventIndex),
          soundUrl = Option("sounds/partidoTerminado.mp3"),
          gameId = game.id,
          userId = jugador.id,
          gameStatusString = Option(s"$statusStr Se termino el partido!"),
          partidoTerminado = true
        )
      )
    } else {
      (
        conCuentas,
        copy(
          index = Option(game.currentEventIndex),
          soundUrl = if (fueHoyo) Option("sounds/hoyo.mp3") else Option("sounds/gano.mp3"),
          gameId = game.id,
          userId = jugador.id,
          gameStatusString =
            leTocaLaSopa.map(_ => s"$statusStr Se termino el juego, esperando a que ${game.turno.fold("")(_.user.name)} haga la sopa")
        )
      )
    }
  }

  override def redoEvent(
    jugador: Jugador,
    game:    Game
  ): Game = processStatusMessages(doEvent(jugador, game)._1)

}
