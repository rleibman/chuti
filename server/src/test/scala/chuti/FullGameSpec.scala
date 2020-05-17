package chuti

import api.ChutiSession
import chuti.InMemoryRepository.{user1, user2, user3, user4}
import chuti.bots.DumbPlayerBot
import dao.{Repository, SessionProvider}
import game.GameService
import game.GameService.GameService
import org.scalatest.Succeeded
import org.scalatest.flatspec.AsyncFlatSpecLike
import zio.duration._
import zio.{RIO, ZIO, clock}

class FullGameSpec extends GameAbstractSpec2 with AsyncFlatSpecLike {
  //TODO move up
  def newGame(): RIO[TestLayer, Game] =
    for {
      gameService <- ZIO.access[GameService](_.get)
      //Start the game
      game <- gameService.newGame().provideSomeLayer[TestLayer](userLayer(user1))
    } yield game

  //TODO move up
  def getReadyToPlay(gameId: GameId): ZIO[TestLayer, Throwable, Game] = {
    for {
      gameOperations <- ZIO.access[Repository](_.get.gameOperations)
      gameService    <- ZIO.access[GameService](_.get)
      game           <- gameOperations.get(gameId).map(_.get).provideSomeLayer[TestLayer](godLayer)
      //Invite people
      _ <- gameService
        .inviteToGame(user2.id.get, game.id.get).provideSomeLayer[TestLayer](
          userLayer(user1)
        )
      _ <- gameService
        .inviteToGame(user3.id.get, game.id.get).provideSomeLayer[TestLayer](
          userLayer(user1)
        )
      _ <- gameService
        .inviteToGame(user4.id.get, game.id.get).provideSomeLayer[TestLayer](
          userLayer(user1)
        )
      //they accept
      _ <- gameService
        .acceptGameInvitation(game.id.get).provideSomeLayer[TestLayer](userLayer(user2))
      _ <- gameService
        .acceptGameInvitation(game.id.get).provideSomeLayer[TestLayer](userLayer(user3))
      _ <- gameService
        .acceptGameInvitation(game.id.get).provideSomeLayer[TestLayer](userLayer(user4))
      result <- gameOperations.get(game.id.get).provideSomeLayer[TestLayer](godLayer)
    } yield result.get
  }

  def canto(gameId: GameId): ZIO[TestLayer, Throwable, Game] = {
    val bot = DumbPlayerBot
    for {
      gameService <- ZIO.access[GameService](_.get)
      game        <- gameService.getGame(gameId).provideSomeLayer[TestLayer](godLayer).map(_.get)
      quienCanta = game.jugadores.find(_.turno).map(_.user).get
      sigiuente1 = game.nextPlayer(quienCanta).user
      sigiuente2 = game.nextPlayer(sigiuente1).user
      sigiuente3 = game.nextPlayer(sigiuente2).user
      _ <- bot
        .takeTurn(gameId).provideSomeLayer[TestLayer](
          SessionProvider.layer(ChutiSession(quienCanta))
        )
      _ <- bot
        .takeTurn(gameId).provideSomeLayer[TestLayer](
          SessionProvider.layer(ChutiSession(sigiuente1))
        )
      _ <- bot
        .takeTurn(gameId).provideSomeLayer[TestLayer](
          SessionProvider.layer(ChutiSession(sigiuente2))
        )
      yaCanto <- bot
        .takeTurn(gameId).provideSomeLayer[TestLayer](
          SessionProvider.layer(ChutiSession(sigiuente3))
        )
    } yield yaCanto
  }

  def playFullGame =
    (for {
      gameService <- ZIO.access[GameService](_.get)
      start       <- newGame()
      _           <- zio.console.putStrLn(s"Game ${start.id.get} started")
      assert1 <- {
        ZIO.succeed {
          assert(start.gameStatus === GameStatus.esperandoJugadoresInvitados)
          assert(start.currentEventIndex === 1)
          assert(start.id.nonEmpty)
          assert(start.jugadores.length == 1)
          assert(start.jugadores.head.user.id === user1.id)
          assert(start.jugadores.forall(j => j.user.userStatus == UserStatus.Playing))
        }
      }
      gameStream = gameService
        .gameStream(start.id.get)
        .provideSomeLayer[TestLayer](SessionProvider.layer(ChutiSession(user1)))
      gameEventsFiber <- gameStream
        .takeUntil {
          case PoisonPill(Some(id), _, _) if id == start.id.get => true
          case _                                                => false
        }.runCollect.fork
      _           <- clock.sleep(1.second)
      readyToPlay <- getReadyToPlay(start.id.get)
      assert2 <- ZIO.succeed {
        assert(readyToPlay.gameStatus === GameStatus.cantando)
        assert(readyToPlay.currentEventIndex === 10)
        assert(readyToPlay.jugadores.length == 4)
        assert(readyToPlay.jugadores.forall(!_.invited))
        assert(readyToPlay.jugadores.forall(_.user.userStatus === UserStatus.Playing))
      }
      afterCanto <- canto(start.id.get)
      assert3 <- ZIO.succeed {
        assert(afterCanto.gameStatus === GameStatus.jugando)
        assertSoloUnoCanta(afterCanto)
      }
      _ <- gameService
        .broadcastGameEvent(PoisonPill(Option(start.id.get))).provideSomeLayer[
          TestLayer
        ](
          SessionProvider.layer(ChutiSession(GameService.god))
        )
      gameEvents <- gameEventsFiber.join
      finalAssert <- ZIO.succeed {
        assert(gameEvents.nonEmpty)
      }
    } yield assert(
      assert1 == Succeeded &&
        assert2 == Succeeded &&
        assert3 == Succeeded &&
        finalAssert == Succeeded
    )).provideCustomLayer(testLayer())

  "Playing a full game" should "look all nice" in {
    testRuntime.unsafeRunToFuture {
      ZIO
//        .foreach(1 to 10) { _ =>
          playFullGame
//        }.map(l => assert(l.forall(_ == Succeeded)))
    }.future
  }

}
