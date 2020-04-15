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

package chat

import java.net.URI
import java.time.{Instant, LocalDateTime, ZoneId}

import caliban.client.scalajs.ScalaJSClientAdapter
import chat.ChatClient.{Mutations, Subscriptions, ChatMessage => CalibanChatMessage, User => CalibanUser}
import io.circe.generic.auto._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{ReactMouseEventFrom, _}
import org.scalajs.dom.raw.HTMLButtonElement
import typings.semanticUiReact.buttonButtonMod.ButtonProps
import typings.semanticUiReact.components.{FormGroup, TableCell, _}
import typings.semanticUiReact.genericMod.SemanticWIDTHS
import typings.semanticUiReact.textAreaTextAreaMod.TextAreaProps

import scala.util.{Failure, Success}

case class User(name: String) //TODO move this to shared

object ChatMessage {
  def apply(
    username: String,
    date:     Long,
    msg:      String
  ): ChatMessage =
    ChatMessage(User(username), date, msg)
}

case class ChatMessage(
  user: User,
  date: Long,
  msg:  String
) //TODO move this to shared

object ChatComponent {
  case class State(
    chatMessages: Seq[ChatMessage] = Seq.empty,
    msgInFlux:    String = ""
  )

  class Backend($ : BackendScope[_, State]) extends ScalaJSClientAdapter {
    val wsHandle = makeWebSocketClient[ChatMessage](
      uri = new URI("ws://localhost:8079/api/chat/ws"),
      query = Subscriptions
        .chatStream(
          (CalibanChatMessage
            .user(CalibanUser.name) ~ CalibanChatMessage.date ~ CalibanChatMessage.msg)
            .mapN((username: String, date: Long, msg: String) =>
              ChatMessage.apply(username, date, msg)
            )
        ),
      onData = {
        _.fold(Callback.empty) { msg =>
          $.modState(s => s.copy(s.chatMessages :+ msg))
        }.runNow()
      }
    )

    private def onMessageInFluxChange = { (_: ReactEventFromTextArea, obj: TextAreaProps) =>
      $.modState(_.copy(msgInFlux = obj.value.get.asInstanceOf[String]))
    }

    private def onSend(
      p: Props,
      s: State
    ) = { (_: ReactMouseEventFrom[HTMLButtonElement], _: ButtonProps) =>
      import sttp.client._
      implicit val backend = FetchBackend()
      val mutation = Mutations.say(s.msgInFlux)
      val serverUri = uri"http://localhost:8079/api/chat"
      val request = mutation.toRequest(serverUri)
      //TODO add headers as necessary
      import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

      Callback.log(s"Sending msg = ${s.msgInFlux}!") >> $.modState(_.copy(msgInFlux = "")) >> AsyncCallback
        .fromFuture(request.send())
        .completeWith {
          case Success(response) if response.code.isSuccess || response.code.isInformational =>
            Callback.log("Message sent")
          case Failure(exception) => Callback.error(exception) //TODO handle error responses better
          case Success(response) =>
            Callback.log(s"Error: ${response.statusText}") //TODO handle error responses better
        }
    }

    def render(
      p: Props,
      s: State
    ): VdomNode =
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
