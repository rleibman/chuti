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

package caliban.client.scalajs

import _root_.util.Config
import caliban.client.CalibanClientError.{DecodingError, ServerError}
import caliban.client.Operations.{IsOperation, RootSubscription}
import caliban.client.{GraphQLRequest, GraphQLResponse, GraphQLResponseError, SelectionBuilder, __Value}
import japgolly.scalajs.react.extra.TimerSupport
import japgolly.scalajs.react.{AsyncCallback, Callback}
import org.scalajs.dom.WebSocket
import sttp.capabilities
import sttp.client3.*
import zio.*
import zio.json.*

import java.net.URI
import java.time.Instant
import scala.concurrent.Future
import scala.util.{Failure, Success}

trait WebSocketHandler {

  def id:      String
  def close(): Callback

}

trait ScalaJSClientAdapter extends TimerSupport {

  // TODO, Caliban has an inner dependency on circe that would be nice to avoid. Oh well.
  given JsonDecoder[__Value] = {
    import io.circe.parser.*
    import caliban.client.__Value.valueDecoder
    JsonDecoder.string.mapOrFail(str => decode[__Value](str).left.map(_.toString))
  }
  given JsonEncoder[__Value] = {
    import io.circe.syntax.*
    import caliban.client.__Value.valueEncoder

    JsonEncoder.string.contramap[__Value](_.asJson.noSpaces)
  }

  given JsonEncoder[io.circe.Json] = JsonEncoder.string.contramap[io.circe.Json](_.noSpaces)
  given JsonDecoder[io.circe.Json] = {
    import io.circe.parser.*
    JsonDecoder.string.mapOrFail(str => parse(str).left.map(_.toString))
  }

  given JsonCodec[GraphQLRequest] = DeriveJsonCodec.gen[GraphQLRequest]
  given JsonCodec[GraphQLResponse] = DeriveJsonCodec.gen[GraphQLResponse]
  given JsonCodec[GraphQLResponseError.Location] = DeriveJsonCodec.gen[GraphQLResponseError.Location]
  given JsonCodec[GraphQLResponseError] = DeriveJsonCodec.gen[GraphQLResponseError]

  val serverUri = uri"http://${Config.chutiHost}/api/game"
  given backend: SttpBackend[Future, capabilities.WebSockets] = FetchBackend()

  def asyncCalibanCall[Origin, A](
    selectionBuilder: SelectionBuilder[Origin, A]
  )(using ev:         IsOperation[Origin]
  ): AsyncCallback[A] = {
    val request = selectionBuilder.toRequest(serverUri)
    // TODO add headers as necessary
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    AsyncCallback
      .fromFuture(request.send(backend))
      .map(_.body match {
        case Left(exception) => throw exception
        case Right(value)    => value
      })
  }

  def asyncCalibanCallThroughJsonOpt[Origin, A: JsonDecoder](
    selectionBuilder: SelectionBuilder[Origin, Option[String]]
  )(using ev:         IsOperation[Origin]
  ): AsyncCallback[Option[A]] =
    asyncCalibanCall[Origin, Option[String]](selectionBuilder).map { jsonOpt =>
      jsonOpt.map(_.fromJson[A]) match {
        case Some(Right(value)) => Some(value)
        case Some(Left(error)) =>
          Callback.throwException(RuntimeException(error)).runNow()
          None
        case None => None
      }
    }

  def calibanCall[Origin, A](
    selectionBuilder: SelectionBuilder[Origin, A],
    callback:         A => Callback
  )(using ev:         IsOperation[Origin]
  ): Callback = {
    val request = selectionBuilder.toRequest(serverUri)
    // TODO add headers as necessary
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

    AsyncCallback
      .fromFuture(request.send(backend))
      .completeWith {
        case Success(response) if response.code.isSuccess || response.code.isInformational =>
          response.body match {
            case Right(a) =>
              callback(a)
            case Left(error) =>
              Callback.log(s"1 Error: $error") // TODO handle error responses better
          }
        case Failure(exception) =>
          Callback.throwException(exception) // TODO handle error responses better
        case Success(response) =>
          Callback.log(s"2 Error: ${response.statusText}") // TODO handle error responses better
      }
  }

  def calibanCallThroughJsonOpt[Origin, A: JsonDecoder](
    selectionBuilder: SelectionBuilder[Origin, Option[String]],
    callback:         Option[A] => Callback
  )(using ev:         IsOperation[Origin]
  ): Callback =
    calibanCall[Origin, Option[String]](
      selectionBuilder,
      {
        case Some(json) =>
          json.fromJson[A] match {
            case Right(obj) => callback(Option(obj))
            case Left(error) =>
              Callback.log(s"3 Error: $error") // TODO handle error responses better
          }
        case None => callback(None)
      }
    )

  def calibanCallThroughJson[Origin, A: JsonDecoder](
    selectionBuilder: SelectionBuilder[Origin, String],
    callback:         A => Callback
  )(using ev:         IsOperation[Origin]
  ): Callback =
    calibanCall[Origin, String](
      selectionBuilder,
      { json =>
        json.fromJson[A] match {
          case Right(obj) => callback(obj)
          case Left(error) =>
            Callback.log(s"4 Error: $error") // TODO handle error responses better
        }
      }
    )

  import GQLOperationMessage.*

  case class GQLOperationMessage(
    `type`:  String,
    id:      Option[String] = None,
    payload: Option[String] = None
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

    given JsonCodec[GQLOperationMessage] = DeriveJsonCodec.gen[GQLOperationMessage]

  }

  import scala.language.unsafeNulls
  // TODO we will replace this with some zio thing as soon as I figure out how, maybe replace all callbacks to zios?
  def makeWebSocketClient[A: JsonDecoder](
    uriOrSocket:      Either[URI, WebSocket],
    query:            SelectionBuilder[RootSubscription, A],
    operationId:      String,
    connectionId:     String,
    onData:           (String, Option[A]) => Callback,
    connectionParams: Option[String] = None,
    timeout: Duration = 8.minutes, // how long the client should wait in ms for a keep-alive message from the server (default 5 minutes), this parameter is ignored if the server does not send keep-alive messages. This will also be used to calculate the max connection time per connect/reconnect
    reconnect:            Boolean = true,
    reconnectionAttempts: Int = 3,
    onConnected:          (String, Option[String]) => Callback = { (_, _) => Callback.empty },
    onReconnected:        (String, Option[String]) => Callback = { (_, _) => Callback.empty },
    onReconnecting:       String => Callback = { _ => Callback.empty },
    onConnecting:         Callback = Callback.empty,
    onDisconnected:       (String, Option[String]) => Callback = { (_, _) => Callback.empty },
    onKeepAlive:          Option[String] => Callback = { _ => Callback.empty },
    onServerError:        (String, Option[String]) => Callback = { (_, _) => Callback.empty },
    onClientError:        String => Callback = { _ => Callback.empty }
  ): WebSocketHandler =
    new WebSocketHandler {

      override val id: String = connectionId

      def GQLConnectionInit(): GQLOperationMessage = GQLOperationMessage(GQL_CONNECTION_INIT, Option(operationId), connectionParams)

      def GQLStart(query: GraphQLRequest): GQLOperationMessage = GQLOperationMessage(GQL_START, Option(operationId), payload = Option(query.toJson))

      def GQLStop(): GQLOperationMessage = GQLOperationMessage(GQL_STOP, Option(operationId))

      def GQLConnectionTerminate(): GQLOperationMessage = GQLOperationMessage(GQL_CONNECTION_TERMINATE, Option(operationId))

      private val graphql: GraphQLRequest = query.toGraphQL()

      val socket: WebSocket = uriOrSocket match {
        case Left(uri)        => new org.scalajs.dom.WebSocket(uri.toString, "graphql-ws")
        case Right(webSocket) => webSocket
      }

      // TODO, move this into some sort of Ref/state class
      case class ConnectionState(
        lastKAOpt:       Option[Instant] = None,
        kaIntervalOpt:   Option[Int] = None,
        firstConnection: Boolean = true,
        reconnectCount:  Int = 0,
        closed:          Boolean = false
      )

      private var connectionState: ConnectionState = ConnectionState()

      def doConnect(): Unit = {
        if (!connectionState.closed) {
          val sendMe = GQLConnectionInit()
          println(s"Sending: $sendMe")
          socket.send(sendMe.toJson)
        } else println("Connection is already closed")
      }

      socket.onmessage = { (e: org.scalajs.dom.MessageEvent) =>
        val strMsg = e.data.toString
        val msg: Either[String, GQLOperationMessage] = strMsg.fromJson[GQLOperationMessage]
//      println(s"Received: $strMsg")
        msg match {
          case Right(GQLOperationMessage(GQL_COMPLETE, id, payload)) =>
            connectionState.kaIntervalOpt.foreach(id => org.scalajs.dom.window.clearInterval(id))
            onDisconnected(id.getOrElse(""), payload).runNow()
//          if (reconnect && connectionState.reconnectCount <= reconnectionAttempts) {
//            connectionState =
//              connectionState.copy(reconnectCount = connectionState.reconnectCount + 1)
//            onReconnecting(id.getOrElse(""))
//            doConnect()
//          } else if (connectionState.reconnectCount > reconnectionAttempts) {
//            println("Maximum number of connection retries exceeded")
//          }
          // Nothing else to do, really
          case Right(GQLOperationMessage(GQL_CONNECTION_ACK, id, payload)) =>
            // We should only do this the first time
            if (connectionState.firstConnection) {
              onConnected(id.getOrElse(""), payload).runNow()
              connectionState = connectionState.copy(firstConnection = false, reconnectCount = 0)
            } else onReconnected(id.getOrElse(""), payload).runNow()
            val sendMe = GQLStart(graphql)
            println(s"Sending: $sendMe")
            socket.send(sendMe.toJson)
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
                            .between(lastKA, Instant.now.nn).nn.toMillis.milliseconds
                        if (timeFromLastKA > timeout) {
                          // Assume we've gotten disconnected, we haven't received a KA in a while
                          if (reconnect && connectionState.reconnectCount <= reconnectionAttempts) {
                            connectionState = connectionState.copy(reconnectCount = connectionState.reconnectCount + 1)
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
            connectionState = connectionState.copy(lastKAOpt = Option(Instant.now().nn))
            onKeepAlive(payload).runNow()
          case Right(GQLOperationMessage(GQL_DATA, id, payloadOpt)) =>
            if (connectionState.closed) println("Connection is already closed")
            else {
              connectionState = connectionState.copy(reconnectCount = 0)
              val res = for {
                payload <- payloadOpt.toRight(DecodingError("No payload"))
                parsed <-
                  payload
                    .fromJson[GraphQLResponse]
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
                  onClientError(error.getMessage).runNow()
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
            onClientError(error)
            println(error)
        }
      }
      socket.onerror = { (e: org.scalajs.dom.Event) =>
        println(s"Got error $e")
        onClientError(s"We've got a socket error, no further info ($e)")
      }
      socket.onopen = { (_: org.scalajs.dom.Event) =>
        onConnecting.runNow()
        doConnect()
      }

      override def close(): Callback = {
        if (socket.readyState == WebSocket.CONNECTING) {
          Callback.log(
            "Socket is currently connecting, waiting a second for it to finish and then we'r trying again"
          ) >>
            setTimeoutMs(close(), 1000)
        } else if (socket.readyState == WebSocket.OPEN) {
          Callback.log(s"Closing socket: $query") >> Callback {
            connectionState = connectionState.copy(closed = true)
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
