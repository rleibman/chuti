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

import chat.ChatComponent
import chuti.{Game, GameEvent}
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._

import scala.collection.SortedSet

object GamePage extends ChutiPage {

  case class State(
    game: Option[Game] = None,
    //TODO events have to get applied to the local copy of the game in order, if we receive
    //TODO an event out of order we need to wait until we have it's match, and if a timeout passes
    //TODO without receiving it, we need to ask the server for the full state of the game again
    gameEventQueue: SortedSet[GameEvent] =
      SortedSet.empty(Ordering.by[GameEvent, Option[Int]](_.index))
  )

  class Backend($ : BackendScope[_, State]) {

    def render(S: State): VdomElement = {
      <.div()
    }
  }

  val component = ScalaComponent
    .builder[Unit]("GamePage")
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply(): Unmounted[Unit, State, Backend] = component()

}
