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
import chuti.{User, UserCreationRequest}
import io.circe.generic.auto._
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
        (if (state.passwordPair._1.trim.isEmpty) Seq("The password cannot be empty") else Nil) ++
        (if (state.passwordPair._1 != state.passwordPair._2) Seq("The passwords need to match")
         else Nil) ++
        (if (state.user.name.trim.isEmpty) Seq("The username cannot be empty") else Nil) ++
        (if (state.user.email.trim.isEmpty) Seq("The user's email cannot be empty") else Nil)

    private def doCreate(
      state:   State,
      context: LoginControllerState
    ) = {
      (
        _: ReactMouseEventFrom[HTMLButtonElement],
        _: ButtonProps
      ) =>
        {
          val async: AsyncCallback[Callback] = for {
            saved <- Ajax("PUT", "")
              .and(_.withCredentials = true)
              .setRequestContentTypeJson
              .send(UserCreationRequest(state.user, state.passwordPair._1).asJson.noSpaces)
              .asAsyncCallback
              .map { xhr =>
                if (xhr.status < 300)
                  context.onModeChanged(Mode.login) >> Toast.success(
                    "Account created successfully!"
                  )
                else
                  Toast.error(s"Error creating account: ${xhr.statusText}")
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
        Header(as = "h1")("User Information"),
        FormGroup()(
          FormField(width = SemanticWIDTHS.`3`)(
            Label()("Name"),
            Input(
              onChange = onUserInputChange { (user, value) =>
                user.copy(name = value)
              },
              value = state.user.name
            )()
          )
        ),
        FormGroup()(
          FormField(width = SemanticWIDTHS.`6`)(
            Label()("Email"),
            Input(
              `type` = "email",
              onChange = onUserInputChange { (user, value) =>
                user.copy(email = value)
              },
              value = state.user.email
            )()
          )
        ),
        FormGroup()(
          FormField(width = SemanticWIDTHS.`3`)(
            Label()("Password"),
            Input(
              `type` = "password",
              name = "password",
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
            Label()("Repeat Password"),
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
          Button(onClick = doCreate(state, context))("Create Account"),
          Button(onClick = { (_: ReactMouseEventFrom[HTMLButtonElement], _: ButtonProps) =>
            Callback(window.location.replace("/"))
          })("Cancel")
        )
      )

    def render(state: State): VdomElement = LoginControllerState.ctx.consume { context =>
      <.div(
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
