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
import chuti.{Ficha, Game, User, UserWallet}
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
  flipFicha:             Ficha => Callback = _ => Callback.empty,
  flippedFichas:         Set[Ficha] = Set.empty,
  onUserChanged:         Option[User] => Callback = _ => Callback.empty,
  user:                  Option[User] = None,
  isFirstLogin:          Boolean = false,
  wallet:                Option[UserWallet] = None,
  serverVersion:         Option[String] = None,
  gameInProgress:        Option[Game] = None,
  modGameInProgress:     (Game => Game, Callback) => Callback = (_, c) => c,
  onRequestGameRefresh:  () => Callback = { () => Callback.empty },
  gameStream:            Option[WebSocketHandler] = None,
  gameViewMode:          GameViewMode = GameViewMode.lobby,
  onGameViewModeChanged: GameViewMode => Callback = _ => Callback.empty,
  showDialog:            GlobalDialog => Callback = _ => Callback.empty,
  friends:               List[User] = List.empty,
  userStream:            Option[WebSocketHandler] = None,
  loggedInUsers:         List[User] = List.empty,
  currentDialog:         GlobalDialog = GlobalDialog.none,
  muted:                 Boolean = false,
  toggleSound:           Callback = Callback.empty,
  playSound:             Option[String] => Callback = _ => Callback.empty
) {
  def isFlipped(ficha: Ficha): Boolean = flippedFichas.contains(ficha)

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
