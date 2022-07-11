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
import chuti.{UpdateInvitedUserRequest, UpdateInvitedUserResponse, User}
import io.circe.generic.auto.*
import io.circe.parser.decode
import io.circe.syntax.*
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

object NewUserAcceptFriendPage {

  case class State(
    passwordPair: (String, String) = ("", ""),
    user:         Option[User] = None
  )

  class Backend($ : BackendScope[Props, State]) {

    private def onUserInputChange(fn: (User, String) => User) = {
      (
        _:   ReactEventFromInput,
        obj: InputOnChangeData
      ) =>
        $.modState(state => state.copy(user = state.user.map(u => fn(u, obj.value.get.asInstanceOf[String]))))
    }

    private def validate(state: State): Seq[String] =
      Seq.empty[String] ++
        (if (state.passwordPair._1.trim.nn.isEmpty) Seq("La contraseña no puede estar vacía")
         else Nil) ++
        (if (state.passwordPair._1 != state.passwordPair._2)
           Seq("Las dos contraseñas tienen que ser iguales")
         else Nil) ++
        (if (state.user.fold(true)(_.name.trim.nn.isEmpty)) Seq("El nombre no puede estar vacío")
         else Nil) ++
        (if (state.user.fold(true)(_.email.trim.nn.isEmpty))
           Seq("La dirección de correo electrónico no puede estar vacía")
         else Nil)

    private[NewUserAcceptFriendPage] def init(props: Props): Callback = {
      import scala.language.unsafeNulls
      Ajax("GET", s"/getInvitedUserByToken?token=${props.token.get}").send.asAsyncCallback
        .map { xhr =>
          if (xhr.status < 300) {
            decode[Option[User]](xhr.responseText)
              .fold(
                e => Toast.error(e.getLocalizedMessage.nn),
                response => $.modState(_.copy(user = response))
              )
          } else
            Toast.error(s"Error creando la cuenta: ${xhr.statusText}")
        }
        .completeWith(_.get)
    }

    private def doCreate(
      state:   State,
      props:   Props,
      context: LoginControllerState
    ): (ReactMouseEventFrom[HTMLButtonElement], ButtonProps) => Callback = {
      (
        _: ReactMouseEventFrom[HTMLButtonElement],
        _: ButtonProps
      ) =>
        {
          val async: AsyncCallback[Callback] = for {
            saved <-
              Ajax("POST", "/updateInvitedUser").setRequestContentTypeJson
                .send(
                  UpdateInvitedUserRequest(
                    state.user.get,
                    state.passwordPair._1,
                    token = props.token.get
                  ).asJson.noSpaces
                )
                .asAsyncCallback
                .map { xhr =>
                  if (xhr.status < 300) {
                    import scala.language.unsafeNulls
                    decode[UpdateInvitedUserResponse](xhr.responseText)
                      .fold(
                        e => Toast.error(e.getLocalizedMessage),
                        response =>
                          response.error.fold(
                            context.onModeChanged(Mode.login, response.error) >> Toast.success(
                              "Cuenta creada con éxito! accede ahora!"
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
      props:   Props,
      context: LoginControllerState
    ): VdomElement =
      state.user.fold(
        <.div(
          "Mil disculpas, la clave ya no es valida, tu invitación ha caducado, o puede ser que ya estes registrado, si no es así, te puedes registrar ",
          <.a(^.href := "/loginForm", " aquí "),
          "de cualquier modo!"
        )
      )(u =>
        <.div(
          FormGroup()(
            FormField().width(SemanticWIDTHS.`3`)(
              Label()("Nombre"),
              Input()
                .onChange(onUserInputChange((user, value) => user.copy(name = value)))
                .value(u.name)()
            )
          ),
          FormGroup()(
            FormField().width(SemanticWIDTHS.`6`)(
              Label()("Correo Electrónico"),
              Input()
                .disabled(true)
                .`type`("email")
                .value(u.email)()
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
              .compact(true).basic(true).onClick(doCreate(state, props, context))(
                "Crear cuenta"
              ),
            Button()
              .compact(true)
              .basic(true)
              .onClick { (_: ReactMouseEventFrom[HTMLButtonElement], _: ButtonProps) =>
                Callback(window.location.replace("/"))
              }("Cancelar")
          )
        )
      )

    def render(
      props: Props,
      state: State
    ): VdomElement =
      LoginControllerState.ctx.consume { context =>
        <.div(
          <.div(<.img(^.src := "/unauth/images/logo.png")),
          <.h1("Registro de cuenta!"),
          Form()(
            renderUserInfo(state, props, context)
          )
        )
      }

  }

  case class Props(token: Option[String])

  private val component = ScalaComponent
    .builder[Props]("NewUserAcceptFriendPage")
    .initialState(State())
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.init($.props))
    .build

  def apply(token: Option[String]): Unmounted[Props, State, Backend] = component(Props(token))

}
