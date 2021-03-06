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

package caliban.client.scalajs

import java.net.URI
import java.time.LocalDateTime

import caliban.client.CalibanClientError.{DecodingError, ServerError}
import caliban.client.Operations.{IsOperation, RootSubscription}
import caliban.client.Value.ObjectValue
import caliban.client.{GraphQLRequest, GraphQLResponse, SelectionBuilder}
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Decoder, Error, Json}
import japgolly.scalajs.react.extra.TimerSupport
import japgolly.scalajs.react.{AsyncCallback, Callback}
import org.scalajs.dom.WebSocket
import util.Config
import zio.duration._

import scala.concurrent.Future
import scala.util.{Failure, Success}

trait WebSocketHandler {
  def id:      String
  def close(): Callback
}

trait ScalaJSClientAdapter extends TimerSupport {
  import sttp.client._
  val serverUri = uri"http://${Config.chutiHost}/api/game"
  implicit val backend: SttpBackend[Future, Nothing, NothingT] = FetchBackend()

  def asyncCalibanCall[Origin, A](
    selectionBuilder: SelectionBuilder[Origin, A]
  )(
    implicit ev: IsOperation[Origin]
  ): AsyncCallback[A] = {
    val request = selectionBuilder.toRequest(serverUri)
    //TODO add headers as necessary
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
    AsyncCallback
      .fromFuture(request.send())
      .map(_.body match {
        case Left(exception) => throw exception
        case Right(value)    => value
      })
  }

  def asyncCalibanCallThroughJsonOpt[Origin, A: Decoder](
    selectionBuilder: SelectionBuilder[Origin, Option[Json]]
  )(
    implicit ev: IsOperation[Origin]
  ): AsyncCallback[Option[A]] =
    asyncCalibanCall[Origin, Option[Json]](selectionBuilder).map { jsonOpt =>
      val decoder = implicitly[Decoder[A]]
      jsonOpt.map(decoder.decodeJson) match {
        case Some(Right(value)) => Some(value)
        case Some(Left(error)) =>
          Callback.throwException(error).runNow()
          None
        case None => None
      }
    }

  def calibanCall[Origin, A](
    selectionBuilder: SelectionBuilder[Origin, A],
    callback:         A => Callback
  )(
    implicit ev: IsOperation[Origin]
  ): Callback = {
    val request = selectionBuilder.toRequest(serverUri)
    //TODO add headers as necessary
    import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue

    AsyncCallback
      .fromFuture(request.send())
      .completeWith {
        case Success(response) if response.code.isSuccess || response.code.isInformational =>
          response.body match {
            case Right(a) =>
              callback(a)
            case Left(error) =>
              Callback.log(s"1 Error: $error") //TODO handle error responses better
          }
        case Failure(exception) =>
          Callback.throwException(exception) //TODO handle error responses better
        case Success(response) =>
          Callback.log(s"2 Error: ${response.statusText}") //TODO handle error responses better
      }
  }

  def calibanCallThroughJsonOpt[Origin, A: Decoder](
    selectionBuilder: SelectionBuilder[Origin, Option[Json]],
    callback:         Option[A] => Callback
  )(
    implicit ev: IsOperation[Origin]
  ): Callback =
    calibanCall[Origin, Option[Json]](
      selectionBuilder,
      {
        case Some(json) =>
          val decoder = implicitly[Decoder[A]]
          decoder.decodeJson(json) match {
            case Right(obj) => callback(Option(obj))
            case Left(error) =>
              Callback.log(s"3 Error: $error") //TODO handle error responses better
          }
        case None => callback(None)
      }
    )

  def calibanCallThroughJson[Origin, A: Decoder](
    selectionBuilder: SelectionBuilder[Origin, Json],
    callback:         A => Callback
  )(
    implicit ev: IsOperation[Origin]
  ): Callback =
    calibanCall[Origin, Json](
      selectionBuilder,
      { json =>
        val decoder = implicitly[Decoder[A]]
        decoder.decodeJson(json) match {
          case Right(obj) => callback(obj)
          case Left(error) =>
            Callback.log(s"4 Error: $error") //TODO handle error responses better
        }
      }
    )

  import GQLOperationMessage._

  case class GQLOperationMessage(
    `type`:  String,
    id:      Option[String] = None,
    payload: Option[Json] = None
  )

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
  }

  lazy private[caliban] val graphQLDecoder = implicitly[Decoder[GraphQLResponse]]

  //TODO we will replace this with some zio thing as soon as I figure out how, maybe replace all callbacks to zios?
  def makeWebSocketClient[A: Decoder](
    uriOrSocket:      Either[URI, WebSocket],
    query:            SelectionBuilder[RootSubscription, A],
    operationId:      String,
    connectionId:     String,
    onData:           (String, Option[A]) => Callback,
    connectionParams: Option[Json] = None,
    timeout: Duration =
      8.minutes, //how long the client should wait in ms for a keep-alive message from the server (default 5 minutes), this parameter is ignored if the server does not send keep-alive messages. This will also be used to calculate the max connection time per connect/reconnect
    reconnect:            Boolean = true,
    reconnectionAttempts: Int = 3,
    onConnected:          (String, Option[Json]) => Callback = { (_, _) => Callback.empty },
    onReconnected:        (String, Option[Json]) => Callback = { (_, _) => Callback.empty },
    onReconnecting:       String => Callback = { _ => Callback.empty },
    onConnecting:         Callback = Callback.empty,
    onDisconnected:       (String, Option[Json]) => Callback = { (_, _) => Callback.empty },
    onKeepAlive:          Option[Json] => Callback = { _ => Callback.empty },
    onServerError:        (String, Option[Json]) => Callback = { (_, _) => Callback.empty },
    onClientError:        Throwable => Callback = { _ => Callback.empty }
  ): WebSocketHandler =
    new WebSocketHandler {
      override val id: String = connectionId

      def GQLConnectionInit(): GQLOperationMessage =
        GQLOperationMessage(GQL_CONNECTION_INIT, Option(operationId), connectionParams)

      def GQLStart(query: GraphQLRequest): GQLOperationMessage =
        GQLOperationMessage(GQL_START, Option(operationId), payload = Option(query.asJson))

      def GQLStop(): GQLOperationMessage =
        GQLOperationMessage(GQL_STOP, Option(operationId))

      def GQLConnectionTerminate(): GQLOperationMessage =
        GQLOperationMessage(GQL_CONNECTION_TERMINATE, Option(operationId))

      private val graphql: GraphQLRequest = query.toGraphQL()

      val socket: WebSocket = uriOrSocket match {
        case Left(uri)        => new org.scalajs.dom.WebSocket(uri.toString, "graphql-ws")
        case Right(webSocket) => webSocket
      }

      //TODO, move this into some sort of Ref/state class
      case class ConnectionState(
        lastKAOpt:       Option[LocalDateTime] = None,
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
          socket.send(sendMe.asJson.noSpaces)
        } else
          println("Connection is already closed")
      }

      socket.onmessage = { (e: org.scalajs.dom.MessageEvent) =>
        val strMsg = e.data.toString
        val msg: Either[Error, GQLOperationMessage] =
          decode[GQLOperationMessage](strMsg)
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
          //Nothing else to do, really
          case Right(GQLOperationMessage(GQL_CONNECTION_ACK, id, payload)) =>
            //We should only do this the first time
            if (connectionState.firstConnection) {
              onConnected(id.getOrElse(""), payload).runNow()
              connectionState = connectionState.copy(firstConnection = false, reconnectCount = 0)
            } else
              onReconnected(id.getOrElse(""), payload).runNow()
            val sendMe = GQLStart(graphql)
            println(s"Sending: $sendMe")
            socket.send(sendMe.asJson.noSpaces)
          case Right(GQLOperationMessage(GQL_CONNECTION_ERROR, id, payload)) =>
            //if this is part of the initial connection, there's nothing to do, we could't connect and that's that.
            onServerError(id.getOrElse(""), payload).runNow()
            println(s"Connection Error from server $payload")
          case Right(GQLOperationMessage(GQL_CONNECTION_KEEP_ALIVE, id, payload)) =>
            println("ka")
            connectionState = connectionState.copy(reconnectCount = 0)

            if (connectionState.lastKAOpt.isEmpty) {
              //This is the first time we get a keep alive, which means the server is configured for keep-alive,
              //If we never get this, then the server does not support it

              connectionState = connectionState.copy(kaIntervalOpt =
                Option(
                  org.scalajs.dom.window.setInterval(
                    () => {
                      connectionState.lastKAOpt.map { lastKA =>
                        val timeFromLastKA =
                          java.time.Duration
                            .between(lastKA, LocalDateTime.now).toMillis.milliseconds
                        if (timeFromLastKA > timeout) {
                          //Assume we've gotten disconnected, we haven't received a KA in a while
                          if (reconnect && connectionState.reconnectCount <= reconnectionAttempts) {
                            connectionState = connectionState.copy(reconnectCount =
                              connectionState.reconnectCount + 1
                            )
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
            connectionState = connectionState.copy(lastKAOpt = Option(LocalDateTime.now()))
            onKeepAlive(payload).runNow()
          case Right(GQLOperationMessage(GQL_DATA, id, payloadOpt)) =>
            if (connectionState.closed)
              println("Connection is already closed")
            else {
              connectionState = connectionState.copy(reconnectCount = 0)
              val res = for {
                payload <- payloadOpt.toRight(DecodingError("No payload"))
                parsed <-
                  graphQLDecoder
                    .decodeJson(payload)
                    .left
                    .map(ex => DecodingError("Json deserialization error", Some(ex)))
                data <-
                  if (parsed.errors.nonEmpty) Left(ServerError(parsed.errors))
                  else Right(parsed.data)
                objectValue <- data match {
                  case Some(o: ObjectValue) => Right(o)
                  case _ => Left(DecodingError(s"Result is not an object ($data)"))
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
            onClientError(error)
            error.printStackTrace()
        }
      }
      socket.onerror = { (e: org.scalajs.dom.Event) =>
        println(s"Got error $e")
        onClientError(new Exception(s"We've got a socket error, no further info ($e)"))
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
            socket.send(GQLStop().asJson.noSpaces)
            socket.send(GQLConnectionTerminate().asJson.noSpaces)
            socket.close()
          }
        } else
          Callback.log(s"Socket is already closed: $query")
      }
    }

}
