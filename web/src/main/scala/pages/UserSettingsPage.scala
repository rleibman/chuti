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

import app.ChutiState
import chuti.User
import components.Toast
import japgolly.scalajs.react.*
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^.{<, *}
import org.scalajs.dom.window
import service.UserRESTClient
import net.leibman.chuti.semanticUiReact.components.{FormGroup, *}
import net.leibman.chuti.semanticUiReact.distCommonjsGenericMod.SemanticWIDTHS
import net.leibman.chuti.semanticUiReact.distCommonjsElementsInputInputMod.InputOnChangeData
import net.leibman.chuti.semanticUiReact.distCommonjsModulesDropdownDropdownItemMod.DropdownItemProps

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
      UserRESTClient.remoteSystem
        .whoami().map(u => $.modState(_.copy(user = u))).completeWith(_.get)

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
      else
        UserRESTClient.remoteSystem
          .changePassword(s.passwordPair._1).completeWith(_ => Toast.success("Contraseña cambiada")) // TODO i8n
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
              .onChange(onUserInputChange((user, value) => user.copy(name = value)))
              .value(state.user.fold("")(_.name))()
          )
        ),
        FormGroup()(
          FormField().width(SemanticWIDTHS.`6`)(
            Label()("Correo Electrónico"), // TODO i8n
            Input()
              .`type`("email")
              .onChange(onUserInputChange((user, value) => user.copy(email = value)))
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
              .onChange { (_, dropDownProps) =>
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
            .onClick((_, _) => doUpdate(state, chutiState))("Guardar") // TODO i8n
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
              .onChange { (_, obj) =>
                $.modState(state => state.copy(passwordPair = (obj.value.get.asInstanceOf[String], state.passwordPair._2)))
              }()
          ),
          FormField().width(SemanticWIDTHS.`3`)(
            Label()("Repite Contraseña"), // TODO i8n
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
            .onClick((_, _) => doChangePassword(state))("Cambiar Contraseña") // TODO i8n
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
    .renderBackend[Backend]
    .componentDidMount(_.backend.init)
    .build

  def apply(): Unmounted[Unit, State, Backend] = component()

}
