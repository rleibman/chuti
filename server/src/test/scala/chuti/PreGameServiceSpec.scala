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
import dao.{InMemoryRepository, Repository, SessionContext}
import game.GameService
import org.scalatest.flatspec.AnyFlatSpec
import zio.*
import zio.duration.*

class PreGameServiceSpec extends AnyFlatSpec with GameAbstractSpec {

  "Creating a new Game" should "create a game" in {
    val repo = new InMemoryRepository(Seq.empty)
    val gameOperations = repo.gameOperations
    val userOperations = repo.userOperations

    val layer = fullLayer(gameOperations, userOperations)

    val game: Game = testRuntime.unsafeRun {
      for {
        gameService <- ZIO.service[GameService.Service].provideCustomLayer(GameService.make())
        operation <-
          gameService
            .newGame(satoshiPerPoint = 100).provideCustomLayer(
              layer ++ SessionContext.live(ChutiSession(user1))
            )
        _ <- writeGame(operation, GAME_NEW)
      } yield operation
    }

    assert(game.gameStatus === GameStatus.esperandoJugadoresInvitados)
    assert(game.currentEventIndex === 1)
    assert(game.id === Option(GameId(1)))
    assert(game.jugadores.length == 1)
    assert(game.jugadores.head.user.id === user1.id)
//    verify(gameOperations).upsert(*[Game])
//    verify(userOperations, times(1)).upsert(*[User])
  }

  "Loading a new game and having 1 more random users join" should "keep the game open" in {
    val repo = new InMemoryRepository(Seq.empty)
    val gameOperations = repo.gameOperations
    val userOperations = repo.userOperations

    val layer = fullLayer(gameOperations, userOperations)
    val (game: Game, gameEvents: Chunk[GameEvent], userEvents: Chunk[UserEvent]) =
      testRuntime.unsafeRun {
        for {
          gameService <- ZIO.service[GameService.Service].provideCustomLayer(GameService.make())
          gameStream =
            gameService
              .gameStream(GameId(1), connectionId).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user1))
              )
          userStream =
            gameService
              .userStream(connectionId).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user1))
              )
          gameEventsFiber <- gameStream.interruptAfter(3.second).runCollect.fork
          userEventsFiber <- userStream.interruptAfter(3.second).runCollect.fork
          _               <- clock.sleep(1.second)
          game            <- readGame(GAME_NEW)
          withUser2 <-
            gameService
              .joinRandomGame().provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user2))
              )
          _          <- writeGame(withUser2, GAME_WITH_2USERS)
          gameEvents <- gameEventsFiber.join
          userEvents <- userEventsFiber.join
        } yield (withUser2, gameEvents, userEvents)

      }

    assert(game.gameStatus === GameStatus.esperandoJugadoresInvitados)
    assert(game.currentEventIndex === 3)
    assert(game.id === Option(GameId(1)))
    assert(game.jugadores.length == 2)
    assert(game.jugadores.head.user.id === user1.id)
    assert(game.jugadores.drop(1).head.user.id === user2.id)
    assert(gameEvents.size === 2)
    assert(
      userEvents.size === 1
    ) // Though 2 happen (log in and log out, only log in should be registering)

//    verify(userOperations, times(1)).upsert(*[User])
  }

  "Loading a new game and having 3 more random users join" should "start the game" in {
    val repo = new InMemoryRepository(Seq.empty)
    val gameOperations = repo.gameOperations
    val userOperations = repo.userOperations

    val layer = fullLayer(gameOperations, userOperations)

    val (game: Game, gameEvents: Chunk[GameEvent], userEvents: Chunk[UserEvent]) =
      testRuntime.unsafeRun {
        for {
          gameService <- ZIO.service[GameService.Service].provideCustomLayer(GameService.make())
          gameStream =
            gameService
              .gameStream(GameId(1), connectionId).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user1))
              )
          userStream =
            gameService
              .userStream(connectionId).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user1))
              )
          gameEventsFiber <- gameStream.interruptAfter(3.second).runCollect.fork
          userEventsFiber <- userStream.interruptAfter(3.second).runCollect.fork
          _               <- clock.sleep(1.second)
          game            <- readGame(GAME_NEW)
          withUser2 <-
            gameService
              .joinRandomGame().provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user2))
              )
          withUser3 <-
            gameService
              .joinRandomGame().provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user3))
              )
          withUser4 <-
            gameService
              .joinRandomGame().provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user4))
              )
          _          <- writeGame(withUser4, GAME_STARTED)
          gameEvents <- gameEventsFiber.join
          userEvents <- userEventsFiber.join
        } yield (withUser4, gameEvents, userEvents)

      }

    assert(game.id === Option(GameId(1)))
    assert(game.jugadores.length == 4)
    assert(game.gameStatus === GameStatus.cantando)
    assert(game.currentEventIndex === 7)
    assert(game.jugadores.head.user.id === user1.id)
    assert(game.jugadores.drop(1).head.user.id === user2.id)
    assert(game.jugadores.drop(2).head.user.id === user3.id)
    assert(game.jugadores.drop(3).head.user.id === user4.id)
    assert(gameEvents.size === 6)
    assert(
      userEvents.size === 3
    ) // Though 2 happen (log in and log out, only log in should be registering)

//    verify(userOperations, times(3)).upsert(*[User])
  }

  "Abandoning an unstarted game" should "result in no penalty, and not close the game" in {
    val repo = new InMemoryRepository(Seq.empty)
    val gameOperations = repo.gameOperations
    val userOperations = repo.userOperations
    val layer = fullLayer(gameOperations, userOperations)

    val (
      abandonedGame: Boolean,
      gameEvents:    Chunk[GameEvent],
      userEvents:    Chunk[UserEvent]
    ) =
      testRuntime.unsafeRun {
        for {
          gameService <- ZIO.service[GameService.Service].provideCustomLayer(GameService.make())
          gameStream =
            gameService
              .gameStream(GameId(1), connectionId).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user1))
              )
          userStream =
            gameService
              .userStream(connectionId).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user1))
              )
          gameEventsFiber <- gameStream.interruptAfter(3.second).runCollect.fork
          userEventsFiber <- userStream.interruptAfter(3.second).runCollect.fork
          _               <- clock.sleep(1.second)
          game            <- readGame(GAME_WITH_2USERS)
          abandonedGame <-
            gameService
              .abandonGame(GameId(1)).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user2))
              )
          gameEvents <- gameEventsFiber.join
          userEvents <- userEventsFiber.join
        } yield (abandonedGame, gameEvents, userEvents)

      }

//    val gameCaptor = ArgCaptor[Game]
////    verify(gameOperations, times(1)).upsert(gameCaptor)
//    val game = gameCaptor.value
//
//    val userCaptor = ArgCaptor[User]
////    verify(userOperations, times(1)).upsert(userCaptor)
//    val updatedUser2 = userCaptor.value
//
//    assert(abandonedGame)
//    assert(game.id === Option(GameId(1)))
//    assert(game.jugadores.length == 1)
//    assert(
//      game.gameStatus == GameStatus.esperandoJugadoresInvitados || game.gameStatus == GameStatus.esperandoJugadoresAzar
//    )
//    assert(game.currentEventIndex === 4)
//    assert(game.jugadores.head.user.id === user1.id)
//    assert(gameEvents.size === 1)
//    assert(
//      userEvents.size === 1
//    ) // Though 2 happen (log in and log out, only log in should be registering)
//    verify(userOperations, times(0)).updateWallet(any[UserWallet])
  }

  "Abandoning a started game" should "result in a penalty, and close the game" in {
    val repo = new InMemoryRepository(Seq.empty)
    val gameOperations = repo.gameOperations
    val userOperations = repo.userOperations
    val layer = fullLayer(gameOperations, userOperations)

    val (
      abandonedGame: Boolean,
      gameEvents:    Chunk[GameEvent],
      userEvents:    Chunk[UserEvent]
    ) =
      testRuntime.unsafeRun {
        for {
          gameService <- ZIO.service[GameService.Service].provideCustomLayer(GameService.make())
          gameStream =
            gameService
              .gameStream(GameId(1), connectionId).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user1))
              )
          userStream =
            gameService
              .userStream(connectionId).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user1))
              )
          gameEventsFiber <- gameStream.interruptAfter(3.second).runCollect.fork
          userEventsFiber <- userStream.interruptAfter(3.second).runCollect.fork
          _               <- clock.sleep(1.second)
          game            <- readGame(GAME_STARTED)
          abandonedGame <-
            gameService
              .abandonGame(GameId(1)).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user2))
              )
          gameEvents <- gameEventsFiber.join
          userEvents <- userEventsFiber.join
        } yield (abandonedGame, gameEvents, userEvents)

      }

//    val gameCaptor = ArgCaptor[Game]
////    verify(gameOperations, times(1)).upsert(gameCaptor)
//    val game = gameCaptor.value
//
//    val userCaptor = ArgCaptor[User]
////    verify(userOperations, times(1)).upsert(userCaptor)
//    val updatedUser2 = userCaptor.value
//
//    assert(abandonedGame)
//    assert(game.id === Option(GameId(1)))
//    assert(game.jugadores.length == 3)
//    assert(game.gameStatus === GameStatus.abandonado)
//    assert(game.currentEventIndex === 8)
//    assert(game.jugadores.head.user.id === user1.id)
//    assert(game.jugadores.drop(1).head.user.id === user3.id)
//    assert(game.jugadores.drop(2).head.user.id === user4.id)
//    assert(gameEvents.size === 1)
//    assert(
//      userEvents.size === 1
//    ) // Though 2 happen (log in and log out, only log in should be registering)
//    verify(userOperations, times(1)).updateWallet(any[UserWallet])
  }

  "Invite to game 1 person" should "add to the game" in {
    val repo = new InMemoryRepository(Seq.empty)
    val gameOperations = repo.gameOperations
    val userOperations = repo.userOperations
    val layer = fullLayer(gameOperations, userOperations)

    val (
      invited:    Boolean,
      gameEvents: Chunk[GameEvent],
      userEvents: Chunk[UserEvent]
    ) =
      testRuntime.unsafeRun {
        for {
          gameService <- ZIO.service[GameService.Service].provideCustomLayer(GameService.make())
          gameStream =
            gameService
              .gameStream(GameId(1), connectionId).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user1))
              )
          userStream =
            gameService
              .userStream(connectionId).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user1))
              )
          gameEventsFiber <- gameStream.interruptAfter(3.second).runCollect.fork
          userEventsFiber <- userStream.interruptAfter(3.second).runCollect.fork
          _               <- clock.sleep(1.second)
          game            <- readGame(GAME_NEW)
          invited <-
            gameService
              .inviteToGame(user2.id.get, game.id.get).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user1))
              )
          gameEvents <- gameEventsFiber.join
          userEvents <- userEventsFiber.join
        } yield (invited, gameEvents, userEvents)
      }

//    val gameCaptor = ArgCaptor[Game]
////    verify(gameOperations, times(1)).upsert(gameCaptor)
//    val game = gameCaptor.value
//
//    assert(invited)
//    assert(game.gameStatus === GameStatus.esperandoJugadoresInvitados)
//    assert(game.currentEventIndex === 2)
//    assert(game.id === Option(GameId(1)))
//    assert(game.jugadores.length == 2)
//    assert(game.jugadores.head.user.id === user1.id)
//    assert(game.jugadores.drop(1).head.user.id === user2.id)
//    assert(game.jugadores.drop(1).head.invited)
//    assert(gameEvents.size === 1)
//    assert(userEvents.size === 0)
  }

  "Invite to game 3 people, and them accepting" should "add to the game, and start it" in {
    val repo = new InMemoryRepository(Seq.empty)
    val gameOperations = repo.gameOperations
    val userOperations = repo.userOperations
    val layer = fullLayer(gameOperations, userOperations)

    val (
      game:       Game,
      gameEvents: Chunk[GameEvent],
      userEvents: Chunk[UserEvent]
    ) =
      testRuntime.unsafeRun {
        for {
          gameService <- ZIO.service[GameService.Service].provideCustomLayer(GameService.make())
          gameStream =
            gameService
              .gameStream(GameId(1), connectionId).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user1))
              )
          userStream =
            gameService
              .userStream(connectionId).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user1))
              )
          gameEventsFiber <- gameStream.interruptAfter(3.second).runCollect.fork
          userEventsFiber <- userStream.interruptAfter(3.second).runCollect.fork
          _               <- clock.sleep(1.second)
          game            <- readGame(GAME_NEW)
          _ <-
            gameService
              .inviteToGame(user2.id.get, game.id.get).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user1))
              )
          _ <-
            gameService
              .inviteToGame(user3.id.get, game.id.get).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user1))
              )
          _ <-
            gameService
              .inviteToGame(user4.id.get, game.id.get).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user1))
              )
          accepted2 <-
            gameService
              .acceptGameInvitation(game.id.get).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user2))
              )
          accepted3 <-
            gameService
              .acceptGameInvitation(game.id.get).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user3))
              )
          accepted4 <-
            gameService
              .acceptGameInvitation(game.id.get).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user4))
              )
          gameEvents <- gameEventsFiber.join
          userEvents <- userEventsFiber.join
        } yield (accepted4, gameEvents, userEvents)
      }

//    val gameCaptor = ArgCaptor[Game]
////    verify(gameOperations, times(6)).upsert(gameCaptor)
//    val gameCapt = gameCaptor.value
//    assert(gameCapt === game)
//
//    assert(game.gameStatus === GameStatus.cantando)
//    assert(game.currentEventIndex === 10)
//    assert(game.id === Option(GameId(1)))
//    assert(game.jugadores.length == 4)
//    assert(game.jugadores.forall(!_.invited))
//    assert(gameEvents.size === 9)
//    assert(userEvents.size === 3)
  }

  "Invite to game 3 people, and one of them declining" should "add two to the game, and then remove one" in {
    val repo = new InMemoryRepository(Seq.empty)
    val gameOperations = repo.gameOperations
    val userOperations = repo.userOperations
    val layer = fullLayer(gameOperations, userOperations)

    val (
      game:       Game,
      gameEvents: Chunk[GameEvent],
      userEvents: Chunk[UserEvent]
    ) =
      testRuntime.unsafeRun {
        for {
          gameService <- ZIO.service[GameService.Service].provideCustomLayer(GameService.make())
          gameStream =
            gameService
              .gameStream(GameId(1), connectionId).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user1))
              )
          userStream =
            gameService
              .userStream(connectionId).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user1))
              )
          gameEventsFiber <- gameStream.interruptAfter(3.second).runCollect.fork
          userEventsFiber <- userStream.interruptAfter(3.second).runCollect.fork
          _               <- clock.sleep(1.second)
          game            <- readGame(GAME_NEW)
          _ <-
            gameService
              .inviteToGame(user2.id.get, game.id.get).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user1))
              )
          _ <-
            gameService
              .inviteToGame(user3.id.get, game.id.get).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user1))
              )
          _ <-
            gameService
              .inviteToGame(user4.id.get, game.id.get).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user1))
              )
          accepted2 <-
            gameService
              .acceptGameInvitation(game.id.get).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user2))
              )
          _ <-
            gameService
              .declineGameInvitation(game.id.get).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user3))
              )
          accepted4 <-
            gameService
              .acceptGameInvitation(game.id.get).provideCustomLayer(
                layer ++ SessionContext.live(ChutiSession(user4))
              )
          gameEvents <- gameEventsFiber.join
          userEvents <- userEventsFiber.join
        } yield (accepted4, gameEvents, userEvents)
      }

//    val gameCaptor = ArgCaptor[Game]
////    verify(gameOperations, times(6)).upsert(gameCaptor)
//    val gameCapt = gameCaptor.value
//    assert(gameCapt === game)
//
//    assert(game.gameStatus === GameStatus.esperandoJugadoresInvitados)
//    assert(game.currentEventIndex === 9)
//    assert(game.id === Option(GameId(1)))
//    assert(game.jugadores.length == 3)
//    assert(game.jugadores.forall(!_.invited))
//    assert(gameEvents.size === 8)
//    assert(userEvents.size === 2)
  }

}
