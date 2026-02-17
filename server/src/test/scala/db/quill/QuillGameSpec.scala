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

package db.quill

import better.files.File
import chuti.api.ChutiEnvironment
import chuti.{*, given}
import db.*
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
        file.contentAsString.fromJson[Game]
      }.mapError(Error(_))

  val GAME_NEW = "server/src/test/resources/newGame.json"
  val GAME_STARTED = "server/src/test/resources/startedGame.json"
  val GAME_WITH_2USERS =
    "server/src/test/resources/with2Users.json"
  val GAME_CANTO4 = "server/src/test/resources/canto4.json"

  override def spec =
    suite("Quill Game Suite")(
      test("CRUD") {
        (for {
          gameRepo            <- ZIO.serviceWith[ZIORepository](_.gameOperations)
          game                <- readGame(GAME_NEW)
          inserted            <- gameRepo.upsert(game.copy(id = GameId.empty))
          gotten              <- gameRepo.get(inserted.id)
          allGamesAfterInsert <- gameRepo.search()
          allGamesCount       <- gameRepo.count()
          updated             <- gameRepo.upsert(inserted.copy(gameStatus = GameStatus.abandonado))
          gottenUpdated       <- gameRepo.get(inserted.id)
          deleted             <- gameRepo.delete(inserted.id)
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
        )).withClock(fixedClock)
      }
    ).provideSomeLayerShared[ChutiEnvironment](godSession)
  //  def getHistoricalUserGames: RepositoryIO[Seq[Game]]
  //  def userInGame(id:      GameId): RepositoryIO[Boolean]
  //  def updatePlayers(game: Game):   RepositoryIO[Game]
  //  def gameInvites:              RepositoryIO[Seq[Game]]
  //  def gamesWaitingForPlayers(): RepositoryIO[Seq[Game]]
  //  def getGameForUser:           RepositoryIO[Option[Game]]

}
