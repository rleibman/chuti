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

import api.token.TokenHolder
import better.files.File
import courier.Envelope
import dao.Repository.GameOperations
import dao.{DatabaseProvider, Repository}
import game.LoggedInUserRepo
import io.circe.Printer
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import mail.Postman
import mail.Postman.Postman
import org.mockito.scalatest.MockitoSugar
import zio._
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger

trait GameAbstractSpec extends MockitoSugar {
  val user1: User =
    User(Option(UserId(1)), "yoyo1@example.com", "yoyo1", userStatus = UserStatus.Idle)
  val user2: User =
    User(Option(UserId(2)), "yoyo2@example.com", "yoyo2", userStatus = UserStatus.Idle)
  val user3: User =
    User(Option(UserId(3)), "yoyo3@example.com", "yoyo3", userStatus = UserStatus.Idle)
  val user4: User =
    User(Option(UserId(4)), "yoyo4@example.com", "yoyo4", userStatus = UserStatus.Idle)

  val testRuntime:      zio.Runtime[zio.ZEnv] = zio.Runtime.default
  val databaseProvider: DatabaseProvider.Service = mock[DatabaseProvider.Service]
  val loggedInUserRepo: LoggedInUserRepo.Service = mock[LoggedInUserRepo.Service]
  def createUserOperations: Repository.UserOperations = {
    val userOperations: Repository.UserOperations = mock[Repository.UserOperations]
    //    when(userOperations.get(UserId(1))).thenReturn(ZIO.succeed(Option(user1)))
    //    when(userOperations.get(UserId(2))).thenReturn(ZIO.succeed(Option(user2)))
    //    when(userOperations.get(UserId(3))).thenReturn(ZIO.succeed(Option(user3)))
    //    when(userOperations.get(UserId(4))).thenReturn(ZIO.succeed(Option(user4)))
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

  class MockPostman extends Postman.Service {
    override def deliver(email: Envelope): ZIO[Postman, Throwable, Unit] = ZIO.succeed(())

    override def webHostName: String = "chuti.fun"
  }

  def fullLayer(
    gameOps: Repository.GameOperations,
    userOps: Repository.UserOperations,
    postman: Postman.Service = new MockPostman
  ): ULayer[
    DatabaseProvider with Repository with LoggedInUserRepo.LoggedInUserRepo with Postman with Logging with TokenHolder
  ] = {
    ZLayer.succeed(databaseProvider) ++
      ZLayer.succeed(new Repository.Service {
        override val gameOperations: GameOperations = gameOps
        override val userOperations: Repository.UserOperations = userOps
      }) ++
      Slf4jLogger.make((_, b) => b) ++
      ZLayer.succeed(TokenHolder.live) ++
      ZLayer.succeed(loggedInUserRepo) ++
      ZLayer.succeed(postman)
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

  val GAME_NEW = "/Volumes/Personal/projects/chuti/server/src/test/resources/newGame.json"
  val GAME_STARTED = "/Volumes/Personal/projects/chuti/server/src/test/resources/startedGame.json"
  val GAME_WITH_2USERS =
    "/Volumes/Personal/projects/chuti/server/src/test/resources/with2Users.json"
  val GAME_CANTO4 = "/Volumes/Personal/projects/chuti/server/src/test/resources/canto4.json"

}
