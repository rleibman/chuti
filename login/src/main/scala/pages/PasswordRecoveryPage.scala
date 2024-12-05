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
import japgolly.scalajs.react.*
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.vdom.html_<^.*
import org.scalajs.dom.HTMLInputElement
import react.Toast
import net.leibman.chuti.semanticUiReact.components.{Button, Form, FormField, Input, Label}
import net.leibman.chuti.semanticUiReact.distCommonjsElementsInputInputMod.InputOnChangeData

import scala.util.{Failure, Success}
import zio.json.*
object PasswordRecoveryPage {

  case class State(
    submitted: Boolean = false,
    email:     String = ""
  )

  class Backend($ : BackendScope[Unit, State]) {

    def onSubmitEmailAddress(email: String) =
      Ajax("POST", s"/passwordRecoveryRequest")
        .and(_.withCredentials = true)
        .setRequestContentTypeJson
        .send(email.toJson)
        .asAsyncCallback
        .completeWith {
          case Success(xhr) if xhr.status < 300 =>
            $.modState(_.copy(submitted = true))
          case Success(xhr) =>
            Toast.error(
              s"Hubo un error en el correo electrónico: ${xhr.statusText}, por favor intenta de nuevo."
            )
          case Failure(e: Throwable) =>
            Toast.error(
              s"Hubo un error en el correo electrónico: ${e.getLocalizedMessage}, por favor intenta de nuevo."
            )
            e.printStackTrace()
            Callback(e.printStackTrace())
        }

    def render(state: State) =
      LoginControllerState.ctx.consume { _ =>
        <.div(
          <.div(<.img(^.src := "/unauth/images/logo.png")),
          <.h1("Recuperar contraseña!"),
          if (state.submitted)
            <.div(
              <.p(
                "Te hemos mandado un correo a tu cuenta con instrucciones para recuperar to contraseña, tienes 3 horas para cambiarla, si no vas a tener que tratar de nuevo"
              ),
              <.p(
                Button()
                  .compact(true)
                  .basic(true)
                  .onClick { (_, _) =>
                    $.modState(_.copy(submitted = false))
                  }("Intenta de nuevo")
              )
            )
          else {
            <.div(
              "Por favor pon tu dirección de correo electrónico, te mandaremos un correo con instrucciones para recuperar tu contraseña",
              Form()(
                FormField()(
                  Label()("Correo electrónico"),
                  Input()
                    .required(true)
                    .name("email")
                    .`type`("email")
                    .value(state.email)
                    .onChange { (_: ReactEventFrom[HTMLInputElement], data: InputOnChangeData) =>
                      $.modState(_.copy(email = data.value.get.asInstanceOf[String]))
                    }()
                ),
                Button()
                  .compact(true)
                  .basic(true)
                  .onClick { (_, _) =>
                    onSubmitEmailAddress(state.email)
                  }("Aceptar")
              )
            )
          }
        )
      }

  }

  val component = ScalaComponent
    .builder[Unit]("PasswordRecoveryPage")
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply() = component()

}
