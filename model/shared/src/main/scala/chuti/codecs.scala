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
import chat.given 

given JsonCodec[UserId] = JsonCodec.long.transform(UserId.apply, _.value)
given JsonCodec[ConnectionId] = JsonCodec.string.transform(ConnectionId.apply, _.value)
given JsonCodec[GameId] = JsonCodec.long.transform(GameId.apply, _.value)
given JsonCodec[Triunfo] = JsonCodec.derived[Triunfo]
given JsonCodec[Fila] = JsonCodec.derived[Fila]
given JsonCodec[CuantasCantas] = JsonCodec.derived[CuantasCantas]
given JsonCodec[(UserId, Seq[Fila])] = JsonCodec.derived[(UserId, Seq[Fila])]
given JsonCodec[Borlote] = JsonCodec.derived[Borlote]
given JsonCodec[PlayEvent] = JsonCodec.derived[PlayEvent]
given JsonCodec[GameEvent] = JsonCodec.derived[GameEvent]
given JsonCodec[Cuenta] = JsonCodec.derived[Cuenta]
given JsonCodec[Jugador] = JsonCodec.derived[Jugador]
given JsonCodec[GameStatus] = JsonCodec.derived[GameStatus]
given JsonCodec[Game] = JsonCodec.derived[Game]
