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
import db.InMemoryRepository.*

import java.time.Instant

object TestGameHelper {

  /** Creates a game with 4 players for testing, with fichas distributed */
  def createTestGame(
    gameId:     GameId = GameId.empty,
    gameStatus: GameStatus = GameStatus.comienzo
  ): Game = {
    // Distribute all 28 tiles among 4 players (7 each)
    val allFichas = Game.todaLaFicha
    val shuffled = scala.util.Random.shuffle(allFichas)

    val jugador1 = Jugador(
      user = user1,
      jugadorType = JugadorType.human,
      invited = true,
      turno = true, // First player has turno
      mano = true,
      cantante = true,
      fichas = shuffled.slice(0, 7)
    )
    val jugador2 = Jugador(
      user = user2,
      jugadorType = JugadorType.human,
      invited = true,
      fichas = shuffled.slice(7, 14)
    )
    val jugador3 = Jugador(
      user = user3,
      jugadorType = JugadorType.human,
      invited = true,
      fichas = shuffled.slice(14, 21)
    )
    val jugador4 = Jugador(
      user = user4,
      jugadorType = JugadorType.human,
      invited = true,
      fichas = shuffled.slice(21, 28)
    )

    Game(
      id = gameId,
      created = Instant.now.nn,
      gameStatus = gameStatus,
      jugadores = List(jugador1, jugador2, jugador3, jugador4)
    )
  }

}
