package chuti

import api.ChutiSession
import chuti.CuantasCantas.{Canto7, CantoTodas, Casa, CuantasCantas}
import chuti.InMemoryRepository.{user1, user2, user3, user4}
import chuti.Triunfo.SinTriunfos
import chuti.bots.DumbPlayerBot
import dao.{Repository, SessionProvider}
import game.GameService
import game.GameService.GameService
import org.scalatest.flatspec.AsyncFlatSpecLike
import org.scalatest.{Assertion, Succeeded}
import zio.clock.Clock
import zio.console.Console
import zio.duration._
import zio.{RIO, ZIO, clock}

import scala.util.Random

class FullGameSpec extends GameAbstractSpec2 with AsyncFlatSpecLike {

//  "Playing a specific game" should "look all nice" in {
//    val game =
//      "/Volumes/Personal/projects/chuti/server/src/test/resources/weird_game1589819797.json"
//    testRuntime.unsafeRunToFuture {
//      playSpecificGame(game)
//    }.future
//  }

  "Playing a full game" should "look all nice" in {
    testRuntime.unsafeRunToFuture {
      ZIO
        .foreach(1 to 100) { _ =>
          playFullGame
        }.map(l => assert(l.forall(_._1 == Succeeded)))
    }.future
  }

  object GameTester {
    def apply(
      description:   String,
      hands:         Seq[String],
      triunfo:       Triunfo,
      cuantasCantas: CuantasCantas,
      testEndState:  Game => Assertion
    ): GameTester = {
      val parsedHands = hands.map(str => str.split(",").toList.map(Ficha.fromString)).toList
      assert(!parsedHands.exists(_.length != 7)) //For now we only support starting games
      val otherHands = Random.shuffle(Game.todaLaFicha.diff(parsedHands.flatten)).grouped(7).toList
      val allHands = parsedHands ++ otherHands
      assert(allHands.size == 4)
      val users = Seq(user1, user2, user3, user4)
      val jugadores = allHands.zip(users).map {
        case (hand, user) =>
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
      val game = Game(None, GameStatus.jugando, triunfo = Option(triunfo), jugadores = jugadores)

      new GameTester(description, game, testEndState)
    }
  }
  case class GameTester(
    description:  String,
    game:         Game,
    testEndState: Game => Assertion
  )

  val gamesToTest = Seq(
    GameTester(
      "cantar 7 pero no de caida (no estan pegadas)",
      Seq("3:3,3:5,3:4,3:2,3:1,1:1,2:2", "3:6,4:5,1:2,2:0,1:4,1:5,1:6"),
      SinTriunfos,
      Canto7,
      game => assert(game.quienCanta.cuenta.map(_.puntos).sum == 7)
    ),
    GameTester(
      "cantar 7 pero no de caida (estan pegadas)",
      Seq("3:3,3:5,3:4,3:2,3:1,1:1,2:2", "3:6,3:0,1:2,2:0,1:4,1:5,1:6"),
      SinTriunfos,
      Canto7,
      game => {
        assert(game.quienCanta.cuenta.head.esHoyo)
        assert(game.quienCanta.cuenta.map(_.puntos).sum == -7)
      }
    ),
    GameTester(
      "cantar chuti pero no de caida (no estan pegadas)",
      Seq("3:3,3:5,3:4,3:2,3:1,1:1,2:2", "3:6,4:5,1:2,2:0,1:4,1:5,1:6"),
      SinTriunfos,
      CantoTodas,
      game => {
        assert(game.quienCanta.cuenta.map(_.puntos).sum == 21)
        assert(game.gameStatus == GameStatus.partidoTerminado)
      }
    ),
    GameTester(
      "cantar chuti pero no de caida (estan pegadas)",
      Seq("3:3,3:5,3:4,3:2,3:1,1:1,2:2", "3:6,3:0,1:2,2:0,1:4,1:5,1:6"),
      SinTriunfos,
      CantoTodas,
      game => {
        assert(game.quienCanta.cuenta.head.esHoyo)
        assert(game.quienCanta.cuenta.map(_.puntos).sum == -21)
      }
    ),
    GameTester(
      "cantar 4, pero hacer 7",
      Seq("3:3,3:5,3:4,3:2,3:1,1:1,2:2", "3:6,4:5,1:2,2:0,1:4,1:5,1:6"),
      SinTriunfos,
      Casa,
      game => assert(game.quienCanta.cuenta.map(_.puntos).sum == 7)
    )
  )

  gamesToTest.map { tester =>
    tester.description should "work" in {
      testRuntime.unsafeRunToFuture {
        (for {
          gameOperations <- ZIO.access[Repository](_.get.gameOperations)
          saved <- gameOperations
            .upsert(tester.game).provideSomeLayer[TestLayer](
              godLayer
            )
          played <- juegaHastaElFinal(saved.id.get)
        } yield {
          assert(tester.testEndState(played._2) == Succeeded && played._1 == Succeeded)
        }).provideCustomLayer(testLayer())
      }.future
    }
  }
}
