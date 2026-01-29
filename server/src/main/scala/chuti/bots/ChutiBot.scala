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
import dao.ZIORepository
import game.{GameEnvironment, GameService}
import zio.{Duration, IO, Random, ZIO}

trait ChutiBot {

  def decideTurn(
    user: User,
    game: Game
  ): IO[GameError, PlayEvent]

  def takeTurn(
    gameId:          GameId,
    delayMultiplier: Int = 1
  ): ZIO[GameEnvironment & ChutiSession & GameService, GameError, Game] = {
    for {
      userOpt <- ZIO.serviceWith[ChutiSession](_.user)
      user    <- ZIO.fromOption(userOpt).orElseFail(GameError("Usuario no autenticado"))
      game    <- ZIO.serviceWithZIO[ZIORepository](_.gameOperations.get(gameId).map(_.get))
      turn    <- decideTurn(user, game)

      delayInSeconds = turn match {
        case chuti.NoOpPlay(_, _, _, _, _, _) | chuti.HoyoTecnico(_, _, _, _, _, _, _) |
            chuti.TerminaJuego(_, _, _, _, _, _, _) | chuti.Sopa(_, _, _, _, _, _, _) =>
          0
        case chuti.Da(_, _, _, _, _, _, _, _, _) | chuti.Pide(_, _, _, _, _, _, _, _, _, _) |
            chuti.Canta(_, _, _, _, _, _, _) | chuti.Caete(_, _, _, _, _, _, _, _, _, _) |
            chuti.MeRindo(_, _, _, _, _, _) =>
          10
      }

      jitter <- Random.nextIntBetween(2, 101)
      _      <- ZIO.sleep(Duration.fromMillis((1000 * delayInSeconds * delayMultiplier * jitter) / 100))
      played <- ZIO.serviceWithZIO[GameService](_.play(gameId, turn))
    } yield played
  }

}
