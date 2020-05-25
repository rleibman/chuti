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

import app.{LoginControllerState, Mode}
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.raw.HTMLButtonElement
import org.scalajs.dom.window
import typings.semanticUiReact.buttonButtonMod.ButtonProps
import typings.semanticUiReact.components.{Button, FormField, Input, Label, Message}
import typings.react.reactStrings.submit
import typings.semanticUiReact.genericMod.SemanticCOLORS

object LoginPage {

  case class State()

  class Backend($ : BackendScope[Props, State]) {
    val query: String = window.location.search.substring(1)
    val isBad: Boolean = query.contains("bad=true")
    def render(P: Props, S: State): VdomElement =
      LoginControllerState.ctx.consume { context =>
        <.div(
          <.span(
            Message(color = SemanticCOLORS.red)(
              "Bad Login! Your email or password did not match, please try again!"
            )
          ).when(isBad),
          P.messageForScreen.fold(EmptyVdom: VdomNode)(str => <.span(Message()(str))),
          <.form(
            ^.action    := "doLogin",
            ^.method    := "post",
            ^.className := "ui form",
            ^.width     := 800.px,
            FormField()(
              Label()("Email"),
              Input(required = true, name = "email", `type` = "email")()
            ),
            FormField()(
              Label()("Password"),
              Input(`type` = "password", required = true, name = "password")()
            ),
            Button(`type` = submit)("Login")
          ),
          Button(onClick = { (_: ReactMouseEventFrom[HTMLButtonElement], _: ButtonProps) =>
            context.onModeChanged(Mode.registration, None)
          })("I'm new to this, create new account"),
          Button(onClick = { (_: ReactMouseEventFrom[HTMLButtonElement], _: ButtonProps) =>
            context.onModeChanged(Mode.passwordRecoveryRequest, None)
          })("I lost my password")
        )
      }
  }

  val component = ScalaComponent
    .builder[Props]("LoginPage")
    .initialState(State())
    .renderBackend[Backend]
    .build

  case class Props(messageForScreen: Option[String])

  def apply(messageForScreen: Option[String]): Unmounted[Props, State, Backend] = component(Props(messageForScreen))
}
