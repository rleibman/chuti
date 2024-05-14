/*
 * Copyright (c) 2024 Roberto Leibman
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package app

import java.util.Locale

import caliban.client.scalajs.WebSocketHandler
import chuti.*
import japgolly.scalajs.react.React.Context
import japgolly.scalajs.react.{Callback, React}
import org.scalajs.dom.window
import pages.LobbyComponent.ExtUser
import _root_.util.LocalizedMessages

enum GameViewMode {

  case lobby, game, none

}

enum GlobalDialog {

  case cuentas, none

}

case class ChutiState(
  flipFicha:        Ficha => Callback = _ => Callback.empty,
  ultimoBorlote:    Option[Borlote] = None,
  flippedFichas:    Set[Ficha] = Set.empty,
  onSessionChanged: (Option[User], String) => Callback = (_, _) => Callback.empty,
  languageTag: String = {
    val loc: String | Null = window.sessionStorage.getItem("languageTag")
    println(s"languageTag = $loc")
    if (loc == null || loc.isEmpty)
      "es-MX"
    else loc
  },
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
  playSound:             String => Callback = _ => Callback.empty
) {

  lazy val locale: Locale =
    if (Locale.forLanguageTag(languageTag) == null) Locale.forLanguageTag("es-MX").nn else Locale.forLanguageTag(languageTag).nn

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
