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

package caliban

import caliban.client.*
import caliban.client.CalibanClientError.{DecodingError, ServerError}
import caliban.client.GraphQLResponseError.Location
import caliban.client.Operations.{IsOperation, RootSubscription}
import chuti.ClientConfiguration
import japgolly.scalajs.react.extra.TimerSupport
import japgolly.scalajs.react.{AsyncCallback, Callback}
import org.scalajs.dom.WebSocket
import sttp.model.Uri
import zio.json.*
import zio.json.ast.*

import java.net.URI
import java.time.Instant
import scala.concurrent.duration.*

trait WebSocketHandler {

  def id: String

  def close(): Callback

}

case class ScalaJSClientAdapter(serverUri: Uri) extends TimerSupport {

  // Custom encoder/decoder for __Value that maps to/from JSON without discriminators
  given JsonEncoder[client.__Value] =
    new JsonEncoder[client.__Value] {
      def unsafeEncode(
        value:  client.__Value,
        indent: Option[Int],
        out:    zio.json.internal.Write
      ): Unit = {
        value match {
          case client.__Value.__StringValue(s)  => JsonEncoder.string.unsafeEncode(s, indent, out)
          case client.__Value.__NumberValue(n)  => JsonEncoder.bigDecimal.unsafeEncode(n.bigDecimal, indent, out)
          case client.__Value.__BooleanValue(b) => JsonEncoder.boolean.unsafeEncode(b, indent, out)
          case client.__Value.__NullValue       => out.write("null")
          case client.__Value.__EnumValue(e)    => JsonEncoder.string.unsafeEncode(e, indent, out)
          case client.__Value.__ListValue(values) =>
            out.write('[')
            var first = true
            values.foreach { v =>
              if (!first) out.write(',')
              first = false
              unsafeEncode(v, indent, out)
            }
            out.write(']')
          case client.__Value.__ObjectValue(fields) =>
            out.write('{')
            var first = true
            fields.foreach { case (k, v) =>
              if (!first) out.write(',')
              first = false
              JsonEncoder.string.unsafeEncode(k, indent, out)
              out.write(':')
              unsafeEncode(v, indent, out)
            }
            out.write('}')
        }
      }
    }

  given JsonDecoder[client.__Value] =
    new JsonDecoder[client.__Value] {
      def unsafeDecode(
        trace: List[JsonError],
        in:    zio.json.internal.RetractReader
      ): client.__Value = {
        // Parse as zio.json.ast.Json first, then convert
        val json = JsonDecoder[Json].unsafeDecode(trace, in)
        jsonToValue(json)
      }

      private def jsonToValue(json: Json): client.__Value =
        json match {
          case Json.Str(s)   => client.__Value.__StringValue(s)
          case Json.Num(n)   => client.__Value.__NumberValue(n)
          case Json.Bool(b)  => client.__Value.__BooleanValue(b)
          case Json.Null     => client.__Value.__NullValue
          case Json.Arr(arr) => client.__Value.__ListValue(arr.map(jsonToValue).toList)
          case Json.Obj(fields) =>
            client.__Value.__ObjectValue(fields.toList.map { case (k, v) => k -> jsonToValue(v) })
        }
    }

  given JsonEncoder[Location] = DeriveJsonEncoder.gen[Location]
  given JsonDecoder[Location] = DeriveJsonDecoder.gen[Location]
  given JsonEncoder[GraphQLResponseError] = DeriveJsonEncoder.gen[GraphQLResponseError]
  given JsonDecoder[GraphQLResponseError] = DeriveJsonDecoder.gen[GraphQLResponseError]

  given JsonEncoder[client.__Value.__ObjectValue] =
    new JsonEncoder[client.__Value.__ObjectValue] {
      def unsafeEncode(
        value:  client.__Value.__ObjectValue,
        indent: Option[Int],
        out:    zio.json.internal.Write
      ): Unit = {
        summon[JsonEncoder[client.__Value]].unsafeEncode(value, indent, out)
      }
    }
  given JsonDecoder[client.__Value.__ObjectValue] =
    new JsonDecoder[client.__Value.__ObjectValue] {
      def unsafeDecode(
        trace: List[JsonError],
        in:    zio.json.internal.RetractReader
      ): client.__Value.__ObjectValue = {
        summon[JsonDecoder[client.__Value]].unsafeDecode(trace, in) match {
          case obj: client.__Value.__ObjectValue => obj
          case other => throw Exception(s"Expected object but got $other")
        }
      }
    }

  given JsonEncoder[GraphQLResponse] = DeriveJsonEncoder.gen[GraphQLResponse]
  given JsonDecoder[GraphQLResponse] = DeriveJsonDecoder.gen[GraphQLResponse]
  given JsonEncoder[GraphQLRequest] = DeriveJsonEncoder.gen[GraphQLRequest]
  given JsonDecoder[GraphQLRequest] = DeriveJsonDecoder.gen[GraphQLRequest]
  given JsonEncoder[GQLOperationMessage] = DeriveJsonEncoder.gen[GQLOperationMessage]
  given JsonDecoder[GQLOperationMessage] = DeriveJsonDecoder.gen[GQLOperationMessage]

  // Enhancement error management switch this, insteaf of returning AsyncCallback, return an Either[Throwable, A]
  def asyncCalibanCallWithAuth[Origin, A](
    selectionBuilder: SelectionBuilder[Origin, A]
  )(using ev:         IsOperation[Origin]
  ): AsyncCallback[A] = {
    util.ApiClientSttp4
      .withAuth(selectionBuilder.toRequest(serverUri))
      .map { s =>
        s match {
          case Left(exception) => throw exception
          case Right(value)    => value
        }
      }
  }

  // Enhancement error management switch this, insteaf of returning AsyncCallback, return an Either[Throwable, A]
  def asyncCalibanCallWithAuthOptional[Origin, A](
    selectionBuilder: SelectionBuilder[Origin, A]
  )(using ev:         IsOperation[Origin]
  ): AsyncCallback[A] = {
    util.ApiClientSttp4
      .withAuthOptional(selectionBuilder.toRequest(serverUri))
      .map { s =>
        s match {
          case Left(exception) => throw exception
          case Right(value)    => value
        }
      }
  }

  import GQLOperationMessage.*

  case class GQLOperationMessage(
    `type`:  String,
    id:      Option[String] = None,
    payload: Option[Json] = None
  )

  object GQLOperationMessage {

    // Client messages
    val GQL_CONNECTION_INIT = "connection_init"
    val GQL_START = "start"
    val GQL_STOP = "stop"
    val GQL_CONNECTION_TERMINATE = "connection_terminate"
    // Server messages
    val GQL_COMPLETE = "complete"
    val GQL_CONNECTION_ACK = "connection_ack"
    val GQL_CONNECTION_ERROR = "connection_error"
    val GQL_CONNECTION_KEEP_ALIVE = "ka"
    val GQL_DATA = "data"
    val GQL_ERROR = "error"
    val GQL_UNKNOWN = "unknown"

  }

  import scala.language.unsafeNulls

  def makeWebSocketClient[A: JsonDecoder](
    path:               String,
    webSocket:          Option[WebSocket],
    query:              SelectionBuilder[RootSubscription, A],
    operationId:        String,
    socketConnectionId: String,
    onData:             (String, Option[A]) => Callback,
    connectionParams:   Option[Json] = None,
    timeout: Duration = 8.minutes, // how long the client should wait in ms for a keep-alive message from the server (default 5 minutes), this parameter is ignored if the server does not send keep-alive messages. This will also be used to calculate the max connection time per connect/reconnect
    reconnect:            Boolean = true,
    reconnectionAttempts: Int = 3,
    onConnected: (String, Option[Json]) => Callback = {
      (
        _,
        _
      ) =>
        Callback.empty
    },
    onReconnected: (String, Option[Json]) => Callback = {
      (
        _,
        _
      ) =>
        Callback.empty
    },
    onReconnecting: String => Callback = { _ => Callback.empty },
    onConnecting:   Callback = Callback.empty,
    onDisconnected: (String, Option[Json]) => Callback = {
      (
        _,
        _
      ) =>
        Callback.empty
    },
    onKeepAlive: Option[Json] => Callback = { _ => Callback.empty },
    onServerError: (String, Option[Json]) => Callback = {
      (
        _,
        _
      ) =>
        Callback.empty
    },
    onClientError: Throwable => Callback = { _ => Callback.empty }
  ): WebSocketHandler =
    new WebSocketHandler {

      override val id: String = socketConnectionId

      def GQLConnectionInit(): GQLOperationMessage =
        GQLOperationMessage(GQL_CONNECTION_INIT, Option(operationId), connectionParams)

      def GQLStart(query: GraphQLRequest): GQLOperationMessage =
        GQLOperationMessage(GQL_START, Option(operationId), payload = query.toJsonAST.toOption)

      def GQLStop(): GQLOperationMessage = GQLOperationMessage(GQL_STOP, Option(operationId))

      def GQLConnectionTerminate(): GQLOperationMessage =
        GQLOperationMessage(GQL_CONNECTION_TERMINATE, Option(operationId))

      private val graphql: GraphQLRequest = query.toGraphQL()

      var socket: WebSocket = webSocket.getOrElse {
        // Use wss:// on server (HTTPS), ws:// locally (HTTP)
        val protocol = if (org.scalajs.dom.window.location.protocol == "https:") "wss" else "ws"
        val uri = URI(s"$protocol://${ClientConfiguration.live.host}/$path")
        println("Connecting to WebSocket at " + uri.toString)

        org.scalajs.dom.WebSocket(uri.toString, "graphql-ws")
      }

      // Enhancement, move this into some sort of Ref/state class
      case class ConnectionState(
        lastKAOpt:          Option[Instant] = None,
        kaIntervalOpt:      Option[Int] = None,
        firstConnection:    Boolean = true,
        reconnectCount:     Int = 0,
        closed:             Boolean = false,
        firstReconnectTime: Option[Instant] = None,
        reconnectTimeoutId: Option[Int] = None
      )

      private var connectionState: ConnectionState = ConnectionState()

      private val MAX_RECONNECT_DELAY_MS = 10 * 60 * 1000 // 10 minutes
      private val MAX_TOTAL_RECONNECT_TIME_MS = 60 * 60 * 1000 // 60 minutes
      private val BASE_RECONNECT_DELAY_MS = 1000 // 1 second

      def calculateBackoffDelay(attemptNumber: Int): Int = {
        val exponentialDelay = (BASE_RECONNECT_DELAY_MS * scala.math.pow(2, attemptNumber)).toInt
        scala.math.min(exponentialDelay, MAX_RECONNECT_DELAY_MS)
      }

      def attemptReconnect(): Unit = {
        if (connectionState.closed) {
          println("Not reconnecting - connection was explicitly closed")
          return
        }

        val now = Instant.now()
        val firstReconnect = connectionState.firstReconnectTime.getOrElse(now)
        val totalReconnectTime = java.time.Duration.between(firstReconnect, now).toMillis

        if (totalReconnectTime > MAX_TOTAL_RECONNECT_TIME_MS) {
          println(s"Giving up reconnection after ${totalReconnectTime / 1000 / 60} minutes")
          onClientError(Exception("Failed to reconnect after 60 minutes")).runNow()
          return
        }

        val delay = calculateBackoffDelay(connectionState.reconnectCount)
        println(
          s"Attempting reconnection #${connectionState.reconnectCount + 1} in ${delay}ms (total reconnect time: ${totalReconnectTime / 1000}s)"
        )

        connectionState = connectionState.copy(
          firstReconnectTime = Some(firstReconnect),
          reconnectCount = connectionState.reconnectCount + 1
        )

        val timeoutId = org.scalajs.dom.window.setTimeout(
          { () =>
            onReconnecting(operationId).runNow()
            // Create a new websocket connection
            val protocol = if (org.scalajs.dom.window.location.protocol == "https:") "wss" else "ws"
            val uri = URI(s"$protocol://${ClientConfiguration.live.host}/$path")
            val newSocket = org.scalajs.dom.WebSocket(uri.toString, "graphql-ws")

            // Replace the old socket with the new one
            socket = newSocket

            // Setup all handlers on the new socket
            setupSocketHandlers(newSocket)
          },
          delay
        )

        connectionState = connectionState.copy(reconnectTimeoutId = Some(timeoutId))
      }

      def setupSocketHandlers(ws: WebSocket): Unit = {
        ws.onmessage = { (e: org.scalajs.dom.MessageEvent) =>
          val strMsg = e.data.toString
          val msg: Either[String, GQLOperationMessage] = strMsg.fromJson[GQLOperationMessage]
          //      println(s"Received: $strMsg")
          msg match {
            case Right(GQLOperationMessage(GQL_COMPLETE, id, payload)) =>
              connectionState.kaIntervalOpt.foreach(id => org.scalajs.dom.window.clearInterval(id))
              onDisconnected(id.getOrElse(""), payload).runNow()
            case Right(GQLOperationMessage(GQL_CONNECTION_ACK, id, payload)) =>
              // We should only do this the first time
              if (connectionState.firstConnection) {
                onConnected(id.getOrElse(""), payload).runNow()
                connectionState = connectionState.copy(firstConnection = false, reconnectCount = 0)
              } else onReconnected(id.getOrElse(""), payload).runNow()
              val sendMe = GQLStart(graphql)
              println(s"Sending: $sendMe")
              ws.send(sendMe.toJson)
            case Right(GQLOperationMessage(GQL_CONNECTION_ERROR, id, payload)) =>
              // if this is part of the initial connection, there's nothing to do, we could't connect and that's that.
              onServerError(id.getOrElse(""), payload).runNow()
              println(s"Connection Error from server $payload")
            case Right(GQLOperationMessage(GQL_CONNECTION_KEEP_ALIVE, id, payload)) =>
              println("ka")
              connectionState = connectionState.copy(reconnectCount = 0)

              if (connectionState.lastKAOpt.isEmpty) {
                // This is the first time we get a keep alive, which means the server is configured for keep-alive,
                // If we never get this, then the server does not support it

                connectionState = connectionState.copy(kaIntervalOpt =
                  Option(
                    org.scalajs.dom.window.setInterval(
                      () => {
                        connectionState.lastKAOpt.map { lastKA =>
                          val timeFromLastKA =
                            java.time.Duration
                              .between(lastKA, Instant.now).nn.toMillis.milliseconds
                          if (timeFromLastKA > timeout) {
                            // Assume we've gotten disconnected, we haven't received a KA in a while
                            if (reconnect && connectionState.reconnectCount <= reconnectionAttempts) {
                              connectionState =
                                connectionState.copy(reconnectCount = connectionState.reconnectCount + 1)
                              onReconnecting(id.getOrElse("")).runNow()
                              doConnect()
                            } else if (connectionState.reconnectCount > reconnectionAttempts)
                              println("Maximum number of connection retries exceeded")
                          }
                        }
                      },
                      timeout.toMillis.toDouble
                    )
                  )
                )
              }
              connectionState = connectionState.copy(lastKAOpt = Option(Instant.now()))
              onKeepAlive(payload).runNow()
            case Right(GQLOperationMessage(GQL_DATA, id, payloadOpt)) =>
              if (connectionState.closed) println("Connection is already closed")
              else {
                connectionState = connectionState.copy(reconnectCount = 0)
                val res = for {
                  payload <- payloadOpt.toRight(DecodingError("No payload"))
                  parsed <-
                    payload
                      .as[GraphQLResponse]
                      .left
                      .map(ex => DecodingError(s"Json deserialization error: $ex"))
                  data <-
                    if (parsed.errors.nonEmpty) Left(ServerError(parsed.errors))
                    else Right(parsed.data)
                  objectValue <- data match {
                    case Some(o) => Right(o)
                    case _       => Left(DecodingError(s"Result is not an object ($data)"))
                  }
                  result <- query.fromGraphQL(objectValue)
                } yield result

                res match {
                  case Right(data) =>
                    onData(id.getOrElse(""), Option(data)).runNow()
                  case Left(error) =>
                    error.printStackTrace()
                    onClientError(error).runNow()
                }
              }
            case Right(GQLOperationMessage(GQL_ERROR, id, payload)) =>
              println(s"Error from server $payload")
              onServerError(id.getOrElse(""), payload)
            case Right(GQLOperationMessage(GQL_UNKNOWN, id, payload)) =>
              println(s"Unknown server operation! GQL_UNKNOWN $payload")
              onServerError(id.getOrElse(""), payload)
            case Right(GQLOperationMessage(typ, id, payload)) =>
              println(s"Unknown server operation! $typ $payload $id")
            case Left(error) =>
              onClientError(DecodingError(error))
          }
        }

        ws.onerror = { (e: org.scalajs.dom.Event) =>
          println(s"Got error $e")
          onClientError(Exception(s"We've got a socket error, no further info ($e)")).runNow()
          // Try to reconnect after error if not explicitly closed
          if (!connectionState.closed) {
            attemptReconnect()
          }
        }

        ws.onopen = { (_: org.scalajs.dom.Event) =>
          println("WebSocket opened")
          onConnecting.runNow()
          // Reset reconnect state on successful connection
          connectionState = connectionState.copy(
            reconnectCount = 0,
            firstReconnectTime = None
          )
          doConnect()
        }

        ws.onclose = { (e: org.scalajs.dom.CloseEvent) =>
          if (connectionState.firstConnection) {
            println(s"Socket closed before connection could be established: $query, ${e.reason}")
          } else {
            println(s"Socket closed: $query, ${e.reason}")
          }
          // Try to reconnect after close if not explicitly closed
          if (!connectionState.closed) {
            attemptReconnect()
          }
        }
      }

      def doConnect(): Unit = {
        if (!connectionState.closed) {
          val sendMe = GQLConnectionInit()
          println(s"Sending: $sendMe")
          socket.send(sendMe.toJson)
        } else println("Connection is already closed")
      }

      // Setup handlers for the initial socket connection
      setupSocketHandlers(socket)

      override def close(): Callback = {
        if (socket.readyState == WebSocket.CONNECTING) {
          Callback.log(
            "Socket is currently connecting, waiting a second for it to finish and then we'r trying again"
          ) >>
            setTimeoutMs(close(), 1000)
        } else if (socket.readyState == WebSocket.OPEN) {
          Callback.log(s"Closing socket: $query") >> Callback {
            connectionState = connectionState.copy(closed = true)
            // Cancel any pending reconnection attempts
            connectionState.reconnectTimeoutId.foreach(id => org.scalajs.dom.window.clearTimeout(id))
            connectionState.kaIntervalOpt.foreach(id => org.scalajs.dom.window.clearInterval(id))
            socket.send(GQLStop().toJson)
            socket.send(GQLConnectionTerminate().toJson)
            socket.close()
          }
        } else
          Callback.log(s"Socket is already closed: $query")
      }

    }

}
