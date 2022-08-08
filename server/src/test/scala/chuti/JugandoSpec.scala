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
import api.token.TokenHolder
import chat.ChatService
import dao.{Repository, SessionContext}
import game.GameService
import mail.Postman
import org.scalatest.flatspec.AnyFlatSpec
import zio.*
import zio.test.{ZIOSpecDefault, assertTrue}
import zio.test.TestAspect

object JugandoSpec extends ZIOSpecDefault with GameAbstractSpec {

  import dao.InMemoryRepository.*

  val spec = suite("Jugando")(
    test("primera mano") {
      val gameId = GameId(1)
      (for {
        gameService <- ZIO.service[GameService]
        gameStream =
          gameService
            .gameStream(gameId, connectionId)
            .provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](SessionContext.live(ChutiSession(user1)))
        gameEventsFiber <-
          gameStream
            .takeUntil {
              case PoisonPill(Some(id), _) if id == gameId => true
              case _                                       => false
            }.runCollect.fork
        _     <- Clock.sleep(1.second)
        mano1 <- juegaMano(gameId)
        _ <-
          gameService
            .broadcastGameEvent(PoisonPill(Option(gameId))).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](
              SessionContext.live(ChutiSession(chuti.god))
            )
        gameEvents <- gameEventsFiber.join
      } yield {
        val ganador = mano1.jugadores.maxBy(_.filas.size)
        println(s"Gano ${ganador.user.name} con ${ganador.filas.last}!")
        assertTrue(mano1.id == Option(gameId)) &&
        assertTrue(mano1.jugadores.count(_.fichas.size == 6) == 4) && // Todos dieron una ficha.
        assertTrue(
          gameEvents.filterNot(_.isInstanceOf[BorloteEvent]).size == 5
        ) // Including the poison pill
      }).provideLayer(testLayer(GAME_CANTO4))
    },
    test("jugando 4 manos") {
      val gameId = GameId(1)

      (for {
        gameService <- ZIO.service[GameService]
        gameStream =
          gameService
            .gameStream(gameId, connectionId)
            .provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](SessionContext.live(ChutiSession(user1)))
        gameEventsFiber <-
          gameStream
            .takeUntil {
              case PoisonPill(Some(id), _) if id == gameId => true
              case _                                       => false
            }.runCollect.fork
        _     <- Clock.sleep(1.second)
        _     <- juegaMano(gameId)
        _     <- juegaMano(gameId)
        _     <- juegaMano(gameId)
        mano4 <- juegaMano(gameId)
        _ <-
          gameService
            .broadcastGameEvent(PoisonPill(Option(gameId))).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](
              SessionContext.live(ChutiSession(chuti.god))
            )
        gameEvents <- gameEventsFiber.join
      } yield {
        val numFilas = mano4.jugadores.map(_.filas.size).sum
        if (mano4.quienCanta.get.yaSeHizo)
          println(s"${mano4.quienCanta.get.user.name} se hizo con ${mano4.quienCanta.get.filas.size}!")
        else
          println(s"Fue hoyo para ${mano4.quienCanta.get.user.name}!")
        assertTrue(mano4.id == Option(gameId)) &&
        assertTrue(mano4.quienCanta.get.fichas.size + numFilas == 7) &&
        assertTrue(gameEvents.nonEmpty)

      }).provideLayer(testLayer(GAME_CANTO4))
    },
    test("jugando hasta que se haga o sea hoyo") {
      val gameId = GameId(1)
      (for {
        gameService <- ZIO.service[GameService]
        gameStream =
          gameService
            .gameStream(gameId, connectionId)
            .provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](SessionContext.live(ChutiSession(user1)))
        gameEventsFiber <-
          gameStream
            .takeWhile {
              case PoisonPill(Some(id), _) if id == gameId => false
              case _                                       => true
            }.runCollect.fork
        _   <- Clock.sleep(1.second)
        end <- juegaHastaElFinal(gameId)
        _ <-
          gameService
            .broadcastGameEvent(PoisonPill(Option(gameId))).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](
              SessionContext.live(ChutiSession(chuti.god))
            )
        gameEvents <- gameEventsFiber.join
      } yield {
        val ganador = end.jugadores.maxBy(_.filas.size)
        println(s"Gano ${ganador.user.name} con ${ganador.filas.size}!")
        assertTrue(end.gameStatus == GameStatus.requiereSopa) &&
        assertTrue(gameEvents.nonEmpty)
      }).provideLayer(testLayer(GAME_CANTO4))
    }
  ) @@ TestAspect.withLiveClock

}
