package db.quill

import api.ChutiEnvironment
import better.files.File
import chuti.{given, *}
import dao.*
// import db.quill.QuillUserSpec.fixedClock
import zio.*
import zio.logging.*
import zio.test.*
import zio.json.*

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime, ZoneOffset}

object QuillGameSpec extends QuillSpec {

  def readGame(filename: String): IO[Error, Game] =
    ZIO
      .fromEither {
        val file = File(filename)
        file.contentAsString.nn.fromJson[Game]
      }.mapError(Error(_))

  val GAME_NEW = "/Volumes/Personal/projects/chuti/server/src/test/resources/newGame.json"
  val GAME_STARTED = "/Volumes/Personal/projects/chuti/server/src/test/resources/startedGame.json"
  val GAME_WITH_2USERS =
    "/Volumes/Personal/projects/chuti/server/src/test/resources/with2Users.json"
  val GAME_CANTO4 = "/Volumes/Personal/projects/chuti/server/src/test/resources/canto4.json"

  override def spec =
    suite("Quill Game Suite")(
      test("CRUD") {
        (for {
          gameRepo            <- ZIO.service[Repository].map(_.gameOperations)
          game                <- readGame(GAME_NEW)
          inserted            <- gameRepo.upsert(game.copy(id = None))
          gotten              <- gameRepo.get(inserted.id.get)
          allGamesAfterInsert <- gameRepo.search()
          allGamesCount       <- gameRepo.count()
          updated             <- gameRepo.upsert(inserted.copy(gameStatus = GameStatus.abandonado))
          gottenUpdated       <- gameRepo.get(inserted.id.get)
          deleted             <- gameRepo.delete(inserted.id.get)
          allGamesAfterDelete <- gameRepo.search()
        } yield assertTrue(
          inserted.id.nonEmpty,
          gotten.nonEmpty,
          allGamesAfterInsert.nonEmpty,
          inserted == gotten.get,
          allGamesAfterInsert.size.toLong == allGamesCount,
          updated.gameStatus == GameStatus.abandonado,
          updated == gottenUpdated.get,
          deleted,
          allGamesAfterDelete.size < allGamesAfterInsert.size
        ))
      }
    ).provideSomeLayerShared[ChutiEnvironment](godSession)
  //  def getHistoricalUserGames: RepositoryIO[Seq[Game]]
  //  def userInGame(id:      GameId): RepositoryIO[Boolean]
  //  def updatePlayers(game: Game):   RepositoryIO[Game]
  //  def gameInvites:              RepositoryIO[Seq[Game]]
  //  def gamesWaitingForPlayers(): RepositoryIO[Seq[Game]]
  //  def getGameForUser:           RepositoryIO[Option[Game]]

}
