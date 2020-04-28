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
import better.files.File
import dao.Repository.GameOperations
import dao.{DatabaseProvider, Repository, SessionProvider}
import game.GameService.GameService
import game.{GameService, LoggedInUserRepo}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import org.mockito.captor.ArgCaptor
import org.mockito.scalatest.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec
import zio._
import zio.duration._

class ChutiSpec extends AnyFlatSpec with MockitoSugar with GameAbstractSpec {
  "Printing the game" should "print it" in {
    testRuntime.unsafeRun {
      for {
        game <- readGame(GAME_STARTED)
        _    <- console.putStrLn(game.toString)
      } yield game
    }

  }
  "Cantando casa sin salve" should "get it done" in {
    val gameOperations: Repository.GameOperations = mock[Repository.GameOperations]
//    when(gameOperations.upsert(*[Game])).thenAnswer((game: Game) =>
//      ZIO.succeed(game.copy(Some(GameId(1))))
//    )
    val userOperations = createUserOperations
//    when(userOperations.upsert(*[User])).thenAnswer { u: User =>
//      ZIO.succeed(u)
//    }

    val layer = fullLayer(gameOperations, userOperations)
    for {
      gameService <- ZIO.access[GameService](_.get).provideCustomLayer(GameService.make())
      gameStream = gameService
        .gameStream(GameId(1)).provideCustomLayer(
          layer ++ SessionProvider.layer(ChutiSession(user1))
        )
      userStream = gameService.userStream.provideCustomLayer(
        layer ++ SessionProvider.layer(ChutiSession(user1))
      )
      gameEventsFiber <- gameStream.take(2).runCollect.timeout(3.second).fork
      userEventsFiber <- userStream.take(1).runCollect.timeout(3.second).fork
      game1           <- readGame(GAME_STARTED)
      _ <- ZIO.succeed(
        when(gameOperations.get(GameId(1))).thenReturn(ZIO.succeed(Option(game1)))
      )
      quienCanta = game1.jugadores.find(_.mano).map(_.user).get
      game2 <- gameService
        .play(Canta(quienCanta, CuantasCantas.Canto4)).provideCustomLayer(
          layer ++ SessionProvider.layer(ChutiSession(quienCanta))
        )
      gameEvents <- gameEventsFiber.join
      userEvents <- userEventsFiber.join
    } yield (game2, gameEvents, userEvents)
    pending
  }
  "Cantando cinco sin salve" should "get it done" in {
    pending
  }
  "Cantando todas" should "get it done" in {
    pending
  }
  "Cantando casa con salve" should "get it done" in {
    pending
  }
}
