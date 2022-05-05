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

  class Backend($ : BackendScope[?, State]) {

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
            //I don't want to be in the lobby if the game is loading
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

  implicit val messageReuse: Reusability[ChatMessage] = Reusability.by(msg =>
    (msg.date.getEpochSecond, msg.fromUser.id.map(_.value))
  )
  implicit val gameReuse: Reusability[Game] =
    Reusability.by(game => (game.id.map(_.value), game.currentEventIndex))
  implicit val userIdReuse:       Reusability[UserId] = Reusability.by(_.value)
  implicit val userReuse:         Reusability[User] = Reusability.by(_.id)
  implicit val bigDecimalReuse:   Reusability[BigDecimal] = Reusability.by(_.toLong)
  implicit val walletReuse:       Reusability[UserWallet] = Reusability.derive[UserWallet]
  implicit val gameViewModeReuse: Reusability[GameViewMode] = Reusability.by(_.toString)
  implicit private val propsReuse: Reusability[Props] =
    Reusability.by(_.chutiState.gameViewMode.toString)
  implicit private val stateReuse: Reusability[State] = Reusability.derive[State]

  private val component = ScalaComponent
    .builder[Props]("GamePageInner")
    .initialState(State())
    .renderBackend[Backend]
    .configure(Reusability.shouldComponentUpdate)
    .build

  private def inner(chutiState: ChutiState): Unmounted[Props, State, Backend] =
    component(Props(chutiState))

  private def innerComponent =
    ScalaComponent
      .builder[Unit]
      .renderStatic {
        ChutiState.ctx.consume(chutiState => inner(chutiState))
      }
      .build

  def apply(): Unmounted[Unit, Unit, Unit] = innerComponent()
}
