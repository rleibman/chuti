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

import chat.ChatComponent
import japgolly.scalajs.react.React.Context
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom

import scala.scalajs.js.annotation.JSExport

object ChutiApp {
  @JSExport
  def main(args: Array[String]): Unit = {

    val component = Content()

    component.renderIntoDOM(dom.document.getElementById("content"))
    ()

  }
}

object ParentComponent {
  val ctx: Context[ParentState] = React.createContext(ParentState())

  case class ChildProps(childName: String, addMenuProvider: (() => Seq[(String, Callback)]) => Callback)
  case class ChildState(childName: String, childString: String = "Child String", strings:         Seq[String] = Seq.empty)

  class ChildBackend($ : BackendScope[ChildProps, ChildState]) {
    def menuProvider(s: ChildState)(): Seq[(String, Callback)] = {
      val items = Seq(
        (
          "MenuItem1",
          Callback
            .log("Hit MenuItem1") >> $.modState(s => s.copy(strings = s.strings :+ "1")) >> Callback(
            println("Hello World1")
          )
        ),
        (
          "MenuItem2",
          Callback
            .log("Hit MenuItem2") >> $.modState(s => s.copy(strings = s.strings :+ "2")) >> Callback(
            println("Hello World2")
          )
        ),
        (
          s.childString,
          Callback
            .log("Hit childString") >> $.modState(s => s.copy(strings = s.strings :+ s.childString)) >> Callback(
            println("childString")
          )
        ),
      )
      if(s.childName == "child1") items.drop(1) else items
    }

    def render(s: ChildState): VdomElement = {
      ctx.consume { useThisState =>
        <.div(<.h1("Stuff"), <.h2(useThisState.someString), s.strings.toVdomArray(str => <.div(str)))
      }
    }
  }

  private val childComponent = ScalaComponent
    .builder[ChildProps]("child")
    .initialStateFromProps(p => ChildState(p.childName))
    .renderBackend[ChildBackend]
    .componentDidMount($ => $.props.addMenuProvider($.backend.menuProvider($.state)))
    .build

  def ChildComponent(props: ChildProps): Unmounted[ChildProps, ChildState, ChildBackend] = childComponent(props)

  case class ParentState(someString: String = "This is just a string", menuProviders: Seq[() => Seq[(String, Callback)]] = Seq.empty)

  class ParentBackend($ : BackendScope[_, ParentState]) {

    def addMenuProvider(menuProvider: () => Seq[(String, Callback)]): Callback = {
      $.modState(s => s.copy(menuProviders = s.menuProviders :+ menuProvider))
    }

    def render(state: ParentState): VdomElement = {
      ctx.provide(state) {
        ctx.consume { useThisState =>
          <.div(
            useThisState.menuProviders
              .flatMap(_ ()).toVdomArray(i =>
              <.div(<.button(^.onClick ==> { _ =>
                i._2
              }, i._1))
            ),
            <.br(),
            ChildComponent(ChildProps("child1", addMenuProvider)),
            ChildComponent(ChildProps("child2", addMenuProvider))
          )
        }
      }
    }
  }

  private val parentComponent = ScalaComponent
    .builder[Unit]("parent")
    .initialState(ParentState())
    .renderBackend[ParentBackend]
    .build

  def apply(): Unmounted[Unit, ParentState, ParentBackend] = parentComponent()
}
