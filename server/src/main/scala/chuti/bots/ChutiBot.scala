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

import chuti.{Game, GameId, PlayEvent, User}
import dao.{Repository, SessionContext}
import game.GameService.{GameLayer, GameService}
import zio.ZIO

trait ChutiBot {

  def decideTurn(
    user: User,
    game: Game
  ): Option[PlayEvent]

  def takeTurn(gameId: GameId): ZIO[GameLayer & GameService, Exception, Game] = {
    for {
      gameOperations <- ZIO.access[Repository](_.get.gameOperations)
      gameService    <- ZIO.access[GameService](_.get)
      user           <- ZIO.access[SessionContext](_.get.session.user)
      game           <- gameOperations.get(gameId).map(_.get)
      played         <- ZIO.foreach(decideTurn(user, game))(gameService.play(gameId, _))
    } yield played.getOrElse(game)
  }

}
