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

import auth.AuthClient
import chuti.{ChutiState, ConnectionId, GameClient, User}
import components.Toast
import japgolly.scalajs.react.*
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^.*
import net.leibman.chuti.semanticUiReact.components.*
import net.leibman.chuti.semanticUiReact.distCommonjsElementsInputInputMod.InputOnChangeData
import net.leibman.chuti.semanticUiReact.distCommonjsGenericMod.SemanticWIDTHS
import net.leibman.chuti.semanticUiReact.distCommonjsModulesDropdownDropdownItemMod.DropdownItemProps
import org.scalajs.dom.window

object UserSettingsPage extends ChutiPage {

  case class State(
    user: Option[User] = None,
    locale: String = {
      val loc: String | Null = window.sessionStorage.getItem("languageTag")
      println(s"languageTag = $loc")
      if (loc == null || loc.isEmpty)
        "es-MX"
      else loc
    },
    passwordPair: (String, String) = ("", "")
  )

  class Backend($ : BackendScope[Unit, State]) {

    def init: Callback =
      AuthClient
        .whoami[User, ConnectionId](Some(GameClient.connectionId))
        .map(u => $.modState(_.copy(user = u)))
        .completeWith(_.get)

    private def onUserInputChange(
      fn: (User, String) => User
    )(
      e:   ReactEventFromInput,
      obj: InputOnChangeData
    ): Callback = {
      val str = obj.value.get.asInstanceOf[String]
      $.modState(state => state.copy(user = state.user.map(fn(_, str))))
    }

    private def validate(state: State): Seq[String] =
      Seq.empty[String] ++
        (if (state.user.get.name.trim.nn.isEmpty) Seq("El nombre no puede estar vacío") // TODO i8n
         else Nil) ++
        (if (state.user.get.email.trim.nn.isEmpty)
           Seq("La dirección de correo electrónico no puede estar vacía") // TODO i8n
         else Nil)

    private def validatePassword(
      state: State
    ): Seq[String] =
      Seq.empty[String] ++
        (if (state.passwordPair._1.trim.nn.isEmpty) Seq("La contraseña no puede estar vacía") // TODO i8n
         else Nil) ++
        (if (state.passwordPair._1 != state.passwordPair._2)
           Seq("Las dos contraseñas tienen que ser iguales") // TODO i8n
         else Nil)

    def doUpdate(
      s:          State,
      chutiState: ChutiState
    ): Callback = {
      val valid: Seq[String] = validate(s)
      if (valid.nonEmpty)
        Toast.error(valid.map(s => <.p(s)).toVdomArray)
      else
        chutiState.onSessionChanged(s.user, s.locale)
    }

    def doChangePassword(
      s: State
    ): Callback = {
      val valid: Seq[String] = validatePassword(s)
      if (valid.nonEmpty)
        Toast.error(valid.map(s => <.p(s)).toVdomArray)
      else {
        GameClient.user.changePassword(s.passwordPair._1).completeWith(_ => Toast.success("Contraseña cambiada"))
      } // TODO i8n
    }

    private def renderUserInfo(
      state:      State,
      chutiState: ChutiState
    ): VdomElement =
      <.div(
        FormGroup()(
          FormField().width(SemanticWIDTHS.`3`)(
            Label()("Nombre"), // TODO i8n
            Input()
              .onChange(
                onUserInputChange(
                  (
                    user,
                    value
                  ) => user.copy(name = value)
                )
              )
              .value(state.user.fold("")(_.name))()
          )
        ),
        FormGroup()(
          FormField().width(SemanticWIDTHS.`6`)(
            Label()("Correo Electrónico"), // TODO i8n
            Input()
              .`type`("email")
              .onChange(
                onUserInputChange(
                  (
                    user,
                    value
                  ) => user.copy(email = value)
                )
              )
              .value(state.user.fold("")(_.email))()
          )
        ),
        FormGroup()(
          FormField().width(SemanticWIDTHS.`6`)(
            Label()("Idioma"), // TODO i8n
            Dropdown()
              .placeholder("Selecciona Idioma") // TODO i8n
              .fluid(true)
              .selection(true)
              .value(state.locale)
              .onChange {
                (
                  _,
                  dropDownProps
                ) =>
                  $.modState(_.copy(locale = dropDownProps.value.asInstanceOf[String]))
              }
              .options(
                scalajs.js.Array(
                  DropdownItemProps()
                    .setValue("en-US")
                    .setFlag("us")
                    .setText("Ingles"), // TODO i8n
                  DropdownItemProps()
                    .setValue("es-MX")
                    .setFlag("mx")
                    .setText("Español") // TODO i8n
                )
              )()
          )
        ),
        FormGroup()(
          Button()
            .compact(true)
            .basic(true)
            .onClick(
              (
                _,
                _
              ) => doUpdate(state, chutiState)
            )("Guardar") // TODO i8n
        ),
        Divider()(),
        FormGroup()(
          FormField().width(SemanticWIDTHS.`3`)(
            Label()("Contraseña"), // TODO i8n
            Input()
              .required(true)
              .name("password")
              .`type`("password")
              .value(state.passwordPair._1)
              .onChange {
                (
                  _,
                  obj
                ) =>
                  $.modState(state =>
                    state.copy(passwordPair = (obj.value.get.asInstanceOf[String], state.passwordPair._2))
                  )
              }()
          ),
          FormField().width(SemanticWIDTHS.`3`)(
            Label()("Repite Contraseña"), // TODO i8n
            Input()
              .`type`("password")
              .name("password")
              .value(state.passwordPair._2)
              .onChange {
                (
                  _,
                  obj
                ) =>
                  $.modState(state =>
                    state.copy(passwordPair = (state.passwordPair._1, obj.value.get.asInstanceOf[String]))
                  )
              }()
          )
        ),
        FormGroup()(
          Button()
            .compact(true)
            .basic(true)
            .onClick(
              (
                _,
                _
              ) => doChangePassword(state)
            )("Cambiar Contraseña") // TODO i8n
        ),
        Divider()(),
        <.h2("Cartera"), // TODO i8n
        <.p(
          s"En cartera tienes ${chutiState.wallet.fold("")(_.amount.toString())} satoshi, si quieres cambiar el numero de satoshi que tienes, comunicate con nosotros a ", // TODO i8n
          <.a(^.href := "mailto:info@chuti.fun", "info@chuti.fun")
        )
      )

    def render(
      state: State
    ): VdomElement =
      ChutiState.ctx.consume { chutiState =>
        <.div(
          <.h1("Administración de Usuario"), // TODO i8n
          Form()(
            renderUserInfo(state, chutiState)
          )
        )
      }

  }

  private val component = ScalaComponent
    .builder[Unit]("UserSettingsPage")
    .initialState(State())
    .backend[Backend](Backend(_))
    .renderS(_.backend.render(_))
    .componentDidMount(_.backend.init)
    .build

  def apply(): Unmounted[Unit, State, Backend] = component()

}
