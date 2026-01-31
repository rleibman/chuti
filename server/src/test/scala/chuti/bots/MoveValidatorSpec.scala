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

package chuti.bots

import chuti.*
import chuti.CuantasCantas.*
import chuti.Triunfo.*
import chuti.bots.llm.*
import dao.InMemoryRepository.*
import zio.*
import zio.test.*

object MoveValidatorSpec extends ZIOSpecDefault {

  def spec = suite("MoveValidatorSpec")(
    test("generates valid Canta options for turno player") {
      val game = TestGameHelper.createTestGame(gameStatus = GameStatus.cantando)
      val jugador = game.jugadores.find(_.turno).get

      val legalMoves = MoveValidator.getLegalMoves(jugador, game)

      assertTrue(
        legalMoves.cantas.contains(Casa),
        legalMoves.cantas.contains(Canto5),
        legalMoves.cantas.contains(Canto6),
        legalMoves.cantas.contains(CantoTodas),
        !legalMoves.cantas.contains(Buenas) // Turno player must bid
      )
    },
    test("generates Buenas for non-turno player") {
      val game = TestGameHelper.createTestGame(gameStatus = GameStatus.cantando)
      val jugador = game.jugadores.find(!_.turno).get

      val legalMoves = MoveValidator.getLegalMoves(jugador, game)

      assertTrue(legalMoves.cantas.contains(Buenas))
    },
    test("allows Sopa only for turno player in requiereSopa phase") {
      val game = TestGameHelper.createTestGame(gameStatus = GameStatus.requiereSopa)
      val turnoJugador = game.jugadores.find(_.turno).get
      val otherJugador = game.jugadores.find(!_.turno).get

      val turnoMoves = MoveValidator.getLegalMoves(turnoJugador, game)
      val otherMoves = MoveValidator.getLegalMoves(otherJugador, game)

      assertTrue(
        turnoMoves.sopa,
        !otherMoves.sopa
      )
    },
    test("toJsonOptions generates valid JSON") {
      val game = TestGameHelper.createTestGame(gameStatus = GameStatus.cantando)
      val jugador = game.jugadores.find(_.turno).get
      val legalMoves = MoveValidator.getLegalMoves(jugador, game)

      val jsonString = MoveValidator.toJsonOptions(legalMoves)

      // Should be valid JSON array
      assertTrue(jsonString.startsWith("[") && jsonString.endsWith("]"))
      // Should contain Canta options
      assertTrue(jsonString.contains("\"moveType\"") && jsonString.contains("\"canta\""))
    }
  )

}
