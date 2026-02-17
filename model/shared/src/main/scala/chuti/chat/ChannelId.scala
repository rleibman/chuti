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

package chuti.chat

opaque type ChannelId = Long

object ChannelId {

  given CanEqual[ChannelId, ChannelId] = CanEqual.derived

  val empty:         ChannelId = 0
  val lobbyChannel:  ChannelId = -1
  val directChannel: ChannelId = -2

  def apply(channelId: Long): ChannelId = channelId

  extension (channelId: ChannelId) {

    def value:                       Long = channelId
    def nonEmpty:                    Boolean = channelId.value != ChannelId.empty
    def isEmpty:                     Boolean = channelId.value == ChannelId.empty
    def orElse(other: => ChannelId): ChannelId = if (channelId.isEmpty) other else channelId
    def toOption:                    Option[ChannelId] = if (channelId.isEmpty) None else Some(channelId)

  }

}
