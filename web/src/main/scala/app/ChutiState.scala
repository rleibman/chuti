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

package app

import app.GameViewMode.GameViewMode
import caliban.client.scalajs.WebSocketHandler
import chuti.{Game, GameEvent, User}
import japgolly.scalajs.react.React.Context
import japgolly.scalajs.react.{Callback, React}
object GameViewMode extends Enumeration {
  type GameViewMode = Value
  val lobby, game, none = Value
}

case class ChutiState(
  onUserChanged: Option[User] => Callback = _ => Callback.empty,
  user:          Option[User] = None,
  serverVersion: Option[String] = None,
  //TODO events have to get applied to the local copy of the game in order, if we receive
  //TODO an event out of order we need to wait until we have it's match, and if a timeout passes
  //TODO without receiving it, we need to ask the server for the full state of the game again
  //    gameEventQueue: SortedSet[GameEvent] =
  //      SortedSet.empty(Ordering.by[GameEvent, Option[Int]](_.index))
  gameInProgress:          Option[Game] = None,
  onGameInProgressChanged: Option[Game] => Callback = _ => Callback.empty,
  onRequestGameRefresh:    Callback = Callback.empty,
  gameStream:              Option[WebSocketHandler] = None,
  gameEventQueue:          Seq[GameEvent] = Seq.empty,
  gameViewMode:            GameViewMode = GameViewMode.lobby,
  onGameViewModeChanged:   GameViewMode => Callback = _ => Callback.empty
) {}

object ChutiState {
  val ctx: Context[ChutiState] = React.createContext(ChutiState())
}
