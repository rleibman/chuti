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

package chuti

import java.time.Instant
import zio.json.*

case class User(
  id:          Option[UserId],
  email:       String,
  name:        String,
  created:     Instant,
  lastUpdated: Instant,
  active:      Boolean = false,
  deleted:     Boolean = false,
  isAdmin:     Boolean = false
) {

  def isBot: Boolean = id.fold(false)(i => i.userId < -1 && i != godUserId)

}

object User {

  given JsonDecoder[User] = DeriveJsonDecoder.gen[User]

  given JsonEncoder[User] = DeriveJsonEncoder.gen[User]

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
      values.find(_.toString == s).toRight(s"No se pudo decodificar $s como CuantasCantas"): Either[String, UserEventType]
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
