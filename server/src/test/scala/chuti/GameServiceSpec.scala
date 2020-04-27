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
import org.mockito.IdiomaticMockitoBase.Times
import org.mockito.matchers.AnyMatcher
import zio.duration._

class GameServiceSpec extends AnyFlatSpec with MockitoSugar {
  val user1: User =
    User(Option(UserId(1)), "yoyo1@example.com", "yoyo", userStatus = UserStatus.Idle)
  val user2: User =
    User(Option(UserId(2)), "yoyo2@example.com", "yoyo", userStatus = UserStatus.Idle)
  val user3: User =
    User(Option(UserId(3)), "yoyo3@example.com", "yoyo", userStatus = UserStatus.Idle)
  val user4: User =
    User(Option(UserId(4)), "yoyo4@example.com", "yoyo", userStatus = UserStatus.Idle)

  private val testRuntime = zio.Runtime.default
  val databaseProvider: DatabaseProvider.Service = mock[DatabaseProvider.Service]
  val loggedInUserRepo: LoggedInUserRepo.Service = mock[LoggedInUserRepo.Service]
  def createUserOperations: Repository.UserOperations = {
    val userOperations: Repository.UserOperations = mock[Repository.UserOperations]
//    when(userOperations.get(UserId(1))).thenReturn(ZIO.succeed(Option(user1)))
//    when(userOperations.get(UserId(2))).thenReturn(ZIO.succeed(Option(user2)))
//    when(userOperations.get(UserId(3))).thenReturn(ZIO.succeed(Option(user3)))
//    when(userOperations.get(UserId(4))).thenReturn(ZIO.succeed(Option(user4)))
    when(userOperations.upsert(*[User])).thenAnswer { u: User =>
      ZIO.succeed(u)
    }
    userOperations
  }
  when(loggedInUserRepo.addUser(*[User])).thenAnswer { user: User =>
    console
      .putStrLn(s"User ${user.id.get} logged in").flatMap(_ => ZIO.succeed(true)).provideLayer(
        zio.console.Console.live
      )
  }
  when(loggedInUserRepo.removeUser(*[UserId])).thenAnswer { userId: Int =>
    console
      .putStrLn(s"User $userId logged out").flatMap(_ => ZIO.succeed(true)).provideLayer(
        zio.console.Console.live
      )
  }

  private def fullLayer(
    gameOps: Repository.GameOperations,
    userOps: Repository.UserOperations
  ) = {
    ZLayer.succeed(databaseProvider) ++
      ZLayer.succeed(new Repository.Service {
        override val gameOperations: GameOperations = gameOps
        override val userOperations: Repository.UserOperations = userOps
      }) ++
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

  "Creating a new Game" should "create a game" in {
    val gameOperations: Repository.GameOperations = mock[Repository.GameOperations]
    when(gameOperations.upsert(*[Game])).thenAnswer((game: Game) =>
      ZIO.succeed(game.copy(Some(GameId(1))))
    )
    val userOperations = createUserOperations

    val layer = fullLayer(gameOperations, userOperations)

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
    assert(game.jugadores.head.user.id === user1.id)
    assert(game.jugadores.forall(j => j.user.userStatus == UserStatus.Playing))
    verify(gameOperations).upsert(*[Game])
    verify(userOperations, times(1)).upsert(*[User])
  }

  "Loading a started game and having 1 more random users join" should "keep the game open" in {
    val gameOperations: Repository.GameOperations = mock[Repository.GameOperations]
    when(gameOperations.upsert(*[Game])).thenAnswer((game: Game) =>
      ZIO.succeed(game.copy(Some(GameId(1))))
    )

    val userOperations = createUserOperations

    val layer = fullLayer(gameOperations, userOperations)
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
          gameEventsFiber <- gameStream.take(2).runCollect.timeout(3.second).fork
          userEventsFiber <- userStream.take(1).runCollect.timeout(3.second).fork
          _               <- clock.sleep(1.second)
          game            <- readGame(NEW_GAME)
          _ <- ZIO.succeed(
            when(gameOperations.gamesWaitingForPlayers()).thenReturn(ZIO.succeed(Seq(game)))
          )
          withUser2 <- gameService
            .joinRandomGame().provideCustomLayer(
              layer ++ SessionProvider.layer(ChutiSession(user2))
            )
          _          <- writeGame(withUser2, WITH_2USERS)
          gameEvents <- gameEventsFiber.join
          userEvents <- userEventsFiber.join
        } yield (withUser2, gameEvents, userEvents)

      }

    assert(game.gameStatus === Estado.esperandoJugadoresInvitados)
    assert(game.currentIndex === 2)
    assert(game.id === Option(GameId(1)))
    assert(game.jugadores.length == 2)
    assert(game.jugadores.head.user.id === user1.id)
    assert(game.jugadores.drop(1).head.user.id === user2.id)
    assert(gameEvents.toSeq.flatten.size === 2)
    assert(userEvents.toSeq.flatten.size === 1) //Though 2 happen (log in and log out, only log in should be registering)
    assert(game.jugadores.forall(j => j.user.userStatus == UserStatus.Playing))
    verify(gameOperations).upsert(*[Game])
    verify(userOperations, times(1)).upsert(*[User])
  }

  "Loading a started game and having 3 more random users join" should "start the game" in {
    val gameOperations: Repository.GameOperations = mock[Repository.GameOperations]
    when(gameOperations.upsert(*[Game])).thenAnswer((game: Game) =>
      ZIO.succeed(game.copy(Some(GameId(1))))
    )

    val userOperations = createUserOperations

    val layer = fullLayer(gameOperations, userOperations)
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
          gameEventsFiber <- gameStream.take(6).runCollect.timeout(3.second).fork
          userEventsFiber <- userStream.take(3).runCollect.timeout(3.second).fork
          _               <- clock.sleep(1.second)
          game            <- readGame(NEW_GAME)
          _ <- ZIO.succeed(
            when(gameOperations.gamesWaitingForPlayers()).thenReturn(ZIO.succeed(Seq(game)))
          )
          withUser2 <- gameService
            .joinRandomGame().provideCustomLayer(
              layer ++ SessionProvider.layer(ChutiSession(user2))
            )
          _ <- ZIO.succeed(
            when(gameOperations.gamesWaitingForPlayers()).thenReturn(ZIO.succeed(Seq(withUser2)))
          )
          withUser3 <- gameService
            .joinRandomGame().provideCustomLayer(
              layer ++ SessionProvider.layer(ChutiSession(user3))
            )
          _ <- ZIO.succeed(
            when(gameOperations.gamesWaitingForPlayers()).thenReturn(ZIO.succeed(Seq(withUser3)))
          )
          withUser4 <- gameService
            .joinRandomGame().provideCustomLayer(
              layer ++ SessionProvider.layer(ChutiSession(user4))
            )
          _          <- writeGame(withUser4, STARTED_GAME)
          gameEvents <- gameEventsFiber.join
          userEvents <- userEventsFiber.join
        } yield (withUser4, gameEvents, userEvents)

      }

    assert(game.id === Option(GameId(1)))
    assert(game.jugadores.length == 4)
    assert(game.gameStatus === Estado.jugando)
    assert(game.currentIndex === 5)
    assert(game.jugadores.head.user.id === user1.id)
    assert(game.jugadores.drop(1).head.user.id === user2.id)
    assert(game.jugadores.drop(2).head.user.id === user3.id)
    assert(game.jugadores.drop(3).head.user.id === user4.id)
    assert(gameEvents.toSeq.flatten.size === 6)
    assert(userEvents.toSeq.flatten.size === 3) //Though 2 happen (log in and log out, only log in should be registering)
    assert(game.jugadores.forall(j => j.user.userStatus == UserStatus.Playing))
    verify(gameOperations, times(3)).upsert(*[Game])
    verify(userOperations, times(3)).upsert(*[User])
  }

  "Abandoning a game" should "result in a penalty, and close the game" in {
    pending
    //TODO write this
  }

  "Invite to game 1 person" should "add to the game" in {
    pending
    //TODO write this
  }

  "Invite to game 3 people" should "add to the game, but not start it" in {
    pending
    //TODO write this
  }

  "Invite to game 3 people, and them accepting" should "add to the game, and start it" in {
    pending
    //TODO write this
  }

  "Invite to game 3 people, and one of them accepting" should "add to the game, and then remove one" in {
    pending
    //TODO write this
  }
}
