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

case class ChannelId(value: Int) extends AnyVal

object ChannelId {

  // some special channels
  val lobbyChannel:  ChannelId = ChannelId(-1)
  val directChannel: ChannelId = ChannelId(-2)

}

case class ChatMessage(
  fromUser:  User,
  msg:       String,
  channelId: ChannelId,
  toUser:    Option[User] = None,
  date:      Instant = Instant.now
)
