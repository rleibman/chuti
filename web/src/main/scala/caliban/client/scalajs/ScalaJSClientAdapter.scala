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

import caliban.client.Operations.RootSubscription
import caliban.client.{GraphQLRequest, SelectionBuilder}
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Decoder, Error, Json}
import org.scalajs.dom
import zio.duration._

trait ScalaJSClientAdapter {

  trait WebSocketHandler {
    def close(): Unit
  }

  //TODO we will replace this with some zio thing as soon as I figure out how
  def makeWebSocketClient[A: Decoder](
    uri:                  URI,
    query:                SelectionBuilder[RootSubscription, A],
    onData:               Option[A] => Unit,
    connectionParams:     Option[Json] = None,
    timeout:              Duration = 30.seconds, //how long the client should wait in ms for a keep-alive message from the server (default 30000 ms), this parameter is ignored if the server does not send keep-alive messages. This will also be used to calculate the max connection time per connect/reconnect
    reconnect:            Boolean = true,
    reconnectionAttempts: Int = 3,
    onConnected: Option[Json] => Unit = { _ =>
      ()
    },
    onReconnected: Option[Json] => Unit = { _ =>
      ()
    },
    onReconnecting: () => Unit = { () =>
      ()
    },
    onConnecting: () => Unit = { () =>
      ()
    },
    onDisconnected: Option[Json] => Unit = { _ =>
      ()
    },
    onKeepAlive: Option[Json] => Unit = { _ =>
      ()
    },
    onServerError: Option[Json] => Unit = { _ =>
      ()
    },
    onClientError: Throwable => Unit = { _ =>
      ()
    }
  )(
    implicit decoder: Decoder[A]
  ): WebSocketHandler = {

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

      def GQLConnectionInit(): GQLOperationMessage =
        GQLOperationMessage(GQL_CONNECTION_INIT, connectionParams)

      def GQLStart(query: GraphQLRequest): GQLOperationMessage =
        GQLOperationMessage(GQL_START, payload = Option(query.asJson))

      def GQLStop(): GQLOperationMessage =
        GQLOperationMessage(GQL_STOP)

      def GQLConnectionTerminate(): GQLOperationMessage =
        GQLOperationMessage(GQL_CONNECTION_TERMINATE)
    }

    import GQLOperationMessage._

    case class GQLOperationMessage(
      `type`:  String,
      payload: Option[Json] = None,
      id:      Option[String] = None
    )

    val socket = new dom.WebSocket(uri.toString, "graphql-ws")

    //TODO, move this into some sort of Ref/state class
    case class ConnectionState(
      lastKAOpt:       Option[LocalDateTime] = None,
      kaIntervalOpt:   Option[Int] = None,
      firstConnection: Boolean = true,
      reconnectCount:  Int = 0
    )

    var connectionState = ConnectionState()

    def doConnect(): Unit = {
      val sendMe = GQLConnectionInit()
      println(s"Sending: $sendMe")
      socket.send(sendMe.asJson.noSpaces)
    }

    socket.onmessage = { (e: dom.MessageEvent) =>
      val msg: Either[Error, GQLOperationMessage] =
        decode[GQLOperationMessage](e.data.toString)
      println(s"Received: $msg")
      msg match {
        case Right(GQLOperationMessage(GQL_COMPLETE, payload, _)) =>
          connectionState.kaIntervalOpt.foreach(id => dom.window.clearInterval(id))
          onDisconnected(payload)
        //Nothing else to do, really
        case Right(GQLOperationMessage(GQL_CONNECTION_ACK, payload, _)) =>
          //We should only do this the first time
          if (connectionState.firstConnection) {
            onConnected(payload)
            connectionState = connectionState.copy(firstConnection = false, reconnectCount = 0)
          } else {
            onReconnected(payload)
          }
          val sendMe = GQLStart(query.toGraphQL())
          println(s"Sending: $sendMe")
          socket.send(sendMe.asJson.noSpaces)
        case Right(GQLOperationMessage(GQL_CONNECTION_ERROR, payload, _)) =>
          //if this is part of the initial connection, there's nothing to do, we could't connect and that's that.
          onServerError(payload)
          println(s"Connection Error from server $payload")
        case Right(GQLOperationMessage(GQL_CONNECTION_KEEP_ALIVE, payload, _)) =>
          println("Keep alive")
          connectionState = connectionState.copy(reconnectCount = 0)

          if (connectionState.lastKAOpt.isEmpty) {
            //This is the first time we get a keep alive, which means the server is configured for keep-alive,
            //If we never get this, then the server does not support it

            connectionState = connectionState.copy(kaIntervalOpt = Option(
              dom.window.setInterval(
                () => {
                  connectionState.lastKAOpt.map { lastKA =>
                    val timeFromLastKA =
                      java.time.Duration.between(lastKA, LocalDateTime.now).toMillis.milliseconds
                    if (timeFromLastKA > timeout) {
                      //Assume we've gotten disconnected, we haven't received a KA in a while
                      if (reconnect && connectionState.reconnectCount <= reconnectionAttempts) {
                        connectionState = connectionState.copy(reconnectCount = connectionState.reconnectCount + 1)
                        onReconnecting()
                        doConnect()
                      } else if (connectionState.reconnectCount > reconnectionAttempts) {
                        println("Maximum number of connection retries exceeded")
                      }
                    }
                  }
                },
                timeout.toMillis.toDouble
              )
            ))
          }
          connectionState = connectionState.copy(lastKAOpt = Option(LocalDateTime.now()))
          onKeepAlive(payload)
        case Right(GQLOperationMessage(GQL_DATA, payload, _)) =>
          connectionState = connectionState.copy(reconnectCount = 0)
          val data = payload.flatMap(json => decoder.decodeJson(json).toOption)
          onData(data)
        case Right(GQLOperationMessage(GQL_ERROR, payload, _)) =>
          println(s"Error from server $payload")
          onServerError(payload)
        case Right(GQLOperationMessage(GQL_UNKNOWN, payload, _)) =>
          println(s"Unknown server operation! GQL_UNKNOWN $payload")
          onServerError(payload)
        case Right(GQLOperationMessage(typ, payload, _)) =>
          println(s"Unknown server operation! $typ $payload")
        case Left(error) =>
          onClientError(error)
          error.printStackTrace()
      }
    }
    socket.onerror = { (_: dom.Event) =>
      onClientError(new Exception(s"We've got a socket error, no further info"))
    }
    socket.onopen = { (e: dom.Event) =>
      println(socket.protocol)
      println(e.`type`)
      onConnecting()
      doConnect()
    }

    () => {
      connectionState.kaIntervalOpt.foreach(id => dom.window.clearInterval(id))
      socket.send(GQLStop().asJson.noSpaces)
      socket.send(GQLConnectionTerminate().asJson.noSpaces)
    }
  }

}
