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

import app.LoginControllerState
import japgolly.scalajs.react.*
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.vdom.html_<^.*
import org.scalajs.dom.HTMLInputElement
import react.Toast
import net.leibman.chuti.semanticUiReact.components.{Button, Form, FormField, Input, Label}
import net.leibman.chuti.semanticUiReact.inputInputMod.InputOnChangeData
import zio.json.*

import scala.util.{Failure, Success}

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
