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
import chuti.Triunfo.{SinTriunfos, TriunfoNumero}
import dao.{DatabaseProvider, Repository, SessionProvider}
import game.GameService
import game.GameService.{GameLayer, GameService}
import game.LoggedInUserRepo.LoggedInUserRepo
import mail.Postman
import mail.Postman.Postman
import org.mockito.scalatest.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec
import zio._
import zio.duration._
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger

class JugandoSpec extends AnyFlatSpec with MockitoSugar with GameAbstractSpec {
  trait PlayerBot {
    def takeTurn(gameId: GameId): RIO[GameLayer with GameService, Game]
  }

  case object MostlyRandomPlayerBot extends PlayerBot {

    private def pideInicial(jugador: Jugador): PideInicial = {
      val mulas = jugador.fichas.filter(_.esMula)
      if (mulas.size >= 4) {
        val ficha = mulas.maxBy(_.arriba.value)
        PideInicial(ficha, SinTriunfos, estrictaDerecha = false)
      } else {
        val triunfo =
          jugador.fichas
            .foldLeft(Seq.empty[Numero])((seq, ficha) => (seq :+ ficha.arriba) :+ ficha.abajo)
            .groupBy(identity)
            .map {
              case (n, l) =>
                (n, l.size)
            }
            .maxBy(_._2)
            ._1
        val triunfos = jugador.fichas.filter(_.es(triunfo))
        PideInicial(
          triunfos.find(_.esMula).getOrElse(triunfos.maxBy(_.other(triunfo).value)),
          TriunfoNumero(triunfo),
          estrictaDerecha = false
        )
      }
    }

    private def pide(
      jugador: Jugador,
      game:    Game
    ): Pide = {
      game.triunfo match {
        case None => throw GameException("Should never happen!")
        case Some(SinTriunfos) =>
          Pide(
            jugador.fichas.maxBy(ficha => if (ficha.esMula) 100 else ficha.arriba.value),
            estrictaDerecha = false
          )
        case Some(TriunfoNumero(triunfo)) =>
          Pide(
            jugador.fichas.maxBy(ficha =>
              (if (ficha.es(triunfo)) 200 else if (ficha.esMula) 100 else 0) + ficha.arriba.value
            ),
            estrictaDerecha = false
          )
      }
    }

    private def da(
      jugador: Jugador,
      game:    Game
    ): Da = {
      val first = game.enJuego.head
      game.triunfo match {
        case None => throw GameException("Should never happen!")
        case Some(SinTriunfos) =>
          val pideNum = first._2.arriba
          Da(
            jugador.fichas
              .filter(_.es(pideNum))
              .sortBy(_.other(pideNum).value)
              .headOption
              .getOrElse(jugador.fichas.minBy(f => if (f.esMula) 100 else f.value))
          )
        case Some(TriunfoNumero(triunfo)) =>
          val pideNum = if (first._2.es(triunfo)) {
            triunfo
          } else {
            first._2.arriba
          }
          Da(
            jugador.fichas
              .filter(_.es(pideNum))
              .sortBy(_.other(pideNum).value)
              .headOption
              .getOrElse(
                jugador.fichas.minBy(f =>
                  if (f.es(triunfo)) {
                    triunfo.value - 100 - f.other(triunfo).value
                  } else {
                    if (f.esMula) 100 else f.value
                  }
                )
              )
          )
      }
    }

    def caite(): PlayEvent = Caite()

    override def takeTurn(gameId: GameId): RIO[GameLayer with GameService, Game] = {
      for {
        gameOperations <- ZIO.access[Repository](_.get.gameOperations)
        gameService    <- ZIO.access[GameService](_.get)
        user           <- ZIO.access[SessionProvider](_.get.session.user)
        game           <- gameOperations.get(gameId).map(_.get)
        played <- {
          val jugador = game.jugador(user.id)
          if (game.gameStatus != GameStatus.jugando) {
            ZIO.succeed(game)
          } else if (jugador.cantante && jugador.mano && jugador.filas.isEmpty) {
            gameService.play(gameId, pideInicial(jugador))
          } else if (jugador.mano && game.puedesCaerte(jugador)) {
            gameService.play(gameId, caite())
          } else if (jugador.mano) {
            gameService.play(gameId, pide(jugador, game))
          } else {
            gameService.play(gameId, da(jugador, game))
          }
        }
      } yield played
    }
  }

  def juegaHastaElFinal(gameId: GameId): RIO[LayerWithoutSession, Game] = {
    for {
      gameOperations <- ZIO.access[Repository](_.get.gameOperations)
      start <- gameOperations
        .get(gameId).map(_.get).provideSomeLayer[LayerWithoutSession](
          SessionProvider.layer(ChutiSession(GameService.god))
        )
      looped <- ZIO.iterate(start)(game => game.gameStatus == GameStatus.jugando)(_ =>
        juegaMano(gameId)
      )
    } yield looped
  }

  type LayerWithoutSession = DatabaseProvider
    with Repository with LoggedInUserRepo with Postman with Logging with TokenHolder
    with GameService

  def juegaMano(gameId: GameId): RIO[LayerWithoutSession, Game] = {
    val bot = MostlyRandomPlayerBot
    for {
      gameOperations <- ZIO.access[Repository](_.get.gameOperations)
      game <- gameOperations
        .get(gameId).map(_.get).provideSomeLayer[LayerWithoutSession](
          SessionProvider.layer(ChutiSession(GameService.god))
        )
      mano = game.mano
      sigiuente1 = game.nextPlayer(mano)
      sigiuente2 = game.nextPlayer(sigiuente1)
      sigiuente3 = game.nextPlayer(sigiuente2)
      _ <- bot
        .takeTurn(gameId).provideSomeLayer[LayerWithoutSession](
          SessionProvider.layer(ChutiSession(mano.user))
        )
      _ <- bot
        .takeTurn(gameId).provideSomeLayer[LayerWithoutSession](
          SessionProvider.layer(ChutiSession(sigiuente1.user))
        )
      _ <- bot
        .takeTurn(gameId).provideSomeLayer[LayerWithoutSession](
          SessionProvider.layer(ChutiSession(sigiuente2.user))
        )
      afterPlayer4 <- bot
        .takeTurn(gameId).provideSomeLayer[LayerWithoutSession](
          SessionProvider.layer(ChutiSession(sigiuente3.user))
        )
    } yield afterPlayer4
  }

  def repositoryLayer(gameFiles: String*): ULayer[Repository] = ZLayer.fromEffect {
    val z:  ZIO[Any, Throwable, List[Game]] = ZIO.foreach(gameFiles)(filename => readGame(filename))
    val zz: UIO[InMemoryRepository] = z.map(games => new InMemoryRepository(games)).orDie
    zz
  }

  final protected def testLayer: ULayer[LayerWithoutSession] = {
    val postman: Postman.Service = new MockPostman
    ZLayer.succeed(databaseProvider) ++
      repositoryLayer(GAME_CANTO4) ++
      ZLayer.succeed(loggedInUserRepo) ++
      ZLayer.succeed(postman) ++
      Slf4jLogger.make((_, b) => b) ++
      ZLayer.succeed(TokenHolder.live) ++
      GameService.make()
  }

  "primera mano" should "work" in {
    val gameId = GameId(1)

    val (
      game:       Game,
      gameEvents: List[GameEvent]
    ) =
      testRuntime.unsafeRun {
        (for {
          gameService <- ZIO.access[GameService](_.get)
          gameStream = gameService
            .gameStream(gameId)
            .provideSomeLayer[LayerWithoutSession](SessionProvider.layer(ChutiSession(user1)))
          gameEventsFiber <- gameStream
            .takeUntil {
              case PoisonPill(Some(id), _, _) if id == gameId => true
              case _                                          => false
            }.runCollect.fork
          _     <- clock.sleep(1.second)
          mano1 <- juegaMano(gameId)
          _ <- gameService
            .broadcastGameEvent(PoisonPill(Option(gameId))).provideSomeLayer[LayerWithoutSession](
              SessionProvider.layer(ChutiSession(GameService.god))
            )
          gameEvents <- gameEventsFiber.join
        } yield (mano1, gameEvents)).provideCustomLayer(testLayer)
      }

    assert(game.id === Option(gameId))
    assert(game.jugadores.count(_.fichas.size == 6) === 4) //Todos dieron una ficha.
    val ganador = game.jugadores.maxBy(_.filas.size)
    println(s"Gano ${ganador.user.name} con ${ganador.filas.last}!")
    assert(gameEvents.size === 5) //Including the poison pill
  }
  "jugando 4 manos" should "work" in {
    val gameId = GameId(1)

    val (
      game:       Game,
      gameEvents: List[GameEvent]
    ) =
      testRuntime.unsafeRun {
        (for {
          gameService <- ZIO.access[GameService](_.get)
          gameStream = gameService
            .gameStream(gameId)
            .provideSomeLayer[LayerWithoutSession](SessionProvider.layer(ChutiSession(user1)))
          gameEventsFiber <- gameStream
            .takeUntil {
              case PoisonPill(Some(id), _, _) if id == gameId => true
              case _                                          => false
            }.runCollect.fork
          _     <- clock.sleep(1.second)
          _     <- juegaMano(gameId)
          _     <- juegaMano(gameId)
          _     <- juegaMano(gameId)
          mano4 <- juegaMano(gameId)
          _ <- gameService
            .broadcastGameEvent(PoisonPill(Option(gameId))).provideSomeLayer[LayerWithoutSession](
              SessionProvider.layer(ChutiSession(GameService.god))
            )
          gameEvents <- gameEventsFiber.join
        } yield (mano4, gameEvents)).provideCustomLayer(testLayer)
      }

    assert(game.id === Option(gameId))
    val numFilas = game.jugadores.map(_.filas.size).sum
    assert(game.quienCanta.fichas.size === (numFilas - 7))
    if (game.quienCanta.yaSeHizo) {
      println(s"${game.quienCanta.user.name} se hizo con ${game.quienCanta.filas.size}!")
    } else {
      println(s"Fue hoyo para ${game.quienCanta.user.name}!")
    }
    assert(gameEvents.nonEmpty)
  }
  "jugando hasta que se haga o sea hoyo" should "work" in {
    val gameId = GameId(1)
    val (
      game:       Game,
      gameEvents: List[GameEvent]
    ) =
      testRuntime.unsafeRun {
        (for {
          gameService <- ZIO.access[GameService](_.get)
          gameStream = gameService
            .gameStream(gameId)
            .provideSomeLayer[LayerWithoutSession](SessionProvider.layer(ChutiSession(user1)))
          gameEventsFiber <- gameStream
            .takeWhile {
              case PoisonPill(Some(id), _, _) if id == gameId => false
              case _                                          => true
            }.runCollect.fork
          _   <- clock.sleep(1.second)
          end <- juegaHastaElFinal(gameId)
          _ <- gameService
            .broadcastGameEvent(PoisonPill(Option(gameId))).provideSomeLayer[LayerWithoutSession](
              SessionProvider.layer(ChutiSession(GameService.god))
            )
          gameEvents      <- gameEventsFiber.join
        } yield (end, gameEvents)).provideCustomLayer(testLayer)
      }

    assert(game.id === Option(gameId))
    assert(game.gameStatus === GameStatus.terminado)
    val ganador = game.jugadores.maxBy(_.filas.size)
    println(s"Gano ${ganador.user.name} con ${ganador.filas.size}!")
    assert(gameEvents.nonEmpty)
  }
}
