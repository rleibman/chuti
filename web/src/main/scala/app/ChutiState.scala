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
import app.GlobalDialog.GlobalDialog
import caliban.client.scalajs.WebSocketHandler
import chuti.{Game, User, UserWallet}
import japgolly.scalajs.react.React.Context
import japgolly.scalajs.react.{Callback, React}
import pages.LobbyComponent.ExtUser
object GameViewMode extends Enumeration {
  type GameViewMode = Value
  val lobby, game, none = Value
}

object GlobalDialog extends Enumeration {
  type GlobalDialog = Value
  val cuentas, none = Value
}

case class ChutiState(
  onUserChanged:         Option[User] => Callback = _ => Callback.empty,
  user:                  Option[User] = None,
  wallet:                Option[UserWallet] = None,
  serverVersion:         Option[String] = None,
  gameInProgress:        Option[Game] = None,
  modGameInProgress:     (Game => Game) => Callback = _ => Callback.empty,
  onRequestGameRefresh:  Callback = Callback.empty,
  gameStream:            Option[WebSocketHandler] = None,
  gameViewMode:          GameViewMode = GameViewMode.lobby,
  onGameViewModeChanged: GameViewMode => Callback = _ => Callback.empty,
  showDialog:            GlobalDialog => Callback = _ => Callback.empty,
  friends:               Seq[User] = Seq.empty,
  userStream:            Option[WebSocketHandler] = None,
  loggedInUsers:         Seq[User] = Seq.empty,
  currentDialog:         GlobalDialog = GlobalDialog.none
) {
  lazy val usersAndFriends: Seq[ExtUser] =
    loggedInUsers.map(user => ExtUser(user, friends.exists(_.id == user.id), isLoggedIn = true)) ++
      friends
        .filterNot(u => loggedInUsers.exists(_.id == u.id)).map(
          ExtUser(_, isFriend = true, isLoggedIn = false)
        ).sortBy(_.user.name)

}

object ChutiState {
  val ctx: Context[ChutiState] = React.createContext(ChutiState())
}
