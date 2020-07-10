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
import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.UUID

import caliban.client.SelectionBuilder
import caliban.client.scalajs.{ScalaJSClientAdapter, WebSocketHandler}
import chat.ChatClient.{
  Mutations,
  Queries,
  Subscriptions,
  ChatMessage => CalibanChatMessage,
  User => CalibanUser
}
import chuti.{ChannelId, ChatMessage, User}
import components.Toast
import io.circe.generic.auto._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.ReusabilityOverlay
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{ReactMouseEventFrom, _}
import org.scalajs.dom.raw.HTMLButtonElement
import typings.semanticUiReact.buttonButtonMod.ButtonProps
import typings.semanticUiReact.components._
import typings.semanticUiReact.textAreaTextAreaMod.TextAreaProps

import scala.concurrent.Future
import scala.util.{Failure, Success}

object ChatComponent extends ScalaJSClientAdapter {
  private val connectionId = UUID.randomUUID().toString
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
    ) = { (_: ReactMouseEventFrom[HTMLButtonElement], _: ButtonProps) =>
      import sttp.client._
      implicit val backend: SttpBackend[Future, Nothing, NothingT] = FetchBackend()
      val mutation = Mutations.say(s.msgInFlux, p.channel.value)
      val serverUri = uri"http://localhost:8079/api/chat"
      val request = mutation.toRequest(serverUri)
      //TODO add headers as necessary
      import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

      Callback.log(s"Sending msg = ${s.msgInFlux}!") >> $.modState(_.copy(msgInFlux = "")) >> AsyncCallback
        .fromFuture(request.send())
        .completeWith {
          case Success(response) if response.code.isSuccess || response.code.isInformational =>
            Callback.log("Message sent")
          case Failure(exception) =>
            Toast.error("Error sending message") >> Callback.throwException(exception)
          case Success(response) =>
            Toast.error("Error sending message") >> Callback.log(s"Error: ${response.statusText}")
        }
    }

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
              <.div(
                ^.key       := s"msg$index",
                ^.className := "receivedMessage",
                <.div(^.className := "sentBy", ^.fontWeight.bold, msg.fromUser.name, " "),
                <.div(^.className := "sentAt", ^.fontWeight.lighter, df.format(msg.date)),
                <.div(^.className := "messageText", msg.msg)
              )
          }
        ),
        <.div(
          ^.className := "sendMessage",
          TextArea(value = s.msgInFlux, onChange = onMessageInFluxChange)(),
          Button(
            compact = true,
            basic = true,
            disabled = s.msgInFlux.trim.isEmpty,
            onClick = onSend(p, s)
          )("Send")
        )
      )

    def init(p: Props): Callback = {
      val chatSelectionBuilder: SelectionBuilder[CalibanChatMessage, ChatMessage] =
        (CalibanChatMessage
          .fromUser(CalibanUser.name) ~ CalibanChatMessage.date ~ CalibanChatMessage.toUser(
          CalibanUser.name
        ) ~ CalibanChatMessage.msg)
          .mapN((fromUsername: String, date: Long, toUsername: Option[String], msg: String) =>
            ChatMessage(
              fromUser = User(None, "", fromUsername),
              msg = msg,
              channelId = p.channel,
              date = LocalDateTime.ofInstant(Instant.ofEpochMilli(date), ZoneOffset.UTC),
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
        calibanCall[Queries, Option[Seq[Int]]](
          Queries.getRecentMessages(p.channel.value)(CalibanChatMessage.channelId),
          response => Callback.log(s"Got messages $response")
        ) >>
          $.modState { s =>
            s.copy(
              chatMessages = recentMessages.toList.flatten,
              ws = Option(
                makeWebSocketClient[Option[ChatMessage]](
                  uriOrSocket = Left(new URI("ws://localhost:8079/api/chat/ws")),
                  query = Subscriptions
                    .chatStream(p.channel.value, connectionId)(
                      chatSelectionBuilder
                    ),
                  onData = { (_, data) =>
                    val flatted = data.flatten
                    Callback.log(s"got data! $flatted")
                    flatted
                      .fold(Callback.empty) { msg =>
                        msg.toUser.fold($.modState(s => s.copy(s.chatMessages :+ msg))) { _ =>
                          $.props.flatMap(_.onPrivateMessage.fold(Callback.empty)(_(msg)))
                        }
                      }
                  },
                  operationId = s"chat$chatId"
                )
              )
            )
          }
      }).completeWith(_.get)
    }
  }

  case class Props(
    user:             User,
    channel:          ChannelId,
    onPrivateMessage: Option[ChatMessage => Callback] = None
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

    .configure(Reusability.shouldComponentUpdateAndLog("Chat"))
    .configure(ReusabilityOverlay.install)
    .build

  def apply(
    user:             User,
    channel:          ChannelId,
    onPrivateMessage: Option[ChatMessage => Callback] = None
  ): Unmounted[Props, State, Backend] = component(Props(user, channel, onPrivateMessage))
}
