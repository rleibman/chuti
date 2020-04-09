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
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, React, ScalaComponent}
import pages.{LoginPage, PasswordRecoveryAfterTokenPage, PasswordRecoveryPage, RegistrationPage}
import react.Toast
//import react.Toast
import org.scalajs.dom.window

object Mode extends Enumeration {
  type Mode = Value
  val login, registration, passwordRecoveryRequest, passwordRecoveryAfterToken = Value
}
import app.Mode._
object LoginControllerState {
  val ctx: Context[LoginControllerState] = React.createContext(LoginControllerState())
}

case class LoginControllerState(
  mode: Mode = login,
  onModeChanged: Mode => Callback = { _ =>
    Callback.empty
  }
)
object LoginController {
  lazy val queryParams = new org.scalajs.dom.experimental.URLSearchParams(window.location.search)

  case class State(
    context: LoginControllerState = LoginControllerState(),
    token:   Option[String] = None
  )

  class Backend($ : BackendScope[_, State]) {

    def onModeChanged(mode: Mode): Callback =
      $.modState(s => s.copy(context = s.context.copy(mode = mode)))

    def render(state: State) =
      LoginControllerState.ctx.provide(state.context) {
        <.div(
          Toast.render(),
          state.context.mode match {
            case Mode.login                      => LoginPage()
            case Mode.registration               => RegistrationPage()
            case Mode.passwordRecoveryRequest    => PasswordRecoveryPage()
            case Mode.passwordRecoveryAfterToken => PasswordRecoveryAfterTokenPage() //(state.token)
          }
        )
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
              if (queryParams.has("passwordReset")) Mode.passwordRecoveryAfterToken else Mode.login,
            onModeChanged = $.backend.onModeChanged
          )
        )
      )
    }
    .build

  def apply() = component()
}
