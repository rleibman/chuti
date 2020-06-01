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

import java.time.LocalDateTime

case class UserId(value: Int) extends AnyVal

sealed trait UserStatus

object UserStatus {
  def fromString(str: String): UserStatus = str match {
    case "Playing" => Playing
    case "Offline" => Offline
    case "Idle"    => Idle
    case _         => throw new Exception(s"Can't build UserStatus out of $str")
  }

  case object Playing extends UserStatus
  case object Offline extends UserStatus
  case object Idle extends UserStatus
}

case class User(
  id:           Option[UserId],
  email:        String,
  name:         String,
  userStatus:   UserStatus = UserStatus.Offline,
  created:      LocalDateTime = LocalDateTime.now,
  lastUpdated:  LocalDateTime = LocalDateTime.now,
  lastLoggedIn: Option[LocalDateTime] = None,
  active:       Boolean = false,
  deleted:      Boolean = false
) {}

case class UserWallet(
  userId: UserId,
  amount: BigDecimal = 0.0
)

sealed trait UserEventType
object UserEventType {
  case object Disconnected extends UserEventType
  case object Connected extends UserEventType
  case object Modified extends UserEventType
  case object JoinedGame extends UserEventType
  case object AbandonedGame extends UserEventType
}

case class UserEvent(
  user:          User,
  userEventType: UserEventType
)

case class ConnectionId(value: String) extends AnyVal
