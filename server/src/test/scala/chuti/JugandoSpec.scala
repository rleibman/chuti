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
import dao.{DatabaseProvider, Repository, RepositoryIO, SessionProvider}
import game.GameService
import game.GameService.GameService
import game.LoggedInUserRepo.LoggedInUserRepo
import mail.Postman.Postman
import org.mockito.scalatest.MockitoSugar
import org.mockito.stubbing.ScalaOngoingStubbing
import org.scalatest.flatspec.AnyFlatSpec
import zio._
import zio.duration._
import zio.logging.Logging

class JugandoSpec extends AnyFlatSpec with MockitoSugar with GameAbstractSpec {
  trait PlayerBot {
    def takeTurn(
      asPlayer: Jugador,
      game:     Game
    ): PlayEvent
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
        case None => throw GameException("WTF?")
      }
    }

    private def da(
      jugador: Jugador,
      game:    Game
    ): Da = {
      val first = game.enJuego.head
      game.triunfo match {
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
        case None => throw GameException("what's up with that?")
      }
    }

    override def takeTurn(
      jugador: Jugador,
      game:    Game
    ): PlayEvent = {
      if (jugador.cantante && jugador.filas.isEmpty) {
        pideInicial(jugador)
      } else if (jugador.mano) {
        pide(jugador, game)
      } else {
        da(jugador, game)
      }
    }
  }

  def mockGameGet(
    mocked: => Repository.GameOperations,
    game:   Game
  ): UIO[ScalaOngoingStubbing[RepositoryIO[Option[Game]]]] = ZIO.succeed(
    when(mocked.get(GameId(1))).thenReturn(ZIO.succeed(Option(game)))
  )

  def juegaHastaElFinal(
    gameService:    GameService.Service,
    gameOperations: Repository.GameOperations,
    layer: ULayer[
      DatabaseProvider with Repository with LoggedInUserRepo with Postman with Logging with TokenHolder
    ],
    start: Game
  ): ZIO[zio.ZEnv, Any, Game] = {
    ZIO.iterate(start)(game => game.gameStatus == GameStatus.jugando
//      game.jugadores.count(_.fichas.nonEmpty) == 4
    )(game => juegaMano(gameService, gameOperations, layer, game))
  }

  def juegaMano(
    gameService:    GameService.Service,
    gameOperations: Repository.GameOperations,
    layer: ULayer[
      DatabaseProvider with Repository with LoggedInUserRepo with Postman with Logging with TokenHolder
    ],
    start: Game
  ): ZIO[zio.ZEnv, Any, Game] = {
    val bot: PlayerBot = MostlyRandomPlayerBot
    for {
      _ <- mockGameGet(gameOperations, start)
      jugador1 = start.mano
      jugador2 = start.nextPlayer(jugador1)
      jugador3 = start.nextPlayer(jugador2)
      jugador4 = start.nextPlayer(jugador3)
      afterPlayer1 <- {
        val event = bot.takeTurn(jugador1, start)
        gameService
          .play(GameId(1), event).provideCustomLayer(
            layer ++ SessionProvider.layer(ChutiSession(jugador1.user))
          )
      }
      _ <- mockGameGet(gameOperations, afterPlayer1)
      afterPlayer2 <- {
        val event = bot.takeTurn(jugador2, afterPlayer1)
        gameService
          .play(GameId(1), event).provideCustomLayer(
            layer ++ SessionProvider.layer(ChutiSession(jugador2.user))
          )
      }
      _ <- mockGameGet(gameOperations, afterPlayer2)
      afterPlayer3 <- {
        val event = bot.takeTurn(jugador3, afterPlayer2)
        gameService
          .play(GameId(1), event).provideCustomLayer(
            layer ++ SessionProvider.layer(ChutiSession(jugador3.user))
          )
      }
      _ <- mockGameGet(gameOperations, afterPlayer3)
      afterPlayer4 <- {
        val event = bot.takeTurn(jugador4, afterPlayer3)
        gameService
          .play(GameId(1), event).provideCustomLayer(
            layer ++ SessionProvider.layer(ChutiSession(jugador4.user))
          )
      }
    } yield afterPlayer4
  }

  "primera mano" should "work" in {
    val gameOperations: Repository.GameOperations = mock[Repository.GameOperations]
    when(gameOperations.upsert(*[Game])).thenAnswer((game: Game) =>
      ZIO.succeed(game.copy(Some(GameId(1))))
    )
    val userOperations = createUserOperations

    val layer = fullLayer(gameOperations, userOperations)
    val (
      game:       Game,
      gameEvents: Option[List[GameEvent]],
      userEvents: Option[List[UserEvent]]
    ) =
      testRuntime.unsafeRun {

        for {
          gameService <- ZIO.access[GameService](_.get).provideCustomLayer(GameService.make())
          gameStream = gameService
            .gameStream(GameId(1)).provideCustomLayer(
              layer ++ SessionProvider.layer(ChutiSession(user1))
            )
          userStream = gameService.userStream.provideCustomLayer(
            layer ++ SessionProvider.layer(ChutiSession(user1))
          )
          gameEventsFiber <- gameStream.take(4).runCollect.timeout(3.second).fork
          userEventsFiber <- userStream.take(0).runCollect.timeout(3.second).fork
          _               <- clock.sleep(1.second)
          start           <- readGame(GAME_CANTO4)
          mano1           <- juegaMano(gameService, gameOperations, layer, start)
          gameEvents      <- gameEventsFiber.join
          userEvents      <- userEventsFiber.join
        } yield (mano1, gameEvents, userEvents)
      }

    assert(game.id === Option(GameId(1)))
    assert(game.jugadores.count(_.fichas.size == 6) === 4) //Todos dieron una ficha.
    val ganador = game.jugadores.maxBy(_.filas.size)
    println(s"Gano ${ganador.user.name} con ${ganador.filas.last}!")
    assert(gameEvents.toSeq.flatten.size === 4)
    assert(userEvents.toSeq.flatten.size === 0) //Though 2 happen (log in and log out, only log in should be registering)
  }
  "jugando 4 manos" should "work" in {
    val gameOperations: Repository.GameOperations = mock[Repository.GameOperations]
    when(gameOperations.upsert(*[Game])).thenAnswer((game: Game) =>
      ZIO.succeed(game.copy(Some(GameId(1))))
    )
    val userOperations = createUserOperations

    val layer = fullLayer(gameOperations, userOperations)
    val (
      game:       Game,
      gameEvents: Option[List[GameEvent]],
      userEvents: Option[List[UserEvent]]
    ) =
      testRuntime.unsafeRun {

        for {
          gameService <- ZIO.access[GameService](_.get).provideCustomLayer(GameService.make())
          gameStream = gameService
            .gameStream(GameId(1)).provideCustomLayer(
              layer ++ SessionProvider.layer(ChutiSession(user1))
            )
          userStream = gameService.userStream.provideCustomLayer(
            layer ++ SessionProvider.layer(ChutiSession(user1))
          )
          gameEventsFiber <- gameStream.take(16).runCollect.timeout(3.second).fork
          userEventsFiber <- userStream.take(0).runCollect.timeout(3.second).fork
          _               <- clock.sleep(1.second)
          start           <- readGame(GAME_CANTO4)
          mano1           <- juegaMano(gameService, gameOperations, layer, start)
          mano2           <- juegaMano(gameService, gameOperations, layer, mano1)
          mano3           <- juegaMano(gameService, gameOperations, layer, mano2)
          mano4           <- juegaMano(gameService, gameOperations, layer, mano3)
          gameEvents      <- gameEventsFiber.join
          userEvents      <- userEventsFiber.join
        } yield (mano4, gameEvents, userEvents)
      }

    assert(game.id === Option(GameId(1)))
    assert(game.jugadores.count(_.fichas.size == 3) === 4) //Todos dieron una ficha.
    val ganador = game.jugadores.maxBy(_.filas.size)
    println(s"Gano ${ganador.user.name} con ${ganador.filas.last}!")
    assert(gameEvents.toSeq.flatten.size === 16)
    assert(userEvents.toSeq.flatten.size === 0) //Though 2 happen (log in and log out, only log in should be registering)
  }
  "jugando hasta que se haga o sea hoyo" should "work" in {
    val gameOperations: Repository.GameOperations = mock[Repository.GameOperations]
    when(gameOperations.upsert(*[Game])).thenAnswer((game: Game) =>
      ZIO.succeed(game.copy(Some(GameId(1))))
    )
    val userOperations = createUserOperations

    val layer = fullLayer(gameOperations, userOperations)
    val (
      game:       Game,
      gameEvents: Option[List[GameEvent]],
      userEvents: Option[List[UserEvent]]
    ) =
      testRuntime.unsafeRun {

        for {
          gameService <- ZIO.access[GameService](_.get).provideCustomLayer(GameService.make())
          gameStream = gameService
            .gameStream(GameId(1)).provideCustomLayer(
              layer ++ SessionProvider.layer(ChutiSession(user1))
            )
          userStream = gameService.userStream.provideCustomLayer(
            layer ++ SessionProvider.layer(ChutiSession(user1))
          )
          gameEventsFiber <- gameStream.take(16).runCollect.timeout(3.second).fork
          userEventsFiber <- userStream.take(0).runCollect.timeout(3.second).fork
          _               <- clock.sleep(1.second)
          start           <- readGame(GAME_CANTO4)
          end             <- juegaHastaElFinal(gameService, gameOperations, layer, start)
          gameEvents      <- gameEventsFiber.join
          userEvents      <- userEventsFiber.join
        } yield (end, gameEvents, userEvents)
      }

    assert(game.id === Option(GameId(1)))
    val ganador = game.jugadores.maxBy(_.filas.size)
    println(s"Gano ${ganador.user.name} con ${ganador.filas.size}!")
    assert(gameEvents.toSeq.flatten.size === 16)
    assert(userEvents.toSeq.flatten.size === 0) //Though 2 happen (log in and log out, only log in should be registering)
  }
}
