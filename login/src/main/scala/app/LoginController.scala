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

import japgolly.scalajs.react.React.Context
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{BackendScope, Callback, React, ScalaComponent}
import pages.*
import react.Toast
//import react.Toast
import org.scalajs.dom.window

enum Mode {

  case login, registration, passwordRecoveryRequest, passwordRecoveryAfterToken, newUserAcceptFriend

}
import app.Mode.*
object LoginControllerState {

  val ctx: Context[LoginControllerState] = React.createContext(LoginControllerState())

}

case class LoginControllerState(
  mode:             Mode = login,
  messageForScreen: Option[String] = None,
  onModeChanged: (Mode, Option[String]) => Callback = {
    (
      _,
      _
    ) => Callback.empty
  }
)
object LoginController {

  lazy val queryParams = new org.scalajs.dom.URLSearchParams(window.location.search)

  case class State(
    context: LoginControllerState = LoginControllerState(),
    token:   Option[String] = None
  )

  class Backend($ : BackendScope[Unit, State]) {

    def onModeChanged(
      mode:             Mode,
      messageForScreen: Option[String]
    ): Callback = $.modState(s => s.copy(context = s.context.copy(mode = mode, messageForScreen = messageForScreen)))

    def render(state: State) = {
      LoginControllerState.ctx.provide(state.context) {
        <.div(
          Toast.render(),
          state.context.mode match {
            case Mode.login                   => LoginPage(state.context.messageForScreen)
            case Mode.registration            => RegistrationPage()
            case Mode.passwordRecoveryRequest => PasswordRecoveryPage()
            case Mode.passwordRecoveryAfterToken =>
              PasswordRecoveryAfterTokenPage(state.token)
            case Mode.newUserAcceptFriend => NewUserAcceptFriendPage(state.token)
          }
        )
      }
    }

  }
  private val component = ScalaComponent
    .builder[Unit]("LoginController")
    .initialState(State())
    .renderBackend[Backend]
    .componentDidMount { $ =>
      $.modState(s =>
        s.copy(
          token = Option(queryParams.get("token")),
          context = s.context.copy(
            mode =
              if (queryParams.has("passwordReset"))
                Mode.passwordRecoveryAfterToken
              else if (queryParams.has("newUserAcceptFriend"))
                Mode.newUserAcceptFriend
              else
                Mode.login,
            onModeChanged = $.backend.onModeChanged,
            messageForScreen =
              if (queryParams.has("registrationFailed"))
                Option(
                  "La confirmación del registro fallo, lo sentimos mucho, tendrás que intentar de nuevo"
                )
              else if (queryParams.has("registrationSucceeded"))
                Option("Estas registrado! ya puedes usar tu correo y contraseña para ingresar!")
              else if (queryParams.has("passwordChangeFailed"))
                Option("El cambio de contraseña fallo, lo siento, tendrás que tratar otra vez.")
              else if (queryParams.has("passwordChangeSucceeded"))
                Option(
                  "El cambio de contraseña fue exitoso, ya puedes usar tu nueva contraseña para ingresar!"
                )
              else
                None
          )
        )
      )
    }
    .build

  def apply() = component()

}
