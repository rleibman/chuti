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

import java.time.Instant
import api.token.{Token, TokenPurpose}
import chuti.*
import zio.ZIO
import zio.clock.Clock
import zio.logging.Logging
import zio.random.Random

import scala.concurrent.duration.Duration

object Repository {

  trait UserOperations extends CRUDOperations[User, UserId, PagedStringSearch] {

    def firstLogin: RepositoryIO[Option[Instant]]

    def login(
      email:    String,
      password: String
    ): ZIO[Clock & Logging, RepositoryError, Option[User]]

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
    ): ZIO[SessionProvider & Logging & Clock & Random, RepositoryError, Token]
    def peek(
      token:   Token,
      purpose: TokenPurpose
    ): RepositoryIO[Option[User]]

  }

  trait Service {

    val gameOperations: GameOperations

    val userOperations: UserOperations

    val tokenOperations: TokenOperations

  }

}
