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

import app.ChutiState
import chuti.User
import components.Toast
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^.{<, _}
import service.UserRESTClient
import typings.semanticUiReact.components.{FormGroup, _}
import typings.semanticUiReact.genericMod.SemanticWIDTHS
import typings.semanticUiReact.inputInputMod.InputOnChangeData

object UserSettingsPage extends ChutiPage {

  case class State(
    user:         Option[User] = None,
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
      println(str)
      $.modState(state => {
        val newUser = state.user.map(fn(_, str))
        println(newUser)
        state.copy(user = newUser)
      })
    }

    private def validate(state: State): Seq[String] =
      Seq.empty[String] ++
        (if (state.user.get.name.trim.isEmpty) Seq("El nombre no puede estar vacío")
         else Nil) ++
        (if (state.user.get.email.trim.isEmpty)
           Seq("La dirección de correo electrónico no puede estar vacía")
         else Nil)

    private def validatePassword(
      state: State
    ): Seq[String] =
      Seq.empty[String] ++
        (if (state.passwordPair._1.trim.isEmpty) Seq("La contraseña no puede estar vacía")
         else Nil) ++
        (if (state.passwordPair._1 != state.passwordPair._2)
           Seq("Las dos contraseñas tienen que ser iguales")
         else Nil)

    def doUpdate(
      s:          State,
      chutiState: ChutiState
    ): Callback = {
      val valid: Seq[String] = validate(s)
      if (valid.nonEmpty)
        Toast.error(valid.map(s => <.p(s)).toVdomArray)
      else
        chutiState.onUserChanged(s.user)
    }

    def doChangePassword(
      s: State
    ): Callback = {
      val valid: Seq[String] = validatePassword(s)
      if (valid.nonEmpty)
        Toast.error(valid.map(s => <.p(s)).toVdomArray)
      else
        UserRESTClient.remoteSystem
          .changePassword(s.passwordPair._1).completeWith(_ => Toast.success("Contraseña cambiada"))
    }

    private def renderUserInfo(
      state:      State,
      chutiState: ChutiState
    ): VdomElement =
      <.div(
        FormGroup()(
          FormField(width = SemanticWIDTHS.`3`)(
            Label()("Nombre"),
            Input(
              onChange = onUserInputChange((user, value) => user.copy(name = value)),
              value = state.user.fold("")(_.name)
            )()
          )
        ),
        FormGroup()(
          FormField(width = SemanticWIDTHS.`6`)(
            Label()("Correo Electrónico"),
            Input(
              `type` = "email",
              onChange = onUserInputChange((user, value) => user.copy(email = value)),
              value = state.user.fold("")(_.email)
            )()
          )
        ),
        FormGroup()(
          Button(
            compact = true,
            basic = true,
            onClick = { (_, _) => doUpdate(state, chutiState) }
          )("Guardar")
        ),
        Divider()(),
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
          Button(
            compact = true,
            basic = true,
            onClick = { (_, _) => doChangePassword(state) }
          )("Cambiar Contraseña")
        ),
        Divider()(),
        <.h2("Cartera"),
        <.p(
          s"En cartera tienes ${chutiState.wallet.fold("")(_.amount.toString())} satoshi, si quieres cambiar el numero de satoshi que tienes, comunicate con nosotros a ",
          <.a(^.href := "mailto:info@chuti.fun", "info@chuti.fun")
        )
      )

    def render(
      state: State
    ): VdomElement =
      ChutiState.ctx.consume { chutiState =>
        <.div(
          <.h1("Administración de Usuario"),
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
