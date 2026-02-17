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
import chuti.api.token.{Token, TokenPurpose}

import java.time.Instant
import scala.concurrent.duration.Duration

/** Collects the basic CRUD operations of a single object (or object graph) against a data source.
  * @tparam E
  *   the typeof the object to be stored.
  * @tparam PK
  *   the type of the primary key of the object.
  * @tparam SEARCH
  *   the type of the search object.
  */
trait CRUDOperations[F[_], E, PK, SEARCH <: Search] {

  def upsert(e: E):  F[E]
  def get(pk:   PK): F[Option[E]]
  def delete(
    pk:         PK,
    softDelete: Boolean = false
  ):                                         F[Boolean]
  def search(search: Option[SEARCH] = None): F[Seq[E]]
  def count(search:  Option[SEARCH] = None): F[Long]

}

abstract class Repository[F[_]] {

  def gameOperations: GameOperations[F]

  def userOperations: UserOperations[F]

  def tokenOperations: TokenOperations[F]

}

trait GameOperations[F[_]] extends CRUDOperations[F, Game, GameId, EmptySearch] {

  def getHistoricalUserGames: F[Seq[Game]]

  def userInGame(id: GameId): F[Boolean]

  def updatePlayers(game: Game): F[Game]

  def gameInvites: F[Seq[Game]]

  def gamesWaitingForPlayers(): F[Seq[Game]]

  def getGameForUser: F[Option[Game]]

}

trait UserOperations[F[_]] extends CRUDOperations[F, User, UserId, PagedStringSearch] {

  def isFirstLoginToday: F[Boolean]

  def firstLogin: F[Option[Instant]]

  def login(
    email:    String,
    password: String
  ): F[Option[User]]

  def userByEmail(email: String): F[Option[User]]

  def changePassword(password: String): F[Boolean]

  def changePassword(
    user:     User,
    password: String
  ): F[Boolean]

  def unfriend(enemy: UserId): F[Boolean]

  def friend(friend: UserId): F[Boolean]

  def friends: F[Seq[User]]

  def getWallet: F[Option[UserWallet]]

  def getWallet(userId: UserId): F[Option[UserWallet]]

  def updateWallet(userWallet: UserWallet): F[UserWallet]

}

trait TokenOperations[F[_]] {

  def cleanup: F[Boolean]

  def validateToken(
    token:   Token,
    purpose: TokenPurpose
  ): F[Option[User]]

  def createToken(
    user:    User,
    purpose: TokenPurpose,
    ttl:     Option[Duration]
  ): F[Token]

  def peek(
    token:   Token,
    purpose: TokenPurpose
  ): F[Option[User]]

}
