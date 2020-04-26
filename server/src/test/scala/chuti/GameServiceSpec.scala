package chuti

import java.io.IOException

import api.ChutiSession
import better.files.File
import dao.Repository.GameOperations
import dao.{DatabaseProvider, Repository, RepositoryIO, SessionProvider}
import game.GameService.GameService
import game.LoggedInUserRepo.LoggedInUserRepo
import game.{GameService, LoggedInUserRepo}
import io.circe.Printer
import org.mockito.invocation.InvocationOnMock
import org.mockito.scalatest.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec
import zio._
import zio.stream.{Sink, ZSink, ZStream}
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser._
import zio.duration._

class GameServiceSpec extends AnyFlatSpec with MockitoSugar {
  val user1 = User(Option(UserId(1)), "yoyo1@example.com", "yoyo")
  val user2 = User(Option(UserId(2)), "yoyo2@example.com", "yoyo")
  val user3 = User(Option(UserId(3)), "yoyo3@example.com", "yoyo")
  val user4 = User(Option(UserId(4)), "yoyo4@example.com", "yoyo")

  private val testRuntime = zio.Runtime.default
  val databaseProvider: DatabaseProvider.Service = mock[DatabaseProvider.Service]
  val loggedInUserRepo: LoggedInUserRepo.Service = mock[LoggedInUserRepo.Service]
  val userOperations:   Repository.UserOperations = mock[Repository.UserOperations]
  when(userOperations.get(UserId(1))).thenReturn(ZIO.succeed(Option(user1)))
  when(userOperations.get(UserId(2))).thenReturn(ZIO.succeed(Option(user2)))
  when(userOperations.get(UserId(3))).thenReturn(ZIO.succeed(Option(user3)))
  when(userOperations.get(UserId(4))).thenReturn(ZIO.succeed(Option(user4)))
  when(loggedInUserRepo.addUser(any[User])).thenAnswer { (i: InvocationOnMock) =>
    val user = i.getArgument[User](0)
    console
      .putStrLn(s"User ${user.id.get} logged in").flatMap(_ => ZIO.succeed(true)).provideLayer(
        zio.console.Console.live
      )
  }
  when(loggedInUserRepo.removeUser(any[UserId])).thenAnswer { (i: InvocationOnMock) =>
    val userId = i.getArgument[Int](0)
    console
      .putStrLn(s"User $userId logged out").flatMap(_ => ZIO.succeed(true)).provideLayer(
        zio.console.Console.live
      )
  }

  private def fullLayer(gameOperations: Repository.GameOperations) = {
    val repository: Repository.Service = mock[Repository.Service]
    when(repository.gameOperations).thenReturn(gameOperations)
    ZLayer.succeed(databaseProvider) ++
      ZLayer.succeed(repository) ++
      ZLayer.succeed(loggedInUserRepo)
  }

  def writeGame(
    game:     Game,
    filename: String
  ): Task[Unit] = ZIO.effect {
    val file = File(filename)
    file.write(game.asJson.printWith(Printer.spaces2))
  }

  def readGame(filename: String): Task[Game] =
    ZIO.effect {
      val file = File(filename)
      decode[Game](file.contentAsString)
    }.absolve

  val NEW_GAME = "/Volumes/Personal/projects/chuti/server/src/test/resources/newGame.json"
  val STARTED_GAME = "/Volumes/Personal/projects/chuti/server/src/test/resources/startedGame.json"
  val WITH_2USERS = "/Volumes/Personal/projects/chuti/server/src/test/resources/with2Users.json"

  "Starting a game" should "start a game" in {
    val gameOperations: Repository.GameOperations = mock[Repository.GameOperations]
    when(gameOperations.upsert(any[Game])).thenAnswer((i: InvocationOnMock) =>
      ZIO.succeed(i.getArgument[Game](0).copy(id = Some(GameId(1))))
    )

    val layer = fullLayer(gameOperations)

    val game: Game = testRuntime.unsafeRun {
      for {
        gameService <- ZIO.access[GameService](_.get).provideCustomLayer(GameService.make())
        operation <- gameService
          .newGame().provideCustomLayer(layer ++ SessionProvider.layer(ChutiSession(user1)))
        _ <- writeGame(operation, NEW_GAME)
      } yield operation
    }

    assert(game.gameStatus === Estado.esperandoJugadoresInvitados)
    assert(game.currentIndex === 1)
    assert(game.id === Option(GameId(1)))
    assert(game.jugadores.length == 1)
    assert(game.jugadores.head.user === user1)
    verify(gameOperations).upsert(any[Game])
  }

  "Loading a started game and having 3 more random users join" should "start a game" in {
    val gameOperations: Repository.GameOperations = mock[Repository.GameOperations]
    when(gameOperations.upsert(any[Game])).thenAnswer((i: InvocationOnMock) =>
      ZIO.succeed(i.getArgument[Game](0).copy(id = Some(GameId(1))))
    )

//    testRuntime.unsafeRun {
//      for {
//        queue <- Queue.unbounded[Int]
//        stream = ZStream.fromQueueWithShutdown(queue)
//        _   <- queue.offer(1).delay(1.second).forever.fork
//        res <- stream.take(5).runCollect
//        _   <- console.putStrLn(res.toString)
//      } yield 0
//    }

    val layer = fullLayer(gameOperations)
    val (game: Game, gameEvents: Option[List[GameEvent]], userEvents: Option[List[UserEvent]]) =
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
          game <- readGame(NEW_GAME)
          _ <- ZIO.succeed(
            when(gameOperations.gamesWaitingForPlayers()).thenReturn(ZIO.succeed(Seq(game)))
          )
          withUser2 <- gameService
            .joinRandomGame().provideCustomLayer(
              layer ++ SessionProvider.layer(ChutiSession(user2))
            )
          _          <- writeGame(withUser2, WITH_2USERS)
          gameEvents <- gameStream.take(2).runCollect.timeout(3.second)
          userEvents <- userStream.take(1).runCollect.timeout(3.second)
        } yield (withUser2, gameEvents, userEvents)

      }

    assert(game.gameStatus === Estado.esperandoJugadoresInvitados)
    assert(game.currentIndex === 2)
    assert(game.id === Option(GameId(1)))
    assert(game.jugadores.length == 2)
    assert(game.jugadores.head.user === user1)
    assert(game.jugadores.drop(1).head.user === user2)
    assert(gameEvents.toSeq.flatten.size === 2)
    assert(userEvents.toSeq.flatten.size === 1) //Though 2 happen (log in and log out, only log in should be registering)
    assert(gameEvents.toSeq.flatten.length === 1)
    verify(gameOperations).upsert(any[Game])
  }
}
