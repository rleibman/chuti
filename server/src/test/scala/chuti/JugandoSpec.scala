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

import api.ChutiSession
import dao.SessionProvider
import game.GameService
import org.mockito.scalatest.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec
import zio._
import zio.duration._

class JugandoSpec extends AnyFlatSpec with MockitoSugar with GameAbstractSpec2 {
  import dao.InMemoryRepository._

  "primera mano" should "work" in {
    val gameId = GameId(1)

    val (
      game:       Game,
      gameEvents: Chunk[GameEvent]
    ) =
      testRuntime.unsafeRun {
        (for {
          gameService <- ZIO.service[GameService.Service]
          gameStream =
            gameService
              .gameStream(gameId, connectionId)
              .provideSomeLayer[TestLayer](SessionProvider.layer(ChutiSession(user1)))
          gameEventsFiber <-
            gameStream
              .takeUntil {
                case PoisonPill(Some(id), _) if id == gameId => true
                case _                                       => false
              }.runCollect.fork
          _     <- clock.sleep(1.second)
          mano1 <- juegaMano(gameId)
          _ <-
            gameService
              .broadcastGameEvent(PoisonPill(Option(gameId))).provideSomeLayer[TestLayer](
                SessionProvider.layer(ChutiSession(chuti.god))
              )
          gameEvents <- gameEventsFiber.join
        } yield (mano1, gameEvents)).provideCustomLayer(testLayer(GAME_CANTO4))
      }

    assert(game.id === Option(gameId))
    assert(game.jugadores.count(_.fichas.size == 6) === 4) //Todos dieron una ficha.
    val ganador = game.jugadores.maxBy(_.filas.size)
    println(s"Gano ${ganador.user.name} con ${ganador.filas.last}!")
    assert(
      gameEvents.filterNot(_.isInstanceOf[BorloteEvent]).size === 5
    ) //Including the poison pill
  }
  "jugando 4 manos" should "work" in {
    val gameId = GameId(1)

    val (
      game:       Game,
      gameEvents: Chunk[GameEvent]
    ) =
      testRuntime.unsafeRun {
        (for {
          gameService <- ZIO.service[GameService.Service]
          gameStream =
            gameService
              .gameStream(gameId, connectionId)
              .provideSomeLayer[TestLayer](SessionProvider.layer(ChutiSession(user1)))
          gameEventsFiber <-
            gameStream
              .takeUntil {
                case PoisonPill(Some(id), _) if id == gameId => true
                case _                                       => false
              }.runCollect.fork
          _     <- clock.sleep(1.second)
          _     <- juegaMano(gameId)
          _     <- juegaMano(gameId)
          _     <- juegaMano(gameId)
          mano4 <- juegaMano(gameId)
          _ <-
            gameService
              .broadcastGameEvent(PoisonPill(Option(gameId))).provideSomeLayer[TestLayer](
                SessionProvider.layer(ChutiSession(chuti.god))
              )
          gameEvents <- gameEventsFiber.join
        } yield (mano4, gameEvents)).provideCustomLayer(testLayer(GAME_CANTO4))
      }

    assert(game.id === Option(gameId))
    val numFilas = game.jugadores.map(_.filas.size).sum
    assert(game.quienCanta.get.fichas.size + numFilas === 7)
    if (game.quienCanta.get.yaSeHizo)
      println(s"${game.quienCanta.get.user.name} se hizo con ${game.quienCanta.get.filas.size}!")
    else
      println(s"Fue hoyo para ${game.quienCanta.get.user.name}!")
    assert(gameEvents.nonEmpty)
  }
  "jugando hasta que se haga o sea hoyo" should "work" in {
    val gameId = GameId(1)
    val (
      game:       Game,
      gameEvents: Chunk[GameEvent]
    ) =
      testRuntime.unsafeRun {
        (for {
          gameService <- ZIO.service[GameService.Service]
          gameStream =
            gameService
              .gameStream(gameId, connectionId)
              .provideSomeLayer[TestLayer](SessionProvider.layer(ChutiSession(user1)))
          gameEventsFiber <-
            gameStream
              .takeWhile {
                case PoisonPill(Some(id), _) if id == gameId => false
                case _                                       => true
              }.runCollect.fork
          _   <- clock.sleep(1.second)
          end <- juegaHastaElFinal(gameId)
          _ <-
            gameService
              .broadcastGameEvent(PoisonPill(Option(gameId))).provideSomeLayer[TestLayer](
                SessionProvider.layer(ChutiSession(chuti.god))
              )
          gameEvents <- gameEventsFiber.join
        } yield (end._2, gameEvents)).provideCustomLayer(testLayer(GAME_CANTO4))
      }

    assert(game.id === Option(gameId))
    assert(game.gameStatus === GameStatus.requiereSopa)
    val ganador = game.jugadores.maxBy(_.filas.size)
    println(s"Gano ${ganador.user.name} con ${ganador.filas.size}!")
    assert(gameEvents.nonEmpty)
  }
}
