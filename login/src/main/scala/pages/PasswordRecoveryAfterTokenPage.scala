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

import app.LoginControllerState
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react._
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, ScalaComponent}
import org.scalajs.dom.window
import react.Toast
import typings.react.reactStrings.submit
import typings.semanticUiReact.components.{Button, Form, FormField, Input, Label}
import org.scalajs.dom.raw.HTMLFormElement

object PasswordRecoveryAfterTokenPage {
  case class State(
    password:      String = "",
    passwordAgain: String = ""
  )

  class Backend($ : BackendScope[Props, State]) {
    def handleSubmit(
      state: State,
      event: ReactEventFrom[HTMLFormElement]
    ): Callback =
      if (state.password.isEmpty)
        Callback(event.preventDefault()) >> Toast.error("La contraseña no puede estar vacía")
      else if (state.password != state.passwordAgain)
        Callback(event.preventDefault()) >> Toast.error("Ambas contraseñas tienen que ser iguales")
      else
        Callback.empty

    def render(
      props: Props,
      state: State
    ): VdomElement = LoginControllerState.ctx.consume { _ =>
      <.div(
        ^.width := 800.px,
        <.div(<.img(^.src := "/unauth/images/logo.png")),
        <.h1("Recuperar contraseña!"),
        Form(action = "passwordReset", method = "post", onSubmit = { (event, _) =>
          handleSubmit(state, event)
        })(
          FormField()(
            Label()("Contraseña"),
            Input(
              required = true,
              name = "password",
              `type` = "password",
              value = state.password,
              onChange = { (_, data) =>
                $.modState(_.copy(password = data.value.get.asInstanceOf[String]))
              }
            )()
          ),
          FormField()(
            Label()("Repite contraseña"),
            Input(
              required = true,
              name = "passwordAgain",
              `type` = "password",
              value = state.passwordAgain,
              onChange = { (_, data) =>
                $.modState(_.copy(passwordAgain = data.value.get.asInstanceOf[String]))
              }
            )()
          ),
          <.input(
            ^.`type` := "hidden",
            ^.id     := "token",
            ^.name   := "token",
            ^.value  := props.token.getOrElse("")
          ),
          Button(compact = true, basic = true, `type` = submit)("Cambia contraseña"),
          Button(compact = true, basic = true, onClick = { (_, _) =>
            Callback(window.location.replace("/"))
          })("Cancel")
        )
      )
    }
  }

  case class Props(token: Option[String])

  private val component = ScalaComponent
    .builder[Props]("PasswordRecoveryAfterTokenPage")
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply(token: Option[String]): Unmounted[Props, State, Backend] = component(Props(token))
}
