/*
 * Copyright (c) 2024 Roberto Leibman
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
