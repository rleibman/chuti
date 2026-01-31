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

package chuti.bots.llm

import chuti.*
import chuti.CuantasCantas.*
import chuti.Triunfo.*
import zio.json.*
import zio.json.ast.Json

case class LegalMoves(
  cantas: Seq[CuantasCantas.CuantasCantas],
  pides:  Seq[(Ficha, Triunfo, Boolean)], // ficha, triunfo, estrictaDerecha
  das:    Seq[Ficha],
  caete:  Boolean,
  sopa:   Boolean
)

object MoveValidator {

  def getLegalMoves(
    jugador: Jugador,
    game:    Game
  ): LegalMoves = {
    game.gameStatus match {
      case GameStatus.cantando =>
        getLegalCantas(jugador, game)
      case GameStatus.jugando =>
        getLegalJugandoMoves(jugador, game)
      case GameStatus.requiereSopa =>
        getLegalSopa(jugador, game)
      case _ =>
        LegalMoves(Seq.empty, Seq.empty, Seq.empty, caete = false, sopa = false)
    }
  }

  private def getLegalCantas(
    jugador: Jugador,
    game:    Game
  ): LegalMoves = {
    import CuantasCantas.*

    val availableCantas =
      if (jugador.turno) {
        // Turno player must bid at least Casa
        posibilidades(Casa).filter(_ != Buenas)
      } else {
        // Other players can bid higher than previous bid or say Buenas
        val prevBid = game.prevPlayer(jugador).cuantasCantas.getOrElse(Casa)
        Buenas +: posibilidades(prevBid).filter(_.prioridad > prevBid.prioridad)
      }

    LegalMoves(
      cantas = availableCantas,
      pides = Seq.empty,
      das = Seq.empty,
      caete = false,
      sopa = false
    )
  }

  private def getLegalJugandoMoves(
    jugador: Jugador,
    game:    Game
  ): LegalMoves = {
    // Check if this is the initial pide (cantante's first move)
    if (game.triunfo.isEmpty && jugador.cantante && jugador.filas.isEmpty && game.enJuego.isEmpty) {
      getLegalPideInicial(jugador, game)
    } else if (jugador.mano && game.enJuego.isEmpty) {
      // Player has mano and needs to pide
      getLegalPide(jugador, game)
    } else if (game.enJuego.nonEmpty) {
      // Player needs to da (follow)
      getLegalDa(jugador, game)
    } else {
      LegalMoves(Seq.empty, Seq.empty, Seq.empty, caete = false, sopa = false)
    }
  }

  private def getLegalPideInicial(
    jugador: Jugador,
    game:    Game
  ): LegalMoves = {
    // Generate all possible trump options
    val triunfos: Seq[Triunfo] = SinTriunfos +: Numero.values.map(TriunfoNumero.apply).toSeq

    // For each trump, calculate possible pides
    val pides = triunfos.flatMap { triunfo =>
      val hypotheticalGame = game.copy(triunfo = Option(triunfo))
      // All fichas are valid for initial pide
      jugador.fichas.map { ficha =>
        (ficha, triunfo, false) // estrictaDerecha defaults to false
      }
    }

    val canCaete = triunfos.exists { triunfo =>
      val hypotheticalGame = game.copy(triunfo = Option(triunfo))
      hypotheticalGame.puedesCaerte(jugador)
    }

    LegalMoves(
      cantas = Seq.empty,
      pides = pides,
      das = Seq.empty,
      caete = canCaete,
      sopa = false
    )
  }

  private def getLegalPide(
    jugador: Jugador,
    game:    Game
  ): LegalMoves = {
    // All player's fichas are valid for pide
    // triunfo is already set in the game, so we use a dummy value here
    val pides = jugador.fichas.map { ficha =>
      (ficha, SinTriunfos: Triunfo, false) // triunfo already set in game, estrictaDerecha defaults to false
    }

    val canCaete = game.puedesCaerte(jugador)

    LegalMoves(
      cantas = Seq.empty,
      pides = pides,
      das = Seq.empty,
      caete = canCaete,
      sopa = false
    )
  }

  private def getLegalDa(
    jugador: Jugador,
    game:    Game
  ): LegalMoves = {
    val pide = game.enJuego.head._2

    game.triunfo match {
      case None =>
        // Should never happen
        LegalMoves(Seq.empty, Seq.empty, Seq.empty, caete = false, sopa = false)

      case Some(SinTriunfos) =>
        val pideNum = pide.arriba
        val validFichas = jugador.fichas.filter(_.es(pideNum))
        val das =
          if (validFichas.nonEmpty) validFichas
          else jugador.fichas // Can play any ficha if can't follow

        LegalMoves(
          cantas = Seq.empty,
          pides = Seq.empty,
          das = das,
          caete = false,
          sopa = false
        )

      case Some(TriunfoNumero(triunfo)) =>
        val pideNum =
          if (pide.es(triunfo)) triunfo
          else pide.arriba

        val validFichas = jugador.fichas.filter(_.es(pideNum))
        val das =
          if (validFichas.nonEmpty) validFichas
          else jugador.fichas // Can play any ficha if can't follow

        LegalMoves(
          cantas = Seq.empty,
          pides = Seq.empty,
          das = das,
          caete = false,
          sopa = false
        )
    }
  }

  private def getLegalSopa(
    jugador: Jugador,
    game:    Game
  ): LegalMoves = {
    if (jugador.turno) {
      LegalMoves(
        cantas = Seq.empty,
        pides = Seq.empty,
        das = Seq.empty,
        caete = false,
        sopa = true
      )
    } else {
      LegalMoves(Seq.empty, Seq.empty, Seq.empty, caete = false, sopa = false)
    }
  }

  def toJsonOptions(moves: LegalMoves): String = {
    val options = scala.collection.mutable.ArrayBuffer[Json]()

    // Add Canta options
    moves.cantas.foreach { cuantasCantas =>
      options += Json.Obj(
        "moveType" -> Json.Str("canta"),
        "cuantasCantas" -> Json.Str(cuantasCantas.toString)
      )
    }

    // Add Pide options
    moves.pides.foreach {
      case (ficha, triunfo, estrictaDerecha) =>
        val baseFields = Seq(
          "moveType" -> Json.Str("pide"),
          "ficha" -> Json.Str(ficha.toString)
        )
        val triunfoField =
          if (triunfo != SinTriunfos) Seq("triunfo" -> Json.Str(triunfo.toString))
          else Seq.empty
        val derechaField =
          if (estrictaDerecha) Seq("estrictaDerecha" -> Json.Bool(true))
          else Seq.empty
        options += Json.Obj((baseFields ++ triunfoField ++ derechaField)*)
    }

    // Add Da options
    moves.das.foreach { ficha =>
      options += Json.Obj(
        "moveType" -> Json.Str("da"),
        "ficha" -> Json.Str(ficha.toString)
      )
    }

    // Add Caete option
    if (moves.caete) {
      options += Json.Obj(
        "moveType" -> Json.Str("caete")
      )
    }

    // Add Sopa option
    if (moves.sopa) {
      options += Json.Obj(
        "moveType" -> Json.Str("sopa"),
        "firstSopa" -> Json.Bool(false) // Will be determined from game context
      )
    }

    Json.Arr(options.toSeq*).toString
  }

}
