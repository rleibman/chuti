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

import _root_.util.LocalizedMessages
import caliban.client.scalajs.WebSocketHandler
import chuti.*
import japgolly.scalajs.react.React.Context
import japgolly.scalajs.react.{Callback, React}
import org.scalajs.dom.window
import pages.LobbyComponent.ExtUser

import java.util.Locale

enum GameViewMode {

  case lobby, game, none

}

enum GlobalDialog {

  case cuentas, none

}

case class ChutiState(
  flipFicha:     Ficha => Callback = _ => Callback.empty,
  ultimoBorlote: Option[Borlote] = None,
  flippedFichas: Set[Ficha] = Set.empty,
  onSessionChanged: (Option[User], String) => Callback = (
    _,
    _
  ) => Callback.empty,
  languageTag: String = {
    val loc: String | Null = window.sessionStorage.getItem("languageTag")
    println(s"languageTag = $loc")
    if (loc == null || loc.isEmpty)
      "es-MX"
    else loc
  },
  user:           Option[User] = None,
  isFirstLogin:   Boolean = false,
  wallet:         Option[UserWallet] = None,
  serverVersion:  Option[String] = None,
  gameInProgress: Option[Game] = None,
  modGameInProgress: (Game => Game, Callback) => Callback = (
    _,
    c
  ) => c,
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
  playSound:             String => Callback = _ => Callback.empty
) {

  lazy val locale: Locale =
    if (Locale.forLanguageTag(languageTag) == null) Locale.forLanguageTag("es-MX").nn
    else Locale.forLanguageTag(languageTag).nn

  def isFlipped(ficha: Ficha): Boolean = flippedFichas.contains(ficha)

  lazy val usersAndFriends: Seq[ExtUser] =
    loggedInUsers.map(user => ExtUser(user, friends.exists(_.id == user.id), isLoggedIn = true)) ++
      friends
        .filterNot(u => loggedInUsers.exists(_.id == u.id)).map(
          ExtUser(_, isFriend = true, isLoggedIn = false)
        ).sortBy(_.user.name)

  object ChutiMessages extends LocalizedMessages {

    override def bundles: Map[String, ChutiMessages.MessageBundle] =
      Map(
        "es" -> MessageBundle(
          "es",
          Map(
            "Chuti.jugador"       -> "Jugador",
            "Chuti.cuentas"       -> "Cuentas",
            "Chuti.total"         -> "Total",
            "Chuti.satoshi"       -> "Satoshi",
            "Chuti.entrarAlJuego" -> "Entrar al Juego"
          )
        ),
        "en" -> MessageBundle(
          "en",
          Map(
            "Chuti.jugador"       -> "Player",
            "Chuti.cuentas"       -> "Accounting",
            "Chuti.total"         -> "Total",
            "Chuti.satoshi"       -> "Satoshi",
            "Chuti.entrarAlJuego" -> "Enter game"
          )
        )
      )

  }

}

object ChutiState {

  val ctx: Context[ChutiState] = React.createContext(ChutiState())

}
