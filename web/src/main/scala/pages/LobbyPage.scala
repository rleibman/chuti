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
import caliban.client.CalibanClientError.DecodingError
import caliban.client.Operations.IsOperation
import caliban.client.SelectionBuilder
import caliban.client.Value.StringValue
import caliban.client.scalajs.ScalaJSClientAdapter
import chat.ChatComponent
import chuti._
import game.GameClient.{
  Mutations,
  Queries,
  Subscriptions,
  ChannelId => CalibanChannelId,
  User => CalibanUser,
  UserEvent => CalibanUserEvent,
  UserEventType => CalibanUserEventType,
  UserId => CalibanUserId,
  UserStatus => CalibanUserStatus
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
      val gameDecoder = implicitly[Decoder[Game]]

      Callback.log(s"Initializing ${props.chutiState.user}") >>
        calibanCallThroughJsonOpt[Queries, Game](
          Queries.getGameForUser,
          game => $.modState(_.copy(gameInProgress = Option(game)))
        ) >>
        calibanCall[Queries, Option[List[UserId]]](
          Queries.getFriends(CalibanUserId.value.map(UserId.apply)),
          friendsOpt => $.modState(_.copy(friends = friendsOpt.toSeq.flatten))
        ) >>
        calibanCall[Queries, Option[List[Json]]](
          Queries.getGameInvites,
          jsonInvites => {
            $.modState(
              _.copy(invites = jsonInvites.toSeq.flatten.map(json =>
                gameDecoder.decodeJson(json) match {
                  case Right(game) => game
                  case Left(error) => throw error
                }
              )
              )
            )
          }
        ) >>
        calibanCall[Queries, Option[List[User]]](
          Queries.getLoggedInUsers(
            (CalibanUser
              .id(CalibanUserId.value) ~ CalibanUser.name ~ CalibanUser.userStatus ~ CalibanUser
              .currentChannelId(CalibanChannelId.value)).mapN(
              (
                id:        Option[Int],
                name:      String,
                status:    CalibanUserStatus,
                channelId: Option[Int]
              ) => {
                val userStatus: UserStatus = CalibanUserStatus.encoder.encode(status) match {
                  case StringValue(str) => UserStatus.fromString(str)
                  case other            => throw DecodingError(s"Can't build UserStatus from input $other")
                }
                User(
                  id = id.map(UserId.apply),
                  email = "",
                  name = name,
                  userStatus = userStatus,
                  currentChannelId = channelId.map(ChannelId.apply)
                )
              })
          ),
          loggedInUsersOpt => $.modState(_.copy(loggedInUsers = loggedInUsersOpt.toSeq.flatten))
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
                      calibanCallThroughJsonOpt[Mutations, Game](
                        Mutations.acceptGameInvitation(game.id.fold(0)(_.value)),
                        game => $.modState(_.copy(gameInProgress = Option(game)))
                      )
                    })("Aceptar"),
                    Button(onClick = (_, _) => {
                      calibanCall[Mutations, Option[Boolean]](
                        Mutations.declineGameInvitation(game.id.fold(0)(_.value)),
                        _ => Callback.alert("Invitacion rechazada")
                      )
                    })("Rechazar")
                  )
                }
              )
            ).when(s.invites.nonEmpty),
            TagMod(
              Container()(
                Header()("Jugadores"),
                s.loggedInUsers.toVdomArray { player =>
                  <.div(
                    ^.key := user.id.fold("")(_.toString),
                    player.name
                  )
                //TODO add flags: isFriend, isPlaying
                //TODO make table, add context menu: invite, friend, unfriend,
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
                case GameStatus.esperandoJugadoresInvitados | GameStatus.esperandoJugadoresAzar =>
                  Container()(
                    <.p(
                      "Esperando Que otros jugadores se junten para poder empezar, en cuanto se junten cuatro empezamos!"
                    ),
                    <.p(s"Hasta ahorita van: ${game.jugadores.map(_.user.name).mkString(",")}")
                  )
                case GameStatus.terminado => EmptyVdom //I don't even know how we got here
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
