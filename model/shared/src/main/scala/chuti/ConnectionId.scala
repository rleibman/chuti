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

import zio.json.{JsonDecoder, JsonEncoder}

opaque type ConnectionId = String

object ConnectionId {

  given CanEqual[ConnectionId, ConnectionId] = CanEqual.derived
  given JsonEncoder[ConnectionId] = JsonEncoder.string
  given JsonDecoder[ConnectionId] = JsonDecoder.string

  val empty:  ConnectionId = ConnectionId("")
  val random: ConnectionId = ConnectionId(java.util.UUID.randomUUID().toString)

  def apply(connectionId: String): ConnectionId = connectionId

  extension (connectionId: ConnectionId) {

    def value:    String = connectionId
    def nonEmpty: Boolean = connectionId.value != ConnectionId.empty

  }

}
