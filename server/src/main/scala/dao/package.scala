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

import chuti.*
import dao.slick.DatabaseProvider
import io.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import zio.clock.Clock
import zio.logging.Logging
import zio.{Has, ZIO}

import java.sql.Timestamp

package object dao {

  type Repository = Has[Repository.Service]

  type DatabaseProvider = Has[DatabaseProvider.Service]

  type SessionProvider = Has[SessionProvider.Session]

  type RepositoryIO[E] =
    ZIO[SessionProvider & Logging & Clock, RepositoryError, E]

  case class FriendsRow(
    one: UserId,
    two: UserId
  )

  object UserRow {

    def fromUser(value: User): UserRow =
      UserRow(
        id = value.id.getOrElse(UserId(0)),
        name = value.name,
        email = value.email,
        created = Timestamp.valueOf(value.created),
        lastUpdated = Timestamp.valueOf(value.lastUpdated),
        active = value.active
      )

  }

  case class UserRow(
    id:          UserId,
    name:        String,
    email:       String,
    created:     Timestamp,
    lastUpdated: Timestamp,
    active:      Boolean = false,
    deleted:     Boolean = false,
    deletedDate: Option[Timestamp] = None
  ) {

    def toUser: User =
      User(
        id = Some(id),
        email = email,
        name = name,
        created = created.toLocalDateTime,
        active = active,
        deleted = deleted
      )

  }
  case class GameRow(
    id:           GameId,
    lastSnapshot: Json,
    gameStatus:   GameStatus,
    created:      Timestamp,
    lastUpdated:  Timestamp,
    currentIndex: Int = 0
  )

  def GameRow2Game(row: GameRow): Game = {
    val decoder = implicitly(Decoder[Game])
    decoder.decodeJson(row.lastSnapshot).map {
      _.copy(
        id = Option(row.id),
        currentEventIndex = row.currentIndex,
        gameStatus = row.gameStatus
      )
    } match {
      case Right(state) => state
      case Left(error)  => throw GameException(error)
    }
  }
  case class GameEventRow(
    gameId:       GameId,
    currentIndex: Int = 0,
    eventData:    String
  )

  case class GamePlayersRow(
    userId:  UserId,
    gameId:  GameId,
    order:   Int,
    invited: Boolean
  )
  case class UserWalletRow(
    user:   UserId,
    amount: BigDecimal
  )

  case class UserLogRow(
    user: UserId,
    time: Timestamp
  )

  case class TokenRow(
    tok:          String,
    tokenPurpose: String,
    expireTime:   Timestamp,
    userId:       UserId
  )

  def Game2GameRow(value: Game): GameRow = {
    val ret = GameRow(
      id = value.id.getOrElse(GameId(0)),
      currentIndex = value.currentEventIndex,
      lastSnapshot = value.asJson,
      gameStatus = value.gameStatus,
      created = Timestamp.valueOf(value.created),
      lastUpdated = new Timestamp(System.currentTimeMillis())
    )
    ret
  }

  def UserWalletRow2UserWallet(row: UserWalletRow): UserWallet = UserWallet(userId = row.user, amount = row.amount)
  def UserWallet2UserWalletRow(value: UserWallet):  UserWalletRow = UserWalletRow(user = value.userId, amount = value.amount)

}
