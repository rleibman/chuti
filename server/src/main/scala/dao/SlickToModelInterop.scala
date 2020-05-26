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
import java.sql.Timestamp

import chuti.GameStatus.comienzo
import chuti.{
  Ficha,
  Game,
  GameException,
  GameId,
  GameStatus,
  Jugador,
  Triunfo,
  User,
  UserId,
  UserWallet
}
import gen.Tables._
import io.circe
import io.circe.Decoder
import io.circe.parser._
import io.circe.syntax._
import io.circe.generic.auto._

trait SlickToModelInterop {
  def UserRow2User(row: UserRow): User = User(
    id = Some(row.id),
    email = row.email,
    name = row.name,
    created = row.created.toLocalDateTime,
    lastUpdated = row.created.toLocalDateTime,
    lastLoggedIn = row.lastloggedin.map(_.toLocalDateTime),
    active = row.active,
    deleted = row.deleted
  )
  def User2UserRow(value: User): UserRow = UserRow(
    id = value.id.getOrElse(UserId(0)),
    name = value.name,
    email = value.email,
    created = Timestamp.valueOf(value.created),
    active = value.active,
    lastupdated = new Timestamp(System.currentTimeMillis()),
    lastloggedin = value.lastLoggedIn.map(Timestamp.valueOf)
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

  def Game2GameRow(value: Game): GameRow = {
    val ret = GameRow(
      id = value.id.getOrElse(GameId(0)),
      currentIndex = value.currentEventIndex,
      lastSnapshot = value.asJson,
      gameStatus = value.gameStatus,
      created = Timestamp.valueOf(value.created),
      lastupdated = new Timestamp(System.currentTimeMillis())
    )
    ret
  }

  def UserWalletRow2UserWallet(row:   UserWalletRow): UserWallet = UserWallet(row.user, row.amount)
  def UserWallet2UserWalletRow(value: UserWallet): UserWalletRow =
    UserWalletRow(value.userId, value.amount)

}
