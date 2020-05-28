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
import java.util.UUID

import app.ChutiState
import caliban.client.CalibanClientError.DecodingError
import caliban.client.Operations.IsOperation
import caliban.client.SelectionBuilder
import caliban.client.Value.{EnumValue, StringValue}
import caliban.client.scalajs.ScalaJSClientAdapter
import chat._
import chuti._
import components.{Confirm, Toast}
import game.GameClient.{ConnectionIdInput, GameIdInput, Mutations, Queries, Subscriptions, ChannelId => CalibanChannelId, User => CalibanUser, UserEvent => CalibanUserEvent, UserEventType => CalibanUserEventType, UserId => CalibanUserId, UserStatus => CalibanUserStatus}
import io.circe.generic.auto._
import io.circe.{Decoder, Json}
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import typings.semanticUiReact.components.{Button, Container, Dropdown, DropdownItem, DropdownMenu, Header, Icon, Loader, Table, TableBody, TableCell, TableRow}
import typings.semanticUiReact.genericMod.{SemanticICONS, SemanticSIZES}

import scala.concurrent.Future
import scala.util.{Failure, Success}

//TODO refactor LobbyPage and GamePage into MainPage with GameComponent and LobbyComponent, with modes
object LobbyPage extends ChutiPage with ScalaJSClientAdapter {
  private val connectionId: ConnectionIdInput = ConnectionIdInput(UUID.randomUUID().toString)

  println(ChatComponent.chatId)
  case class State(
    friends:        Set[UserId] = Set.empty,
    loggedInUsers:  Seq[User] = Seq.empty,
    privateMessage: Option[ChatMessage] = None,
    gameInProgress: Option[Game] = None,
    invites:        Seq[Game] = Seq.empty,
    userStream:     Option[WebSocketHandler] = None,
    gameStream:     Option[WebSocketHandler] = None,
    gameEventQueue: Seq[GameEvent] = Seq.empty
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
        case Failure(exception) =>
          Callback.throwException(exception) //TODO handle error responses better
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

  class Backend($ : BackendScope[Props, State]) {
    def init(
      props: Props,
      state: State
    ): Callback = {
      val gameDecoder = implicitly[Decoder[Game]]
      val gameEventDecoder = implicitly[Decoder[GameEvent]]

      def processQueue: Callback = Callback.empty //TODO write this

      def onGameEvent(gameEvent: GameEvent): Callback = {
        Callback.log(gameEvent.toString) >>
          $.modState(
            { s =>
              val moddedGame = s.gameInProgress.map {
                currentGame: Game =>
                  gameEvent.index match {
                    case None =>
                      throw GameException(
                        "This clearly could not be happening (event has no index)"
                      )
                    case Some(index) if index == currentGame.currentEventIndex => {
                      //If the game event is the next one to be applied, apply it to the game
                      currentGame.reapplyEvent(gameEvent)
                    }
                    case Some(index) if index > currentGame.currentEventIndex => {
                      //If it's a future event, put it in the queue, and add a timer to wait: if we haven't gotten the filling event
                      //In a few seconds, just get the full game.
                      currentGame
                    }
                    case Some(index) => {
                      //If it's past, mark an error, how could we have a past event???
                      throw GameException(
                        s"This clearly could not be happening eventIndex = $index, gameIndex = ${currentGame.currentEventIndex}"
                      )
                    }
                  }
              }
              val moddedQueue = s.gameInProgress.toSeq.flatMap { currentGame: Game =>
                gameEvent.index match {
                  case Some(index) if index == currentGame.nextIndex + 1 => {
                    //If it's a future event, put it in the queue, and add a timer to wait: if we haven't gotten the filling event
                    //In a few seconds, just get the full game.
                    s.gameEventQueue :+ gameEvent
                  }
                  case _ => s.gameEventQueue
                }
              }

              s.copy(gameInProgress = moddedGame, gameEventQueue = moddedQueue)
            },
            processQueue
          )
      }

      Callback.log(s"Initializing ${props.chutiState.user}") >>
        calibanCallThroughJsonOpt[Queries, Game](
          Queries.getGameForUser,
          game =>
            $.modState(
              _.copy(
                gameInProgress = Option(game),
                gameStream = Option(
                  makeWebSocketClient[Json](
                    uriOrSocket = Left(new URI("ws://localhost:8079/api/game/ws")),
                    query = Subscriptions.gameStream(GameIdInput(game.id.get.value), connectionId),
                    onData = { (_, data) =>
                      data.fold(Callback.empty)(json =>
                        gameEventDecoder
                          .decodeJson(json)
                          .fold(failure => Callback.throwException(failure), g => onGameEvent(g))
                      )
                    },
                    operationId = "-"
                  )
                )
              )
            )
        ) >>
        calibanCall[Queries, Option[List[UserId]]](
          Queries.getFriends(CalibanUserId.value.map(UserId.apply)),
          friendsOpt => $.modState(_.copy(friends = friendsOpt.toSet.flatten))
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
                  case EnumValue(str)   => UserStatus.fromString(str)
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
          loggedInUsersOpt =>
            $.modState(_.copy(loggedInUsers = loggedInUsersOpt.toSeq.flatten.distinctBy(_.id)))
        ) >> $.modState(
        _.copy(userStream = Option(
          makeWebSocketClient[(User, CalibanUserEventType)](
            uriOrSocket = Left(new URI("ws://localhost:8079/api/game/ws")),
            query = Subscriptions
              .userStream(connectionId.value)(
                (CalibanUserEvent.user(CalibanUser.id(CalibanUserId.value)) ~
                  CalibanUserEvent.user(CalibanUser.name) ~
                  CalibanUserEvent.user(CalibanUser.email) ~
                  CalibanUserEvent.userEventType).map {
                  case (((idOpt, name), email), eventType) =>
                    (
                      User(id = idOpt.map(UserId), name = name, email = email),
                      eventType
                    )
                }
              ),
            onData = { (_, data) =>
              onUserStreamData(data)
            },
            operationId = "-"
          )
        )
        )
      )
    }

    def onUserStreamData(data: Option[(User, CalibanUserEventType)]): Callback = {
      import CalibanUserEventType._
      Callback.log(data.toString) >> {
        data match {
          case None                        => Callback.empty
          case Some((user, AbandonedGame)) => Callback.empty
          case Some((user, Connected)) =>
            $.modState(s => s.copy(loggedInUsers = s.loggedInUsers.filter(_.id != user.id) :+ user))
          case Some((user, Disconnected)) =>
            $.modState(s => s.copy(loggedInUsers = s.loggedInUsers.filter(_.id != user.id)))
          case Some((user, JoinedGame)) => Callback.empty
          case Some((user, Modified))   => Callback.empty
        }
      }
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
            Container(key = "privateMessage")(s.privateMessage.fold("")(_.msg)),
            TagMod(
              Button(onClick = (_, _) =>
                Callback.log(s"Calling joinRandomGame") >>
                  calibanCallThroughJsonOpt[Mutations, Game](
                    Mutations.joinRandomGame,
                    game =>
                      Toast.success("Sentado a la mesa!") >> $.modState(
                        _.copy(gameInProgress = Option(game))
                      )
                  )
              )("Juega Con Quien sea"),
              Button(onClick = (_, _) =>
                Callback.log(s"Calling newGame") >>
                  calibanCallThroughJsonOpt[Mutations, Game](
                    Mutations.newGame,
                    game =>
                      Toast.success("Juego empezado!") >> $.modState(
                        _.copy(gameInProgress = Option(game))
                      )
                  )
              )("Empezar Juego Nuevo")
            ).when(s.gameInProgress.isEmpty),
            s.gameInProgress.fold(EmptyVdom)(game =>
              Button(onClick = (_, _) =>
                Confirm.confirm(
                  header = Option("Abandonar juego"),
                  question =
                    s"Estas seguro que quieres abandonar el juego en el que te encuentras? Acuérdate que si ya empezó te va a costar ${game.abandonedPenalty} satoshi",
                  onConfirm = Callback.log(s"Abandoning game") >>
                    calibanCall[Mutations, Option[Boolean]](
                      Mutations.abandonGame(game.id.get.value),
                      res =>
                        (
                          if (res.getOrElse(false)) Toast.success("Juego abandonado!")
                          else Toast.error("Error abandonando juego!")
                        ) >> $.modState(_.copy(gameInProgress = None))
                    )
                )
              )("Abandona Juego")
            ),
            TagMod(
              Container(key = "invitaciones")(
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
              Container(key = "jugadores")(
                Header()("Jugadores"),
                Table(className = "playersTable")(
                  TableBody()(
                    s.loggedInUsers.filter(_.id != user.id).toVdomArray { player =>
                      TableRow(key = player.id.fold("")(_.toString))(
                        TableCell()(
                          if (s.friends.contains(player.id.get))
                            Icon(className = "icon", name = SemanticICONS.`star outline`)()
                          else
                            EmptyVdom,
                          if(player.userStatus == UserStatus.Playing)
                            <.img( ^.src:="images/6_6.svg", ^.height := 16.px)
                          else
                            EmptyVdom,
                        ),
                        TableCell()(player.name),
                        TableCell()(
                          Dropdown(
                            //TODO hide class 'dropdown icon'
                            trigger = Icon(className = "icon", name = SemanticICONS.`ellipsis vertical`)()
                          )(
                            DropdownMenu()(
                              if(player.userStatus != UserStatus.Playing)
                                DropdownItem(onClick = {(_, _) => Callback.empty})("Invitar a jugar")
                              else
                                EmptyVdom,
                              if (!s.friends.contains(player.id.get))
                                DropdownItem(onClick = {(_, _) =>Callback.empty})("Agregar como amigo")
                              else
                                EmptyVdom,
                              if (s.friends.contains(player.id.get))
                                DropdownItem(onClick = {(_, _) => Callback.empty})("Quitar como amigo")
                              else
                                EmptyVdom,
                            )
                          )
                        )
                      )
                    }
                  )
                )
                //TODO add flags: isFriend, isPlaying
                //TODO make table, add context menu: invite, friend, unfriend,
              )
            ).when(s.loggedInUsers.nonEmpty),
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
                  Container(key = "esperandoJugadores")(
                    <.p(
                      "Esperando Que otros jugadores se junten para poder empezar, en cuanto se junten cuatro empezamos!"
                    ),
                    <.p(s"Hasta ahorita van: ${game.jugadores.map(_.user.name).mkString(",")}")
                  )
                case GameStatus.`requiereSopa` => EmptyVdom //I don't even know how we got here
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
