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

import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.raw.HTMLButtonElement
import org.scalajs.dom.window
import typings.semanticUiReact.buttonButtonMod.ButtonProps
import typings.semanticUiReact.components.{Button, FormField, Input, Label, Message}
import typings.react.reactStrings.submit
import typings.semanticUiReact.genericMod.SemanticCOLORS

object LoginPage {

  case class State()

  class Backend($ : BackendScope[_, State]) {
    val query = window.location.search.substring(1)
    val isBad = query.contains("bad=true")
    def render(S: State) =
      LoginControllerState.ctx.consume { context =>
        <.div(
          <.img(
            ^.src           := "/unauth/images/meal-o-rama-big.png",
            ^.paddingBottom := 40.px,
            ^.paddingTop    := 40.px
          ),
          <.span(
            Message(color = SemanticCOLORS.red)(
              "Bad Login! Your account, user name or password did not match, please try again!"
            )
          ).when(isBad),
          <.form(
            ^.action    := "doLogin",
            ^.method    := "post",
            ^.className := "ui form",
            ^.width     := 800.px,
            FormField()(
              Label()("Account Name"),
              Input(required = true, name = "accountname")()
            ),
            FormField()(
              Label()("User Name"),
              Input(required = true, name = "username")()
            ),
            FormField()(
              Label()("Password"),
              Input(`type` = "password", required = true, name = "password")()
            ),
            Button(`type` = submit)("Login")
          ),
          Button(onClick = { (_: ReactMouseEventFrom[HTMLButtonElement], _: ButtonProps) =>
            context.onModeChanged(Mode.newAccount)
          })("I'm new to this, create new account"),
          Button(onClick = { (_: ReactMouseEventFrom[HTMLButtonElement], _: ButtonProps) =>
            context.onModeChanged(Mode.passwordRecoveryRequest)
          })("I lost my password")
        )
      }
  }

  val component = ScalaComponent
    .builder[Unit]("LoginPage")
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply() = component()
}
