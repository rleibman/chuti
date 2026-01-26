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
import zio.{Clock, Console, *}

object CantandoSpec extends ZIOSpec[ChutiEnvironment] with GameAbstractSpec {

  override def bootstrap: ULayer[ChutiEnvironment] = EnvironmentBuilder.withContainer.orDie

  val spec = suite("Cantando")(
    test("printing the game")(
      for {
        game <- readGame(GAME_STARTED)
        _    <- Console.printLine(game.toString)
      } yield assertCompletes
    ),
    test("Cantando casa sin salve should get it done")(
      for {
        gameService <- ZIO.service[GameService].provide(GameService.make())
        gameStream = gameService
          .gameStream(GameId(1), connectionId).provideSomeLayer[ZIORepository & Postman & TokenHolder](
            ChutiSession(user1).toLayer
          )
        userStream = gameService
          .userStream(connectionId).provideSomeLayer[ZIORepository & Postman & TokenHolder](ChutiSession(user1).toLayer)
        gameEventsFiber <- gameStream.interruptAfter(3.second).runCollect.fork
        userEventsFiber <- userStream.interruptAfter(3.second).runCollect.fork
        _               <- Clock.sleep(1.second)
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
        gameEvents <- gameEventsFiber.join
        userEvents <- userEventsFiber.join
      } yield {
        val cantante = game5.jugadores.find(_.cantante).get
        assertTrue(
          game5.id == Option(GameId(1)),
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
    )
  )

}

//class CantandoSpec2 extends AnyFlatSpec with GameAbstractSpec {
//
//  "Cantando cinco sin salve" should "get it done" in {
//    val repo = new InMemoryRepository(Seq.empty)
//    val gameOperations = repo.gameOperations
//    val userOperations = repo.userOperations
//
//    val layer = fullLayer(gameOperations, userOperations)
//    val (
//      game,
//      gameEvents,
//      userEvents
//    ) =
//      Unsafe.unsafe { u ?=>
//        testRuntime.unsafe
//          .run {
//            for {
//              gameService <- ZIO.service[GameService].provide(GameService.make())
//              gameStream =
//                gameService
//                  .gameStream(GameId(1), connectionId).provideSomeLayer[ChutiEnvironment](
//                    layer ++ ChutiSession(user1).toLayer
//                  )
//              userStream =
//                gameService
//                  .userStream(connectionId).provideSomeLayer[ChutiEnvironment](
//                    layer ++ ChutiSession(user1).toLayer
//                  )
//              gameEventsFiber <- gameStream.interruptAfter(3.second).runCollect.fork
//              userEventsFiber <- userStream.interruptAfter(3.second).runCollect.fork
//              _               <- Clock.sleep(1.second)
//              game1           <- readGame(GAME_STARTED)
//              quienCanta = game1.jugadores.find(_.turno).map(_.user).get
//              game2 <-
//                gameService
//                  .play(GameId(1), Canta(CuantasCantas.Canto5)).provideSomeLayer[ChutiEnvironment](
//                    layer ++ SessionContext.live(ChutiSession(quienCanta))
//                  )
//              jugador2 = game2.nextPlayer(quienCanta).user
//              game3 <-
//                gameService
//                  .play(GameId(1), Canta(CuantasCantas.Buenas)).provideSomeLayer[ChutiEnvironment](
//                    layer ++ SessionContext.live(ChutiSession(jugador2))
//                  )
//              jugador3 = game3.nextPlayer(jugador2).user
//              game4 <-
//                gameService
//                  .play(GameId(1), Canta(CuantasCantas.Buenas)).provideSomeLayer[ChutiEnvironment](
//                    layer ++ SessionContext.live(ChutiSession(jugador3))
//                  )
//              jugador4 = game4.nextPlayer(jugador3).user
//              game5 <-
//                gameService
//                  .play(GameId(1), Canta(CuantasCantas.Buenas)).provideSomeLayer[ChutiEnvironment](
//                    layer ++ SessionContext.live(ChutiSession(jugador4))
//                  )
//              gameEvents <- gameEventsFiber.join
//              userEvents <- userEventsFiber.join
//            } yield (game5, gameEvents, userEvents)
//          }.getOrThrow()
//      }
//
//    assert(game.id == Option(GameId(1)))
//    assert(game.jugadores.length == 4)
//    assert(game.gameStatus == GameStatus.jugando)
//    assert(game.currentEventIndex == 11)
//    val cantante = game.jugadores.find(_.cantante).get
//    assert(cantante.mano)
//    assert(cantante.fichas.contains(Ficha(Numero(6), Numero(6))))
//    assertSoloUnoCanta(game)
//    assert(gameEvents.size == 4)
//    assert(
//      userEvents.size == 0
//    ) // Though 2 happen (log in and log out, only log in should be registering)
//  }
//  "Cantando todas" should "get it done" in {
//    val repo = new InMemoryRepository(Seq.empty)
//    val gameOperations = repo.gameOperations
//    val userOperations = repo.userOperations
//
//    val layer = fullLayer(gameOperations, userOperations)
//    val (
//      game,
//      gameEvents,
//      userEvents
//    ) =
//      Unsafe.unsafe { u ?=>
//        testRuntime.unsafe
//          .run {
//            for {
//              gameService <- ZIO.service[GameService].provide(GameService.make())
//              gameStream =
//                gameService
//                  .gameStream(GameId(1), connectionId).provideSomeLayer[ChutiEnvironment](
//                    layer ++ ChutiSession(user1).toLayer
//                  )
//              userStream =
//                gameService
//                  .userStream(connectionId).provideSomeLayer[ChutiEnvironment](
//                    layer ++ ChutiSession(user1).toLayer
//                  )
//              gameEventsFiber <- gameStream.interruptAfter(3.second).runCollect.fork
//              userEventsFiber <- userStream.interruptAfter(3.second).runCollect.fork
//              _               <- Clock.sleep(1.second)
//              game1           <- readGame(GAME_STARTED)
//              quienCanta = game1.jugadores.find(_.turno).map(_.user).get
//              game2 <-
//                gameService
//                  .play(GameId(1), Canta(CuantasCantas.CantoTodas)).provideSomeLayer[ChutiEnvironment](
//                    layer ++ SessionContext.live(ChutiSession(quienCanta))
//                  )
//              // Hay que pararle aqui, ya canto todas.
//              gameEvents <- gameEventsFiber.join
//              userEvents <- userEventsFiber.join
//            } yield (game2, gameEvents, userEvents)
//          }.getOrThrow()
//      }
//
//    assert(game.id == Option(GameId(1)))
//    assert(game.jugadores.length == 4)
//    assert(game.gameStatus == GameStatus.jugando)
//    assert(game.currentEventIndex == 8)
//    val cantante = game.jugadores.find(_.cantante).get
//    assert(cantante.mano)
//    assert(cantante.fichas.contains(Ficha(Numero(6), Numero(6))))
//    assertSoloUnoCanta(game)
//    assert(gameEvents.size == 1)
//    assert(
//      userEvents.size == 0
//    ) // Though 2 happen (log in and log out, only log in should be registering)
//  }
//  "Cantando casa con salve" should "get it done" in {
//    val repo = new InMemoryRepository(Seq.empty)
//    val gameOperations = repo.gameOperations
//    val userOperations = repo.userOperations
//
//    val layer = fullLayer(gameOperations, userOperations)
//    val (
//      game,
//      gameEvents,
//      userEvents
//    ) =
//      Unsafe.unsafe { u ?=>
//        testRuntime.unsafe
//          .run {
//            for {
//              gameService <- ZIO.service[GameService].provide(GameService.make())
//              gameStream =
//                gameService
//                  .gameStream(GameId(1), connectionId).provideSomeLayer[ChutiEnvironment](
//                    layer ++ ChutiSession(user1).toLayer
//                  )
//              userStream =
//                gameService
//                  .userStream(connectionId).provideSomeLayer[ChutiEnvironment](
//                    layer ++ ChutiSession(user1).toLayer
//                  )
//              gameEventsFiber <- gameStream.interruptAfter(3.second).runCollect.fork
//              userEventsFiber <- userStream.interruptAfter(3.second).runCollect.fork
//              _               <- Clock.sleep(1.second)
//              game1           <- readGame(GAME_STARTED)
//              quienCanta = game1.jugadores.find(_.turno).map(_.user).get
//              game2 <-
//                gameService
//                  .play(GameId(1), Canta(CuantasCantas.Casa)).provideSomeLayer[ChutiEnvironment](
//                    layer ++ SessionContext.live(ChutiSession(quienCanta))
//                  )
//              jugador2 = game2.nextPlayer(quienCanta).user
//              game3 <-
//                gameService
//                  .play(GameId(1), Canta(CuantasCantas.Canto5)).provideSomeLayer[ChutiEnvironment](
//                    layer ++ SessionContext.live(ChutiSession(jugador2))
//                  )
//              jugador3 = game3.nextPlayer(jugador2).user
//              game4 <-
//                gameService
//                  .play(GameId(1), Canta(CuantasCantas.Buenas)).provideSomeLayer[ChutiEnvironment](
//                    layer ++ SessionContext.live(ChutiSession(jugador3))
//                  )
//              jugador4 = game4.nextPlayer(jugador3).user
//              game5 <-
//                gameService
//                  .play(GameId(1), Canta(CuantasCantas.Buenas)).provideSomeLayer[ChutiEnvironment](
//                    layer ++ SessionContext.live(ChutiSession(jugador4))
//                  )
//              gameEvents <- gameEventsFiber.join
//              userEvents <- userEventsFiber.join
//            } yield (game5, gameEvents, userEvents)
//          }.getOrThrow()
//      }
//
//    assert(game.id == Option(GameId(1)))
//    assert(game.jugadores.length == 4)
//    assert(game.gameStatus == GameStatus.jugando)
//    assert(game.currentEventIndex == 11)
//    val cantante = game.jugadores.find(_.cantante).get
//    val turno = game.jugadores.find(_.turno).get
//    val salvador = game.nextPlayer(turno)
//    assert(cantante == salvador)
//    assert(cantante.mano)
//    assert(!cantante.fichas.contains(Ficha(Numero(6), Numero(6))))
//    assert(salvador.cuantasCantas == Option(CuantasCantas.Canto5))
//    assertSoloUnoCanta(game)
//    assert(gameEvents.size == 4)
//    assert(
//      userEvents.size == 0
//    ) // Though 2 happen (log in and log out, only log in should be registering)
//  }
//  "Cantando casa con salve de chuti" should "get it done" in {
//    val repo = new InMemoryRepository(Seq.empty)
//    val gameOperations = repo.gameOperations
//    val userOperations = repo.userOperations
//
//    val layer = fullLayer(gameOperations, userOperations)
//    val (
//      game,
//      gameEvents,
//      userEvents
//    ) =
//      Unsafe.unsafe { u ?=>
//        testRuntime.unsafe
//          .run {
//            for {
//              gameService <- ZIO.service[GameService].provide(GameService.make())
//              gameStream =
//                gameService
//                  .gameStream(GameId(1), connectionId).provideSomeLayer[ChutiEnvironment](
//                    layer ++ ChutiSession(user1).toLayer
//                  )
//              userStream =
//                gameService
//                  .userStream(connectionId).provideSomeLayer[ChutiEnvironment](
//                    layer ++ ChutiSession(user1).toLayer
//                  )
//              gameEventsFiber <- gameStream.interruptAfter(3.second).runCollect.fork
//              userEventsFiber <- userStream.interruptAfter(3.second).runCollect.fork
//              _               <- Clock.sleep(1.second)
//              game1           <- readGame(GAME_STARTED)
//              quienCanta = game1.jugadores.find(_.turno).map(_.user).get
//              game2 <-
//                gameService
//                  .play(GameId(1), Canta(CuantasCantas.Casa)).provideSomeLayer[ChutiEnvironment](
//                    layer ++ SessionContext.live(ChutiSession(quienCanta))
//                  )
//              jugador2 = game2.nextPlayer(quienCanta).user
//              game3 <-
//                gameService
//                  .play(GameId(1), Canta(CuantasCantas.CantoTodas)).provideSomeLayer[ChutiEnvironment](
//                    layer ++ SessionContext.live(ChutiSession(jugador2))
//                  )
//              gameEvents <- gameEventsFiber.join
//              userEvents <- userEventsFiber.join
//            } yield (game3, gameEvents, userEvents)
//          }.getOrThrow()
//      }
//
//    assert(game.id == Option(GameId(1)))
//    assert(game.jugadores.length == 4)
//    assert(game.gameStatus == GameStatus.jugando)
//    assert(game.currentEventIndex == 9)
//    val cantante = game.jugadores.find(_.cantante).get
//    val turno = game.jugadores.find(_.turno).get
//    val salvador = game.nextPlayer(turno)
//    assert(cantante == salvador)
//    assert(cantante.mano)
//    assert(!cantante.fichas.contains(Ficha(Numero(6), Numero(6))))
//    assert(salvador.cuantasCantas == Option(CuantasCantas.CantoTodas))
//    assertSoloUnoCanta(game)
//    assert(gameEvents.size == 2)
//    assert(
//      userEvents.size == 0
//    ) // Though 2 happen (log in and log out, only log in should be registering)
//  }
//  "Cantando casa con salve de 5,6,7" should "get it done" in {
//    val repo = new InMemoryRepository(Seq.empty)
//    val gameOperations = repo.gameOperations
//    val userOperations = repo.userOperations
//
//    val layer = fullLayer(gameOperations, userOperations)
//    val (
//      game,
//      gameEvents,
//      userEvents
//    ) =
//      Unsafe.unsafe { u ?=>
//        testRuntime.unsafe
//          .run {
//            for {
//              gameService <- ZIO.service[GameService].provide(GameService.make())
//              gameStream =
//                gameService
//                  .gameStream(GameId(1), connectionId).provideSomeLayer[ChutiEnvironment](
//                    layer ++ ChutiSession(user1).toLayer
//                  )
//              userStream =
//                gameService
//                  .userStream(connectionId).provideSomeLayer[ChutiEnvironment](
//                    layer ++ ChutiSession(user1).toLayer
//                  )
//              gameEventsFiber <- gameStream.interruptAfter(3.second).runCollect.fork
//              userEventsFiber <- userStream.interruptAfter(3.second).runCollect.fork
//              _               <- Clock.sleep(1.second)
//              game1           <- readGame(GAME_STARTED)
//              quienCanta = game1.jugadores.find(_.turno).map(_.user).get
//              game2 <-
//                gameService
//                  .play(GameId(1), Canta(CuantasCantas.Casa)).provideSomeLayer[ChutiEnvironment](
//                    layer ++ SessionContext.live(ChutiSession(quienCanta))
//                  )
//              jugador2 = game2.nextPlayer(quienCanta).user
//              game3 <-
//                gameService
//                  .play(GameId(1), Canta(CuantasCantas.Canto5)).provideSomeLayer[ChutiEnvironment](
//                    layer ++ SessionContext.live(ChutiSession(jugador2))
//                  )
//              jugador3 = game3.nextPlayer(jugador2).user
//              game4 <-
//                gameService
//                  .play(GameId(1), Canta(CuantasCantas.Canto6)).provideSomeLayer[ChutiEnvironment](
//                    layer ++ SessionContext.live(ChutiSession(jugador3))
//                  )
//              jugador4 = game4.nextPlayer(jugador3).user
//              game5 <-
//                gameService
//                  .play(GameId(1), Canta(CuantasCantas.CantoTodas)).provideSomeLayer[ChutiEnvironment](
//                    layer ++ SessionContext.live(ChutiSession(jugador4))
//                  )
//              gameEvents <- gameEventsFiber.join
//              userEvents <- userEventsFiber.join
//            } yield (game5, gameEvents, userEvents)
//          }.getOrThrow()
//      }
//
//    assert(game.id == Option(GameId(1)))
//    assert(game.jugadores.length == 4)
//    assert(game.gameStatus == GameStatus.jugando)
//    assert(game.currentEventIndex == 11)
//    val cantante = game.jugadores.find(_.cantante).get
//    val turno = game.jugadores.find(_.turno).get
//    val salvador = game.prevPlayer(turno) // Nota esto, es el que da la vuelta
//    assert(cantante == salvador)
//    assert(cantante.mano)
//    assert(!cantante.fichas.contains(Ficha(Numero(6), Numero(6))))
//    assert(salvador.cuantasCantas == Option(CuantasCantas.CantoTodas))
//    assertSoloUnoCanta(game)
//    assert(gameEvents.size == 4)
//    assert(
//      userEvents.size == 0
//    ) // Though 2 happen (log in and log out, only log in should be registering)
//  }
//
//}
