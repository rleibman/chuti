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

import api.{ChutiEnvironment, ChutiSession, EnvironmentBuilder, toLayer}
import api.token.TokenHolder
import chuti.CuantasCantas.Buenas
import dao.{InMemoryRepository, ZIORepository}
import dao.InMemoryRepository.*
import game.GameService
import mail.Postman
import org.scalatest.Assertion
import org.scalatest.Assertions.*
import org.scalatest.flatspec.AnyFlatSpec
import zio.test.*
import zio.test.TestClock
import zio.{Clock, Console, *}

object CantandoSpec extends ZIOSpec[ChutiEnvironment] with GameAbstractSpec {

  override def bootstrap: ULayer[ChutiEnvironment] = EnvironmentBuilder.testLayer(GAME_STARTED)

  val spec = suite("Cantando")(
    test("printing the game")(
      for {
        game <- readGame(GAME_STARTED)
        _    <- Console.printLine(game.toString)
      } yield assertCompletes
    ),
    test("Cantando casa sin salve should get it done")(
      for {
        gameService <- ZIO.service[GameService].provideLayer(GameService.makeWithoutAIBot())
        gameStream = gameService
          .gameStream(GameId(1), connectionId).provideSomeLayer[ZIORepository & Postman & TokenHolder](
            ChutiSession(user1).toLayer
          )
        userStream = gameService
          .userStream(connectionId).provideSomeLayer[ZIORepository & Postman & TokenHolder](ChutiSession(user1).toLayer)
        gameEventsFiber <- gameStream.interruptAfter(3.second).runCollect.fork
        userEventsFiber <- userStream.interruptAfter(3.second).runCollect.fork
        _               <- ZIO.yieldNow *> TestClock.adjust(1.second)
        game1           <- readGame(GAME_STARTED)
        quienCanta = game1.jugadores.find(_.turno).map(_.user).get
        game2 <-
          gameService
            .play(GameId(1), Canta(CuantasCantas.Casa))
            .provideSomeLayer[ChutiEnvironment](ChutiSession(quienCanta).toLayer)
        jugador2 = game2.nextPlayer(quienCanta).user
        game3 <-
          gameService
            .play(GameId(1), Canta(CuantasCantas.Buenas))
            .provideSomeLayer[ChutiEnvironment](ChutiSession(jugador2).toLayer)
        jugador3 = game3.nextPlayer(jugador2).user
        game4 <-
          gameService
            .play(GameId(1), Canta(CuantasCantas.Buenas))
            .provideSomeLayer[ChutiEnvironment](ChutiSession(jugador3).toLayer)
        jugador4 = game4.nextPlayer(jugador3).user
        game5 <-
          gameService
            .play(GameId(1), Canta(CuantasCantas.Buenas))
            .provideSomeLayer[ChutiEnvironment](ChutiSession(jugador4).toLayer)
        _          <- writeGame(game5, GAME_CANTO4)
        _          <- TestClock.adjust(3.second) // Trigger stream interruption
        gameEvents <- gameEventsFiber.join
        userEvents <- userEventsFiber.join
      } yield {
        val cantante = game5.jugadores.find(_.cantante).get
        assertTrue(
          game5.id == GameId(1),
          game5.jugadores.length == 4,
          game5.gameStatus == GameStatus.jugando,
          game5.currentEventIndex == 11,
          cantante.mano,
          cantante.fichas.contains(Ficha(Numero(6), Numero(6))),
          gameEvents.size == 4,
          userEvents.size == 0
        ) &&
        assertSoloUnoCanta(game5) // Though 2 happen (log in and log out, only log in should be registering)
      }
    ),
    test("Cantando cinco sin salve should get it done")(
      for {
        gameService    <- ZIO.service[GameService].provideLayer(GameService.makeWithoutAIBot())
        gameOperations <- ZIO.serviceWith[ZIORepository](_.gameOperations)
        game1          <- readGame(GAME_STARTED)
        freshGame <- gameOperations.upsert(game1.copy(id = GameId.empty)).provideSomeLayer[ChutiEnvironment](godLayer)
        gameId = freshGame.id
        gameStream = gameService
          .gameStream(gameId, connectionId).provideSomeLayer[ZIORepository & Postman & TokenHolder](
            ChutiSession(user1).toLayer
          )
        userStream = gameService
          .userStream(connectionId).provideSomeLayer[ZIORepository & Postman & TokenHolder](ChutiSession(user1).toLayer)
        gameEventsFiber <- gameStream.interruptAfter(3.second).runCollect.fork
        userEventsFiber <- userStream.interruptAfter(3.second).runCollect.fork
        _               <- ZIO.yieldNow *> TestClock.adjust(1.second)
        quienCanta = freshGame.jugadores.find(_.turno).map(_.user).get
        game2 <-
          gameService
            .play(gameId, Canta(CuantasCantas.Canto5))
            .provideSomeLayer[ChutiEnvironment](ChutiSession(quienCanta).toLayer)
        jugador2 = game2.nextPlayer(quienCanta).user
        game3 <-
          gameService
            .play(gameId, Canta(CuantasCantas.Buenas))
            .provideSomeLayer[ChutiEnvironment](ChutiSession(jugador2).toLayer)
        jugador3 = game3.nextPlayer(jugador2).user
        game4 <-
          gameService
            .play(gameId, Canta(CuantasCantas.Buenas))
            .provideSomeLayer[ChutiEnvironment](ChutiSession(jugador3).toLayer)
        jugador4 = game4.nextPlayer(jugador3).user
        game5 <-
          gameService
            .play(gameId, Canta(CuantasCantas.Buenas))
            .provideSomeLayer[ChutiEnvironment](ChutiSession(jugador4).toLayer)
        _          <- TestClock.adjust(3.second) // Trigger stream interruption
        gameEvents <- gameEventsFiber.join
        userEvents <- userEventsFiber.join
      } yield {
        val cantante = game5.jugadores.find(_.cantante).get
        assertTrue(
          game5.jugadores.length == 4,
          game5.gameStatus == GameStatus.jugando,
          cantante.mano,
          cantante.fichas.contains(Ficha(Numero(6), Numero(6))),
          gameEvents.size == 4,
          userEvents.size == 0
        ) &&
        assertSoloUnoCanta(game5)
      }
    ),
    test("Cantando todas should get it done")(
      for {
        gameService    <- ZIO.service[GameService].provideLayer(GameService.makeWithoutAIBot())
        gameOperations <- ZIO.serviceWith[ZIORepository](_.gameOperations)
        game1          <- readGame(GAME_STARTED)
        freshGame <- gameOperations.upsert(game1.copy(id = GameId.empty)).provideSomeLayer[ChutiEnvironment](godLayer)
        gameId = freshGame.id
        gameStream = gameService
          .gameStream(gameId, connectionId).provideSomeLayer[ZIORepository & Postman & TokenHolder](
            ChutiSession(user1).toLayer
          )
        userStream = gameService
          .userStream(connectionId).provideSomeLayer[ZIORepository & Postman & TokenHolder](ChutiSession(user1).toLayer)
        gameEventsFiber <- gameStream.interruptAfter(3.second).runCollect.fork
        userEventsFiber <- userStream.interruptAfter(3.second).runCollect.fork
        _               <- ZIO.yieldNow *> TestClock.adjust(1.second)
        quienCanta = freshGame.jugadores.find(_.turno).map(_.user).get
        game2 <-
          gameService
            .play(gameId, Canta(CuantasCantas.CantoTodas))
            .provideSomeLayer[ChutiEnvironment](ChutiSession(quienCanta).toLayer)
        // Hay que pararle aqui, ya canto todas.
        _          <- TestClock.adjust(3.second) // Trigger stream interruption
        gameEvents <- gameEventsFiber.join
        userEvents <- userEventsFiber.join
      } yield {
        val cantante = game2.jugadores.find(_.cantante).get
        assertTrue(
          game2.jugadores.length == 4,
          game2.gameStatus == GameStatus.jugando,
          cantante.mano,
          cantante.fichas.contains(Ficha(Numero(6), Numero(6))),
          gameEvents.size == 1,
          userEvents.size == 0
        ) &&
        assertSoloUnoCanta(game2)
      }
    ),
    test("Cantando casa con salve should get it done")(
      for {
        gameService    <- ZIO.service[GameService].provideLayer(GameService.makeWithoutAIBot())
        gameOperations <- ZIO.serviceWith[ZIORepository](_.gameOperations)
        game1          <- readGame(GAME_STARTED)
        freshGame <- gameOperations.upsert(game1.copy(id = GameId.empty)).provideSomeLayer[ChutiEnvironment](godLayer)
        gameId = freshGame.id
        gameStream = gameService
          .gameStream(gameId, connectionId).provideSomeLayer[ZIORepository & Postman & TokenHolder](
            ChutiSession(user1).toLayer
          )
        userStream = gameService
          .userStream(connectionId).provideSomeLayer[ZIORepository & Postman & TokenHolder](ChutiSession(user1).toLayer)
        gameEventsFiber <- gameStream.interruptAfter(3.second).runCollect.fork
        userEventsFiber <- userStream.interruptAfter(3.second).runCollect.fork
        _               <- ZIO.yieldNow *> TestClock.adjust(1.second)
        quienCanta = freshGame.jugadores.find(_.turno).map(_.user).get
        game2 <-
          gameService
            .play(gameId, Canta(CuantasCantas.Casa))
            .provideSomeLayer[ChutiEnvironment](ChutiSession(quienCanta).toLayer)
        jugador2 = game2.nextPlayer(quienCanta).user
        game3 <-
          gameService
            .play(gameId, Canta(CuantasCantas.Canto5))
            .provideSomeLayer[ChutiEnvironment](ChutiSession(jugador2).toLayer)
        jugador3 = game3.nextPlayer(jugador2).user
        game4 <-
          gameService
            .play(gameId, Canta(CuantasCantas.Buenas))
            .provideSomeLayer[ChutiEnvironment](ChutiSession(jugador3).toLayer)
        jugador4 = game4.nextPlayer(jugador3).user
        game5 <-
          gameService
            .play(gameId, Canta(CuantasCantas.Buenas))
            .provideSomeLayer[ChutiEnvironment](ChutiSession(jugador4).toLayer)
        _          <- TestClock.adjust(3.second) // Trigger stream interruption
        gameEvents <- gameEventsFiber.join
        userEvents <- userEventsFiber.join
      } yield {
        val cantante = game5.jugadores.find(_.cantante).get
        val turno = game5.jugadores.find(_.turno).get
        val salvador = game5.nextPlayer(turno)
        assertTrue(
          game5.jugadores.length == 4,
          game5.gameStatus == GameStatus.jugando,
          cantante == salvador,
          cantante.mano,
          !cantante.fichas.contains(Ficha(Numero(6), Numero(6))),
          salvador.cuantasCantas == Option(CuantasCantas.Canto5),
          gameEvents.size == 4,
          userEvents.size == 0
        ) &&
        assertSoloUnoCanta(game5)
      }
    ),
    test("Cantando casa con salve de chuti should get it done")(
      for {
        gameService    <- ZIO.service[GameService].provideLayer(GameService.makeWithoutAIBot())
        gameOperations <- ZIO.serviceWith[ZIORepository](_.gameOperations)
        game1          <- readGame(GAME_STARTED)
        freshGame <- gameOperations.upsert(game1.copy(id = GameId.empty)).provideSomeLayer[ChutiEnvironment](godLayer)
        gameId = freshGame.id
        gameStream = gameService
          .gameStream(gameId, connectionId).provideSomeLayer[ZIORepository & Postman & TokenHolder](
            ChutiSession(user1).toLayer
          )
        userStream = gameService
          .userStream(connectionId).provideSomeLayer[ZIORepository & Postman & TokenHolder](ChutiSession(user1).toLayer)
        gameEventsFiber <- gameStream.interruptAfter(3.second).runCollect.fork
        userEventsFiber <- userStream.interruptAfter(3.second).runCollect.fork
        _               <- ZIO.yieldNow *> TestClock.adjust(1.second)
        quienCanta = freshGame.jugadores.find(_.turno).map(_.user).get
        game2 <-
          gameService
            .play(gameId, Canta(CuantasCantas.Casa))
            .provideSomeLayer[ChutiEnvironment](ChutiSession(quienCanta).toLayer)
        jugador2 = game2.nextPlayer(quienCanta).user
        game3 <-
          gameService
            .play(gameId, Canta(CuantasCantas.CantoTodas))
            .provideSomeLayer[ChutiEnvironment](ChutiSession(jugador2).toLayer)
        _          <- TestClock.adjust(3.second) // Trigger stream interruption
        gameEvents <- gameEventsFiber.join
        userEvents <- userEventsFiber.join
      } yield {
        val cantante = game3.jugadores.find(_.cantante).get
        val turno = game3.jugadores.find(_.turno).get
        val salvador = game3.nextPlayer(turno)
        assertTrue(
          game3.jugadores.length == 4,
          game3.gameStatus == GameStatus.jugando,
          cantante == salvador,
          cantante.mano,
          !cantante.fichas.contains(Ficha(Numero(6), Numero(6))),
          salvador.cuantasCantas == Option(CuantasCantas.CantoTodas),
          gameEvents.size == 2,
          userEvents.size == 0
        ) &&
        assertSoloUnoCanta(game3)
      }
    ),
    test("Cantando casa con salve de 5,6,7 should get it done")(
      for {
        gameService    <- ZIO.service[GameService].provideLayer(GameService.makeWithoutAIBot())
        gameOperations <- ZIO.serviceWith[ZIORepository](_.gameOperations)
        game1          <- readGame(GAME_STARTED)
        freshGame <- gameOperations.upsert(game1.copy(id = GameId.empty)).provideSomeLayer[ChutiEnvironment](godLayer)
        gameId = freshGame.id
        gameStream = gameService
          .gameStream(gameId, connectionId).provideSomeLayer[ZIORepository & Postman & TokenHolder](
            ChutiSession(user1).toLayer
          )
        userStream = gameService
          .userStream(connectionId).provideSomeLayer[ZIORepository & Postman & TokenHolder](ChutiSession(user1).toLayer)
        gameEventsFiber <- gameStream.interruptAfter(3.second).runCollect.fork
        userEventsFiber <- userStream.interruptAfter(3.second).runCollect.fork
        _               <- ZIO.yieldNow *> TestClock.adjust(1.second)
        quienCanta = freshGame.jugadores.find(_.turno).map(_.user).get
        game2 <-
          gameService
            .play(gameId, Canta(CuantasCantas.Casa))
            .provideSomeLayer[ChutiEnvironment](ChutiSession(quienCanta).toLayer)
        jugador2 = game2.nextPlayer(quienCanta).user
        game3 <-
          gameService
            .play(gameId, Canta(CuantasCantas.Canto5))
            .provideSomeLayer[ChutiEnvironment](ChutiSession(jugador2).toLayer)
        jugador3 = game3.nextPlayer(jugador2).user
        game4 <-
          gameService
            .play(gameId, Canta(CuantasCantas.Canto6))
            .provideSomeLayer[ChutiEnvironment](ChutiSession(jugador3).toLayer)
        jugador4 = game4.nextPlayer(jugador3).user
        game5 <-
          gameService
            .play(gameId, Canta(CuantasCantas.CantoTodas))
            .provideSomeLayer[ChutiEnvironment](ChutiSession(jugador4).toLayer)
        _          <- TestClock.adjust(3.second) // Trigger stream interruption
        gameEvents <- gameEventsFiber.join
        userEvents <- userEventsFiber.join
      } yield {
        val cantante = game5.jugadores.find(_.cantante).get
        val turno = game5.jugadores.find(_.turno).get
        val salvador = game5.prevPlayer(turno) // Nota esto, es el que da la vuelta
        assertTrue(
          game5.jugadores.length == 4,
          game5.gameStatus == GameStatus.jugando,
          cantante == salvador,
          cantante.mano,
          !cantante.fichas.contains(Ficha(Numero(6), Numero(6))),
          salvador.cuantasCantas == Option(CuantasCantas.CantoTodas),
          gameEvents.size == 4,
          userEvents.size == 0
        ) &&
        assertSoloUnoCanta(game5)
      }
    )
  )

}
