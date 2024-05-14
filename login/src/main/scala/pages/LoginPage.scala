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

import app.{LoginControllerState, Mode}
import japgolly.scalajs.react.*
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^.*
import org.scalajs.dom.window
import net.leibman.chuti.react.reactStrings.submit
import net.leibman.chuti.semanticUiReact.components.*
import net.leibman.chuti.semanticUiReact.genericMod.SemanticCOLORS

object LoginPage {

  case class State()

  class Backend($ : BackendScope[Props, State]) {

    val query: String = window.location.search.substring(1).nn
    val isBad: Boolean = query.contains("bad=true")
    def render(
      P: Props
    ): VdomElement =
      LoginControllerState.ctx.consume { context =>
        <.div(
          <.div(<.img(^.src := "/unauth/images/logo.png")),
          <.div(
            "Bienvenido a el juego de chuti... por favor pon tu nombre y contraseña, o registrate si todavía no lo has hecho!"
          ),
          <.span(
            Message().color(SemanticCOLORS.red)(
              "Contraseña errónea! Tu correo electrónico y contraseña no están en el sistema (o no han sido activados), intentalo de nuevo!"
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
              Input().required(true).name("email").`type`("email")()
            ),
            FormField()(
              Label()("Contraseña"),
              Input().`type`("password").required(true).name("password")()
            ),
            Button().compact(true).basic(true).`type`(submit)("Entrar")
          ),
          Button()
            .compact(true)
            .basic(true)
            .onClick { (_, _) =>
              context.onModeChanged(Mode.registration, None)
            }("Registrarse por primera vez"),
          Button()
            .compact(true)
            .basic(true)
            .onClick { (_, _) =>
              context.onModeChanged(Mode.passwordRecoveryRequest, None)
            }("Perdí mi Contraseña")
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
