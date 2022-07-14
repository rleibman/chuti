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

import api.token.{Token, TokenPurpose}
import chuti.*
import dao.Repository.{GameOperations, TokenOperations, UserOperations}
import game.GameService.godLayer
import zio.cache.{Cache, Lookup}
import zio.logging.*
import zio.{Clock, Random, ZIO, *}

import java.time.Instant

trait Repository {

  def gameOperations: GameOperations

  def userOperations: UserOperations

  def tokenOperations: TokenOperations

}

object Repository {

  lazy val cached: URLayer[Repository, Repository] = ZLayer.fromZIO {
    for {
      repository <- ZIO.service[Repository]
      cache <- zio.cache.Cache.make[GameId, Any, RepositoryError, Game](
        capacity = 10,
        timeToLive = 1.hour,
        lookup = Lookup { gameId =>
          for {
            game <- repository.gameOperations
              .get(gameId)
              .flatMap(ZIO.fromOption)
              .orElseFail(RepositoryError(s"Could not find game: $gameId"))
              .provideLayer(godLayer)
          } yield game
        }
      )
    } yield CachedRepository(cache, repository): Repository
  }

  case class CachedRepository(
    gameCache:  Cache[GameId, RepositoryError, Game],
    repository: Repository
  ) extends Repository {

    override def gameOperations: Repository.GameOperations =
      new Repository.GameOperations {

        override def getHistoricalUserGames: RepositoryIO[Seq[Game]] = repository.gameOperations.getHistoricalUserGames

        override def userInGame(id: GameId): RepositoryIO[Boolean] = repository.gameOperations.userInGame(id)

        override def updatePlayers(game: Game): RepositoryIO[Game] =
          game.id.fold(ZIO.unit)(i => gameCache.invalidate(i)) *>
            repository.gameOperations.updatePlayers(game)

        override def gameInvites: RepositoryIO[Seq[Game]] = repository.gameOperations.gameInvites

        override def gamesWaitingForPlayers(): RepositoryIO[Seq[Game]] = repository.gameOperations.gamesWaitingForPlayers()

        override def getGameForUser: RepositoryIO[Option[Game]] = repository.gameOperations.getGameForUser

        override def upsert(game: Game): RepositoryIO[Game] =
          game.id.fold(ZIO.unit)(i => gameCache.invalidate(i)) *>
            repository.gameOperations.upsert(game)

        override def get(pk: GameId): RepositoryIO[Option[Game]] = gameCache.get(pk).map(Option.apply)

        override def delete(
          pk:         GameId,
          softDelete: Boolean
        ): RepositoryIO[Boolean] =
          gameCache.invalidate(pk) *>
            repository.gameOperations.delete(pk, softDelete)

        override def search(search: Option[EmptySearch]): RepositoryIO[Seq[Game]] = repository.gameOperations.search(search)

        override def count(search: Option[EmptySearch]): RepositoryIO[Long] = repository.gameOperations.count(search)

      }

    override def userOperations: Repository.UserOperations = repository.userOperations

    override def tokenOperations: Repository.TokenOperations = repository.tokenOperations

  }

  trait UserOperations extends CRUDOperations[User, UserId, PagedStringSearch] {

    def firstLogin: RepositoryIO[Option[Instant]]

    def login(
      email:    String,
      password: String
    ): ZIO[Any, RepositoryError, Option[User]]

    def userByEmail(email: String): RepositoryIO[Option[User]]

    def changePassword(
      user:     User,
      password: String
    ): RepositoryIO[Boolean]

    def unfriend(enemy: User): RepositoryIO[Boolean]
    def friend(friend:  User): RepositoryIO[Boolean]
    def friends: RepositoryIO[Seq[User]]

    def getWallet: RepositoryIO[Option[UserWallet]]
    def getWallet(userId:        UserId):     RepositoryIO[Option[UserWallet]]
    def updateWallet(userWallet: UserWallet): RepositoryIO[UserWallet]

  }
  trait GameOperations extends CRUDOperations[Game, GameId, EmptySearch] {

    def getHistoricalUserGames: RepositoryIO[Seq[Game]]
    def userInGame(id:      GameId): RepositoryIO[Boolean]
    def updatePlayers(game: Game):   RepositoryIO[Game]
    def gameInvites:              RepositoryIO[Seq[Game]]
    def gamesWaitingForPlayers(): RepositoryIO[Seq[Game]]
    def getGameForUser:           RepositoryIO[Option[Game]]

  }

  trait TokenOperations {

    def cleanup: RepositoryIO[Boolean]

    def validateToken(
      token:   Token,
      purpose: TokenPurpose
    ): RepositoryIO[Option[User]]

    def createToken(
      user:    User,
      purpose: TokenPurpose,
      ttl:     Option[Duration]
    ): ZIO[SessionContext, RepositoryError, Token]

    def peek(
      token:   Token,
      purpose: TokenPurpose
    ): RepositoryIO[Option[User]]

  }

}
