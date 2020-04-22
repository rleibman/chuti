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

package pages

import java.net.URI

import caliban.client.Operations.RootMutation
import caliban.client.SelectionBuilder
import caliban.client.scalajs.ScalaJSClientAdapter
import chat.ChatComponent
import chuti.{ChannelId, ChatMessage, GameId, GameState, User}
import game.GameClient.{Mutations, Subscriptions, User => CalibanUser, UserEvent => CalibanUserEvent, UserEventType => CalibanUserEventType}
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import typings.semanticUiReact.components.{Button, Container, Header, Loader}
import typings.semanticUiReact.genericMod.SemanticSIZES
import io.circe.generic.auto._

import scala.concurrent.Future
import scala.util.{Failure, Success}

object LobbyPage extends ChutiPage {
  case class State(
    friends:        Seq[User] = Seq.empty,
    loggedInUsers:  Seq[User] = Seq.empty,
    privateMessage: Option[ChatMessage] = None,
    gameInProgress: Option[GameState] = None,
    invites:        Seq[GameState] = Seq.empty
  )

  class Backend($ : BackendScope[_, State]) extends ScalaJSClientAdapter {

    val wsHandle = makeWebSocketClient[(String, CalibanUserEventType)](
      uriOrSocket = Left(new URI("ws://localhost:8079/api/game/ws")),
      query = Subscriptions
        .userStream(
          CalibanUserEvent.user(CalibanUser.name) ~ CalibanUserEvent.userEventType
        ),
      onData = { (_, data) =>
        Callback
          .log(s"got data! $data")
          .runNow()
      },
      operationId = "-"
    )

    import sttp.client._
    val serverUri = uri"http://localhost:8079/api/game"
    implicit val backend: SttpBackend[Future, Nothing, NothingT] = FetchBackend()

    def onNewGame(): Callback = {
      Callback.empty
//      val selectionBuilder = CalibanGameState
//      val mutation = Mutations.newGame(selectionBuilder)
//      val request = mutation.toRequest(serverUri)
//      //TODO add headers as necessary
//      import scala.scalajs.concurrent.JSExecutionContext.Implicits.queue
//
//      Callback.log(s"Sending msg = ${s.msgInFlux}!") >> $.modState(_.copy(msgInFlux = "")) >> AsyncCallback
//        .fromFuture(request.send())
//        .completeWith {
//          case Success(response) if response.code.isSuccess || response.code.isInformational =>
//            Callback.log("Message sent")
//          case Failure(exception) => Callback.error(exception) //TODO handle error responses better
//          case Success(response) =>
//            Callback.log(s"Error: ${response.statusText}") //TODO handle error responses better
//        }
    }

    def render(s: State): VdomElement = {
      chutiContext.consume { chutiState =>
        chutiState.user.fold(<.div(Loader(active = true, size = SemanticSIZES.massive)("Cargando"))) {
          user =>
            <.div(
              ChatComponent(
                user,
                ChannelId.lobbyChannel,
                onPrivateMessage = Option(msg => $.modState(_.copy(privateMessage = Option(msg))))
              ),
              Container()(s.privateMessage.fold("")(_.msg)),
              Button()("Juega Con Quien sea"),
              Button(onClick = (_, _) => onNewGame())("Empezar Juego Nuevo"),
              TagMod(
                Container()(
                  Header()("Invitaciones"),
                  s.invites.toVdomArray { game =>
                    <.div(
                      ^.key := game.id.fold("")(_.toString),
                      game.jugadores.map(_.user.name).mkString(","),
                      Button()("Aceptar")
                    )
                  }
                )
              ).when(s.invites.nonEmpty),
              TagMod(
                Container()(
                  Header()("Amigos"),
                  s.friends.toVdomArray { friend =>
                    <.div(
                      ^.key := friend.id.fold("")(_.toString),
                      user.name
                    )
                  }
                )
              ).when(s.friends.nonEmpty),
              TagMod(
                Container()(
                  Header()("Juego En Progreso"),
                  Button()("Reanudar Juego"),
                  Button()("Abandonar Juego") //Que ojete!
                )
              ).when(s.gameInProgress.nonEmpty)
            )
        }
      }
    }
  }

  val component = ScalaComponent
    .builder[Unit]("LobbyPage")
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply(): Unmounted[Unit, State, Backend] = component()

}
