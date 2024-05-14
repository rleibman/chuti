/*
 * Copyright (c) 2024 Roberto Leibman
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package chuti.bots

import chuti.*
import dao.{Repository, SessionContext}
import game.GameService
import game.GameService.GameLayer
import zio.*

trait ChutiBot {

  def decideTurn(
    user: User,
    game: Game
  ): IO[GameError, PlayEvent]

  def takeTurn(gameId: GameId): ZIO[GameLayer & GameService, Exception, Game] = {
    for {
      gameOperations <- ZIO.service[Repository].map(_.gameOperations)
      gameService    <- ZIO.service[GameService]
      user           <- ZIO.service[SessionContext].map(_.session.user)
      game           <- gameOperations.get(gameId).map(_.get)
      toPlay         <- decideTurn(user, game)
      played <-
        toPlay match {
          case _: NoOpPlay => ZIO.succeed(game)
          case _ => gameService.play(gameId, toPlay)
        }
    } yield played
  }

}
