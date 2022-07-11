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

import api.token.{Token, TokenHolder, TokenPurpose}
import better.files.File
import chat.ChatService
import chat.ChatService.ChatService
import dao.Repository.GameOperations
import dao.{InMemoryRepository, Repository, RepositoryIO}
import io.circe.Printer
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
import mail.Postman
import mail.Postman.Postman
import zio.*
import zio.cache.{Cache, Lookup}
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger

import java.util.UUID
import zio.duration.*
import org.scalatest.Assertions.*

trait GameAbstractSpec {

  val connectionId: ConnectionId = ConnectionId(5)

  val user1: User =
    User(Option(UserId(1)), "yoyo1@example.com", "yoyo1")
  val user2: User =
    User(Option(UserId(2)), "yoyo2@example.com", "yoyo2")
  val user3: User =
    User(Option(UserId(3)), "yoyo3@example.com", "yoyo3")
  val user4: User =
    User(Option(UserId(4)), "yoyo4@example.com", "yoyo4")

  val testRuntime: zio.Runtime[zio.ZEnv] = zio.Runtime.default

  def fullLayer(
    gameOps: Repository.GameOperations,
    userOps: Repository.UserOperations,
    postman: Postman.Service = new MockPostman
  ): ULayer[
    Repository & Postman & Logging & TokenHolder & ChatService
  ] = {
    val loggingLayer = Slf4jLogger.make((_, b) => b)
    ZLayer.succeed(new Repository.Service {
      override val gameOperations: GameOperations = gameOps
      override val userOperations: Repository.UserOperations = userOps
      override val tokenOperations: Repository.TokenOperations = new Repository.TokenOperations {
        override def cleanup: RepositoryIO[Boolean] = ???

        override def validateToken(
          token:   Token,
          purpose: TokenPurpose
        ): RepositoryIO[Option[User]] = ???

        override def createToken(
          user:    User,
          purpose: TokenPurpose,
          ttl:     Option[Duration]
        ): RepositoryIO[Token] = ???

        override def peek(
          token:   Token,
          purpose: TokenPurpose
        ): RepositoryIO[Option[User]] = ???
      }
    }) ++
      loggingLayer ++
      (for {
        cache <- Cache.make[(String, TokenPurpose), Any, Nothing, User](100, 5.days, Lookup(str => ZIO.succeed(chuti.god))) // TODO: fix this
      } yield TokenHolder.tempCache(cache)).toLayer ++
      ZLayer.succeed(postman) ++
      (loggingLayer >>> ChatService.make())
  }

  def writeGame(
    game:     Game,
    filename: String
  ): Task[Unit] =
    ZIO.effect {
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
