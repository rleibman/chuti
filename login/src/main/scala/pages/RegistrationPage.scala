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

import java.time.LocalDateTime

import app.{LoginControllerState, Mode}
import chuti.{User, UserCreationRequest, UserCreationResponse}
import io.circe.generic.auto._
import io.circe.parser.decode
import io.circe.syntax._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{ReactMouseEventFrom, _}
import org.scalajs.dom.raw.HTMLButtonElement
import org.scalajs.dom.window
import react.Toast
import typings.semanticUiReact.buttonButtonMod.ButtonProps
import typings.semanticUiReact.components._
import typings.semanticUiReact.genericMod.SemanticWIDTHS
import typings.semanticUiReact.inputInputMod.InputOnChangeData

object RegistrationPage {
  case class State(
    passwordPair: (String, String) = ("", ""),
    user:         User = User(id = None, email = "", name = "", created = LocalDateTime.now)
  )

  class Backend($ : BackendScope[_, State]) {
    private def onUserInputChange(fn: (User, String) => User) = {
      (
        _:   ReactEventFromInput,
        obj: InputOnChangeData
      ) =>
        $.modState(state => state.copy(user = fn(state.user, obj.value.get.asInstanceOf[String])))
    }

    private def validate(state: State): Seq[String] =
      Seq.empty[String] ++
        (if (state.passwordPair._1.trim.isEmpty) Seq("La contraseña no puede estar vacía")
         else Nil) ++
        (if (state.passwordPair._1 != state.passwordPair._2)
           Seq("Las dos contraseñas tienen que ser iguales")
         else Nil) ++
        (if (state.user.name.trim.isEmpty) Seq("El nombre no puede estar vacío") else Nil) ++
        (if (state.user.email.trim.isEmpty)
           Seq("La dirección de correo electrónico no puede estar vacía")
         else Nil)

    private def doCreate(
      state:   State,
      context: LoginControllerState
    ): (ReactMouseEventFrom[HTMLButtonElement], ButtonProps) => Callback = {
      (
        _: ReactMouseEventFrom[HTMLButtonElement],
        _: ButtonProps
      ) =>
        {
          val async: AsyncCallback[Callback] = for {
            saved <-
              Ajax("PUT", "/userCreation").setRequestContentTypeJson
                .send(UserCreationRequest(state.user, state.passwordPair._1).asJson.noSpaces)
                .asAsyncCallback
                .map { xhr =>
                  if (xhr.status < 300) {
                    decode[UserCreationResponse](xhr.responseText)
                      .fold(
                        e => Toast.error(e.getLocalizedMessage),
                        response =>
                          response.error.fold(
                            context.onModeChanged(Mode.login, response.error) >> Toast.success(
                              "Cuenta creada con éxito! por favor espera un correo electrónico que confirme tu registro!"
                            )
                          )(errorMsg => Toast.error(errorMsg))
                      )
                  } else
                    Toast.error(s"Error creando cuenta: ${xhr.statusText}")
                }
          } yield saved
          val valid: Seq[String] = validate(state)
          if (valid.nonEmpty)
            Toast.error(valid.map(s => <.p(s)).toVdomArray)
          else
            async.completeWith(_.get)
        }
    }

    private def renderUserInfo(
      state:   State,
      context: LoginControllerState
    ): VdomElement =
      <.div(
        FormGroup()(
          FormField(width = SemanticWIDTHS.`3`)(
            Label()("Nombre"),
            Input(
              onChange = onUserInputChange((user, value) => user.copy(name = value)),
              value = state.user.name
            )()
          )
        ),
        FormGroup()(
          FormField(width = SemanticWIDTHS.`6`)(
            Label()("Correo Electrónico"),
            Input(
              `type` = "email",
              onChange = onUserInputChange((user, value) => user.copy(email = value)),
              value = state.user.email
            )()
          )
        ),
        FormGroup()(
          FormField(width = SemanticWIDTHS.`3`)(
            Label()("Contraseña"),
            Input(
              required = true,
              name = "password",
              `type` = "password",
              value = state.passwordPair._1,
              onChange = { (_, obj) =>
                $.modState(state =>
                  state.copy(passwordPair =
                    (obj.value.get.asInstanceOf[String], state.passwordPair._2)
                  )
                )
              }
            )()
          ),
          FormField(width = SemanticWIDTHS.`3`)(
            Label()("Repite Contraseña"),
            Input(
              `type` = "password",
              name = "password",
              value = state.passwordPair._2,
              onChange = { (_, obj) =>
                $.modState(state =>
                  state.copy(passwordPair =
                    (state.passwordPair._1, obj.value.get.asInstanceOf[String])
                  )
                )
              }
            )()
          )
        ),
        FormGroup()(
          Button(compact = true, basic = true, onClick = doCreate(state, context))("Crear cuenta"),
          Button(
            compact = true,
            basic = true,
            onClick = { (_: ReactMouseEventFrom[HTMLButtonElement], _: ButtonProps) =>
              Callback(window.location.replace("/"))
            }
          )("Cancelar")
        )
      )

    def render(state: State): VdomElement =
      LoginControllerState.ctx.consume { context =>
        <.div(
          <.div(<.img(^.src := "/unauth/images/logo.png")),
          <.h1("Registro de cuenta!"),
          Form()(
            renderUserInfo(state, context)
          )
        )
      }
  }

  private val component = ScalaComponent
    .builder[Unit]("RegistrationPage")
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply(): Unmounted[Unit, State, Backend] = component()
}
