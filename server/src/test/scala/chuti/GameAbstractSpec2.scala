package chuti

import api.ChutiSession
import api.token.TokenHolder
import better.files.File
import chuti.bots.DumbPlayerBot
import dao.{DatabaseProvider, Repository, SessionProvider}
import game.GameService.GameService
import game.LoggedInUserRepo.LoggedInUserRepo
import game.{GameService, LoggedInUserRepo}
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import mail.Postman
import mail.Postman.Postman
import org.mockito.scalatest.MockitoSugar
import org.scalatest.Assertion
import zio._
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger

trait GameAbstractSpec2 extends MockitoSugar {
  lazy protected val testRuntime:      zio.Runtime[zio.ZEnv] = zio.Runtime.default
  lazy protected val databaseProvider: DatabaseProvider.Service = mock[DatabaseProvider.Service]
  lazy protected val loggedInUserRepo: LoggedInUserRepo.Service = {
    val value = mock[LoggedInUserRepo.Service]
    when(value.addUser(*[User])).thenAnswer { user: User =>
      console
        .putStrLn(s"User ${user.id.get} logged in").flatMap(_ => ZIO.succeed(true)).provideLayer(
          zio.console.Console.live
        )
    }
    when(value.removeUser(*[UserId])).thenAnswer { userId: Int =>
      console
        .putStrLn(s"User $userId logged out").flatMap(_ => ZIO.succeed(true)).provideLayer(
          zio.console.Console.live
        )
    }
    value
  }

  def juegaHastaElFinal(gameId: GameId): RIO[TestLayer, Game] = {
    for {
      gameOperations <- ZIO.access[Repository](_.get.gameOperations)
      start <- gameOperations
        .get(gameId).map(_.get).provideSomeLayer[TestLayer](
          SessionProvider.layer(ChutiSession(GameService.god))
        )
      looped <- ZIO.iterate(start)(game => game.gameStatus == GameStatus.jugando)(_ =>
        juegaMano(gameId)
      )
    } yield looped
  }

  def juegaMano(gameId: GameId): RIO[TestLayer, Game] = {
    val bot = DumbPlayerBot
    for {
      gameOperations <- ZIO.access[Repository](_.get.gameOperations)
      game <- gameOperations
        .get(gameId).map(_.get).provideSomeLayer[TestLayer](
          SessionProvider.layer(ChutiSession(GameService.god))
        )
      mano = game.mano
      sigiuente1 = game.nextPlayer(mano)
      sigiuente2 = game.nextPlayer(sigiuente1)
      sigiuente3 = game.nextPlayer(sigiuente2)
      _ <- bot
        .takeTurn(gameId).provideSomeLayer[TestLayer](
          SessionProvider.layer(ChutiSession(mano.user))
        )
      _ <- bot
        .takeTurn(gameId).provideSomeLayer[TestLayer](
          SessionProvider.layer(ChutiSession(sigiuente1.user))
        )
      _ <- bot
        .takeTurn(gameId).provideSomeLayer[TestLayer](
          SessionProvider.layer(ChutiSession(sigiuente2.user))
        )
      afterPlayer4 <- bot
        .takeTurn(gameId).provideSomeLayer[TestLayer](
          SessionProvider.layer(ChutiSession(sigiuente3.user))
        )
    } yield afterPlayer4
  }

  def repositoryLayer(gameFiles: String*): ULayer[Repository] = ZLayer.fromEffect {
    val z:  ZIO[Any, Throwable, List[Game]] = ZIO.foreach(gameFiles)(filename => readGame(filename))
    val zz: UIO[InMemoryRepository] = z.map(games => new InMemoryRepository(games)).orDie
    zz
  }

  type TestLayer = DatabaseProvider
    with Repository with LoggedInUserRepo with Postman with Logging with TokenHolder
    with GameService

  final protected def testLayer(gameFiles: String*): ULayer[TestLayer] = {
    val postman: Postman.Service = new MockPostman
    ZLayer.succeed(databaseProvider) ++
      repositoryLayer(gameFiles: _*) ++
      ZLayer.succeed(loggedInUserRepo) ++
      ZLayer.succeed(postman) ++
      Slf4jLogger.make((_, b) => b) ++
      ZLayer.succeed(TokenHolder.live) ++
      GameService.make()
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

  lazy final val GAME_NEW =
    "/Volumes/Personal/projects/chuti/server/src/test/resources/newGame.json"
  lazy final val GAME_STARTED =
    "/Volumes/Personal/projects/chuti/server/src/test/resources/startedGame.json"
  lazy final val GAME_WITH_2USERS =
    "/Volumes/Personal/projects/chuti/server/src/test/resources/with2Users.json"
  lazy final val GAME_CANTO4 =
    "/Volumes/Personal/projects/chuti/server/src/test/resources/canto4.json"

  protected def userLayer(user: User): ULayer[SessionProvider] = {
    SessionProvider.layer(ChutiSession(user))
  }
  protected def godLayer: ULayer[SessionProvider] = {
    SessionProvider.layer(ChutiSession(GameService.god))
  }

  def assertSoloUnoCanta(game: Game): Assertion = {
    assert(game.jugadores.count(_.cantante) === 1)
    assert(game.jugadores.count(_.mano) === 1)
    assert(game.jugadores.count(_.turno) === 1)
    assert(game.jugadores.count(j => j.cuantasCantas.nonEmpty) === 1)
    val cantante = game.jugadores.find(_.cantante).get
    assert(cantante.mano)
    assert(cantante.cuantasCantas.fold(false)(c => c > CuantasCantas.Buenas))
  }

}
