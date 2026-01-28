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
import dao.TokenOperations
import zio.logging.*
import zio.*

import java.time.Instant
import scala.concurrent.duration.Duration as ScalaDuration

object InMemoryRepository {

  private val now = Instant.now().nn

  val user1: User =
    User(Option(UserId(1)), "yoyo1@example.com", "yoyo1", created = now, lastUpdated = now)
  val user2: User =
    User(Option(UserId(2)), "yoyo2@example.com", "yoyo2", created = now, lastUpdated = now)
  val user3: User =
    User(Option(UserId(3)), "yoyo3@example.com", "yoyo3", created = now, lastUpdated = now)
  val user4: User =
    User(Option(UserId(4)), "yoyo4@example.com", "yoyo4", created = now, lastUpdated = now)

  def fromGames(games: Seq[Game]): ULayer[ZIORepository] =
    ZLayer.fromZIO(for {
      games <- Ref.make(games.map(g => g.id.get -> g).toMap)
      users <- Ref.make(
        Map(
          UserId(1) -> user1,
          UserId(2) -> user2,
          UserId(3) -> user3,
          UserId(4) -> user4
        )
      )
      tokens <- Ref.make(Map.empty[String, Token])
    } yield InMemoryRepository(games, users, tokens))

  val make: ULayer[ZIORepository] = ZLayer.fromZIO(for {
    games <- Ref.make(Map.empty[GameId, Game])
    users <- Ref.make(
      Map(
        UserId(1) -> user1,
        UserId(2) -> user2,
        UserId(3) -> user3,
        UserId(4) -> user4
      )
    )
    tokens <- Ref.make(Map.empty[String, Token])
  } yield InMemoryRepository(games, users, tokens))

}

case class InMemoryRepository(
  games:  Ref[Map[GameId, Game]],
  users:  Ref[Map[UserId, User]],
  tokens: Ref[Map[String, Token]]
) extends ZIORepository {

  import InMemoryRepository.*

  override val gameOperations: GameOperations[RepositoryIO] = new GameOperations[RepositoryIO] {

    override def getHistoricalUserGames: RepositoryIO[Seq[Game]] = ???

    override def gameInvites: RepositoryIO[Seq[Game]] = ???

    override def gamesWaitingForPlayers(): RepositoryIO[Seq[Game]] = ???

    override def getGameForUser: RepositoryIO[Option[Game]] = ???

    override def upsert(game: Game): RepositoryIO[Game] =
      for {
        id <- zio.Random.nextInt
        newGame = game.copy(id = game.id.orElse(Some(GameId(id))))
        _ <- games.update(map => map + (newGame.id.get -> newGame))
      } yield newGame

    override def get(pk: GameId): RepositoryIO[Option[Game]] = games.get.map(_.get(pk))

    override def delete(
      pk:         GameId,
      softDelete: Boolean
    ): RepositoryIO[Boolean] = games.update(_ - pk).as(true)

    override def search(search: Option[EmptySearch]): RepositoryIO[Seq[Game]] = games.get.map(_.values.toSeq)

    override def count(search: Option[EmptySearch]): RepositoryIO[Long] = games.get.map(_.size.toLong)

    override def updatePlayers(game: Game): RepositoryIO[Game] = upsert(game)

    override def userInGame(id: GameId): RepositoryIO[Boolean] = ZIO.succeed(true)

  }

  override val userOperations: UserOperations[RepositoryIO] = new UserOperations[RepositoryIO] {

    override def login(
      email:    String,
      password: String
    ): ZIO[Any, RepositoryError, Option[User]] = ???

    override def userByEmail(email: String): RepositoryIO[Option[User]] = ???

    override def changePassword(
      user:     User,
      password: String
    ): RepositoryIO[Boolean] = ???

    override def changePassword(password: String): RepositoryIO[Boolean] = ???

    override def unfriend(enemy: UserId): RepositoryIO[Boolean] = ???

    override def friend(friend: UserId): RepositoryIO[Boolean] = ???

    override def friends: RepositoryIO[Seq[User]] = ???

    override def getWallet: RepositoryIO[Option[UserWallet]] = ???

    override def updateWallet(userWallet: UserWallet): RepositoryIO[UserWallet] = ???

    override def upsert(user: User): RepositoryIO[User] =
      for {
        id <- zio.Random.nextInt
        newUser = user.copy(id = user.id.orElse(Some(UserId(id))))
        _ <- users.update(map => map + (newUser.id.get -> newUser))
      } yield newUser

    override def get(pk: UserId): RepositoryIO[Option[User]] = users.get.map(_.get(pk))

    override def delete(
      pk:         UserId,
      softDelete: Boolean
    ): RepositoryIO[Boolean] = ???

    override def search(search: Option[PagedStringSearch]): RepositoryIO[Seq[User]] = ???

    override def count(search: Option[PagedStringSearch]): RepositoryIO[Long] = ???

    override def getWallet(userId: UserId): RepositoryIO[Option[UserWallet]] = ???

    override def firstLogin: RepositoryIO[Option[Instant]] = ???

    override def isFirstLoginToday: RepositoryIO[Boolean] = ???
  }
  override val tokenOperations: TokenOperations[RepositoryIO] = new TokenOperations[RepositoryIO] {

    override def validateToken(
      token:   Token,
      purpose: TokenPurpose
    ): RepositoryIO[Option[User]] = ???

    override def createToken(
      user:    User,
      purpose: TokenPurpose,
      ttl:     Option[ScalaDuration]
    ): RepositoryIO[Token] = ???

    override def peek(
      token:   Token,
      purpose: TokenPurpose
    ): RepositoryIO[Option[User]] = ???

    override def cleanup: RepositoryIO[Boolean] = ???

  }

}
