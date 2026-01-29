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

import java.time.Instant
import zio.json.*

import java.util.Locale

case class User(
  id:          UserId,
  email:       String,
  name:        String,
  created:     Instant,
  lastUpdated: Instant,
  active:      Boolean = false,
  deleted:     Boolean = false,
  isAdmin:     Boolean = false,
  locale:      Locale = Locale.forLanguageTag("es")
) {

  def isBot: Boolean = id.value < -1 && id != UserId.godUserId && id != UserId.godlessUserId

}

object User {

  given JsonCodec[Locale] =
    JsonCodec(
      JsonEncoder.string.contramap(_.toString),
      JsonDecoder.string.mapOrFail(s =>
        Locale.forLanguageTag(s) match {
          case l: Locale => Right(l)
          case null => Left(s"invalid locale $s")
        }
      )
    )

  given JsonCodec[User] = JsonCodec.derived[User]

}

case class UserWallet(
  userId: UserId,
  amount: BigDecimal = 0.0
)

object UserWallet {

  given JsonDecoder[UserWallet] = DeriveJsonDecoder.gen[UserWallet]
  given JsonEncoder[UserWallet] = DeriveJsonEncoder.gen[UserWallet]

}

enum UserEventType {

  case Disconnected, Connected, Modified, JoinedGame, AbandonedGame

}

object UserEventType {

  given JsonDecoder[UserEventType] =
    JsonDecoder.string.mapOrFail(s =>
      values
        .find(_.toString == s).toRight(s"No se pudo decodificar $s como CuantasCantas"): Either[String, UserEventType]
    )

  given JsonEncoder[UserEventType] = JsonEncoder.string.contramap(_.toString)

}

case class UserEvent(
  user:          User,
  userEventType: UserEventType,
  gameId:        Option[GameId]
)

object UserEvent {

  given JsonDecoder[UserEvent] = DeriveJsonDecoder.gen[UserEvent]
  given JsonEncoder[UserEvent] = DeriveJsonEncoder.gen[UserEvent]

}
