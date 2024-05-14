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

package chat

import _root_.util.Config
import caliban.client.SelectionBuilder
import caliban.client.scalajs.{ScalaJSClientAdapter, WebSocketHandler}
import chat.ChatClient.{ChatMessage as CalibanChatMessage, Instant as CalibanInstant, Mutations, Queries, Subscriptions, User as CalibanUser}
import chuti.{ChannelId, ChatMessage, User}
import components.Toast
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{Ref as ReactRef, *}
import net.leibman.chuti.semanticUiReact.components.*
import net.leibman.chuti.semanticUiReact.textAreaTextAreaMod.TextAreaProps
import org.scalajs.dom.html.Div
import sttp.client3.*

import java.net.URI
import java.time.{Instant, ZoneId}
import java.time.format.DateTimeFormatter
import java.util.{Locale, UUID}
import scala.util.{Failure, Success}
import chuti.*
import chuti.ChannelId.*
import chuti.UserId.*
import zio.json.*

object ChatComponent extends ScalaJSClientAdapter {

  given JsonCodec[ChatMessage] = DeriveJsonCodec.gen[ChatMessage]

  private val connectionId = UUID.randomUUID().toString
  override val serverUri = uri"http://${Config.chutiHost}/api/chat"
  private val df = DateTimeFormatter.ofPattern("MM/dd HH:mm").nn.withLocale(Locale.US).nn.withZone(ZoneId.systemDefault()).nn

  case class State(
    chatMessages: List[ChatMessage] = Nil,
    msgInFlux:    String = "",
    ws:           Option[WebSocketHandler] = None
  )

  lazy private val chatId = UUID.randomUUID().toString

  class Backend($ : BackendScope[Props, State]) {

    def close(): Callback = $.state.flatMap(s => s.ws.fold(Callback.empty)(handler => Callback.log("Closing chat ws handler") >> handler.close()))

    private def onMessageInFluxChange = { (_: ReactEventFromTextArea, obj: TextAreaProps) =>
      $.modState(_.copy(msgInFlux = obj.value.get.asInstanceOf[String]))
    }

    private def onSend(
      p: Props,
      s: State
    ): Callback = {
      val mutation = Mutations.say(s.msgInFlux, p.channel.channelId)
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

    def scrollToBottom: Callback = messagesRef.foreach(_.scrollIntoView(top = true))

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
          s.chatMessages.zipWithIndex.toVdomArray { case (msg, index) =>
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
            .onKeyPress { e =>
              if (e.which == 13 && !e.shiftKey && e.target.value.trim.nn.nonEmpty)
                Callback {
                  e.preventDefault()
                } >> onSend(p, s) >> Callback(e.target.focus())
              else Callback.empty
            }(),
          Button
            .compact(true)
            .basic(true)
            .disabled(s.msgInFlux.trim.nn.isEmpty)
            .onClick((_, _) => onSend(p, s))("Send")
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
              date:         CalibanInstant,
              toUsername:   Option[String],
              msg:          String
            ) =>
              ChatMessage(
                fromUser = User(None, "", fromUsername, created = Instant.now().nn, lastUpdated = Instant.now().nn),
                msg = msg,
                channelId = p.channel,
                date = Instant.parse(date).nn,
                toUser = toUsername.map(name => User(None, "", name, created = Instant.now().nn, lastUpdated = Instant.now().nn))
              )
          )
      (for {
        recentMessages <- asyncCalibanCall(
          Queries.getRecentMessages(p.channel.channelId)(chatSelectionBuilder)
        ).handleError { error =>
          error.printStackTrace()
          AsyncCallback.pure(None)
        }
      } yield {
        $.modState { s =>
          import scala.language.unsafeNulls
          s.copy(
            chatMessages = recentMessages.toList.flatten,
            ws = Option(
              makeWebSocketClient[Option[ChatMessage]](
                uriOrSocket = Left(new URI(s"ws://${Config.chutiHost}/api/chat/ws")),
                query = Subscriptions
                  .chatStream(p.channel.channelId, connectionId)(
                    chatSelectionBuilder
                  ),
                onData = { (_, data) =>
                  val flatted = data.flatten
                  Callback.log(s"got data! $flatted") >>
                    flatted
                      .fold(Callback.empty) { msg =>
                        msg.toUser.fold(
                          $.props.flatMap(_.onMessage(msg)) >> $.modState(s => s.copy(s.chatMessages :+ msg)) >> scrollToBottom
                        ) { _ =>
                          $.props.flatMap(_.onPrivateMessage(msg))
                        }
                      }
                },
                operationId = s"chat$chatId",
                connectionId = s"$chatId-${p.channel.channelId}"
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

  import scala.language.unsafeNulls
  given messageReuse: Reusability[ChatMessage] = Reusability.by(msg => (msg.date.getEpochSecond, msg.fromUser.id.map(_.userId)))
  given propsReuse:   Reusability[Props] = Reusability.by(_.channel.channelId)
  given stateReuse:   Reusability[State] = Reusability.caseClassExcept("ws")

  private val component = ScalaComponent
    .builder[Props]("content")
    .initialState(State())
    .renderBackend[Backend]
    .componentDidMount($ => Callback.log(s"ChatComponent.componentDidMount ${$.props.channel}") >> $.backend.init($.props))
    .componentWillUnmount($ => $.backend.close())
    .configure(Reusability.shouldComponentUpdate)
    .build

  def apply(
    user:             User,
    channel:          ChannelId,
    onPrivateMessage: ChatMessage => Callback = _ => Callback.empty,
    onMessage:        ChatMessage => Callback = _ => Callback.empty
  ): Unmounted[Props, State, Backend] =
    component.withKey(s"chatChannel${channel.channelId}")(
      Props(user, channel, onPrivateMessage, onMessage)
    )

}
