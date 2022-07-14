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

import api.token.TokenHolder
import chat.ChatService
import chuti.CuantasCantas.{Canto7, CantoTodas, Casa, CuantasCantas}
import chuti.Triunfo.TriunfoNumero
import dao.InMemoryRepository.{user1, user2, user3, user4}
import dao.Repository
import game.GameService
import mail.Postman
import org.scalatest.Assertions.*
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.{Assertion, Succeeded}
import zio.{Unsafe, ZIO}

import java.time.Instant
import scala.util.Random

class FullGameSpec extends GameAbstractSpec2 with AsyncFlatSpecLike {

//  "Playing a specific game" should "look all nice" in {
//    val game =
//      "/Volumes/Personal/projects/chuti/server/src/test/resources/weird_game1589819797.json"
//    testRuntime.unsafeRunToFuture {
//      playSpecificGame(game)
//    }.future
//  }

  "Playing a bunch of full games" should "look all nice" in {
    Unsafe.unsafe {
      u ?=>
        testRuntime.unsafe.runToFuture {
          ZIO
            .foreachPar(1 to 100) { _ =>
              playFullGame
            }.as(assert(true))
        }.future
    }
  }

  object GameTester {

    def apply(
               description: String,
               hands: Seq[String],
               triunfo: Option[Triunfo],
               cuantasCantas: CuantasCantas,
               testEndState: Game => Assertion
             ): GameTester = {
      val parsedHands = hands.map(str => str.split(",").nn.toList.map(s => Ficha.fromString(s.nn))).toList
      assert(!parsedHands.exists(_.length != 7)) // For now we only support starting games
      val otherHands = Random.shuffle(Game.todaLaFicha.diff(parsedHands.flatten)).grouped(7).toList
      val allHands = parsedHands ++ otherHands
      assert(allHands.size == 4)
      val users = Seq(user1, user2, user3, user4)
      val jugadores = allHands.zip(users).map { case (hand, user) =>
        if (user == user1) {
          Jugador(
            user,
            fichas = hand,
            turno = true,
            cantante = true,
            mano = true,
            cuantasCantas = Option(cuantasCantas)
          )
        } else {
          Jugador(
            user,
            fichas = hand
          )
        }
      }
      val game = Game(None, created = Instant.now.nn, GameStatus.jugando, triunfo = triunfo, jugadores = jugadores)

      new GameTester(description, game, testEndState)
    }

  }
  case class GameTester(
    description:  String,
    game:         Game,
    testEndState: Game => Assertion
  )

  private val gamesToTest = Seq(
    GameTester(
      "cantar 7 pero no de caida (no estan pegadas)",
      Seq("3:3,5:3,4:3,3:2,3:1,6:6,6:4", "6:3,4:5,1:2,2:0,1:4,1:5,6:5"),
      None,
      Canto7,
      game => {
        assert(game.triunfo == Option(TriunfoNumero(Numero.Numero3)))
        assert(game.gameStatus == GameStatus.requiereSopa)
        assert(game.quienCanta.get.cuenta.map(_.puntos).sum == 7)
      }
    ),
    GameTester(
      "cantar 7 pero no de caida (estan pegadas)",
      Seq("3:3,5:3,4:3,3:2,3:1,6:6,6:4", "6:3,1:0,2:1,6:0,6:1,6:2,6:5"),
      None,
      Canto7,
      game => {
        assert(game.triunfo == Option(TriunfoNumero(Numero.Numero3)))
        assert(game.gameStatus == GameStatus.requiereSopa)
        assert(game.quienCanta.get.cuenta.head.esHoyo)
        assert(game.quienCanta.get.cuenta.map(_.puntos).sum == -7)
      }
    ),
    GameTester(
      "cantar chuti pero no de caida (no estan pegadas)",
      Seq("3:3,3:5,3:4,3:2,3:1,1:1,2:2", "3:6,4:5,1:2,2:0,1:4,1:5,1:6"),
      None,
      CantoTodas,
      game => {
        assert(game.triunfo == Option(TriunfoNumero(Numero.Numero3)))
        assert(game.gameStatus == GameStatus.partidoTerminado)
        assert(game.quienCanta.get.cuenta.map(_.puntos).sum == 21)
        assert(game.gameStatus == GameStatus.partidoTerminado)
      }
    ),
    GameTester(
      "cantar chuti pero no de caida (estan pegadas)",
      Seq("3:3,3:5,3:4,3:2,3:1,1:1,2:2", "3:6,3:0,1:2,2:0,1:4,1:5,1:6"),
      None,
      CantoTodas,
      game => {
        assert(game.triunfo == Option(TriunfoNumero(Numero.Numero3)))
        assert(game.gameStatus == GameStatus.requiereSopa)
        assert(game.quienCanta.get.cuenta.head.esHoyo)
        assert(game.quienCanta.get.cuenta.map(_.puntos).sum == -21)
      }
    ),
    GameTester(
      "cantar 4, pero hacer 7",
      Seq("3:3,3:5,3:4,3:2,3:1,6:6,6:5", "3:6,4:5,1:2,2:0,1:4,1:5,1:6"),
      None,
      Casa,
      game => {
        assert(game.triunfo == Option(TriunfoNumero(Numero.Numero3)))
        assert(game.gameStatus == GameStatus.requiereSopa)
        assert(game.quienCanta.get.cuenta.map(_.puntos).sum == 7)
      }
    )
  )

  gamesToTest.foreach { tester =>
    tester.description should "work" in {
      Unsafe.unsafe {
        u ?=>
          testRuntime.unsafe.runToFuture {
            (for {
              gameOperations <- ZIO.service[Repository].map(_.gameOperations)
              saved <-
                gameOperations
                  .upsert(tester.game).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](
                  godLayer
                )
              played <- juegaHastaElFinal(saved.id.get)
            } yield assert(tester.testEndState(played) == Succeeded))
              .provide(testLayer())
          }.future
      }
    }
  }

}
