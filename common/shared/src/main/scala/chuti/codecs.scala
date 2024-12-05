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

given JsonDecoder[UserId] = JsonDecoder.int.map(UserId.apply)
given JsonEncoder[UserId] = JsonEncoder.int.contramap(_.userId)
given JsonDecoder[ConnectionId] = JsonDecoder.int.map(ConnectionId.apply)
given JsonEncoder[ConnectionId] = JsonEncoder.int.contramap(_.connectionId)
given JsonDecoder[GameId] = JsonDecoder.int.map(GameId.apply)
given JsonEncoder[GameId] = JsonEncoder.int.contramap(_.gameId)
given JsonDecoder[ChannelId] = JsonDecoder.int.map(ChannelId.apply)
given JsonEncoder[ChannelId] = JsonEncoder.int.contramap(_.channelId)

given JsonEncoder[Triunfo] = DeriveJsonEncoder.gen[Triunfo]
given JsonDecoder[Triunfo] = DeriveJsonDecoder.gen[Triunfo]

given JsonEncoder[Fila] = DeriveJsonEncoder.gen[Fila]
given JsonDecoder[Fila] = DeriveJsonDecoder.gen[Fila]

given JsonEncoder[CuantasCantas] = DeriveJsonEncoder.gen[CuantasCantas]
given JsonDecoder[CuantasCantas] = DeriveJsonDecoder.gen[CuantasCantas]

given JsonEncoder[(UserId, Seq[Fila])] = DeriveJsonEncoder.gen[(UserId, Seq[Fila])]

given JsonEncoder[Borlote] = DeriveJsonEncoder.gen[Borlote]
given JsonDecoder[Borlote] = DeriveJsonDecoder.gen[Borlote]

given JsonEncoder[PlayEvent] = DeriveJsonEncoder.gen[PlayEvent]
given JsonDecoder[PlayEvent] = DeriveJsonDecoder.gen[PlayEvent]

given JsonEncoder[GameEvent] = DeriveJsonEncoder.gen[GameEvent]
given JsonDecoder[GameEvent] = DeriveJsonDecoder.gen[GameEvent]

given JsonEncoder[Cuenta] = DeriveJsonEncoder.gen[Cuenta]
given JsonDecoder[Cuenta] = DeriveJsonDecoder.gen[Cuenta]

given JsonEncoder[Jugador] = DeriveJsonEncoder.gen[Jugador]
given JsonDecoder[Jugador] = DeriveJsonDecoder.gen[Jugador]

given JsonEncoder[GameStatus] = DeriveJsonEncoder.gen[GameStatus]
given JsonDecoder[GameStatus] = DeriveJsonDecoder.gen[GameStatus]

given JsonEncoder[Game] = DeriveJsonEncoder.gen[Game]
given JsonDecoder[Game] = DeriveJsonDecoder.gen[Game]

given JsonEncoder[ChatMessage] = DeriveJsonEncoder.gen[ChatMessage]
given JsonDecoder[ChatMessage] = DeriveJsonDecoder.gen[ChatMessage]
