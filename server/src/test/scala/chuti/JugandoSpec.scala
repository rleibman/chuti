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

import api.{ChutiEnvironment, ChutiSession, EnvironmentBuilder}
import api.token.TokenHolder
import chat.ChatService
import dao.InMemoryRepository.*
import dao.ZIORepository
import game.GameService
import mail.Postman
import org.scalatest.flatspec.AnyFlatSpec
import zio.*
import zio.test.{ZIOSpec, ZIOSpecDefault, assertTrue}

object JugandoSpec extends ZIOSpec[GameService & ChatService] with GameAbstractSpec {

  val spec = suite("Jugando")(
    test("primera mano") {
      val gameId = GameId(1)
      (for {
        gameService <- ZIO.service[GameService]
        gameStream =
          gameService
            .gameStream(gameId, connectionId)
            .provideSomeLayer[ChutiEnvironment & GameService & ChatService](ChutiSession(user1).toLayer)
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
            .broadcastGameEvent(PoisonPill(Option(gameId))).provideSomeLayer[
              ChutiEnvironment & GameService & ChatService
            ](
              ChutiSession(chuti.god).toLayer
            )
        gameEvents <- gameEventsFiber.join
      } yield {
        val ganador = mano1.jugadores.maxBy(_.filas.size)
        println(s"Gano ${ganador.user.name} con ${ganador.filas.last}!")
        assertTrue(
          mano1.id == Option(gameId),
          mano1.jugadores.count(_.fichas.size == 6) == 4, // Todos dieron una ficha.
          gameEvents.filterNot(_.isInstanceOf[BorloteEvent]).size == 5
        ) // Including the poison pill
      }).provideSomeLayer[GameService & ChatService](EnvironmentBuilder.testLayer(GAME_CANTO4))
    },
    test("jugando 4 manos") {
      val gameId = GameId(1)

      (for {
        gameService <- ZIO.service[GameService]
        gameStream =
          gameService
            .gameStream(gameId, connectionId)
            .provideSomeLayer[ChutiEnvironment & GameService & ChatService](ChutiSession(user1).toLayer)
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
            .broadcastGameEvent(PoisonPill(Option(gameId))).provideSomeLayer[
              ChutiEnvironment & GameService & ChatService
            ](
              ChutiSession(chuti.god).toLayer
            )
        gameEvents <- gameEventsFiber.join
      } yield {
        val numFilas = mano4.jugadores.map(_.filas.size).sum
        if (mano4.quienCanta.get.yaSeHizo)
          println(s"${mano4.quienCanta.get.user.name} se hizo con ${mano4.quienCanta.get.filas.size}!")
        else
          println(s"Fue hoyo para ${mano4.quienCanta.get.user.name}!")
        assertTrue(mano4.id == Option(gameId), mano4.quienCanta.get.fichas.size + numFilas == 7, gameEvents.nonEmpty)

      }).provideSomeLayer[GameService & ChatService](EnvironmentBuilder.testLayer(GAME_CANTO4))
    },
    test("jugando hasta que se haga o sea hoyo") {
      val gameId = GameId(1)
      (for {
        gameService <- ZIO.service[GameService]
        gameStream =
          gameService
            .gameStream(gameId, connectionId)
            .provideSomeLayer[ChutiEnvironment & GameService & ChatService](ChutiSession(user1).toLayer)
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
            .broadcastGameEvent(PoisonPill(Option(gameId))).provideSomeLayer[
              ChutiEnvironment & GameService & ChatService
            ](
              ChutiSession(chuti.god).toLayer
            )
        gameEvents <- gameEventsFiber.join
      } yield {
        val ganador = end.jugadores.maxBy(_.filas.size)
        println(s"Gano ${ganador.user.name} con ${ganador.filas.size}!")
        assertTrue(end.id == Option(gameId), end.gameStatus == GameStatus.requiereSopa, gameEvents.nonEmpty)
      }).provideSomeLayer[GameService & ChatService](EnvironmentBuilder.testLayer(GAME_CANTO4))
    }
  )

  override def bootstrap: ZLayer[Any, Any, GameService & ChatService] =
    ZLayer.make[GameService & ChatService](
      GameService.make(),
      ChatService.make()
    )

}
