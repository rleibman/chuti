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

import _root_.util.Config
import caliban.client.SelectionBuilder
import caliban.client.scalajs.{ScalaJSClientAdapter, WebSocketHandler}
import chat.ChatClient.{Mutations, Queries, Subscriptions, ChatMessage => CalibanChatMessage, LocalDateTime => CalibanLocalDateTime, User => CalibanUser}
import chuti.{ChannelId, ChatMessage, User}
import components.Toast
import io.circe.generic.auto.*
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{Ref => ReactRef, _}
import net.leibman.chuti.semanticUiReact.components.*
import net.leibman.chuti.semanticUiReact.textAreaTextAreaMod.TextAreaProps
import org.scalajs.dom.html.Div
import sttp.client3.*

import java.net.URI
import java.time.format.DateTimeFormatter
import java.time.{LocalDateTime, ZoneOffset}
import java.util.UUID
import scala.util.{Failure, Success}

object ChatComponent extends ScalaJSClientAdapter {
  private val connectionId = UUID.randomUUID().toString
  override val serverUri = uri"http://${Config.chutiHost}/api/chat"
  private val df = DateTimeFormatter.ofPattern("MM/dd HH:mm")

  case class State(
    chatMessages: List[ChatMessage] = Nil,
    msgInFlux:    String = "",
    ws:           Option[WebSocketHandler] = None
  )

  lazy private val chatId = UUID.randomUUID().toString

  class Backend($ : BackendScope[Props, State]) {

    def close(): Callback =
      $.state.flatMap(s =>
        s.ws.fold(Callback.empty)(handler =>
          Callback.log("Closing chat ws handler") >> handler.close()
        )
      )

    private def onMessageInFluxChange = { (_: ReactEventFromTextArea, obj: TextAreaProps) =>
      $.modState(_.copy(msgInFlux = obj.value.get.asInstanceOf[String]))
    }

    private def onSend(
      p: Props,
      s: State
    ): Callback = {
      val mutation = Mutations.say(s.msgInFlux, p.channel.value)
      val serverUri = uri"http://${Config.chutiHost}/api/chat"
      val request = mutation.toRequest(serverUri)

      import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

      Callback.log(s"Sending msg = ${s.msgInFlux}!") >> $.modState(
        _.copy(msgInFlux = "")
      ) >> AsyncCallback
        .fromFuture(request.send(FetchBackend()))
        .completeWith {
          case Success(response) if response.code.isSuccess || response.code.isInformational =>
            Callback.log("Message sent")
          case Failure(exception) =>
            Toast.error("Error sending message") >> Callback.throwException(exception)
          case Success(response) =>
            Toast.error("Error sending message") >> Callback.log(s"Error: ${response.statusText}")
        }
    }

    private val messagesRef = ReactRef[Div]

    def scrollToBottom: Callback =
      messagesRef.foreach(_.scrollIntoView(top = true))

    def render(
      p: Props,
      s: State
    ): VdomNode =
      <.div(
        ^.key       := "chatComponent",
        ^.className := "chat",
        <.h1(^.className := "title", "Chuti Chat"),
        <.div(
          ^.className := "messages",
          s.chatMessages.zipWithIndex.toVdomArray {
            case (msg, index) =>
              val div = <.div(
                ^.key       := s"msg$index",
                ^.className := "receivedMessage",
                <.div(^.className := "sentBy", ^.fontWeight.bold, msg.fromUser.name, " "),
                <.div(^.className := "sentAt", ^.fontWeight.lighter, df.format(msg.date)),
                <.div(^.className := "messageText", msg.msg)
              )
              if (index == s.chatMessages.size - 1)
                div.withRef(messagesRef)
              else
                div
          }
        ),
        <.div(
          ^.className := "sendMessage",
          TextArea
            .value(s.msgInFlux)
            .onChange(onMessageInFluxChange)
            .onKeyPress({ e =>
              if (e.which == 13 && !e.shiftKey && e.target.value.trim.nonEmpty)
                Callback {
                  e.preventDefault()
                } >> onSend(p, s) >> Callback(e.target.focus())
              else Callback.empty
            })
            (),
          Button
            .compact(true)
            .basic(true)
            .disabled(s.msgInFlux.trim.isEmpty)
            .onClick({ (_, _) => onSend(p, s) })
            ("Send")
        )
      )

    def init(p: Props): Callback = {
      val chatSelectionBuilder: SelectionBuilder[CalibanChatMessage, ChatMessage] =
        (CalibanChatMessage
          .fromUser(CalibanUser.name) ~ CalibanChatMessage.date ~ CalibanChatMessage.toUser(
          CalibanUser.name
        ) ~ CalibanChatMessage.msg)
          .mapN(
            (
              fromUsername: String,
              date:         CalibanLocalDateTime,
              toUsername:   Option[String],
              msg:          String
            ) =>
              ChatMessage(
                fromUser = User(None, "", fromUsername),
                msg = msg,
                channelId = p.channel,
                date = LocalDateTime.parse(date),
                toUser = toUsername.map(name => User(None, "", name))
              )
          )
      (for {
        recentMessages <- asyncCalibanCall(
          Queries.getRecentMessages(p.channel.value)(chatSelectionBuilder)
        ).handleError { error =>
          error.printStackTrace()
          AsyncCallback.pure(None)
        }
      } yield {
        $.modState { s =>
          s.copy(
            chatMessages = recentMessages.toList.flatten,
            ws = Option(
              makeWebSocketClient[Option[ChatMessage]](
                uriOrSocket = Left(new URI(s"ws://${Config.chutiHost}/api/chat/ws")),
                query = Subscriptions
                  .chatStream(p.channel.value, connectionId)(
                    chatSelectionBuilder
                  ),
                onData = { (_, data) =>
                  val flatted = data.flatten
                  Callback.log(s"got data! $flatted") >>
                    flatted
                      .fold(Callback.empty) { msg =>
                        msg.toUser.fold(
                          $.props.flatMap(_.onMessage(msg)) >> $.modState(s =>
                            s.copy(s.chatMessages :+ msg)
                          ) >> scrollToBottom
                        ) { _ =>
                          $.props.flatMap(_.onPrivateMessage(msg))
                        }
                      }
                },
                operationId = s"chat$chatId",
                connectionId = s"$chatId-${p.channel.value}"
              )
            )
          )
        } >> scrollToBottom
      }).completeWith(_.get)
    }
  }

  case class Props(
    user:             User,
    channel:          ChannelId,
    onPrivateMessage: ChatMessage => Callback,
    onMessage:        ChatMessage => Callback
  )

  implicit val messageReuse: Reusability[ChatMessage] = Reusability.by(msg =>
    (msg.date.toInstant(ZoneOffset.UTC).getEpochSecond, msg.fromUser.id.map(_.value))
  )
  implicit val propsReuse: Reusability[Props] = Reusability.by(_.channel.value)
  implicit val stateReuse: Reusability[State] = Reusability.caseClassExcept("ws")

  private val component = ScalaComponent
    .builder[Props]("content")
    .initialState(State())
    .renderBackend[Backend]
    .componentDidMount($ =>
      Callback.log(s"ChatComponent.componentDidMount ${$.props.channel}") >> $.backend.init($.props)
    )
    .componentWillUnmount($ => $.backend.close())
    .configure(Reusability.shouldComponentUpdate)
    .build

  def apply(
    user:             User,
    channel:          ChannelId,
    onPrivateMessage: ChatMessage => Callback = _ => Callback.empty,
    onMessage:        ChatMessage => Callback = _ => Callback.empty
  ): Unmounted[Props, State, Backend] =
    component.withKey(s"chatChannel${channel.value}")(
      Props(user, channel, onPrivateMessage, onMessage)
    )
}
