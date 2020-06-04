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
import chuti.{UpdateInvitedUserRequest, UpdateInvitedUserResponse, User, UserCreationResponse}
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
        $.modState(state =>
          state.copy(user = state.user.map(u => fn(u, obj.value.get.asInstanceOf[String])))
        )
    }

    private def validate(state: State): Seq[String] =
      Seq.empty[String] ++
        (if (state.passwordPair._1.trim.isEmpty) Seq("The password cannot be empty") else Nil) ++
        (if (state.passwordPair._1 != state.passwordPair._2) Seq("The passwords need to match")
         else Nil) ++
        (if (state.user.fold(true)(_.name.trim.isEmpty)) Seq("The username cannot be empty")
         else Nil)

    private[NewUserAcceptFriendPage] def init(props: Props): Callback =
      Ajax("GET", s"/getInvitedUserByToken?token=${props.token.get}").send.asAsyncCallback
        .map { xhr =>
          if (xhr.status < 300) {
            decode[Option[User]](xhr.responseText)
              .fold(
                e => Toast.error(e.getLocalizedMessage),
                response => $.modState(_.copy(user = response))
              )
          } else {
            Toast.error(s"Error creating account: ${xhr.statusText}")
          }
        }
        .completeWith(_.get)

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
            saved <- Ajax("POST", "/updateInvitedUser").setRequestContentTypeJson
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
                  decode[UpdateInvitedUserResponse](xhr.responseText)
                    .fold(
                      e => Toast.error(e.getLocalizedMessage),
                      response =>
                        response.error.fold(
                          context.onModeChanged(Mode.login, response.error) >> Toast.success(
                            "Account created successfully! Log in now!"
                          )
                        )(errorMsg => Toast.error(errorMsg))
                    )
                } else {
                  Toast.error(s"Error creating account: ${xhr.statusText}")
                }
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
          "Sorry, the user token is no longer valid, your invitation has been rescinded, but you can register ",
          <.a(^.href := "", "here anyway!")
        )
      )(u =>
        <.div(
          Header(as = "h1")("User Information"),
          FormGroup()(
            FormField(width = SemanticWIDTHS.`3`)(
              Label()("Name"),
              Input(
                onChange = onUserInputChange { (user, value) =>
                  user.copy(name = value)
                },
                value = u.name
              )()
            )
          ),
          FormGroup()(
            FormField(width = SemanticWIDTHS.`6`)(
              Label()("Email"),
              Input(
                disabled = true,
                `type` = "email",
                value = u.email
              )()
            )
          ),
          FormGroup()(
            FormField(width = SemanticWIDTHS.`3`)(
              Label()("Password"),
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
            Button(onClick = doCreate(state, props, context))("Create Account"),
            Button(onClick = { (_: ReactMouseEventFrom[HTMLButtonElement], _: ButtonProps) =>
              Callback(window.location.replace("/"))
            })("Cancel")
          )
        )
      )

    def render(
      props: Props,
      state: State
    ): VdomElement = LoginControllerState.ctx.consume { context =>
      <.div(
        Form()(
          "Text goes here", //TODO
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
