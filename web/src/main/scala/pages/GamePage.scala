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

package pages

import java.time.ZoneOffset

import app.{ChutiState, GameViewMode}
import caliban.client.scalajs.ScalaJSClientAdapter
import chuti.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.{StateSnapshot, TimerSupport}
import japgolly.scalajs.react.vdom.html_<^.*
import org.scalajs.dom.window

object GamePage extends ChutiPage with ScalaJSClientAdapter with TimerSupport {

  import app.GameViewMode.*

  case class State()

  class Backend($ : BackendScope[Props, State]) {

    def onModeChanged(
      p: Props
    )(
      opt:      Option[GameViewMode],
      callback: Callback
    ): Callback = {
      p.chutiState.onGameViewModeChanged(opt.getOrElse(GameViewMode.none)) >> callback
    }

    def onGameInProgressChanged(
      chutiState: ChutiState
    )(
      opt:      Option[Option[Game]],
      callback: Callback
    ): Callback = {
      opt.flatten.fold(Callback.empty)(g => chutiState.modGameInProgress(_ => g, callback))
    }

    def render(
      p: Props
    ): VdomNode = {
      ChutiState.ctx.consume { chutiState =>
        val gameViewMode =
          if (chutiState.gameInProgress.isEmpty && p.chutiState.gameViewMode == game)
            // I don't want to be in the lobby if the game is loading
            none
          else {
            if (p.chutiState.gameViewMode != GameViewMode.none)
              window.sessionStorage.setItem("gamePageMode", p.chutiState.gameViewMode.toString)
            p.chutiState.gameViewMode
          }

        gameViewMode match {
          case GameViewMode.lobby =>
            LobbyComponent(
              StateSnapshot(chutiState.gameInProgress)(onGameInProgressChanged(chutiState)),
              StateSnapshot(gameViewMode)(onModeChanged(p))
            )
          case GameViewMode.game =>
            GameComponent(
              chutiState.gameInProgress,
              StateSnapshot(gameViewMode)(onModeChanged(p))
            )
          case _ =>
            <.div(^.hidden := true, "We should never, ever get here, you should not be seeing this")
        }
      }
    }

  }
  case class Props(chutiState: ChutiState)
  import scala.language.unsafeNulls
  given messageReuse:      Reusability[ChatMessage] = Reusability.by(msg => (msg.date.getEpochSecond, msg.fromUser.id.map(_.userId)))
  given gameReuse:         Reusability[Game] = Reusability.by(game => (game.id.map(_.gameId), game.currentEventIndex))
  given userIdReuse:       Reusability[UserId] = Reusability.by(_.userId)
  given userReuse:         Reusability[User] = Reusability.by(_.id)
  given bigDecimalReuse:   Reusability[BigDecimal] = Reusability.by(_.toLong)
  given walletReuse:       Reusability[UserWallet] = Reusability.derive[UserWallet]
  given gameViewModeReuse: Reusability[GameViewMode] = Reusability.by(_.toString)
  given propsReuse:        Reusability[Props] = Reusability.by(_.chutiState.gameViewMode.toString)
  given stateReuse:        Reusability[State] = Reusability.derive[State]

  private val component = ScalaComponent
    .builder[Props]("GamePageInner")
    .initialState(State())
    .renderBackend[Backend]
    .configure(Reusability.shouldComponentUpdate)
    .build

  private def inner(chutiState: ChutiState): Unmounted[Props, State, Backend] = component(Props(chutiState))

  private def innerComponent =
    ScalaComponent
      .builder[Unit]
      .renderStatic {
        ChutiState.ctx.consume(chutiState => inner(chutiState))
      }
      .build

  def apply(): Unmounted[Unit, Unit, Unit] = innerComponent()

}
