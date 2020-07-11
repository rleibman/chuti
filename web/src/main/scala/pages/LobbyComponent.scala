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

import java.time.ZoneOffset

import app.ChutiState
import caliban.client.scalajs.ScalaJSClientAdapter
import chat._
import chuti._
import components.{Confirm, Toast}
import game.GameClient.{Mutations, Queries}
import io.circe.generic.auto._
import io.circe.{Decoder, Json}
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.{ReusabilityOverlay, StateSnapshot}
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.raw.HTMLInputElement
import typings.react.reactStrings.center
import typings.semanticUiReact.components._
import typings.semanticUiReact.genericMod.{
  SemanticCOLORS,
  SemanticICONS,
  SemanticSIZES,
  SemanticWIDTHS
}
import typings.semanticUiReact.inputInputMod.InputOnChangeData

//NOTE: things that change the state indirectly need to ask the snapshot to regen
object LobbyComponent extends ChutiPage with ScalaJSClientAdapter {
  import app.GameViewMode._

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
    privateMessage:            Option[ChatMessage] = None,
    invites:                   List[Game] = Nil,
    dlg:                       Dialog = Dialog.none,
    newGameDialogState:        Option[NewGameDialogState] = None,
    inviteExternalDialogState: Option[InviteExternalDialogState] = None
  ) {}

  class Backend($ : BackendScope[Props, State]) {
    private val gameDecoder = implicitly[Decoder[Game]]

    def refresh(): Callback = {
      calibanCall[Queries, Option[List[Json]]](
        Queries.getGameInvites,
        jsonInvites => {
          $.modState(
            _.copy(invites = jsonInvites.toList.flatten.map(json =>
              gameDecoder.decodeJson(json) match {
                case Right(game) => game
                case Left(error) => throw error
              }
            )
            )
          )
        }
      )
    }

    def init(): Callback = {
      Callback.log(s"Initializing LobbyComponent") >>
        refresh()
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
          Button(
            compact = true,
            basic = true,
            onClick = { (_, _) =>
              $.modState(_.copy(dlg = Dialog.none, newGameDialogState = None))
            }
          )("Cancelar"),
          Button(
            compact = true,
            basic = true,
            onClick = { (_, _) =>
              calibanCallThroughJsonOpt[Mutations, Game](
                Mutations.newGame(s.newGameDialogState.fold(100)(_.satoshiPerPoint)),
                callback = { gameOpt =>
                  Toast.success("Juego empezado!") >> p.gameInProgress
                    .setState(gameOpt) >> $.modState(
                    _.copy(
                      dlg = Dialog.none,
                      newGameDialogState = None
                    )
                  )
                }
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
          Button(
            compact = true,
            basic = true,
            onClick = { (_, _) =>
              $.modState(_.copy(dlg = Dialog.none, inviteExternalDialogState = None))
            }
          )("Cancelar"),
          p.gameInProgress.value.fold(EmptyVdom) { game =>
            Button(
              compact = true,
              basic = true,
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

      ChutiState.ctx.consume { chutiState =>
        chutiState.user
          .fold(
            <.div(
              Loader(key = "cargando", active = true, size = SemanticSIZES.massive)("Cargando")
            )
          ) { user =>
            <.div(
              ^.key       := "lobby",
              ^.className := "lobby",
              <.div(
                ^.className := "lobbyCol1",
                <.div(
                  ^.className := "lobbyActions",
                  VdomArray(
                    Button(
                      key = "juegaConQuienSea",
                      compact = true,
                      basic = true,
                      onClick = (_, _) =>
                        Callback.log(s"Calling joinRandomGame") >>
                          calibanCallThroughJsonOpt[Mutations, Game](
                            Mutations.joinRandomGame,
                            callback = gameOpt =>
                              Toast.success("Sentado a la mesa!") >> p.gameInProgress.setState(
                                gameOpt
                              )
                          )
                    )("Juega Con Quien sea"),
                    Button(
                      key = "empezarJuegoNuevo",
                      compact = true,
                      basic = true,
                      onClick = (_, _) =>
                        $.modState(
                          _.copy(
                            dlg = Dialog.newGame,
                            newGameDialogState = Option(NewGameDialogState())
                          )
                        )
                    )(
                      "Empezar Juego Nuevo"
                    )
                  ).when(
                    p.gameInProgress.value.fold(true)(_.gameStatus == GameStatus.partidoTerminado)
                  ),
                  p.gameInProgress.value.toVdomArray { game =>
                    VdomArray(
                      game.gameStatus match {
                        case status if status.enJuego =>
                          EmptyVdom //Put here any action that should only happen when game is active
                        case GameStatus.esperandoJugadoresInvitados =>
                          VdomArray(
                            if (game.jugadores
                                  .exists(_.invited) && game.jugadores.head.id == user.id) {
                              Button(
                                key = "cancelarInvitaciones",
                                compact = true,
                                basic = true,
                                onClick = { (_, _) =>
                                  calibanCall[Mutations, Option[Boolean]](
                                    Mutations.cancelUnacceptedInvitations(game.id.get.value),
                                    _ => chutiState.onRequestGameRefresh >> refresh()
                                  )
                                }
                              )("Cancelar invitaciones a aquellos que todavía no aceptan")
                            } else {
                              EmptyVdom
                            },
                            if (game.jugadores.head.id == user.id) {
                              Button(
                                key = "invitarPorCorreo",
                                compact = true,
                                basic = true,
                                onClick = (_, _) =>
                                  $.modState(
                                    _.copy(
                                      dlg = Dialog.inviteExternal,
                                      inviteExternalDialogState =
                                        Option(InviteExternalDialogState())
                                    )
                                  )
                              )("Invitar por correo electrónico")
                            } else {
                              EmptyVdom
                            }
                          )
                        case _ => EmptyVdom
                      },
                      if (game.gameStatus != GameStatus.partidoTerminado) {
                        Button(
                          key = "abandonarJuego",
                          compact = true,
                          basic = true,
                          onClick = (_, _) =>
                            Confirm.confirm(
                              header = Option("Abandonar juego"),
                              question =
                                s"Estas seguro que quieres abandonar el juego en el que te encuentras? Acuérdate que si ya empezó te va a costar ${game.abandonedPenalty * game.satoshiPerPoint} satoshi",
                              onConfirm = Callback.log(s"Abandoning game") >>
                                calibanCall[Mutations, Option[Boolean]](
                                  Mutations.abandonGame(game.id.get.value),
                                  res =>
                                    if (res.getOrElse(false)) Toast.success("Juego abandonado!")
                                    else
                                      Toast.error("Error abandonando juego!") //>> p.gameInProgress.setState(None)
                                )
                            )
                        )("Abandona Juego")
                      } else if (s.invites.isEmpty) {
                        Button(
                          key = "nuevoJuegoMismosJugadores",
                          compact = true,
                          basic = true,
                          onClick = (_, _) =>
                            calibanCallThroughJsonOpt[Mutations, Game](
                              Mutations.newGameSameUsers(game.id.get.value),
                              gameOpt =>
                                Toast.success("Juego empezado!") >> p.gameInProgress
                                  .setState(gameOpt) >> $.modState(
                                  _.copy(
                                    dlg = Dialog.none,
                                    newGameDialogState = None
                                  )
                                )
                            )
                        )("Nuevo partido con los mismos jugadores")
                      } else {
                        EmptyVdom
                      }
                    )
                  }
                ),
                p.gameInProgress.value.toVdomArray { game =>
                  <.div(
                    ^.key       := "gameInProgress",
                    ^.className := "gameInProgress",
                    <.h1("Juego en Curso"),
                    <.div(
                      <.h2("En este juego"),
                      <.div(game.gameStatus match {
                        case GameStatus.jugando  => <.p("Estamos a medio juego")
                        case GameStatus.cantando => <.p("Estamos a medio juego (cantando)")
                        case GameStatus.requiereSopa =>
                          <.p("Estamos a medio juego (esperando la sopa)")
                        case GameStatus.abandonado => <.p("Juego abandonado")
                        case GameStatus.comienzo   => <.p("Juego empezado")
                        case GameStatus.partidoTerminado =>
                          <.p("Juego terminado")
                        case GameStatus.esperandoJugadoresAzar =>
                          <.p(
                            "Esperando Que otros jugadores se junten para poder empezar, en cuanto se junten cuatro empezamos!"
                          )
                        case GameStatus.esperandoJugadoresInvitados =>
                          <.span(
                            <.p(
                              "Esperando Que otros jugadores se junten para poder empezar, en cuanto se junten cuatro empezamos!"
                            ),
                            <.p(
                              s"Tienes que invitar otros ${4 - game.jugadores.size} jugadores"
                            ).when(
                              game.jugadores.size < 4 && game.jugadores.head.id == user.id
                            )
                          )
                        case _ => EmptyVdom
                      }),
                      <.table(
                        ^.className := "playersTable",
                        <.tbody(
                          game.jugadores.toVdomArray { jugador =>
                            <.tr(
                              ^.key := s"jugador${jugador.id}",
                              <.td(jugador.user.name),
                              <.td(game.jugadorState(jugador).description)
                            )
                          }
                        )
                      )
                    ),
                    TagMod(
                      <.div(
                        ^.key := "invitaciones",
                        <.h1("Invitaciones"),
                        <.table(
                          ^.className := "playersTable",
                          <.tbody(
                            s.invites.toVdomArray {
                              game =>
                                <.tr(
                                  ^.key := game.id.fold("")(_.toString),
                                  <.td(
                                    s"Juego con ${game.jugadores.map(_.user.name).mkString(",")}"
                                  ),
                                  <.td(
                                    Button(
                                      compact = true,
                                      basic = true,
                                      onClick = (_, _) => {
                                        calibanCallThroughJsonOpt[Mutations, Game](
                                          Mutations.acceptGameInvitation(
                                            game.id.fold(0)(_.value)
                                          ),
                                          gameOpt => p.gameInProgress.setState(gameOpt) >> refresh()
                                        )
                                      }
                                    )("Aceptar"),
                                    Button(
                                      compact = true,
                                      basic = true,
                                      onClick = (_, _) => {
                                        calibanCall[Mutations, Option[Boolean]](
                                          Mutations.declineGameInvitation(
                                            game.id.fold(0)(_.value)
                                          ),
                                          _ =>
                                            Toast.success("Invitación rechazada") >>
                                              p.gameInProgress.setState(None) >> refresh()
                                        )
                                      }
                                    )("Rechazar")
                                  )
                                )
                            }
                          )
                        )
                      )
                    ).when(s.invites.nonEmpty)
                  )
                }
              ),
              <.div(
                ^.className := "lobbyCol2",
                <.div(
                  <.h1(s"Bienvenido ${chutiState.user.fold("")(_.name)}!"),
                  <.p(
                    s"En cartera tienes ${chutiState.wallet.fold("")(_.amount.toString())} satoshi"
                  )
                ),
                <.div(
                  ^.className := "users",
                  renderNewGameDialog,
                  renderInviteExternalDialog,
                  <.div(^.key := "privateMessage", s.privateMessage.fold("")(_.msg)),
                  TagMod(
                    <.div(
                      ^.key := "jugadores",
                      <.h1("Otros usuarios"),
                      <.h2("(en linea o amigos)"),
                      <.table(
                        ^.className := "playersTable",
                        <.tbody(
                          chutiState.usersAndFriends.filter(_.user.id != user.id).toVdomArray { player =>
                            TableRow(key = player.user.id.fold("")(_.toString))(
                              TableCell(width = SemanticWIDTHS.`1`)(
                                if (player.isFriend)
                                  Popup(
                                    content = "Amigo",
                                    trigger = Icon(
                                      className = "icon",
                                      name = SemanticICONS.star,
                                      color = SemanticCOLORS.yellow,
                                      circular = true,
                                      fitted = true
                                    )()
                                  )()
                                else
                                  EmptyVdom,
                                if (player.user.userStatus == UserStatus.Playing)
                                  <.img(^.src := "images/6_6.svg", ^.height := 16.px)
                                else
                                  EmptyVdom,
                                if (player.isLoggedIn)
                                  Popup(
                                    content = "En linea",
                                    trigger = Icon(
                                      className = "icon",
                                      name = SemanticICONS.`user outline`,
                                      circular = true,
                                      fitted = true
                                    )()
                                  )()
                                else
                                  EmptyVdom
                              ),
                              TableCell(width = SemanticWIDTHS.`1`, align = center)(
                                Dropdown(
                                  className = "menuBurger",
                                  trigger = Icon(
                                    name = SemanticICONS.`ellipsis vertical`,
                                    fitted = true
                                  )()
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
                                            Mutations
                                              .inviteToGame(playerId.value, gameId.value),
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
                                              chutiState.onRequestGameRefresh >> refresh() >> Toast
                                                .success("Un nuevo amiguito!")
                                            else
                                              Toast.error("Error haciendo amigos!")
                                        )
                                      })("Agregar como amigo")
                                  )
                                )
                              ),
                              TableCell(width = SemanticWIDTHS.`14`)(player.user.name)
                            )
                          }
                        )
                      )
                    )
                  ).when(chutiState.loggedInUsers.nonEmpty), {
                    ""
                  }
                )
              )
            )
          }
      }
    }
  }

  case class Props(
    gameInProgress: StateSnapshot[Option[Game]],
    gameViewMode:   StateSnapshot[GameViewMode]
  )

  implicit val messageReuse: Reusability[ChatMessage] = Reusability.by(msg =>
    (msg.date.toInstant(ZoneOffset.UTC).getEpochSecond, msg.fromUser.id.map(_.value))
  )
  implicit val triunfoReuse: Reusability[Triunfo] = Reusability.by(_.toString)
  implicit val gameReuse: Reusability[Game] =
    Reusability.by(game => (game.id.map(_.value), game.currentEventIndex, game.triunfo))
  implicit private val propsReuse: Reusability[Props] = Reusability.derive[Props]
  implicit private val stateReuse: Reusability[State] =
    Reusability.caseClassExcept("dlg", "newGameDialogState", "inviteExternalDialogState")

  private val component = ScalaComponent
    .builder[Props]
    .initialState(State())
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.init())
    .configure(Reusability.shouldComponentUpdateAndLog("Lobby"))
    .configure(ReusabilityOverlay.install)
    .build

  def apply(
    gameInProgress: StateSnapshot[Option[Game]],
    mode:           StateSnapshot[GameViewMode]
  ): Unmounted[Props, State, Backend] =
    component(Props(gameInProgress, mode))
}
