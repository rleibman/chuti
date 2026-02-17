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

package chuti

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

import api.ChutiSession
import chuti.{*, given}
import zio.{Clock, ZIO}
import zio.logging.*
import zio.json.*

import java.sql.Timestamp

package object db {

  type RepositoryIO[E] =
    ZIO[ChutiSession, RepositoryError, E]

  case class FriendsRow(
    one: Long,
    two: Long
  )

  object UserRow {

    def fromUser(value: User): UserRow =
      UserRow(
        id = value.id.value,
        name = value.name,
        email = value.email,
        created = Timestamp.from(value.created).nn,
        lastUpdated = Timestamp.from(value.lastUpdated).nn,
        active = value.active
      )

  }

  case class UserRow(
    id:          Long,
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
        id = UserId(id),
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
        id = value.id.value,
        current_index = value.currentEventIndex,
        lastSnapshot = value.toJson,
        status = value.gameStatus,
        created = Timestamp.from(value.created).nn,
        lastUpdated = Timestamp(System.currentTimeMillis())
      )
      ret
    }

  }

  case class GameRow(
    id:            Long,
    lastSnapshot:  String,
    status:        GameStatus,
    created:       Timestamp,
    lastUpdated:   Timestamp,
    current_index: Int = 0,
    deleted:       Boolean = false
  ) {

    def toGame: ZIO[Any, RepositoryError, Game] = {
      ZIO
        .fromEither(lastSnapshot.fromJson[Game].map {
          _.copy(
            id = GameId(id),
            currentEventIndex = current_index,
            gameStatus = status
          )
        }).mapError(s => RepositoryError(s))
    }

  }

  case class GameEventRow(
    gameId:       Long,
    currentIndex: Int = 0,
    eventData:    String
  )

  case class GamePlayersRow(
    userId:  Long,
    gameId:  Long,
    order:   Long,
    invited: Boolean
  )

  object UserWalletRow {

    def fromUserWallet(value: UserWallet): UserWalletRow =
      UserWalletRow(userId = value.userId.value, amount = value.amount)

  }
  case class UserWalletRow(
    userId: Long,
    amount: BigDecimal
  ) {

    def toUserWallet: UserWallet = UserWallet(userId = UserId(userId), amount = amount)

  }

  case class UserLogRow(
    userId: Long,
    time:   Timestamp
  )

  case class TokenRow(
    tok:          String,
    tokenPurpose: String,
    expireTime:   Timestamp,
    userId:       Long
  )

}
