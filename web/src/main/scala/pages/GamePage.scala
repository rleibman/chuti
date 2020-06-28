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

import app.ChutiState
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
  object Mode extends Enumeration {
    type Mode = Value
    val lobby, game, none = Value
  }
  import Mode._

  case class State(
    mode:           Mode
  )

  class Backend($ : BackendScope[_, State]) {

//    def menuProvider(s: State)(): Seq[(String, Callback)] = {
//      s.gameInProgress
//        .filter(_.gameStatus.enJuego).toSeq.flatMap(_ =>
//          Seq(("Entrar al juego", $.modState(_.copy(mode = Mode.game))))
//        ) ++
//        Seq(("Lobby", $.modState(_.copy(mode = Mode.lobby))))
//    }

    //    private val gameDecoder = implicitly[Decoder[Game]]

    def onModeChanged(
      opt:      Option[Mode],
      callback: Callback
    ): Callback = {
      $.modState(_.copy(mode = opt.getOrElse(Mode.none))) >> callback
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
      s: State
    ): VdomNode = {
      ChutiState.ctx.consume { chutiState =>
        println(s"ReRendering GamePage with state $s")

        def renderDebugBar = chutiState.gameInProgress.fold(EmptyVdom) { game =>
          <.div(
            ^.className := "debugBar",
            ^.border := "10px solid orange",
            <.span(s"Game Status = ${game.gameStatus}, "),
            <.span(s"Game Index = ${game.currentEventIndex}, "),
            <.span(s"Quien canta = ${game.quienCanta.map(_.user.name)}, "),
            <.span(s"Mano = ${game.mano.map(_.user.name)}, "),
            <.span(s"Turno = ${game.turno.map(_.user.name)}, ")
          )
        }

        val mode = if (chutiState.gameInProgress.isEmpty && s.mode == game) {
          //I don't want to be in the lobby if the game is loading
          none
        } else {
          if (s.mode != Mode.none) {
            window.sessionStorage.setItem("gamePageMode", s.mode.toString)
          }
          s.mode
        }

        mode match {
          case Mode.lobby =>
            VdomArray(
              //            renderDebugBar,
              LobbyComponent(
                StateSnapshot(chutiState.gameInProgress)(onGameInProgressChanged(chutiState)),
                StateSnapshot(mode)(onModeChanged)
              )
            )
          case Mode.game =>
            VdomArray(
              //            renderDebugBar,
              GameComponent(
                StateSnapshot(chutiState.gameInProgress)(onGameInProgressChanged(chutiState)),
                StateSnapshot(mode)(onModeChanged)
              )
            )
          case _ =>
            <.div(^.hidden := true, "We should never, ever get here, you should not be seeing this")
        }
      }
    }
  }

  private val component = ScalaComponent
    .builder[Unit]("GamePageInner")
    .initialState {
      val modeStr = window.sessionStorage.getItem("gamePageMode")
      val mode = {
        val ret =
          try {
            Mode.withName(modeStr)
          } catch {
            case _: Throwable =>
              Mode.lobby
          }
        if (ret == none) {
          //Should not happen, but let's be sure it doesn't happen again
          window.sessionStorage.setItem("gamePageMode", Mode.lobby.toString)
          Mode.lobby
        } else {
          ret
        }
      }
      State(mode = mode)
    }
    .renderBackend[Backend]
    .build

  def apply(): Unmounted[Unit, State, Backend] = component()
}
