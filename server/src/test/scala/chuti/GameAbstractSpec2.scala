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
import api.token.{Token, TokenHolder, TokenPurpose}
import better.files.File
import chat.ChatService
import chat.ChatService.ChatService
import chuti.CuantasCantas.Buenas
import chuti.bots.DumbChutiBot
import dao.InMemoryRepository.{user1, user2, user3, user4}
import dao.{InMemoryRepository, Repository, SessionContext}
import game.GameService
import game.GameService.GameService
import io.circe.Printer
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import mail.Postman
import mail.Postman.Postman
import org.scalatest.Assertions.*
import org.scalatest.{Assertion, Succeeded}
import zio.*
import zio.cache.{Cache, Lookup}
import zio.clock.Clock
import zio.console.Console
import zio.duration.*
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger

import java.util.{Random, UUID}

trait GameAbstractSpec2 {

  val connectionId:               ConnectionId = ConnectionId(new Random().nextInt())
  lazy protected val testRuntime: zio.Runtime[zio.ZEnv] = zio.Runtime.default

  def juegaHastaElFinal(gameId: GameId): RIO[TestLayer, Game] = {
    for {
      gameOperations <- ZIO.access[Repository](_.get.gameOperations)
      start <-
        gameOperations
          .get(gameId).map(_.get).provideSomeLayer[TestLayer](
            SessionContext.live(ChutiSession(chuti.god))
          )
      looped <-
        ZIO.iterate(start)(game => game.gameStatus == GameStatus.jugando)(_ => juegaMano(gameId))
      _ <- ZIO {
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
            assert(cantante.fichas.size == (numFilas - 7))
          } else {
            println(
              s"Fue hoyo de ${cantante.cuantasCantas.get} para ${cantante.user.name}!"
            )
          }
          if (looped.gameStatus == GameStatus.partidoTerminado)
            println("Partido terminado!")
          assert(
            looped.gameStatus == GameStatus.requiereSopa || looped.gameStatus == GameStatus.partidoTerminado
          )
          // May want to assert Que las cuentas quedaron claras
        }
      }
    } yield looped
  }

  def juegaMano(gameId: GameId): RIO[TestLayer, Game] = {
    val bot = DumbChutiBot
    for {
      gameOperations <- ZIO.access[Repository](_.get.gameOperations)
      game <-
        gameOperations
          .get(gameId).map(_.get).provideSomeLayer[TestLayer](
            SessionContext.live(ChutiSession(chuti.god))
          )
      mano = game.mano.get
      sigiuente1 = game.nextPlayer(mano)
      sigiuente2 = game.nextPlayer(sigiuente1)
      sigiuente3 = game.nextPlayer(sigiuente2)
      _ <-
        bot
          .takeTurn(gameId).provideSomeLayer[TestLayer](
            SessionContext.live(ChutiSession(mano.user))
          )
      _ <-
        bot
          .takeTurn(gameId).provideSomeLayer[TestLayer](
            SessionContext.live(ChutiSession(sigiuente1.user))
          )
      _ <-
        bot
          .takeTurn(gameId).provideSomeLayer[TestLayer](
            SessionContext.live(ChutiSession(sigiuente2.user))
          )
      afterPlayer4 <-
        bot
          .takeTurn(gameId).provideSomeLayer[TestLayer](
            SessionContext.live(ChutiSession(sigiuente3.user))
          )
    } yield afterPlayer4
  }

  def repositoryLayer(gameFiles: String*): ULayer[Repository] = {
    val z: ZIO[Any, Throwable, Seq[Game]] =
      ZIO.foreachPar(gameFiles)(filename => readGame(filename))
    val zz: UIO[InMemoryRepository] = z.map(games => new InMemoryRepository(games)).orDie
    zz
  }.toLayer

  type TestLayer = Repository & Postman & Logging & TokenHolder & GameService & ChatService & Clock

  final protected def testLayer(gameFiles: String*): ULayer[TestLayer] = {
    val postman: Postman.Service = new MockPostman
    val loggingLayer = Slf4jLogger.make((_, b) => b)
    repositoryLayer(gameFiles: _*) ++
      ZLayer.succeed(postman) ++
      loggingLayer ++
      (for {
        cache <- Cache.make[(String, TokenPurpose), Any, Nothing, User](100, 5.days, Lookup(_ => ZIO.succeed(chuti.god)))
      } yield TokenHolder.tempCache(cache)).toLayer ++
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

  protected def userLayer(user: User): ULayer[SessionContext] = {
    SessionContext.live(ChutiSession(user))
  }
  protected def godLayer: ULayer[SessionContext] = {
    SessionContext.live(ChutiSession(chuti.god))
  }

  def assertSoloUnoCanta(game: Game): Assertion = {
    assert(game.jugadores.count(_.cantante) == 1)
    assert(game.jugadores.count(_.mano) == 1)
    assert(game.jugadores.count(_.turno) == 1)
    assert(game.jugadores.count(j => j.cuantasCantas.fold(false)(_ != Buenas)) == 1)
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
            SessionContext.live(ChutiSession(quienCanta))
          )
      g2 <-
        if (g1.jugadores.exists(_.cuantasCantas == Option(CuantasCantas.CantoTodas)))
          ZIO.succeed(g1)
        else {
          bot
            .takeTurn(gameId).provideSomeLayer[TestLayer](
              SessionContext.live(ChutiSession(sigiuente1))
            )
        }
      g3 <-
        if (g2.jugadores.exists(_.cuantasCantas == Option(CuantasCantas.CantoTodas)))
          ZIO.succeed(g2)
        else {
          bot
            .takeTurn(gameId).provideSomeLayer[TestLayer](
              SessionContext.live(ChutiSession(sigiuente2))
            )
        }
      g4 <-
        if (g3.jugadores.exists(_.cuantasCantas == Option(CuantasCantas.CantoTodas)))
          ZIO.succeed(g3)
        else {
          bot
            .takeTurn(gameId).provideSomeLayer[TestLayer](
              SessionContext.live(ChutiSession(sigiuente3))
            )
        }
    } yield g4
  }

  def playFullGame: ZIO[zio.ZEnv, Throwable, Game] =
    (for {
      gameService <- ZIO.service[GameService.Service]
      start       <- newGame(satoshiPerPoint = 100)
      _           <- zio.console.putStrLn(s"Game ${start.id.get} started")
      _ <- ZIO {
        assert(start.gameStatus == GameStatus.esperandoJugadoresInvitados)
        assert(start.currentEventIndex == 1)
        assert(start.id.nonEmpty)
        assert(start.jugadores.length == 1)
        assert(start.jugadores.head.user.id == user1.id)
      }
      gameStream =
        gameService
          .gameStream(start.id.get, connectionId)
          .provideSomeLayer[TestLayer](SessionContext.live(ChutiSession(user1)))
      gameEventsFiber <-
        gameStream
          .takeUntil {
            case PoisonPill(Some(id), _) if id == start.id.get => true
            case _                                             => false
          }.runCollect.fork
      _           <- clock.sleep(1.second)
      readyToPlay <- getReadyToPlay(start.id.get)
      _ <- ZIO {
        assert(readyToPlay.gameStatus == GameStatus.cantando)
        assert(readyToPlay.currentEventIndex == 10)
        assert(readyToPlay.jugadores.length == 4)
        assert(readyToPlay.jugadores.forall(!_.invited))
      }
      played <- ZIO.iterate(start)(
        _.gameStatus != GameStatus.partidoTerminado
      )(playRound)
      _ <-
        gameService
          .broadcastGameEvent(PoisonPill(Option(start.id.get))).provideSomeLayer[
            TestLayer
          ](
            SessionContext.live(ChutiSession(chuti.god))
          )
      gameEvents <- gameEventsFiber.join
      _ <- ZIO {
        assert(!gameEvents.exists(_.isInstanceOf[HoyoTecnico]))
        assert(gameEvents.nonEmpty)
      }
    } yield played).provideCustomLayer(testLayer())

  private def playRound(game: Game): ZIO[TestLayer, Throwable, Game] = {
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
      _ <- ZIO {
        assert(afterCanto.gameStatus == GameStatus.jugando)
        assertSoloUnoCanta(afterCanto)
      }
      round <- juegaHastaElFinal(start.id.get)
    } yield round
  }

  def playGame(
    gameToPlay: Game
  ): ZIO[TestLayer & Clock & Console, Throwable, Game] =
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
          .provideSomeLayer[TestLayer](SessionContext.live(ChutiSession(user1)))
      gameEventsFiber <-
        gameStream
          .takeUntil {
            case PoisonPill(Some(id), _) if id == game.id.get => true
            case _                                            => false
          }.runCollect.fork
      _      <- clock.sleep(1.second)
      played <- juegaHastaElFinal(game.id.get)
      _ <-
        gameService
          .broadcastGameEvent(PoisonPill(Option(game.id.get))).provideSomeLayer[
            TestLayer
          ](
            SessionContext.live(ChutiSession(chuti.god))
          )
      gameEvents <- gameEventsFiber.join
      _ <- ZIO {
        assert(!gameEvents.exists(_.isInstanceOf[HoyoTecnico]))
        assert(gameEvents.nonEmpty)
      }
    } yield played

  def playRound(filename: String): ZIO[zio.ZEnv, Throwable, Game] =
    (for {
      gameService <- ZIO.service[GameService.Service]
      game        <- gameService.getGame(GameId(1)).map(_.get).provideSomeLayer[TestLayer](godLayer)
      played      <- juegaHastaElFinal(game.id.get)
    } yield played).provideCustomLayer(testLayer(filename))

}
