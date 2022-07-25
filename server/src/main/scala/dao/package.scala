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
import io.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import zio.{Clock, ZIO}
import zio.logging.*

import java.sql.Timestamp

package object dao {

  type RepositoryIO[E] =
    ZIO[SessionContext, RepositoryError, E]

  case class FriendsRow(
    one: Int,
    two: Int
  )

  object UserRow {

    def fromUser(value: User): UserRow =
      UserRow(
        id = value.id.fold(0)(_.userId),
        name = value.name,
        email = value.email,
        created = Timestamp.from(value.created).nn,
        lastUpdated = Timestamp.from(value.lastUpdated).nn,
        active = value.active
      )

  }

  case class UserRow(
    id:          Int,
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
        id = Some(UserId(id)),
        email = email,
        name = name,
        created = created.toInstant.nn,
        lastUpdated = lastUpdated.toInstant.nn,
        active = active,
        deleted = deleted
      )

  }

  object GameRow {

    def fromGame(value: Game): GameRow = {
      val ret = GameRow(
        id = value.id.fold(0)(_.gameId),
        current_index = value.currentEventIndex,
        lastSnapshot = value.asJson,
        status = value.gameStatus,
        created = Timestamp.from(value.created).nn,
        lastUpdated = new Timestamp(System.currentTimeMillis())
      )
      ret
    }

  }

  case class GameRow(
    id:            Int,
    lastSnapshot:  Json,
    status:        GameStatus,
    created:       Timestamp,
    lastUpdated:   Timestamp,
    current_index: Int = 0,
    deleted:       Boolean = false
  ) {

    def toGame: Game = {
      import scala.language.unsafeNulls
      val decoder = summon[Decoder[Game]]
      decoder.decodeJson(lastSnapshot).map {
        _.copy(
          id = Option(GameId(id)),
          currentEventIndex = current_index,
          gameStatus = status
        )
      } match {
        case Right(state) => state
        case Left(error)  => throw GameError(error)
      }
    }

  }

  case class GameEventRow(
    gameId:       Int,
    currentIndex: Int = 0,
    eventData:    String
  )

  case class GamePlayersRow(
    userId:  Int,
    gameId:  Int,
    order:   Int,
    invited: Boolean
  )

  object UserWalletRow {

    def fromUserWallet(value: UserWallet): UserWalletRow = UserWalletRow(userId = value.userId.userId, amount = value.amount)

  }
  case class UserWalletRow(
    userId: Int,
    amount: BigDecimal
  ) {

    def toUserWallet: UserWallet = UserWallet(userId = UserId(userId), amount = amount)

  }

  case class UserLogRow(
    userId: Int,
    time:   Timestamp
  )

  case class TokenRow(
    tok:          String,
    tokenPurpose: String,
    expireTime:   Timestamp,
    userId:       Int
  )

}
