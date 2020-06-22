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
import caliban.client.SelectionBuilder
import caliban.client.Value.{EnumValue, StringValue}
import caliban.client.scalajs.ScalaJSClientAdapter
import chat._
import chuti._
import components.{Confirm, Toast}
import game.GameClient.{
  Mutations,
  Queries,
  Subscriptions,
  User => CalibanUser,
  UserEvent => CalibanUserEvent,
  UserEventType => CalibanUserEventType,
  UserStatus => CalibanUserStatus
}
import io.circe.generic.auto._
import io.circe.{Decoder, Json}
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.StateSnapshot
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.raw.HTMLInputElement
import typings.semanticUiReact.components._
import typings.semanticUiReact.genericMod.{SemanticICONS, SemanticSIZES}
import typings.semanticUiReact.inputInputMod.InputOnChangeData

//NOTE: things that change the state indirectly need to ask the snapshot to regen
object LobbyComponent extends ChutiPage with ScalaJSClientAdapter {
  import GamePage.Mode._

  private val connectionId = UUID.randomUUID().toString

  case class ExtUser(
    user:       User,
    isFriend:   Boolean,
    isLoggedIn: Boolean
  )

  object Dialog extends Enumeration {
    type Dialog = Value
    val none, newGame, inviteExternal = Value
  }
  import Dialog._

  case class NewGameDialogState(satoshiPerPoint: Int = 100)

  case class InviteExternalDialogState(
    name:  String = "",
    email: String = ""
  )

  case class State(
    friends:                   Seq[User] = Seq.empty,
    loggedInUsers:             Seq[User] = Seq.empty,
    privateMessage:            Option[ChatMessage] = None,
    invites:                   Seq[Game] = Seq.empty,
    userStream:                Option[WebSocketHandler] = None,
    dlg:                       Dialog = Dialog.none,
    newGameDialogState:        Option[NewGameDialogState] = None,
    inviteExternalDialogState: Option[InviteExternalDialogState] = None
  ) {
    lazy val usersAndFriends: Seq[ExtUser] =
      loggedInUsers.map(user => ExtUser(user, friends.exists(_.id == user.id), isLoggedIn = true)) ++
        friends
          .filterNot(u => loggedInUsers.exists(_.id == u.id)).map(
            ExtUser(_, isFriend = true, isLoggedIn = false)
          ).sortBy(_.user.name)
  }

  class Backend($ : BackendScope[Props, State]) {
    private val gameDecoder = implicitly[Decoder[Game]]

    lazy private val userSelectionBuilder: SelectionBuilder[CalibanUser, User] =
      (CalibanUser.id ~ CalibanUser.name ~ CalibanUser.userStatus).mapN(
        (
          id:     Option[Int],
          name:   String,
          status: CalibanUserStatus
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
            userStatus = userStatus
          )
        })

    def refresh(): Callback = {
      calibanCall[Queries, Option[List[User]]](
        Queries.getFriends(userSelectionBuilder),
        loggedInUsersOpt => $.modState(_.copy(friends = loggedInUsersOpt.toSeq.flatten))
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
          Queries.getLoggedInUsers(userSelectionBuilder),
          loggedInUsersOpt =>
            $.modState(_.copy(loggedInUsers = loggedInUsersOpt.toSeq.flatten.distinctBy(_.id)))
        )
    }

    def init(): Callback = {
      Callback.log(s"Initializing LobbyComponent") >>
        refresh() >>
        $.modState(
          _.copy(userStream = Option(
            makeWebSocketClient[(User, CalibanUserEventType)](
              uriOrSocket = Left(new URI("ws://localhost:8079/api/game/ws")),
              query = Subscriptions
                .userStream(connectionId)(
                  (CalibanUserEvent.user(CalibanUser.id) ~
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
          case None => Callback.empty
          case Some((user, Disconnected)) =>
            $.modState(s => s.copy(loggedInUsers = s.loggedInUsers.filter(_.id != user.id)))
          case Some((user, AbandonedGame | Connected | JoinedGame | Modified)) =>
            $.modState(s => s.copy(loggedInUsers = s.loggedInUsers.filter(_.id != user.id) :+ user))
        }
      }
    }

    def render(
      p: Props,
      s: State
    ): VdomElement = {
      def renderNewGameDialog = Modal(open = s.dlg == Dialog.newGame)(
        ModalHeader()("Juego Nuevo"),
        ModalContent()(
          FormField()(
            Label()("Satoshi por punto"),
            Input(
              required = true,
              name = "satoshiPerPoint",
              `type` = "number",
              min = 100,
              max = 10000,
              step = 100,
              value = s.newGameDialogState.fold(100.0)(_.satoshiPerPoint.toDouble),
              onChange = { (_: ReactEventFrom[HTMLInputElement], data: InputOnChangeData) =>
                $.modState(s =>
                  s.copy(newGameDialogState = s.newGameDialogState
                    .map(_.copy(satoshiPerPoint = data.value.get.asInstanceOf[String].toInt))
                  )
                )
              }
            )()
          )
        ),
        ModalActions()(
          Button(compact = true, onClick = { (_, _) =>
            $.modState(_.copy(dlg = Dialog.none, newGameDialogState = None))
          })("Cancelar"),
          Button(
            compact = true,
            onClick = { (_, _) =>
              Callback.log(s"Calling newGame") >>
                calibanCallThroughJsonOpt[Mutations, Game](
                  Mutations.newGame(s.newGameDialogState.fold(100)(_.satoshiPerPoint)),
                  game =>
                    Toast.success("Juego empezado!") >> p.gameInProgress
                      .setState(Option(game)) >> $.modState(
                      _.copy(
                        dlg = Dialog.none,
                        newGameDialogState = None
                      )
                    )
                )
            }
          )("Crear")
        )
      )

      def renderInviteExternalDialog = Modal(open = s.dlg == Dialog.inviteExternal)(
        ModalHeader()("Invitar amigo externo"),
        ModalContent()(
          FormField()(
            Label()("Nombre"),
            Input(
              required = true,
              name = "Nombre",
              value = s.inviteExternalDialogState.fold("")(_.name),
              onChange = { (_: ReactEventFrom[HTMLInputElement], data: InputOnChangeData) =>
                $.modState(s =>
                  s.copy(inviteExternalDialogState = s.inviteExternalDialogState
                    .map(_.copy(name = data.value.get.asInstanceOf[String]))
                  )
                )
              }
            )()
          ),
          FormField()(
            Label()("Correo"),
            Input(
              required = true,
              name = "Correo",
              `type` = "email",
              value = s.inviteExternalDialogState.fold("")(_.email),
              onChange = { (_: ReactEventFrom[HTMLInputElement], data: InputOnChangeData) =>
                $.modState(s =>
                  s.copy(inviteExternalDialogState = s.inviteExternalDialogState
                    .map(_.copy(email = data.value.get.asInstanceOf[String]))
                  )
                )
              }
            )()
          )
        ),
        ModalActions()(
          Button(compact = true, onClick = { (_, _) =>
            $.modState(_.copy(dlg = Dialog.none, inviteExternalDialogState = None))
          })("Cancelar"),
          p.gameInProgress.value.fold(EmptyVdom) { game =>
            Button(
              compact = true,
              onClick = {
                (_, _) =>
                  Callback.log(s"Inviting user by email") >>
                    calibanCall[Mutations, Option[Boolean]](
                      Mutations.inviteByEmail(
                        s.inviteExternalDialogState.fold("")(_.name),
                        s.inviteExternalDialogState.fold("")(_.email),
                        game.id.fold(0)(_.value)
                      ),
                      _ =>
                        Toast.success("Invitación mandada!") >> $.modState(
                          _.copy(dlg = Dialog.none, inviteExternalDialogState = None)
                        )
                    )
              }
            )("Invitar")
          }
        )
      )

      p.chutiState.user
        .fold(<.div(Loader(active = true, size = SemanticSIZES.massive)("Cargando"))) { user =>
          <.div(
            renderNewGameDialog,
            renderInviteExternalDialog,
            p.gameInProgress.value.fold("")(_.currentEventIndex.toString),
            ChatComponent(
              user,
              ChannelId.lobbyChannel,
              onPrivateMessage = Option(msg =>
                $.modState(_.copy(privateMessage = Option(msg))) >> Toast.info(
                  <.div(s"Tienes un nuevo mensaje!", <.br(), msg.msg)
                ) >> p.onRequestGameRefresh >> refresh()
              )
            ),
            Container(key = "privateMessage")(s.privateMessage.fold("")(_.msg)),
            TagMod(
              Button(
                compact = true,
                onClick = (_, _) =>
                  Callback.log(s"Calling joinRandomGame") >>
                    calibanCallThroughJsonOpt[Mutations, Game](
                      Mutations.joinRandomGame,
                      game =>
                        Toast.success("Sentado a la mesa!") >> p.gameInProgress.setState(
                          Option(game)
                        )
                    )
              )("Juega Con Quien sea"),
              Button(
                compact = true,
                onClick = (_, _) =>
                  $.modState(
                    _.copy(dlg = Dialog.newGame, newGameDialogState = Option(NewGameDialogState()))
                  )
              )(
                "Empezar Juego Nuevo"
              )
            ).when(p.gameInProgress.value.isEmpty),
            TagMod(
              Container(key = "invitaciones")(
                Header()("Invitaciones"),
                s.invites.toVdomArray { game =>
                  <.div(
                    ^.key := game.id.fold("")(_.toString),
                    game.jugadores.map(_.user.name).mkString(","),
                    Button(
                      compact = true,
                      onClick = (_, _) => {
                        calibanCallThroughJsonOpt[Mutations, Game](
                          Mutations.acceptGameInvitation(game.id.fold(0)(_.value)),
                          game => p.gameInProgress.setState(Option(game)) >> refresh()
                        )
                      }
                    )("Aceptar"),
                    Button(
                      compact = true,
                      onClick = (_, _) => {
                        calibanCall[Mutations, Option[Boolean]](
                          Mutations.declineGameInvitation(game.id.fold(0)(_.value)),
                          _ =>
                            Toast.success("Invitación rechazada") >>
                              p.gameInProgress.setState(None) >> refresh()
                        )
                      }
                    )("Rechazar")
                  )
                }
              )
            ).when(s.invites.nonEmpty),
            TagMod(
              Container(key = "jugadores")(
                Header()("Jugadores"),
                Table(className = "playersTable")(
                  TableBody()(
                    s.usersAndFriends.filter(_.user.id != user.id).toVdomArray { player =>
                      TableRow(key = player.user.id.fold("")(_.toString))(
                        TableCell()(
                          if (player.isFriend)
                            Icon(className = "icon", name = SemanticICONS.`star outline`)()
                          else
                            EmptyVdom,
                          if (player.user.userStatus == UserStatus.Playing)
                            <.img(^.src := "images/6_6.svg", ^.height := 16.px)
                          else
                            EmptyVdom,
                          if (player.isLoggedIn)
                            Icon(className = "icon", name = SemanticICONS.`user outline`)()
                          else
                            EmptyVdom
                        ),
                        TableCell()(player.user.name),
                        TableCell()(
                          Dropdown(
                            className = "menuBurger",
                            trigger = Icon(name = SemanticICONS.`ellipsis vertical`)()
                          )(
                            DropdownMenu()(
                              (for {
                                game     <- p.gameInProgress.value
                                userId   <- user.id
                                playerId <- player.user.id
                                gameId   <- game.id
                              } yield
                                if (player.user.userStatus != UserStatus.Playing &&
                                    game.gameStatus == GameStatus.esperandoJugadoresInvitados &&
                                    game.jugadores.head.id == user.id &&
                                    !game.jugadores.exists(_.id == player.user.id))
                                  DropdownItem(onClick = { (_, _) =>
                                    calibanCall[Mutations, Option[Boolean]](
                                      Mutations.inviteToGame(playerId.value, gameId.value),
                                      res =>
                                        if (res.getOrElse(false))
                                          Toast.success("Jugador Invitado!")
                                        else Toast.error("Error invitando jugador!")
                                    )
                                  })("Invitar a jugar"): VdomNode
                                else
                                  EmptyVdom).getOrElse(EmptyVdom),
                              if (player.isFriend)
                                DropdownItem(onClick = { (_, _) =>
                                  calibanCall[Mutations, Option[Boolean]](
                                    Mutations.unfriend(player.user.id.get.value),
                                    res =>
                                      if (res.getOrElse(false))
                                        refresh() >> Toast.success(
                                          s"Cortalas, ${player.user.name} ya no es tu amigo!"
                                        )
                                      else
                                        Toast.error("Error haciendo amigos!")
                                  )
                                })("Ya no quiero ser tu amigo")
                              else
                                DropdownItem(onClick = { (_, _) =>
                                  calibanCall[Mutations, Option[Boolean]](
                                    Mutations.friend(player.user.id.get.value),
                                    res =>
                                      if (res.getOrElse(false))
                                        p.onRequestGameRefresh >> refresh() >> Toast
                                          .success("Un nuevo amiguito!")
                                      else
                                        Toast.error("Error haciendo amigos!")
                                  )
                                })("Agregar como amigo")
                            )
                          )
                        )
                      )
                    }
                  )
                )
              )
            ).when(s.loggedInUsers.nonEmpty), {
              println(p.gameInProgress)
              ""
            },
            p.gameInProgress.value.toVdomArray { game =>
              Container(key = "gameInProgress")(
                Header()("Juego en Curso"),
                game.gameStatus match {
                  case status if status.enJuego =>
                    <.div(
                      s"Jugando con ${game.jugadores.map(_.user.name).mkString(",")}",
                      Button(compact = true, onClick = { (_, _) =>
                        p.mode.setState(GamePage.Mode.game)
                      })("Sentarse a la mesa de juego")
                    )
                  case GameStatus.esperandoJugadoresAzar =>
                    Container(key = "esperandoJugadores")(
                      <.p(
                        "Esperando Que otros jugadores se junten para poder empezar, en cuanto se junten cuatro empezamos!"
                      ),
                      <.p(s"Hasta ahorita van: ${game.jugadores.map(_.user.name).mkString(",")}")
                    )
                  case GameStatus.esperandoJugadoresInvitados =>
                    Container(key = "esperandoJugadores")(
                      <.div(
                        <.p(
                          "Esperando Que otros jugadores se junten para poder empezar, en cuanto se junten cuatro empezamos!"
                        ),
                        <.p(s"Tienes que invitar otros ${4 - game.jugadores.size} jugadores")
                          .when(game.jugadores.size < 4 && game.jugadores.head.id == user.id),
                        <.p(s"Invitados que no han aceptado todavía: ${game.jugadores
                          .filter(_.invited).map(_.user.name).mkString(",")}")
                          .when(game.jugadores.exists(_.invited)),
                        <.p(s"Invitados que ya están listos: ${game.jugadores
                          .filter(!_.invited).map(_.user.name).mkString(",")}")
                          .when(game.jugadores.exists(!_.invited)),
                        Button(
                          compact = true,
                          onClick = { (_, _) =>
                            calibanCall[Mutations, Option[Boolean]](
                              Mutations.cancelUnacceptedInvitations(game.id.get.value),
                              _ => Toast.success("Jugadores cancelados") >> refresh() >> p.onRequestGameRefresh
                            )
                          }
                        )("Cancelar invitaciones a aquellos que todavía no aceptan")
                          .when(
                            game.jugadores.exists(_.invited) && game.jugadores.head.id == user.id
                          )
                      ),
                      if (game.jugadores.head.id == user.id) {
                        Button(
                          compact = true,
                          onClick = (_, _) =>
                            $.modState(
                              _.copy(
                                dlg = Dialog.inviteExternal,
                                inviteExternalDialogState = Option(InviteExternalDialogState())
                              )
                            )
                        )("Invitar por correo electrónico")
                      } else {
                        EmptyVdom
                      }
                    )
                  case GameStatus.abandonado => EmptyVdom
                },
                Button(
                  compact = true,
                  onClick = (_, _) =>
                    Confirm.confirm(
                      header = Option("Abandonar juego"),
                      question =
                        s"Estas seguro que quieres abandonar el juego en el que te encuentras? Acuérdate que si ya empezó te va a costar ${game.abandonedPenalty * game.satoshiPerPoint} satoshi",
                      onConfirm = Callback.log(s"Abandoning game") >>
                        calibanCall[Mutations, Option[Boolean]](
                          Mutations.abandonGame(game.id.get.value),
                          res =>
                            (
                              if (res.getOrElse(false)) Toast.success("Juego abandonado!")
                              else Toast.error("Error abandonando juego!")
                            ) //>> p.gameInProgress.setState(None)
                        )
                    )
                )("Abandona Juego")
              )
            }
          )
        }
    }
  }

  case class Props(
    chutiState:           ChutiState,
    gameInProgress:       StateSnapshot[Option[Game]],
    onRequestGameRefresh: Callback,
    mode:                 StateSnapshot[Mode]
  )

  private val component = ScalaComponent
    .builder[Props]
    .initialState(State())
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.init())
    .build

  def apply(
    chutiState:           ChutiState,
    gameInProgress:       StateSnapshot[Option[Game]],
    onRequestGameRefresh: Callback,
    mode:                 StateSnapshot[Mode]
  ): Unmounted[Props, State, Backend] =
    component(Props(chutiState, gameInProgress, onRequestGameRefresh, mode))
}
