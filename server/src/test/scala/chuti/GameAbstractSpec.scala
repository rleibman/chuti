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
import api.token.{TokenHolder, TokenPurpose}
import better.files.File
import chat.ChatService
import chuti.bots.DumbChutiBot
import dao.InMemoryRepository.{now, user1, user2, user3, user4}
import dao.Repository.GameOperations
import dao.{InMemoryRepository, Repository, RepositoryIO, SessionContext}
import game.GameService
import io.circe.Printer
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import mail.Postman
import org.scalatest.Assertion
import org.scalatest.Assertions.*
import zio.*
import zio.cache.{Cache, Lookup}
import zio.logging.*
import zio.test.{TestResult, assertTrue}

import java.time.Instant
import java.util.UUID

trait GameAbstractSpec {

  import CuantasCantas.*

  val connectionId: ConnectionId = ConnectionId(5)

  lazy final val GAME_NEW = "/Volumes/Personal/projects/chuti/server/src/test/resources/newGame.json"
  lazy final val GAME_STARTED = "/Volumes/Personal/projects/chuti/server/src/test/resources/startedGame.json"
  lazy final val GAME_WITH_2USERS = "/Volumes/Personal/projects/chuti/server/src/test/resources/with2Users.json"
  lazy final val GAME_CANTO4 = "/Volumes/Personal/projects/chuti/server/src/test/resources/canto4.json"

  def WEIRD_GAME(prefix: String) =
    s"/Volumes/Personal/projects/chuti/server/src/test/resources/$prefix${java.lang.System
        .currentTimeMillis() / 1000}.json"

  def writeGame(
    game:     Game,
    filename: String
  ): Task[Unit] =
    ZIO.attempt {
      val file = File(filename)
      file.write(game.asJson.printWith(Printer.spaces2))
    }

  def readGame(filename: String): Task[Game] = {
    ZIO.attempt {
      import scala.language.unsafeNulls
      val file = File(filename)
      decode[Game](file.contentAsString.nn)
    }.absolve
  }

  def assertSoloUnoCanta(game: Game): TestResult = {
    assertTrue(game.jugadores.count(_.cantante) === 1) &&
    assertTrue(game.jugadores.count(_.mano) === 1) &&
    assertTrue(game.jugadores.count(_.turno) === 1) &&
    assertTrue(game.jugadores.count(j => j.cuantasCantas.fold(false)(_ != Buenas)) === 1) &&
    assertTrue(
      game.jugadores
        .find(_.cantante).fold(false)(
          _.cuantasCantas.fold(false)(c => c.prioridad > CuantasCantas.Buenas.prioridad)
        )
    )
  }

  def repositoryLayer(gameFiles: String*): ULayer[Repository] =
    ZLayer.fromZIO {
      ZIO
        .foreachPar(gameFiles)(filename => readGame(filename))
        .map(games => InMemoryRepository.fromGames(games))
        .orDie
    }.flatten

  def juegaHastaElFinal(gameId: GameId): RIO[Repository & Postman & TokenHolder & GameService & ChatService, Game] = {
    for {
      gameOperations <- ZIO.service[Repository].map(_.gameOperations)
      start <-
        gameOperations
          .get(gameId).map(_.get).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](
            SessionContext.live(ChutiSession(chuti.god))
          )
      looped <-
        ZIO.iterate(start)(game => game.gameStatus == GameStatus.jugando)(_ => juegaMano(gameId))
      _ <- ZIO.succeed {
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

  def juegaMano(gameId: GameId): RIO[Repository & Postman & TokenHolder & GameService & ChatService, Game] = {
    val bot = DumbChutiBot
    for {
      gameOperations <- ZIO.service[Repository].map(_.gameOperations)
      game <-
        gameOperations
          .get(gameId).map(_.get).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](
            SessionContext.live(ChutiSession(chuti.god))
          )
      mano = game.mano.get
      sigiuente1 = game.nextPlayer(mano)
      sigiuente2 = game.nextPlayer(sigiuente1)
      sigiuente3 = game.nextPlayer(sigiuente2)
      _ <-
        bot
          .takeTurn(gameId).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](
            SessionContext.live(ChutiSession(mano.user))
          )
      _ <-
        bot
          .takeTurn(gameId).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](
            SessionContext.live(ChutiSession(sigiuente1.user))
          )
      _ <-
        bot
          .takeTurn(gameId).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](
            SessionContext.live(ChutiSession(sigiuente2.user))
          )
      afterPlayer4 <-
        bot
          .takeTurn(gameId).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](
            SessionContext.live(ChutiSession(sigiuente3.user))
          )
    } yield afterPlayer4
  }

  final protected def testLayer(gameFiles: String*): ULayer[Repository & Postman & TokenHolder & GameService & ChatService] =
    ZLayer.make[Repository & Postman & TokenHolder & GameService & ChatService](
      repositoryLayer(gameFiles: _*),
      ZLayer.succeed(new MockPostman),
      ZLayer.fromZIO(for {
        cache <- Cache.make[(String, TokenPurpose), Any, Nothing, User](100, 5.days, Lookup(_ => ZIO.succeed(chuti.god)))
      } yield TokenHolder.tempCache(cache)),
      GameService.make(),
      ChatService.make()
    )

  protected def userLayer(user: User): ULayer[SessionContext] = {
    SessionContext.live(ChutiSession(user))
  }
  protected def godLayer: ULayer[SessionContext] = {
    SessionContext.live(ChutiSession(chuti.god))
  }

  def newGame(satoshiPerPoint: Int): RIO[Repository & Postman & TokenHolder & GameService & ChatService, Game] =
    for {
      gameService <- ZIO.service[GameService]
      // Start the game
      game <- gameService.newGame(satoshiPerPoint).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](userLayer(user1))
    } yield game

  def getReadyToPlay(gameId: GameId): ZIO[Repository & Postman & TokenHolder & GameService & ChatService, Throwable, Game] = {
    for {
      gameOperations <- ZIO.service[Repository].map(_.gameOperations)
      gameService    <- ZIO.service[GameService]
      game <- gameOperations.get(gameId).map(_.get).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](godLayer)
      // Invite people
      _ <-
        gameService
          .inviteToGame(user2.id.get, game.id.get).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](
            userLayer(user1)
          )
      _ <-
        gameService
          .inviteToGame(user3.id.get, game.id.get).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](
            userLayer(user1)
          )
      _ <-
        gameService
          .inviteToGame(user4.id.get, game.id.get).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](
            userLayer(user1)
          )
      // they accept
      _ <-
        gameService
          .acceptGameInvitation(game.id.get).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](userLayer(user2))
      _ <-
        gameService
          .acceptGameInvitation(game.id.get).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](userLayer(user3))
      _ <-
        gameService
          .acceptGameInvitation(game.id.get).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](userLayer(user4))
      result <- gameOperations.get(game.id.get).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](godLayer)
    } yield result.get
  }

  def canto(gameId: GameId): ZIO[Repository & Postman & TokenHolder & GameService & ChatService, Throwable, Game] = {
    val bot = DumbChutiBot
    for {
      gameService <- ZIO.service[GameService]
      game        <- gameService.getGame(gameId).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](godLayer).map(_.get)
      quienCanta = game.jugadores.find(_.turno).map(_.user).get
      sigiuente1 = game.nextPlayer(quienCanta).user
      sigiuente2 = game.nextPlayer(sigiuente1).user
      sigiuente3 = game.nextPlayer(sigiuente2).user
      // Una vez que alguien ya canto chuti, pasa a los demas.
      g1 <-
        bot
          .takeTurn(gameId).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](
            SessionContext.live(ChutiSession(quienCanta))
          )
      g2 <-
        if (g1.jugadores.exists(_.cuantasCantas == Option(CuantasCantas.CantoTodas)))
          ZIO.succeed(g1)
        else {
          bot
            .takeTurn(gameId).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](
              SessionContext.live(ChutiSession(sigiuente1))
            )
        }
      g3 <-
        if (g2.jugadores.exists(_.cuantasCantas == Option(CuantasCantas.CantoTodas)))
          ZIO.succeed(g2)
        else {
          bot
            .takeTurn(gameId).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](
              SessionContext.live(ChutiSession(sigiuente2))
            )
        }
      g4 <-
        if (g3.jugadores.exists(_.cuantasCantas == Option(CuantasCantas.CantoTodas)))
          ZIO.succeed(g3)
        else {
          bot
            .takeTurn(gameId).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](
              SessionContext.live(ChutiSession(sigiuente3))
            )
        }
    } yield g4
  }

  def playFullGame: ZIO[Any, Throwable, Game] =
    (for {
      gameService <- ZIO.service[GameService]
      start       <- newGame(satoshiPerPoint = 100)
      _           <- zio.Console.printLine(s"Game ${start.id.get} started")
      _ <- ZIO.succeed {
        assert(start.gameStatus == GameStatus.esperandoJugadoresInvitados)
        assert(start.currentEventIndex == 1)
        assert(start.id.nonEmpty)
        assert(start.jugadores.length == 1)
        assert(start.jugadores.head.user.id == user1.id)
      }
      gameStream =
        gameService
          .gameStream(start.id.get, connectionId)
          .provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](SessionContext.live(ChutiSession(user1)))
      gameEventsFiber <-
        gameStream
          .takeUntil {
            case PoisonPill(Some(id), _) if id == start.id.get => true
            case _                                             => false
          }.runCollect.fork
      _           <- Clock.sleep(1.second)
      readyToPlay <- getReadyToPlay(start.id.get)
      _ <- ZIO.succeed {
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
            Repository & Postman & TokenHolder & GameService & ChatService
          ](
            SessionContext.live(ChutiSession(chuti.god))
          )
      gameEvents <- gameEventsFiber.join
      _ <- ZIO.succeed {
        assert(!gameEvents.exists(_.isInstanceOf[HoyoTecnico]))
        assert(gameEvents.nonEmpty)
      }
    } yield played).provideLayer(testLayer())

  private def playRound(game: Game): ZIO[Repository & Postman & TokenHolder & GameService & ChatService, Throwable, Game] = {
    for {
      gameService <- ZIO.service[GameService]
      start <-
        if (game.gameStatus == GameStatus.requiereSopa) {
          gameService
            .play(game.id.get, Sopa()).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](
              userLayer(game.jugadores.find(_.turno).get.user)
            )
        } else
          ZIO.succeed(game)
      afterCanto <- canto(start.id.get)
      _ <- ZIO.succeed {
        assert(afterCanto.gameStatus == GameStatus.jugando)
        assertSoloUnoCanta(afterCanto)
      }
      round <- juegaHastaElFinal(start.id.get)
    } yield round
  }

  def playGame(
    gameToPlay: Game
  ): ZIO[Repository & Postman & TokenHolder & GameService & ChatService, Throwable, Game] =
    for {
      gameService    <- ZIO.service[GameService]
      gameOperations <- ZIO.service[Repository].map(_.gameOperations)
      getGame <-
        ZIO
          .foreach(gameToPlay.id)(id => gameService.getGame(id)).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](
            godLayer
          )
      game <-
        getGame.flatten
          .fold(gameOperations.upsert(gameToPlay))(g => ZIO.succeed(g)).provideSomeLayer[
            Repository & Postman & TokenHolder & GameService & ChatService
          ](
            godLayer
          )
      _ <- zio.Console.printLine(s"Game ${game.id.get} loaded")
      gameStream =
        gameService
          .gameStream(game.id.get, connectionId)
          .provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](SessionContext.live(ChutiSession(user1)))
      gameEventsFiber <-
        gameStream
          .takeUntil {
            case PoisonPill(Some(id), _) if id == game.id.get => true
            case _                                            => false
          }.runCollect.fork
      _      <- Clock.sleep(1.second)
      played <- juegaHastaElFinal(game.id.get)
      _ <-
        gameService
          .broadcastGameEvent(PoisonPill(Option(game.id.get))).provideSomeLayer[
            Repository & Postman & TokenHolder & GameService & ChatService
          ](
            SessionContext.live(ChutiSession(chuti.god))
          )
      gameEvents <- gameEventsFiber.join
      _ <- ZIO.succeed {
        assert(!gameEvents.exists(_.isInstanceOf[HoyoTecnico]))
        assert(gameEvents.nonEmpty)
      }
    } yield played

  def playRound(filename: String): ZIO[Any, Throwable, Game] =
    (for {
      gameService <- ZIO.service[GameService]
      game   <- gameService.getGame(GameId(1)).map(_.get).provideSomeLayer[Repository & Postman & TokenHolder & GameService & ChatService](godLayer)
      played <- juegaHastaElFinal(game.id.get)
    } yield played).provideLayer(testLayer(filename))

}
