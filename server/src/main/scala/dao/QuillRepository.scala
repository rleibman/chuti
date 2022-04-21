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

import chuti.*
import scalacache.Cache
import scalacache.caffeine.CaffeineCache
import zio.*
import api.config.*
import api.token.*
import io.getquill.*

import java.time.LocalDateTime
import scala.concurrent.duration.Duration

object QuillRepository {

  implicit val gameCache: Cache[Option[Game]] = CaffeineCache[Option[Game]]
  val live: URLayer[Config, Repository] =
    (for {
      config <- ZIO.service[Config.Service]
    } yield QuillRepository(config)).toLayer
}

case class QuillRepository(config: Config.Service) extends Repository.Service {

  override val gameOperations: Repository.GameOperations = new Repository.GameOperations {

    override def getHistoricalUserGames: RepositoryIO[Seq[Game]] = ???

    override def userInGame(id: GameId): RepositoryIO[Boolean] = ???

    override def updatePlayers(game: Game): RepositoryIO[Game] = ???

    override def gameInvites: RepositoryIO[Seq[Game]] = ???

    override def gamesWaitingForPlayers(): RepositoryIO[Seq[Game]] = ???

    override def getGameForUser: RepositoryIO[Option[Game]] = ???

    override def upsert(e: Game): RepositoryIO[Game] = ???

    override def get(pk: GameId): RepositoryIO[Option[Game]] = ???

    override def delete(
      pk:         GameId,
      softDelete: Boolean
    ): RepositoryIO[Boolean] = ???

    override def search(search: Option[EmptySearch]): RepositoryIO[Seq[Game]] = ???

    override def count(search: Option[EmptySearch]): RepositoryIO[Long] = ???

  }
  override val userOperations: Repository.UserOperations = new Repository.UserOperations {

    override def firstLogin: RepositoryIO[Option[LocalDateTime]] = ???

    override def login(
      email:    String,
      password: String
    ): RepositoryIO[Option[User]] = ???

    override def userByEmail(email: String): RepositoryIO[Option[User]] = ???

    override def changePassword(
      user:     User,
      password: String
    ): RepositoryIO[Boolean] = ???

    override def unfriend(enemy: User): RepositoryIO[Boolean] = ???

    override def friend(friend: User): RepositoryIO[Boolean] = ???

    override def friends: RepositoryIO[Seq[User]] = ???

    override def getWallet: RepositoryIO[Option[UserWallet]] = ???

    override def getWallet(userId: UserId): RepositoryIO[Option[UserWallet]] = ???

    override def updateWallet(userWallet: UserWallet): RepositoryIO[UserWallet] = ???

    override def upsert(e: User): RepositoryIO[User] = ???

    override def get(pk: UserId): RepositoryIO[Option[User]] = ???

    override def delete(
      pk:         UserId,
      softDelete: Boolean
    ): RepositoryIO[Boolean] = ???

    override def search(search: Option[PagedStringSearch]): RepositoryIO[Seq[User]] = ???

    override def count(search: Option[PagedStringSearch]): RepositoryIO[Long] = ???

  }
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

}
