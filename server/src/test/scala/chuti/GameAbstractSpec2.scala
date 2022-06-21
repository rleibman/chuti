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
import better.files.File
import chat.ChatService
import chat.ChatService.ChatService
import chuti.CuantasCantas.Buenas
import chuti.bots.DumbChutiBot
import dao.InMemoryRepository.{user1, user2, user3, user4}
import dao.slick.DatabaseProvider
import dao.{DatabaseProvider, InMemoryRepository, Repository, SessionProvider}
import game.GameService
import game.GameService.GameService
import io.circe.Printer
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import mail.Postman
import mail.Postman.Postman
import org.mockito.scalatest.MockitoSugar
import org.scalatest.{Assertion, Succeeded}
import zio.*
import zio.clock.Clock
import zio.console.Console
import zio.duration.*
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger

import java.util.UUID

trait GameAbstractSpec2 extends MockitoSugar {

  val connectionId:                    ConnectionId = ConnectionId(UUID.randomUUID().toString)
  lazy protected val testRuntime:      zio.Runtime[zio.ZEnv] = zio.Runtime.default
  lazy protected val databaseProvider: DatabaseProvider.Service = mock[DatabaseProvider.Service]

  def juegaHastaElFinal(gameId: GameId): RIO[TestLayer, (Assertion, Game)] = {
    for {
      gameOperations <- ZIO.access[Repository](_.get.gameOperations)
      start <-
        gameOperations
          .get(gameId).map(_.get).provideSomeLayer[TestLayer](
            SessionProvider.layer(ChutiSession(chuti.god))
          )
      looped <-
        ZIO.iterate(start)(game => game.gameStatus == GameStatus.jugando)(_ => juegaMano(gameId))
      asserted <- ZIO.succeed {
        val numFilas = looped.jugadores.map(_.filas.size).sum
        looped.quienCanta.fold(fail()) { cantante =>
          if (cantante.yaSeHizo) {
            println(
              s"${cantante.user.name} canto ${cantante.cuantasCantas.get} se hizo con ${cantante.filas.size}!"
            )
//          if (cantante.fichas.size !== (numFilas - 7)) {
//            println("what?!") //Save it to analyze
//            testRuntime.unsafeRun(writeGame(afterCanto, WEIRD_GAME("regalos_dont_match")))
//          }
            assert(cantante.fichas.size === (numFilas - 7))
          } else {
            println(
              s"Fue hoyo de ${cantante.cuantasCantas.get} para ${cantante.user.name}!"
            )
          }
          if (looped.gameStatus === GameStatus.partidoTerminado)
            println("Partido terminado!")
          assert(
            looped.gameStatus === GameStatus.requiereSopa || looped.gameStatus === GameStatus.partidoTerminado
          )
        // May want to assert Que las cuentas quedaron claras
        }
      }
    } yield (asserted, looped)
  }

  def juegaMano(gameId: GameId): RIO[TestLayer, Game] = {
    val bot = DumbChutiBot
    for {
      gameOperations <- ZIO.access[Repository](_.get.gameOperations)
      game <-
        gameOperations
          .get(gameId).map(_.get).provideSomeLayer[TestLayer](
            SessionProvider.layer(ChutiSession(chuti.god))
          )
      mano = game.mano.get
      sigiuente1 = game.nextPlayer(mano)
      sigiuente2 = game.nextPlayer(sigiuente1)
      sigiuente3 = game.nextPlayer(sigiuente2)
      _ <-
        bot
          .takeTurn(gameId).provideSomeLayer[TestLayer](
            SessionProvider.layer(ChutiSession(mano.user))
          )
      _ <-
        bot
          .takeTurn(gameId).provideSomeLayer[TestLayer](
            SessionProvider.layer(ChutiSession(sigiuente1.user))
          )
      _ <-
        bot
          .takeTurn(gameId).provideSomeLayer[TestLayer](
            SessionProvider.layer(ChutiSession(sigiuente2.user))
          )
      afterPlayer4 <-
        bot
          .takeTurn(gameId).provideSomeLayer[TestLayer](
            SessionProvider.layer(ChutiSession(sigiuente3.user))
          )
    } yield afterPlayer4
  }

  def repositoryLayer(gameFiles: String*): ULayer[Repository] = {
    val z: ZIO[Any, Throwable, Seq[Game]] =
      ZIO.foreachPar(gameFiles)(filename => readGame(filename))
    val zz: UIO[InMemoryRepository] = z.map(games => new InMemoryRepository(games)).orDie
    zz
  }.toLayer

  type TestLayer = DatabaseProvider
  & Repository & Postman & Logging & TokenHolder & GameService & ChatService & Clock

  final protected def testLayer(gameFiles: String*): ULayer[TestLayer] = {
    val postman: Postman.Service = new MockPostman
    val loggingLayer = Slf4jLogger.make((_, b) => b)
    ZLayer.succeed(databaseProvider) ++
      repositoryLayer(gameFiles: _*) ++
      ZLayer.succeed(postman) ++
      loggingLayer ++
      ZLayer.succeed(TokenHolder.tempCache) ++
      GameService.make() ++
      (loggingLayer >>> ChatService.make()) ++
      Clock.live // Change for a fixed clock
  }

  def writeGame(
    game:     Game,
    filename: String
  ): Task[Unit] =
    ZIO.effect {
      val file = File(filename)
      file.write(game.asJson.printWith(Printer.spaces2))
    }

  def readGame(filename: String): Task[Game] =
    ZIO.effect {
      val file = File(filename)
      decode[Game](file.contentAsString)
    }.absolve

  lazy final val GAME_NEW =
    "/Volumes/Personal/projects/chuti/server/src/test/resources/newGame.json"
  lazy final val GAME_STARTED =
    "/Volumes/Personal/projects/chuti/server/src/test/resources/startedGame.json"
  lazy final val GAME_WITH_2USERS =
    "/Volumes/Personal/projects/chuti/server/src/test/resources/with2Users.json"
  lazy final val GAME_CANTO4 =
    "/Volumes/Personal/projects/chuti/server/src/test/resources/canto4.json"
  def WEIRD_GAME(prefix: String) =
    s"/Volumes/Personal/projects/chuti/server/src/test/resources/$prefix${System
        .currentTimeMillis() / 1000}.json"

  protected def userLayer(user: User): ULayer[SessionProvider] = {
    SessionProvider.layer(ChutiSession(user))
  }
  protected def godLayer: ULayer[SessionProvider] = {
    SessionProvider.layer(ChutiSession(chuti.god))
  }

  def assertSoloUnoCanta(game: Game): Assertion = {
    assert(game.jugadores.count(_.cantante) === 1)
    assert(game.jugadores.count(_.mano) === 1)
    assert(game.jugadores.count(_.turno) === 1)
    assert(game.jugadores.count(j => j.cuantasCantas.fold(false)(_ != Buenas)) === 1)
    val cantante = game.jugadores.find(_.cantante).get
    assert(cantante.mano)
    assert(cantante.cuantasCantas.fold(false)(c => c.prioridad > CuantasCantas.Buenas.prioridad))
  }

  def newGame(satoshiPerPoint: Int): RIO[TestLayer, Game] =
    for {
      gameService <- ZIO.service[GameService.Service]
      // Start the game
      game <- gameService.newGame(satoshiPerPoint).provideSomeLayer[TestLayer](userLayer(user1))
    } yield game

  def getReadyToPlay(gameId: GameId): ZIO[TestLayer, Throwable, Game] = {
    for {
      gameOperations <- ZIO.access[Repository](_.get.gameOperations)
      gameService    <- ZIO.service[GameService.Service]
      game           <- gameOperations.get(gameId).map(_.get).provideSomeLayer[TestLayer](godLayer)
      // Invite people
      _ <-
        gameService
          .inviteToGame(user2.id.get, game.id.get).provideSomeLayer[TestLayer](
            userLayer(user1)
          )
      _ <-
        gameService
          .inviteToGame(user3.id.get, game.id.get).provideSomeLayer[TestLayer](
            userLayer(user1)
          )
      _ <-
        gameService
          .inviteToGame(user4.id.get, game.id.get).provideSomeLayer[TestLayer](
            userLayer(user1)
          )
      // they accept
      _ <-
        gameService
          .acceptGameInvitation(game.id.get).provideSomeLayer[TestLayer](userLayer(user2))
      _ <-
        gameService
          .acceptGameInvitation(game.id.get).provideSomeLayer[TestLayer](userLayer(user3))
      _ <-
        gameService
          .acceptGameInvitation(game.id.get).provideSomeLayer[TestLayer](userLayer(user4))
      result <- gameOperations.get(game.id.get).provideSomeLayer[TestLayer](godLayer)
    } yield result.get
  }

  def canto(gameId: GameId): ZIO[TestLayer, Throwable, Game] = {
    val bot = DumbChutiBot
    for {
      gameService <- ZIO.service[GameService.Service]
      game        <- gameService.getGame(gameId).provideSomeLayer[TestLayer](godLayer).map(_.get)
      quienCanta = game.jugadores.find(_.turno).map(_.user).get
      sigiuente1 = game.nextPlayer(quienCanta).user
      sigiuente2 = game.nextPlayer(sigiuente1).user
      sigiuente3 = game.nextPlayer(sigiuente2).user
      // Una vez que alguien ya canto chuti, pasa a los demas.
      g1 <-
        bot
          .takeTurn(gameId).provideSomeLayer[TestLayer](
            SessionProvider.layer(ChutiSession(quienCanta))
          )
      g2 <-
        if (g1.jugadores.exists(_.cuantasCantas == Option(CuantasCantas.CantoTodas)))
          ZIO.succeed(g1)
        else {
          bot
            .takeTurn(gameId).provideSomeLayer[TestLayer](
              SessionProvider.layer(ChutiSession(sigiuente1))
            )
        }
      g3 <-
        if (g2.jugadores.exists(_.cuantasCantas == Option(CuantasCantas.CantoTodas)))
          ZIO.succeed(g2)
        else {
          bot
            .takeTurn(gameId).provideSomeLayer[TestLayer](
              SessionProvider.layer(ChutiSession(sigiuente2))
            )
        }
      g4 <-
        if (g3.jugadores.exists(_.cuantasCantas == Option(CuantasCantas.CantoTodas)))
          ZIO.succeed(g3)
        else {
          bot
            .takeTurn(gameId).provideSomeLayer[TestLayer](
              SessionProvider.layer(ChutiSession(sigiuente3))
            )
        }
    } yield g4
  }

  def playFullGame: ZIO[zio.ZEnv, Throwable, (Assertion, Game)] =
    (for {
      gameService <- ZIO.service[GameService.Service]
      start       <- newGame(satoshiPerPoint = 100)
      _           <- zio.console.putStrLn(s"Game ${start.id.get} started")
      assert1 <- {
        ZIO.succeed {
          assert(start.gameStatus === GameStatus.esperandoJugadoresInvitados)
          assert(start.currentEventIndex === 1)
          assert(start.id.nonEmpty)
          assert(start.jugadores.length == 1)
          assert(start.jugadores.head.user.id === user1.id)
        }
      }
      gameStream =
        gameService
          .gameStream(start.id.get, connectionId)
          .provideSomeLayer[TestLayer](SessionProvider.layer(ChutiSession(user1)))
      gameEventsFiber <-
        gameStream
          .takeUntil {
            case PoisonPill(Some(id), _) if id == start.id.get => true
            case _                                             => false
          }.runCollect.fork
      _           <- clock.sleep(1.second)
      readyToPlay <- getReadyToPlay(start.id.get)
      assert2 <- ZIO.succeed {
        assert(readyToPlay.gameStatus === GameStatus.cantando)
        assert(readyToPlay.currentEventIndex === 10)
        assert(readyToPlay.jugadores.length == 4)
        assert(readyToPlay.jugadores.forall(!_.invited))
      }
      played <- ZIO.iterate((Succeeded: Assertion, start))(
        _._2.gameStatus != GameStatus.partidoTerminado
      ) { case (previousAssert, looped) =>
        playRound(looped).map { case (newAssert, played) =>
          (assert(previousAssert == Succeeded && newAssert == Succeeded), played)
        }
      }
      _ <-
        gameService
          .broadcastGameEvent(PoisonPill(Option(start.id.get))).provideSomeLayer[
            TestLayer
          ](
            SessionProvider.layer(ChutiSession(chuti.god))
          )
      gameEvents <- gameEventsFiber.join
      finalAssert <- ZIO.succeed {
        assert(!gameEvents.exists(_.isInstanceOf[HoyoTecnico]))
        assert(gameEvents.nonEmpty)
      }
    } yield (
      assert(
        assert1 == Succeeded &&
          assert2 == Succeeded &&
          played._1 == Succeeded &&
          finalAssert == Succeeded
      ),
      played._2
    )).provideCustomLayer(testLayer())

  private def playRound(game: Game): ZIO[TestLayer, Throwable, (Assertion, Game)] = {
    for {
      gameService <- ZIO.service[GameService.Service]
      start <-
        if (game.gameStatus == GameStatus.requiereSopa) {
          gameService
            .play(game.id.get, Sopa()).provideSomeLayer[TestLayer](
              userLayer(game.jugadores.find(_.turno).get.user)
            )
        } else
          ZIO.succeed(game)
      afterCanto <- canto(start.id.get)
      assert1 <- ZIO.succeed {
        assert(afterCanto.gameStatus === GameStatus.jugando)
        assertSoloUnoCanta(afterCanto)
      }
      (assert2, round) <- juegaHastaElFinal(start.id.get)
    } yield (assert(assert1 == Succeeded && assert2 == Succeeded), round)
  }

  def playGame(
    gameToPlay: Game
  ): ZIO[TestLayer & Clock & Console, Throwable, (Assertion, Game)] =
    for {
      gameService    <- ZIO.service[GameService.Service]
      gameOperations <- ZIO.access[Repository](_.get.gameOperations)
      getGame <-
        ZIO
          .foreach(gameToPlay.id)(id => gameService.getGame(id)).provideSomeLayer[TestLayer](
            godLayer
          )
      game <-
        getGame.flatten
          .fold(gameOperations.upsert(gameToPlay))(g => ZIO.succeed(g)).provideSomeLayer[TestLayer](
            godLayer
          )
      _ <- zio.console.putStrLn(s"Game ${game.id.get} loaded")
      gameStream =
        gameService
          .gameStream(game.id.get, connectionId)
          .provideSomeLayer[TestLayer](SessionProvider.layer(ChutiSession(user1)))
      gameEventsFiber <-
        gameStream
          .takeUntil {
            case PoisonPill(Some(id), _) if id == game.id.get => true
            case _                                            => false
          }.runCollect.fork
      _                 <- clock.sleep(1.second)
      (assert4, played) <- juegaHastaElFinal(game.id.get)
      _ <-
        gameService
          .broadcastGameEvent(PoisonPill(Option(game.id.get))).provideSomeLayer[
            TestLayer
          ](
            SessionProvider.layer(ChutiSession(chuti.god))
          )
      gameEvents <- gameEventsFiber.join
      finalAssert <- ZIO.succeed {
        assert(!gameEvents.exists(_.isInstanceOf[HoyoTecnico]))
        assert(gameEvents.nonEmpty)
      }
    } yield (
      assert(
        assert4 == Succeeded &&
          finalAssert == Succeeded
      ),
      played
    )

  def playRound(filename: String): ZIO[zio.ZEnv, Throwable, (Assertion, Game)] =
    (for {
      gameService       <- ZIO.service[GameService.Service]
      game              <- gameService.getGame(GameId(1)).map(_.get).provideSomeLayer[TestLayer](godLayer)
      (asserts, played) <- juegaHastaElFinal(game.id.get)
    } yield (
      asserts,
      played
    )).provideCustomLayer(testLayer(filename))

}
