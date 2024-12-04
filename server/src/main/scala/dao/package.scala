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

import api.ChutiSession
import chuti.*
import zio.{Clock, ZIO}
import zio.logging.*
import zio.json.*

import java.sql.Timestamp

package object dao {

  type RepositoryIO[E] =
    ZIO[ChutiSession, RepositoryError, E]

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
        lastSnapshot = value.toJson,
        status = value.gameStatus,
        created = Timestamp.from(value.created).nn,
        lastUpdated = new Timestamp(System.currentTimeMillis())
      )
      ret
    }

  }

  case class GameRow(
    id:            Int,
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
            id = Option(GameId(id)),
            currentEventIndex = current_index,
            gameStatus = status
          )
        }).mapError(s => RepositoryError(s))
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
