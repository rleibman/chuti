package db.quill

import api.config.Config
import better.files.File
import chuti.*
import dao.{Repository, RepositoryError, RepositoryPermissionError}
import db.quill.QuillUserSpec.{clockLayer, configLayer, containerLayer, godSession, loggingLayer, quillLayer, suite, testUserZIO}
import io.circe
import io.circe.{Decoder, Printer}
import zio.clock.Clock
import zio.logging.Logging
import zio.magic.*
import zio.random.Random
import zio.test.*
import zio.test.environment.TestEnvironment
import io.circe.Printer
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import zio.*
import zio.test.Assertion.*

import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.time.format.DateTimeFormatter

object QuillGameSpec extends QuillSpec {

  implicit val localDateTimeDecoder: Decoder[LocalDateTime] = Decoder.decodeLocalDateTimeWithFormatter(DateTimeFormatter.ISO_DATE_TIME)
  implicit val instantDecoder: Decoder[Instant] =
    Decoder.decodeLocalDateTimeWithFormatter(DateTimeFormatter.ISO_DATE_TIME).map(_.toInstant(ZoneOffset.UTC))

  def readGame(filename: String): IO[circe.Error, Game] =
    ZIO.fromEither {
      val file = File(filename)
      decode[Game](file.contentAsString)
    }

  val GAME_NEW = "/Volumes/Personal/projects/chuti/server/src/test/resources/newGame.json"
  val GAME_STARTED = "/Volumes/Personal/projects/chuti/server/src/test/resources/startedGame.json"
  val GAME_WITH_2USERS =
    "/Volumes/Personal/projects/chuti/server/src/test/resources/with2Users.json"
  val GAME_CANTO4 = "/Volumes/Personal/projects/chuti/server/src/test/resources/canto4.json"

  override def spec: Spec[TestEnvironment, TestFailure[Any], TestSuccess] =
    suite("Quill Game Suite")(
      testM("CRUD") {
        for {
          testUser             <- testUserZIO
          userRepo             <- ZIO.service[Repository.Service].map(_.userOperations)
          gameRepo             <- ZIO.service[Repository.Service].map(_.gameOperations)
          allGamesBeforeInsert <- gameRepo.search()
          game                 <- readGame(GAME_NEW)
          inserted             <- gameRepo.upsert(game.copy(id = None))
          gotten               <- gameRepo.get(inserted.id.get)
          allGamesAfterInsert  <- gameRepo.search()
          allGamesCount        <- gameRepo.count()
          updated              <- gameRepo.upsert(inserted.copy(gameStatus = GameStatus.abandonado))
          gottenUpdated        <- gameRepo.get(inserted.id.get)
          deleted              <- gameRepo.delete(inserted.id.get)
          allGamesAfterDelete  <- gameRepo.search()
        } yield assertTrue(inserted.id.nonEmpty) &&
          assertTrue(gotten.nonEmpty) &&
          assertTrue(allGamesAfterInsert.nonEmpty) &&
          assertTrue(inserted == gotten.get) &&
          assertTrue(allGamesAfterInsert.size.toLong == allGamesCount) &&
          assertTrue(updated.gameStatus == GameStatus.abandonado) &&
          assertTrue(updated == gottenUpdated.get) &&
          assertTrue(deleted) &&
          assertTrue(allGamesAfterDelete.size < allGamesAfterInsert.size)
      }
    ).injectShared(containerLayer, configLayer, quillLayer, loggingLayer, godSession, clockLayer, Random.live)
  //  def getHistoricalUserGames: RepositoryIO[Seq[Game]]
  //  def userInGame(id:      GameId): RepositoryIO[Boolean]
  //  def updatePlayers(game: Game):   RepositoryIO[Game]
  //  def gameInvites:              RepositoryIO[Seq[Game]]
  //  def gamesWaitingForPlayers(): RepositoryIO[Seq[Game]]
  //  def getGameForUser:           RepositoryIO[Option[Game]]

}
