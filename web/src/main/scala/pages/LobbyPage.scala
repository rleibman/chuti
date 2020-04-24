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

import app.ChutiState
import caliban.client.Operations.IsOperation
import caliban.client.SelectionBuilder
import caliban.client.scalajs.ScalaJSClientAdapter
import chuti._
import game.GameClient.{
  Mutations,
  Queries,
  Subscriptions,
  User => CalibanUser,
  UserEvent => CalibanUserEvent,
  UserEventType => CalibanUserEventType
}
import io.circe.generic.auto._
import io.circe.{Decoder, Json}
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import typings.semanticUiReact.components.{Button, Container, Header, Loader}
import typings.semanticUiReact.genericMod.SemanticSIZES

import scala.concurrent.Future
import scala.util.{Failure, Success}

object LobbyPage extends ChutiPage {
  case class State(
                    friends:        Seq[UserId] = Seq.empty,
                    loggedInUsers:  Seq[User] = Seq.empty,
                    privateMessage: Option[ChatMessage] = None,
                    gameInProgress: Option[Game] = None,
                    invites:        Seq[Game] = Seq.empty
  )

  //TODO Move to common area
  import sttp.client._
  val serverUri = uri"http://localhost:8079/api/game"
  implicit val backend: SttpBackend[Future, Nothing, NothingT] = FetchBackend()

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
        case Failure(exception) => Callback.error(exception) //TODO handle error responses better
        case Success(response) =>
          Callback.log(s"2 Error: ${response.statusText}") //TODO handle error responses better
      }
  }

  def calibanCallThroughJsonOpt[Origin, A: Decoder](
    selectionBuilder: SelectionBuilder[Origin, Option[Json]],
    callback:         A => Callback
  )(
    implicit ev: IsOperation[Origin]
  ): Callback = calibanCall[Origin, Option[Json]](
    selectionBuilder, {
      case Some(json) =>
        val decoder = implicitly[Decoder[A]]
        decoder.decodeJson(json) match {
          case Right(obj) => callback(obj)
          case Left(error) =>
            Callback.log(s"3 Error: $error") //TODO handle error responses better
        }
      case None =>
        Callback
          .log(s"Did not receive a valid json object") //TODO handle error responses better
    }
  )

  def calibanCallThroughJson[Origin, A: Decoder](
    selectionBuilder: SelectionBuilder[Origin, Json],
    callback:         A => Callback
  )(
    implicit ev: IsOperation[Origin]
  ): Callback = calibanCall[Origin, Json](
    selectionBuilder, { json =>
      val decoder = implicitly[Decoder[A]]
      decoder.decodeJson(json) match {
        case Right(obj) => callback(obj)
        case Left(error) =>
          Callback.log(s"4 Error: $error") //TODO handle error responses better
      }
    }
  )

  class Backend($ : BackendScope[Props, State]) extends ScalaJSClientAdapter {
    val wsHandle: WebSocketHandler = makeWebSocketClient[(String, CalibanUserEventType)](
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

    def init(
      props: Props,
      state: State
    ): Callback = {
      Callback.log(s"Initializing ${props.chutiState.user}") >>
        calibanCallThroughJsonOpt[Queries, Game](
          Queries.getGameForUser,
          game => $.modState(_.copy(gameInProgress = Option(game)))
        ) >>
        calibanCallThroughJson[Queries, Seq[UserId]](
          Queries.getFriends,
          friends => $.modState(_.copy(friends = friends))
        ) >>
        calibanCallThroughJson[Queries, Seq[Game]](
          Queries.getInvites,
          invites => $.modState(_.copy(invites = invites))
        ) >>
        calibanCallThroughJson[Queries, Seq[User]](
          Queries.getLoggedInUsers,
          loggedInUsers => $.modState(_.copy(loggedInUsers = loggedInUsers))
        )
    }

    def render(
      p: Props,
      s: State
    ): VdomElement = {
      p.chutiState.user
        .fold(<.div(Loader(active = true, size = SemanticSIZES.massive)("Cargando"))) { user =>
          <.div(
            ChatComponent(
              user,
              ChannelId.lobbyChannel,
              onPrivateMessage = Option(msg => $.modState(_.copy(privateMessage = Option(msg))))
            ),
            Container()(s.privateMessage.fold("")(_.msg)),
            Button(onClick = (_, _) =>
              Callback.log(s"Calling joinRandomGame") >>
                calibanCallThroughJsonOpt[Mutations, Game](
                  Mutations.joinRandomGame,
                  game => $.modState(_.copy(gameInProgress = Option(game)))
                )
            )("Juega Con Quien sea"),
            Button(onClick = (_, _) =>
              Callback.log(s"Calling newGame") >>
                calibanCallThroughJsonOpt[Mutations, Game](
                  Mutations.newGame,
                  game => $.modState(_.copy(gameInProgress = Option(game)))
                )
            )("Empezar Juego Nuevo"),
            TagMod(
              Container()(
                Header()("Invitaciones"),
                s.invites.toVdomArray { game =>
                  <.div(
                    ^.key := game.id.fold("")(_.toString),
                    game.jugadores.map(_.user.name).mkString(","),
                    Button(onClick = (_, _) => {
                      calibanCallThroughJson[Mutations, Game](
                        Mutations.acceptGame(game.id.getOrElse(0)),
                        game => $.modState(_.copy(gameInProgress = Option(game)))
                      )
                    })("Aceptar")
                  )
                }
              )
            ).when(s.invites.nonEmpty),
            TagMod(
              Container()(
                Header()("Jugadores"),
                s.friends.toVdomArray { friend =>
                  <.div(
                    ^.key := friend.id.fold("")(_.toString),
                    user.name
                  )
                }
              )
            ).when(s.friends.nonEmpty),
            s.gameInProgress.toVdomArray { game =>
              game.gameStatus match {
                case status if status.enJuego =>
                  EmptyVdom
                /* //This is not necessary, I don't think. You never get to the lobby if you have a game in progress
                  Container()(
                    Header()("Juego En Progreso"),
                    game.jugadores.map(_.user.name).mkString(","),
                    Button()("Reanudar Juego"),
                    Button()("Abandonar Juego") //Que ojete!
                  )
                 */
                case Estado.esperandoJugadoresInvitados | Estado.esperandoJugadoresAzar =>
                  Container()(
                    <.p(
                      "Esperando Que otros jugadores se junten para poder empezar, en cuanto se junten cuatro empezamos!"
                    ),
                    <.p(s"Hasta ahorita van: ${game.jugadores.map(_.user.name).mkString(",")}")
                  )
                case Estado.terminado => EmptyVdom //I don't even know how we got here
              }
            }
          )
        }
    }
  }

  case class Props(chutiState: ChutiState)

  private val component = ScalaComponent
    .builder[Props]("LobbyPage")
    .initialState(State())
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.init($.props, $.state))
    .build

  def apply(chutiState: ChutiState): Unmounted[Props, State, Backend] = component(Props(chutiState))

}
