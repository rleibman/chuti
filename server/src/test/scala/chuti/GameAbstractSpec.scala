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

import api.token.{TokenHolder, TokenPurpose}
import api.{ChutiEnvironment, ChutiSession, toLayer}
import better.files.File
import chat.ChatService
import chuti.CuantasCantas.Buenas
import chuti.bots.DumbChutiBot
import dao.InMemoryRepository.*
import dao.{GameOperations, InMemoryRepository, RepositoryIO, ZIORepository}
import game.GameService
import mail.Postman
import org.scalatest.Assertion
import org.scalatest.Assertions.*
import zio.*
import zio.cache.{Cache, Lookup}
import zio.json.*
import zio.logging.*
import zio.test.{TestResult, assertTrue}

import java.time.Instant
import java.util.UUID

trait GameAbstractSpec {

  val connectionId: ConnectionId = ConnectionId("5")

  val GAME_NEW = "/Volumes/Personal/projects/chuti/server/src/test/resources/newGame.json"
  val GAME_STARTED = "/Volumes/Personal/projects/chuti/server/src/test/resources/startedGame.json"
  val GAME_WITH_2USERS =
    "/Volumes/Personal/projects/chuti/server/src/test/resources/with2Users.json"
  val GAME_CANTO4 = "/Volumes/Personal/projects/chuti/server/src/test/resources/canto4.json"

  def writeGame(
    game:     Game,
    filename: String
  ): Task[Unit] =
    ZIO.succeed {
      val file = File(filename)
      file.write(game.toJson)
    }

  def readGame(filename: String): Task[Game] = {
    val file = File(filename)
    ZIO.fromEither(file.contentAsString.fromJson[Game]).mapError(e => GameError(e))
  }

  def juegaHastaElFinal(gameId: GameId): ZIO[ChutiEnvironment & ChatService & GameService, Exception, Game] = {
    for {
      gameOperations <- ZIO.serviceWith[ZIORepository](_.gameOperations)
      start <-
        gameOperations
          .get(gameId).map(_.get).provideSomeLayer[ChutiEnvironment & ChatService & GameService](
            ChutiSession(chuti.god).toLayer
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

  def juegaMano(gameId: GameId): ZIO[ChutiEnvironment & ChatService & GameService, Exception, Game] = {
    val bot = DumbChutiBot
    for {
      gameOperations <- ZIO.serviceWith[ZIORepository](_.gameOperations)
      game <-
        gameOperations
          .get(gameId).map(_.get).provideSomeLayer[ChutiEnvironment & ChatService & GameService](
            ChutiSession(chuti.god).toLayer
          )
      mano = game.mano.get
      sigiuente1 = game.nextPlayer(mano)
      sigiuente2 = game.nextPlayer(sigiuente1)
      sigiuente3 = game.nextPlayer(sigiuente2)
      _ <-
        bot
          .takeTurn(gameId).provideSomeLayer[ChutiEnvironment & ChatService & GameService](
            ChutiSession(mano.user).toLayer
          )
      _ <-
        bot
          .takeTurn(gameId).provideSomeLayer[ChutiEnvironment & ChatService & GameService](
            ChutiSession(sigiuente1.user).toLayer
          )
      _ <-
        bot
          .takeTurn(gameId).provideSomeLayer[ChutiEnvironment & ChatService & GameService](
            ChutiSession(sigiuente2.user).toLayer
          )
      afterPlayer4 <-
        bot
          .takeTurn(gameId).provideSomeLayer[ChutiEnvironment & ChatService & GameService](
            ChutiSession(sigiuente3.user).toLayer
          )
    } yield afterPlayer4
  }

  def WEIRD_GAME(prefix: String) =
    s"/Volumes/Personal/projects/chuti/server/src/test/resources/$prefix${java.lang.System
        .currentTimeMillis() / 1000}.json"

  protected def userLayer(user: User): ULayer[ChutiSession] = {
    ChutiSession(user).toLayer
  }
  protected def godLayer: ULayer[ChutiSession] = {
    ChutiSession(chuti.god).toLayer
  }

  def assertSoloUnoCanta(game: Game): TestResult = {
    val cantante = game.jugadores.find(_.cantante).get
    assertTrue(
      game.jugadores.count(_.cantante) == 1,
      game.jugadores.count(_.mano) == 1,
      game.jugadores.count(_.turno) == 1,
      game.jugadores.count(j => j.cuantasCantas.fold(false)(_ != Buenas)) == 1,
      cantante.mano,
      cantante.cuantasCantas.fold(false)(c => c.prioridad > CuantasCantas.Buenas.prioridad)
    )
  }

  def newGame(satoshiPerPoint: Long): ZIO[ChutiEnvironment & GameService & ChatService, GameError, Game] =
    ZIO.serviceWithZIO[GameService](
      _.newGame(satoshiPerPoint).provideSomeLayer[ChutiEnvironment & GameService & ChatService](userLayer(user1))
    )

  def getReadyToPlay(gameId: GameId): ZIO[ChutiEnvironment & GameService & ChatService, Throwable, Game] = {
    for {
      gameOperations <- ZIO.service[ZIORepository].map(_.gameOperations)
      gameService    <- ZIO.service[GameService]
      game <- gameOperations
        .get(gameId).map(_.get).provideSomeLayer[ChutiEnvironment & GameService & ChatService](godLayer)
      // Invite people
      _ <-
        gameService
          .inviteToGame(user2.id.get, game.id.get).provideSomeLayer[ChutiEnvironment & GameService & ChatService](
            userLayer(user1)
          )
      _ <-
        gameService
          .inviteToGame(user3.id.get, game.id.get).provideSomeLayer[ChutiEnvironment & GameService & ChatService](
            userLayer(user1)
          )
      _ <-
        gameService
          .inviteToGame(user4.id.get, game.id.get).provideSomeLayer[ChutiEnvironment & GameService & ChatService](
            userLayer(user1)
          )
      // they accept
      _ <-
        gameService
          .acceptGameInvitation(game.id.get).provideSomeLayer[ChutiEnvironment & GameService & ChatService](
            userLayer(user2)
          )
      _ <-
        gameService
          .acceptGameInvitation(game.id.get).provideSomeLayer[ChutiEnvironment & GameService & ChatService](
            userLayer(user3)
          )
      _ <-
        gameService
          .acceptGameInvitation(game.id.get).provideSomeLayer[ChutiEnvironment & GameService & ChatService](
            userLayer(user4)
          )
      result <- gameOperations.get(game.id.get).provideSomeLayer[ChutiEnvironment & GameService & ChatService](godLayer)
    } yield result.get
  }

  def canto(gameId: GameId): ZIO[ChutiEnvironment & GameService & ChatService, Throwable, Game] = {
    val bot = DumbChutiBot
    for {
      gameService <- ZIO.service[GameService]
      game <- gameService
        .getGame(gameId).provideSomeLayer[ChutiEnvironment & GameService & ChatService](godLayer).map(_.get)
      quienCanta = game.jugadores.find(_.turno).map(_.user).get
      sigiuente1 = game.nextPlayer(quienCanta).user
      sigiuente2 = game.nextPlayer(sigiuente1).user
      sigiuente3 = game.nextPlayer(sigiuente2).user
      // Una vez que alguien ya canto chuti, pasa a los demas.
      g1 <-
        bot
          .takeTurn(gameId).provideSomeLayer[ChutiEnvironment & GameService & ChatService](
            ChutiSession(quienCanta).toLayer
          )
      g2 <-
        if (g1.jugadores.exists(_.cuantasCantas == Option(CuantasCantas.CantoTodas)))
          ZIO.succeed(g1)
        else {
          bot
            .takeTurn(gameId).provideSomeLayer[ChutiEnvironment & GameService & ChatService](
              ChutiSession(sigiuente1).toLayer
            )
        }
      g3 <-
        if (g2.jugadores.exists(_.cuantasCantas == Option(CuantasCantas.CantoTodas)))
          ZIO.succeed(g2)
        else {
          bot
            .takeTurn(gameId).provideSomeLayer[ChutiEnvironment & GameService & ChatService](
              ChutiSession(sigiuente2).toLayer
            )
        }
      g4 <-
        if (g3.jugadores.exists(_.cuantasCantas == Option(CuantasCantas.CantoTodas)))
          ZIO.succeed(g3)
        else {
          bot
            .takeTurn(gameId).provideSomeLayer[ChutiEnvironment & GameService & ChatService](
              ChutiSession(sigiuente3).toLayer
            )
        }
    } yield g4
  }

  def playFullGame =
    for {
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
          .provideSomeLayer[ChutiEnvironment & GameService & ChatService](ChutiSession(user1).toLayer)
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
            ChutiEnvironment & GameService & ChatService
          ](
            ChutiSession(chuti.god).toLayer
          )
      gameEvents <- gameEventsFiber.join
      _ <- ZIO.succeed {
        assert(!gameEvents.exists(_.isInstanceOf[HoyoTecnico]))
        assert(gameEvents.nonEmpty)
      }
    } yield played

  private def playRound(game: Game): ZIO[ChutiEnvironment & GameService & ChatService, Throwable, Game] = {
    for {
      gameService <- ZIO.service[GameService]
      start <-
        if (game.gameStatus == GameStatus.requiereSopa) {
          gameService
            .play(game.id.get, Sopa()).provideSomeLayer[ChutiEnvironment & GameService & ChatService](
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
  ): ZIO[ChutiEnvironment & GameService & ChatService, Throwable, Game] =
    for {
      gameService    <- ZIO.service[GameService]
      gameOperations <- ZIO.serviceWith[ZIORepository](_.gameOperations)
      getGame <-
        ZIO
          .foreach(gameToPlay.id)(id => gameService.getGame(id)).provideSomeLayer[
            ChutiEnvironment & GameService & ChatService
          ](
            godLayer
          )
      game <-
        getGame.flatten
          .fold(gameOperations.upsert(gameToPlay))(g => ZIO.succeed(g)).provideSomeLayer[
            ChutiEnvironment & GameService & ChatService
          ](
            godLayer
          )
      _ <- zio.Console.printLine(s"Game ${game.id.get} loaded")
      gameStream =
        gameService
          .gameStream(game.id.get, connectionId)
          .provideSomeLayer[ChutiEnvironment & GameService & ChatService](ChutiSession(user1).toLayer)
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
            ChutiEnvironment & GameService & ChatService
          ](
            ChutiSession(chuti.god).toLayer
          )
      gameEvents <- gameEventsFiber.join
      _ <- ZIO.succeed {
        assert(!gameEvents.exists(_.isInstanceOf[HoyoTecnico]))
        assert(gameEvents.nonEmpty)
      }
    } yield played

  def playRound(filename: String): ZIO[ChutiEnvironment & GameService & ChatService, Exception, Game] =
    for {
      gameService <- ZIO.service[GameService]
      game <- gameService
        .getGame(GameId(1)).map(_.get).provideSomeLayer[ChutiEnvironment & GameService & ChatService](godLayer)
      played <- juegaHastaElFinal(game.id.get)
    } yield played

}
