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

import caliban.client.GraphQLRequest
import chat.ChatClient.{Mutations, Subscriptions, ChatMessage => CalibanChatMessage}
import io.circe
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.parser._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{ReactMouseEventFrom, _}
import org.scalajs.dom.raw.HTMLButtonElement
import org.scalajs.dom.{experimental, window}
import typings.semanticUiReact.buttonButtonMod.ButtonProps
import typings.semanticUiReact.components._
import typings.semanticUiReact.genericMod.SemanticWIDTHS
import typings.semanticUiReact.inputInputMod.InputOnChangeData
import typings.semanticUiReact.textAreaTextAreaMod.TextAreaProps
import org.scalajs.dom.experimental.{BodyInit, Request => FetchRequest}
import chat.ChatClient.{ChatMessage => CalibanChatMessage, User => CalibanUser}
import org.scalajs._

import scala.scalajs.js.{Promise, UndefOr}
import scala.util.{Failure, Success}
import io.circe.generic.auto._
import io.circe.syntax._

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
    //TODO: move all of this into the chat client
    //TODO: abstract into a common library
    //TODO: handle errors
    //TODO: add properties:
      // timeout - how long the client should wait in ms for a keep-alive message from the server (default 30000 ms), this parameter is ignored if the server does not send keep-alive messages. This will also be used to calculate the max connection time per connect/reconnect
      // connection params - passed to the server on init
      // reconnect - automatic reconnect in case of connection error
      // reconnectionAttempts
      // connectionCallback optional, callback that called after the first init message, with the error (if there is one)
      // inactivityTimeout?: number : how long the client should wait in ms, when there are no active subscriptions, before disconnecting from the server. Set to 0 to disable this behavior. (default 0)
      // onConnected
      // onReconnected
      // onConnecting
      // onReconnecting
      // onDisconnected
      // onError
      // close

    object GQLOperationMessage {
      //Client messages
      val GQL_CONNECTION_INIT = "connection_init"
      val GQL_START = "start"
      val GQL_STOP = "stop"
      val GQL_CONNECTION_TERMINATE = "connection_terminate"
      //Server messages
      val GQL_COMPLETE = "complete"
      val GQL_CONNECTION_ACK = "connection_ack"
      val GQL_CONNECTION_ERROR = "connection_error"
      val GQL_CONNECTION_KEEP_ALIVE = "ka"
      val GQL_DATA = "data"
      val GQL_ERROR = "error"
      val GQL_UNKNOWN = "unknown"

      def GQLConnectionInit(payload: Json = Json.Null): GQLOperationMessage =
        GQLOperationMessage(GQL_CONNECTION_INIT, Option(payload))
      def GQLStart(query: GraphQLRequest): GQLOperationMessage =
        GQLOperationMessage(GQL_START, payload = Option(query.asJson))
    }
    import GQLOperationMessage._
    case class GQLOperationMessage(
      `type`:  String,
      payload: Option[Json] = None,
      id:      Option[String] = None
    )

    val socket = new dom.WebSocket("ws://localhost:8079/api/chat/ws", "graphql-ws")
    socket.onmessage = { (e: dom.MessageEvent) =>
      val msg: Either[circe.Error, GQLOperationMessage] =
        decode[GQLOperationMessage](e.data.toString)
      println(s"Received: $msg")
      msg match {
        case Right(GQLOperationMessage(GQL_COMPLETE, payload, id)) => //TODO
        case Right(GQLOperationMessage(GQL_CONNECTION_ACK, payload, _)) => //We should only do this the first time
          val graphql = Subscriptions
            .chatStream(
              CalibanChatMessage
                .user(CalibanUser.name) ~ CalibanChatMessage.date ~ CalibanChatMessage.msg
            ).toGraphQL()
          val sendMe = GQLStart(graphql).asJson.noSpaces
          println(s"Sending: $sendMe")
          socket.send(sendMe)
        case Right(GQLOperationMessage(GQL_CONNECTION_ERROR, payload, _)) =>
          println(s"Connection Error from server $payload") //TODO
        case Right(GQLOperationMessage(GQL_CONNECTION_KEEP_ALIVE, payload, id)) =>
          println("Keep alive")
        //TODO
        case Right(GQLOperationMessage(GQL_DATA, payload, id)) =>
          val msg = for {
            obj  <- payload.flatMap(_.asObject)
            data <- obj("data").flatMap(_.asObject)
            chatStream <- data("chatStream").flatMap(_.asObject)
            user     <- chatStream("user").flatMap(_.asObject)
            username <- {
              println(s"user $user")
              user("name").flatMap(_.asString)
            }
            date     <- {
              println(s"username $username")
              chatStream("date").flatMap(_.asNumber).flatMap(_.toLong)
            }
            msg      <- {
              println(s"date $date")
              chatStream("msg").flatMap(_.asString)
            }
          } yield (
            ChatMessage(
              User(username),
              msg,
              date.toLong
            )
          )
          println(msg)

          payload
            .fold(Callback.empty) { payload =>
              $.modState(s => s.copy(s.chatMessages ++ msg.toSeq))
            }.runNow()
        case Right(GQLOperationMessage(GQL_ERROR, payload, _)) =>
          println(s"Error from server $payload")
        case Right(GQLOperationMessage(typ, payload, id)) =>
          println(s"Unknown server operation! $typ $payload")
        case Left(error) => error.printStackTrace()
      }
    }
    socket.onerror = { (e: dom.Event) =>
      println(e.`type`)
    }
    socket.onopen = { (e: dom.Event) =>
      println(socket.protocol)
      println(e.`type`)
      val sendMe = GQLConnectionInit().asJson.noSpaces
      println(s"Sending: $sendMe")
      socket.send(sendMe)
    }

    private def onMessageInFluxChange = { (_: ReactEventFromTextArea, obj: TextAreaProps) =>
      $.modState(_.copy(msgInFlux = obj.value.get.asInstanceOf[String]))
    }

    private def onSend(
      p: Props,
      s: State
    ) = { (_: ReactMouseEventFrom[HTMLButtonElement], _: ButtonProps) =>
      import sttp.client._
      import sttp.client.monad.{Canceler, MonadAsyncError}
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
