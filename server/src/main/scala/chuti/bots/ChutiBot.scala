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

import api.ChutiSession
import chuti.*
import dao.Repository
import game.GameService
import zio.{IO, ZIO}

trait ChutiBot {

  def decideTurn(
    user: User,
    game: Game
  ): IO[GameError, PlayEvent]

  def takeTurn(gameId: GameId): ZIO[ChutiSession & GameService & Repository, GameError, Game] = {
    for {
      gameOperations <- ZIO.service[Repository].map(_.gameOperations)
      gameService    <- ZIO.service[GameService]
      user           <- ZIO.serviceWith[ChutiSession](_.user)
      game           <- gameOperations.get(gameId).map(_.get)
      turn           <- decideTurn(user, game)
      played         <- gameService.play(gameId, turn)
    } yield played
  }

}
