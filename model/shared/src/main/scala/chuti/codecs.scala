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

import chuti.CuantasCantas.CuantasCantas
import zio.json.*

given JsonCodec[UserId] = JsonCodec.long.transform(UserId.apply, _.value)
given JsonCodec[ConnectionId] = JsonCodec.string.transform(ConnectionId.apply, _.value)
given JsonCodec[GameId] = JsonCodec.long.transform(GameId.apply, _.value)
given JsonCodec[(UserId, Seq[Fila])] = JsonCodec.derived[(UserId, Seq[Fila])]
given JsonCodec[CuantasCantas] = JsonCodec.derived[CuantasCantas]

// Custom codec for JugadorType with backwards compatibility for old bot names
given JsonCodec[JugadorType] =
  JsonCodec.string.transformOrFail(
    str =>
      str match {
        case "human"     => Right(JugadorType.human)
        case "dumbBot"   => Right(JugadorType.dumbBot)
        case "aiBot"     => Right(JugadorType.aiBot)
        case "claudeBot" => Right(JugadorType.claudeBot)
        // Backwards compatibility mappings for old bot types
        case "smartBot" => Right(JugadorType.aiBot) // Old name for AI bot
        case "ai"       => Right(JugadorType.aiBot) // Another possible old name
        case other      => Left(s"Unknown JugadorType: $other (valid values: human, dumbBot, aiBot, claudeBot)")
      },
    jugadorType => jugadorType.toString
  )
