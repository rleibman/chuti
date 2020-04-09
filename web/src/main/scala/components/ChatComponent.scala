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

package components

import java.time.{Instant, LocalDateTime, ZoneId}

import io.circe.generic.auto._
import io.circe.syntax._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{ReactMouseEventFrom, _}
import org.scalajs.dom.raw.HTMLButtonElement
import org.scalajs.dom.window
import typings.semanticUiReact.buttonButtonMod.ButtonProps
import typings.semanticUiReact.components._
import typings.semanticUiReact.genericMod.SemanticWIDTHS
import typings.semanticUiReact.inputInputMod.InputOnChangeData
import typings.semanticUiReact.textAreaTextAreaMod.TextAreaProps

case class User(name: String) //TODO move this to shared
case class ChatMessage(
  user: User,
  msg:  String,
  date: Long
) //TODO move this to shared

object ChatComponent {
  case class State(
    chatMessages: Seq[ChatMessage] = Seq.empty,
    msgInFlux:    String = ""
  )
  class Backend($ : BackendScope[_, State]) {
    private def onMessageInFluxChange = { (_: ReactEventFromTextArea, obj: TextAreaProps) =>
      $.modState(_.copy(msgInFlux = obj.value.get.asInstanceOf[String]))
    }

    private def onSend(p: Props, s: State) = { (_: ReactMouseEventFrom[HTMLButtonElement], _: ButtonProps) =>
      val msg = ChatMessage(p.user, s.msgInFlux, System.currentTimeMillis())
      Callback.log(s"Sending msg = $msg!") >> $.modState(_.copy(msgInFlux = ""))
    }

    def render(p: Props, s: State): VdomNode =
      Form()(
        FormGroup()(
          FormField(width = SemanticWIDTHS.`16`)(
            Label()(s"User: ${p.user.name}")
          )
        ),
        Header()("Messages"),
        FormGroup()(
          FormField(width = SemanticWIDTHS.`16`)(
            Table()(
              TableBody()(
                s.chatMessages.toVdomArray(msg =>
                  TableRow()(
                    TableCell()(
                      LocalDateTime
                        .ofInstant(Instant.ofEpochMilli(msg.date), ZoneId.systemDefault())
                        .toString
                    ),
                    TableCell()(msg.user.name),
                    TableCell()(msg.msg)
                  )
                )
              )
            )
          )
        ),
        FormGroup()(FormField(width = SemanticWIDTHS.`16`)(Label()("Message"))),
        FormGroup()(
          FormField(width = SemanticWIDTHS.`16`)(
            TextArea(value = s.msgInFlux, onChange = onMessageInFluxChange)()
          )
        ),
        FormGroup()(FormField(width = SemanticWIDTHS.`16`)(Button(onClick = onSend(p, s))("Send")))
      )

    def refresh(s: State): Callback = Callback.empty //TODO add ajax initalization stuff here
  }

  case class Props(user: User)

  private val component = ScalaComponent
    .builder[Props]("content")
    .initialState(State())
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.refresh($.state))
    .build



  def apply(user: String): Unmounted[Props, State, Backend] = component(Props(User(user)))

}
