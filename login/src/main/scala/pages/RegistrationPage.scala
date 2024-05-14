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

import java.time.Instant

import app.{LoginControllerState, Mode}
import chuti.{User, UserCreationRequest, UserCreationResponse}
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{ReactMouseEventFrom, _}
import org.scalajs.dom.HTMLButtonElement
import org.scalajs.dom.window
import react.Toast
import net.leibman.chuti.semanticUiReact.buttonButtonMod.ButtonProps
import net.leibman.chuti.semanticUiReact.components.*
import net.leibman.chuti.semanticUiReact.genericMod.SemanticWIDTHS
import net.leibman.chuti.semanticUiReact.inputInputMod.InputOnChangeData
import zio.json.*
import zio.json.EncoderOps.*

object RegistrationPage {

  case class State(
    passwordPair: (String, String) = ("", ""),
    user:         User = User(id = None, email = "", name = "", created = Instant.now.nn, lastUpdated = Instant.now.nn)
  )

  class Backend($ : BackendScope[Unit, State]) {

    private def onUserInputChange(fn: (User, String) => User) = {
      (
        _:   ReactEventFromInput,
        obj: InputOnChangeData
      ) =>
        $.modState(state => state.copy(user = fn(state.user, obj.value.get.asInstanceOf[String])))
    }

    private def validate(state: State): Seq[String] =
      Seq.empty[String] ++
        (if (state.passwordPair._1.trim.nn.isEmpty) Seq("La contraseña no puede estar vacía")
         else Nil) ++
        (if (state.passwordPair._1 != state.passwordPair._2)
           Seq("Las dos contraseñas tienen que ser iguales")
         else Nil) ++
        (if (state.user.name.trim.nn.isEmpty) Seq("El nombre no puede estar vacío") else Nil) ++
        (if (state.user.email.trim.nn.isEmpty)
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
                .send(UserCreationRequest(state.user, state.passwordPair._1).toJson)
                .asAsyncCallback
                .map { xhr =>
                  if (xhr.status < 300) {
                    import scala.language.unsafeNulls
                    xhr.responseText
                      .fromJson[UserCreationResponse]
                      .fold(
                        e => Toast.error(e),
                        response =>
                          response.error.fold(
                            context.onModeChanged(Mode.login, response.error) >> Toast.success(
                              "Cuenta creada con éxito! por favor espera un correo electrónico que confirme tu registro!"
                            )
                          )(errorMsg => Toast.error(errorMsg))
                      )
                  } else Toast.error(s"Error creando cuenta: ${xhr.statusText}")
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
          FormField().width(SemanticWIDTHS.`3`)(
            Label()("Nombre"),
            Input()
              .onChange(onUserInputChange((user, value) => user.copy(name = value)))
              .value(state.user.name)()
          )
        ),
        FormGroup()(
          FormField().width(SemanticWIDTHS.`6`)(
            Label()("Correo Electrónico"),
            Input()
              .`type`("email")
              .onChange(onUserInputChange((user, value) => user.copy(email = value)))
              .value(state.user.email)()
          )
        ),
        FormGroup()(
          FormField().width(SemanticWIDTHS.`3`)(
            Label()("Contraseña"),
            Input()
              .required(true)
              .name("password")
              .`type`("password")
              .value(state.passwordPair._1)
              .onChange { (_, obj) =>
                $.modState(state => state.copy(passwordPair = (obj.value.get.asInstanceOf[String], state.passwordPair._2)))
              }()
          ),
          FormField().width(SemanticWIDTHS.`3`)(
            Label()("Repite Contraseña"),
            Input()
              .`type`("password")
              .name("password")
              .value(state.passwordPair._2)
              .onChange { (_, obj) =>
                $.modState(state => state.copy(passwordPair = (state.passwordPair._1, obj.value.get.asInstanceOf[String])))
              }()
          )
        ),
        FormGroup()(
          Button()
            .compact(true)
            .basic(true)
            .onClick(doCreate(state, context))("Crear cuenta"),
          Button()
            .compact(true)
            .basic(true)
            .onClick { (_: ReactMouseEventFrom[HTMLButtonElement], _: ButtonProps) =>
              Callback(window.location.replace("/"))
            }("Cancelar")
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
