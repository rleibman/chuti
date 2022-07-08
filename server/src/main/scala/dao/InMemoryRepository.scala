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

package dao

import api.token.*
import chuti.*
import dao.Repository.TokenOperations
import zio.clock.Clock
import zio.logging.Logging
import zio.{Task, ZIO}

import java.time.Instant
import zio.duration.*

object InMemoryRepository {

  val user1: User =
    User(Option(UserId(1)), "yoyo1@example.com", "yoyo1")
  val user2: User =
    User(Option(UserId(2)), "yoyo2@example.com", "yoyo2")
  val user3: User =
    User(Option(UserId(3)), "yoyo3@example.com", "yoyo3")
  val user4: User =
    User(Option(UserId(4)), "yoyo4@example.com", "yoyo4")

}

class InMemoryRepository(loadedGames: Seq[Game]) extends Repository.Service {

  import InMemoryRepository.*
  private val games =
    scala.collection.mutable.Map[GameId, Game](loadedGames.map(game => game.id.get -> game): _*)
  private val users = scala.collection.mutable.Map[UserId, User](
    UserId(1) -> user1,
    UserId(2) -> user2,
    UserId(3) -> user3,
    UserId(4) -> user4
  )

  override val gameOperations: Repository.GameOperations = new Repository.GameOperations {

    override def getHistoricalUserGames:   RepositoryIO[Seq[Game]] = ???
    override def gameInvites:              RepositoryIO[Seq[Game]] = ???
    override def gamesWaitingForPlayers(): RepositoryIO[Seq[Game]] = ???
    override def getGameForUser:           RepositoryIO[Option[Game]] = ???
    override def upsert(e: Game): RepositoryIO[Game] = {
      val id = e.id.getOrElse(GameId(games.size + 1))
      Task.succeed {
        games.put(id, e.copy(id = Option(id)))
        games(id)
      }
    }
    override def get(pk: GameId): RepositoryIO[Option[Game]] = Task.succeed(games.get(pk))
    override def delete(
      pk:         GameId,
      softDelete: Boolean
    ): RepositoryIO[Boolean] = ???
    override def search(search: Option[EmptySearch]): RepositoryIO[Seq[Game]] = Task.succeed(games.values.toSeq)
    override def count(search: Option[EmptySearch]):  RepositoryIO[Long] = Task.succeed(games.size.toLong)

    override def updatePlayers(game: Game): RepositoryIO[Game] = Task.succeed(game)

    override def userInGame(id: GameId): RepositoryIO[Boolean] = Task.succeed(true)

  }

  override val userOperations: Repository.UserOperations = new Repository.UserOperations {

    override def login(
      email:    String,
      password: String
    ): ZIO[Clock & Logging, RepositoryError, Option[User]] = ???

    override def userByEmail(email: String): RepositoryIO[Option[User]] = ???

    override def changePassword(
      user:     User,
      password: String
    ): RepositoryIO[Boolean] = ???

    override def unfriend(enemy: User): RepositoryIO[Boolean] = ???

    override def friend(friend: User): RepositoryIO[Boolean] = ???

    override def friends: RepositoryIO[Seq[User]] = ???

    override def getWallet: RepositoryIO[Option[UserWallet]] = ???

    override def updateWallet(userWallet: UserWallet): RepositoryIO[UserWallet] = ???

    override def upsert(e: User): RepositoryIO[User] = {
      val id = e.id.getOrElse(UserId(users.size + 1))
      Task.succeed {
        users.put(id, e.copy(id = Option(id)))
        users(id)
      }
    }

    override def get(pk: UserId): RepositoryIO[Option[User]] = Task.succeed(users.get(pk))

    override def delete(
      pk:         UserId,
      softDelete: Boolean
    ): RepositoryIO[Boolean] = ???

    override def search(search: Option[PagedStringSearch]): RepositoryIO[Seq[User]] = ???

    override def count(search: Option[PagedStringSearch]): RepositoryIO[Long] = ???

    override def getWallet(userId: UserId): RepositoryIO[Option[UserWallet]] = ???

    override def firstLogin: RepositoryIO[Option[Instant]] = ???

  }
  override val tokenOperations: Repository.TokenOperations = new TokenOperations {

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

    override def cleanup: RepositoryIO[Boolean] = ???

  }

}
