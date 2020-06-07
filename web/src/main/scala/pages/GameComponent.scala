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

import app.ChutiState
import chat._
import chuti._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.StateSnapshot
import japgolly.scalajs.react.vdom.html_<^._

object GameComponent {
  case class Props(
    chutiState:     ChutiState,
    gameInProgress: StateSnapshot[Option[Game]]
  )
  case class State()

  class Backend($ : BackendScope[Props, State]) {
    def refresh(): Callback = {
      Callback.empty
    }

    def init(): Callback = {
      Callback.log(s"Initializing") >>
        refresh()
    }

    def render(
      p: Props,
      s: State
    ): VdomNode = {
      EmptyVdom
    }
  }

  private val component = ScalaComponent
    .builder[Props]
    .initialState(State())
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.init())
    .build

  def apply(
    chutiState:     ChutiState,
    gameInProgress: StateSnapshot[Option[Game]]
  ): Unmounted[Props, State, Backend] = component(Props(chutiState, gameInProgress))
}
