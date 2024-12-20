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

package api

import chuti.User
import zio.ULayer

import java.util.Locale
import zio.json.*

object ChutiSession {

  val adminSession: ChutiSession = ChutiSession(chuti.god)

  private given JsonDecoder[Locale] =
    JsonDecoder.string.mapOrFail(s =>
      Locale.forLanguageTag(s) match {
        case l: Locale => Right(l)
        case null => Left(s"invalid locale $s")
      }
    )

  private given JsonEncoder[Locale] = JsonEncoder.string.contramap(_.toString)

  given JsonCodec[ChutiSession] = DeriveJsonCodec.gen[ChutiSession]

}

case class ChutiSession(
  user:   User,
  locale: Locale = Locale.of("es", "MX")
) {

  def toLayer: ULayer[ChutiSession] = zio.ZLayer.succeed(this)

}
