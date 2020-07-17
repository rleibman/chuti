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
import org.scalajs.dom.window
import typings.react.reactStrings.submit
import typings.semanticUiReact.components._
import typings.semanticUiReact.genericMod.SemanticCOLORS

object LoginPage {

  case class State()

  class Backend($ : BackendScope[Props, State]) {
    val query: String = window.location.search.substring(1)
    val isBad: Boolean = query.contains("bad=true")
    def render(
      P: Props,
      S: State
    ): VdomElement =
      LoginControllerState.ctx.consume { context =>
        <.div(
          <.div(<.img(^.src := "/unauth/images/logo.png")),
          <.div(
            "Bienvenido a el juego de chuti... por favor pon tu nombre y contraseña, o registrate si todavía no lo has hecho!"
          ),
          <.span(
            Message(color = SemanticCOLORS.red)(
              "Contraseña errónea! Tu correo electrónico y contraseña no están en el sistema, intentalo de nuevo!"
            )
          ).when(isBad),
          P.messageForScreen.fold(EmptyVdom: VdomNode)(str => <.span(Message()(str))),
          <.form(
            ^.action    := "doLogin",
            ^.method    := "post",
            ^.className := "ui form",
            ^.width     := 800.px,
            FormField()(
              Label()("Correo Electrónico"),
              Input(required = true, name = "email", `type` = "email")()
            ),
            FormField()(
              Label()("Contraseña"),
              Input(`type` = "password", required = true, name = "password")()
            ),
            Button(compact = true, basic = true, `type` = submit)("Entrar")
          ),
          Button(
            compact = true,
            basic = true,
            onClick = { (_, _) =>
              context.onModeChanged(Mode.registration, None)
            }
          )("Registrarse por primera vez"),
          Button(
            compact = true,
            basic = true,
            onClick = { (_, _) =>
              context.onModeChanged(Mode.passwordRecoveryRequest, None)
            }
          )("Perdí mi Contraseña")
        )
      }
  }

  val component = ScalaComponent
    .builder[Props]("LoginPage")
    .initialState(State())
    .renderBackend[Backend]
    .build

  case class Props(messageForScreen: Option[String])

  def apply(messageForScreen: Option[String]): Unmounted[Props, State, Backend] =
    component(Props(messageForScreen))
}
