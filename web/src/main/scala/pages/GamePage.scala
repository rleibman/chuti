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

import java.net.URI
import java.util.UUID

import app.{ChutiState, GameViewMode}
import caliban.client.scalajs.ScalaJSClientAdapter
import chat.ChatComponent
import chuti._
import game.GameClient.{Queries, Subscriptions}
import io.circe.generic.auto._
import io.circe.{Decoder, Json}
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.{StateSnapshot, TimerSupport}
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.window

object GamePage extends ChutiPage with ScalaJSClientAdapter with TimerSupport {
  import app.GameViewMode._

  case class State()

  class Backend($ : BackendScope[_, State]) {

    def init(
      p: Props,
      s: State
    ): Callback = {
      Callback.empty
    }
    //    private val gameDecoder = implicitly[Decoder[Game]]

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
      chutiState.onGameInProgressChanged(opt.flatten) >> callback
    }

    def render(
      p: Props,
      s: State
    ): VdomNode = {
      ChutiState.ctx.consume { chutiState =>
        println(s"ReRendering GamePage with state $s")

        def renderDebugBar = chutiState.gameInProgress.fold(EmptyVdom) { game =>
          <.div(
            ^.className := "debugBar",
            ^.border    := "10px solid orange",
            <.span(s"Game Status = ${game.gameStatus}, "),
            <.span(s"Game Index = ${game.currentEventIndex}, "),
            <.span(s"Quien canta = ${game.quienCanta.map(_.user.name)}, "),
            <.span(s"Mano = ${game.mano.map(_.user.name)}, "),
            <.span(s"Turno = ${game.turno.map(_.user.name)}, ")
          )
        }

        val gameViewMode =
          if (chutiState.gameInProgress.isEmpty && p.chutiState.gameViewMode == game) {
            //I don't want to be in the lobby if the game is loading
            none
          } else {
            if (p.chutiState.gameViewMode != GameViewMode.none) {
              window.sessionStorage.setItem("gamePageMode", p.chutiState.gameViewMode.toString)
            }
            p.chutiState.gameViewMode
          }

        gameViewMode match {
          case GameViewMode.lobby =>
            VdomArray(
              //            renderDebugBar,
              LobbyComponent(
                StateSnapshot(chutiState.gameInProgress)(onGameInProgressChanged(chutiState)),
                StateSnapshot(gameViewMode)(onModeChanged(p))
              )
            )
          case GameViewMode.game =>
            VdomArray(
              //            renderDebugBar,
              GameComponent(
                StateSnapshot(chutiState.gameInProgress)(onGameInProgressChanged(chutiState)),
                StateSnapshot(gameViewMode)(onModeChanged(p))
              )
            )
          case _ =>
            <.div(^.hidden := true, "We should never, ever get here, you should not be seeing this")
        }
      }
    }
  }
  case class Props(chutiState: ChutiState)

  private val component = ScalaComponent
    .builder[Props]("GamePageInner")
    .initialState(State())
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.init($.props, $.state))
    .build

  private def inner(chutiState: ChutiState): Unmounted[Props, State, Backend] =
    component(Props(chutiState))

  private def innerComponent =
    ScalaComponent
      .builder[Unit]
      .renderStatic {
        ChutiState.ctx.consume { chutiState =>
          inner(chutiState)
        }
      }
      .build

  def apply(): Unmounted[Unit, Unit, Unit] = innerComponent()
}
