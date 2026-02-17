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

package chuti.db

import chuti.*
import chuti.api.ChutiSession
import chuti.api.token.{Token, TokenPurpose}
import game.GameService.godLayer
import zio.*
import zio.cache.{Cache, Lookup}
import zio.logging.*

import java.time.Instant

trait ZIORepository extends Repository[RepositoryIO]

object ZIORepository {

  lazy val cached: URLayer[ZIORepository, ZIORepository] = ZLayer.fromZIO {
    for {
      repository <- ZIO.service[ZIORepository]
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
    } yield CachedRepository(cache, repository): ZIORepository
  }

  case class CachedRepository(
    gameCache:  Cache[GameId, RepositoryError, Game],
    repository: ZIORepository
  ) extends ZIORepository {

    override def gameOperations: db.GameOperations[RepositoryIO] =
      new GameOperations[RepositoryIO] {

        override def getHistoricalUserGames: RepositoryIO[Seq[Game]] = repository.gameOperations.getHistoricalUserGames

        override def userInGame(id: GameId): RepositoryIO[Boolean] = repository.gameOperations.userInGame(id)

        override def updatePlayers(game: Game): RepositoryIO[Game] =
          gameCache.invalidate(game.id) *>
            repository.gameOperations.updatePlayers(game)

        override def gameInvites: RepositoryIO[Seq[Game]] = repository.gameOperations.gameInvites

        override def gamesWaitingForPlayers(): RepositoryIO[Seq[Game]] =
          repository.gameOperations.gamesWaitingForPlayers()

        override def getGameForUser: RepositoryIO[Option[Game]] = repository.gameOperations.getGameForUser

        override def upsert(game: Game): RepositoryIO[Game] =
          gameCache.invalidate(game.id) *> repository.gameOperations.upsert(game)

        override def get(pk: GameId): RepositoryIO[Option[Game]] = gameCache.get(pk).map(Option.apply)

        override def delete(
          pk:         GameId,
          softDelete: Boolean
        ): RepositoryIO[Boolean] =
          gameCache.invalidate(pk) *>
            repository.gameOperations.delete(pk, softDelete)

        override def search(search: Option[EmptySearch]): RepositoryIO[Seq[Game]] =
          repository.gameOperations.search(search)

        override def count(search: Option[EmptySearch]): RepositoryIO[Long] = repository.gameOperations.count(search)

      }

    override def userOperations: UserOperations[RepositoryIO] = repository.userOperations

    override def tokenOperations: TokenOperations[RepositoryIO] = repository.tokenOperations

  }

}
