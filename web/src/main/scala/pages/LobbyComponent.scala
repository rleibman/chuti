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

import app.ChutiState
import caliban.client.scalajs.ScalaJSClientAdapter
import chuti.*
import components.{Confirm, Toast}
import game.GameClient.{Mutations, Queries}
import io.circe.generic.auto.*
import io.circe.{Decoder, Json}
import japgolly.scalajs.react.*
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.StateSnapshot
import japgolly.scalajs.react.vdom.html_<^.{<, _}
import org.scalajs.dom.HTMLInputElement
import net.leibman.chuti.react.reactStrings.center
import net.leibman.chuti.semanticUiReact.components.{ModalActions, _}
import net.leibman.chuti.semanticUiReact.genericMod.{SemanticCOLORS, SemanticICONS, SemanticSIZES, SemanticWIDTHS}
import net.leibman.chuti.semanticUiReact.inputInputMod.InputOnChangeData

//NOTE: things that change the state indirectly need to ask the snapshot to regen
object LobbyComponent extends ChutiPage with ScalaJSClientAdapter {

  import app.GameViewMode.*

  case class ExtUser(
    user:       User,
    isFriend:   Boolean,
    isLoggedIn: Boolean
  )

  object Dialog extends Enumeration {

    type Dialog = Value
    val none, newGame, inviteExternal, startWithBots = Value

  }
  import Dialog.*

  case class NewGameDialogState(satoshiPerPoint: Int = 100)

  case class InviteExternalDialogState(
    name:  String = "",
    email: String = ""
  )

  case class State(
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
            _.copy(invites =
              jsonInvites.toList.flatten.map(json =>
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
      def renderStartWithBotsDialog =
        Modal().open(s.dlg == Dialog.startWithBots)(
          ModalHeader()("Empezar juego (el resto de los jugadores serán bots)"), // TODO i8n
          ModalContent()("Estas seguro que quieres empezar este juego? Los jugadores que faltan serán bots, y son muy malos jugando (pero están aprendiendo), nota que no puedes ganar satoshis jugando con bots"), // TODO i8n
          ModalActions()(
            Button()
              .compact(true)
              .basic(true)
              .onClick { (_, _) =>
                $.modState(_.copy(dlg = Dialog.none))
              }("Cancelar"), // TODO i8n
            Button()
              .compact(true)
              .basic(true)
              .onClick { (_, _) =>
                Callback.log("Starting game") >> calibanCall(
                  Mutations.startGame(p.gameInProgress.value.flatMap(_.id).fold(-1)(_.value)),
                  callback = (_: Option[Boolean]) => Toast.success("Juego creado!") // TODO i8n
                )
              }("Crear") // TODO i8n
          )
        )

      def renderNewGameDialog =
        Modal().open(s.dlg == Dialog.newGame)(
          ModalHeader()("Juego Nuevo"), // TODO i8n
          ModalContent()(
            FormField()(
              Label()("Satoshi por punto"), // TODO i8n
              Input()
                .required(true)
                .name("satoshiPerPoint")
                .`type`("number")
                .min(100)
                .max(10000)
                .step(100)
                .value(s.newGameDialogState.fold(100.0)(_.satoshiPerPoint.toDouble))
                .onChange { (_: ReactEventFrom[HTMLInputElement], data: InputOnChangeData) =>
                  $.modState(s =>
                    s.copy(newGameDialogState =
                      s.newGameDialogState
                        .map(_.copy(satoshiPerPoint = data.value.get.asInstanceOf[String].toInt))
                    )
                  )
                }()
            )
          ),
          ModalActions()(
            Button()
              .compact(true)
              .basic(true)
              .onClick { (_, _) =>
                $.modState(_.copy(dlg = Dialog.none, newGameDialogState = None))
              }("Cancelar"), // TODO i8n
            Button()
              .compact(true)
              .basic(true)
              .onClick { (_, _) =>
                Callback.log("Calling new Game") >> calibanCallThroughJsonOpt[Mutations, Game](
                  Mutations.newGame(s.newGameDialogState.fold(100)(_.satoshiPerPoint)),
                  callback = { gameOpt =>
                    p.gameInProgress.setState(
                      gameOpt,
                      $.modState(
                        _.copy(
                          dlg = Dialog.none,
                          newGameDialogState = None
                        )
                      )
                    ) >>
                      Toast.success("Juego empezado!") // TODO i8n
                  }
                )
              }("Crear") // TODO i8n
          )
        )

      def renderFirstLoginWelcome(chutiState: ChutiState): VdomElement = {
        <.div(
          <.h1(^.marginBottom := 10.px, s"Bienvenido ${chutiState.user.fold("")(_.name)}!"), // TODO i8n
          <.p(
            ^.marginBottom := 10.px,
            s"Como regalo empiezas con 10,000 satoshi en tu cartera, ahorita tienes ${chutiState.wallet // TODO i8n
                .fold("")(_.amount.toString())}, disfrutalos!" // TODO i8n
          ),
          <.p(
            ^.marginBottom := 10.px,
            "Si nunca as jugado chuti, o tiene muchos años que no juegas, familiarizate con las ", // TODO i8n
            <.a(^.href := "#rules", "reglas de chuti") // TODO i8n
          ),
          <.p(^.marginBottom := 10.px, "Tienes varias opciones para empezar a jugar:"), // TODO i8n
          <.ul(
            <.li(
              "Si aprietas 'Juega con quien sea', entraras a un juego con otros jugadores al azar (o empezaras un juego nuevo si ningun juego esta esperando jugadores)" // TODO i8n
            ),
            <.li(
              "Si aprietas 'Crear Juego Nuevo', entonces puedes invitar amigos, ya sea otras personas que ya estén registradas, o si no están registradas entonces por correo electrónico" // TODO i8n
            ),
            <.li("Si alguien empezó un juego y te invito, solo tienes que aceptar la invitación") // TODO i8n
          ),
          <.p(
            ^.marginBottom := 10.px,
            "Una vez que se junten 4 jugadores el juego empieza automáticamente, usa el menu para 'Entrar al Juego'" // TODO i8n
          ),
          <.p(^.marginBottom := 10.px, "Nada mas puedes jugar un solo juego a la vez."), // TODO i8n
          <.p(
            ^.marginBottom := 10.px,
            "Usa el menu para regresar al lobby, ver las cuentas del juego actual, ver la historia de juegos pasados, administrar tu información, etc." // TODO i8n
          )
        )
      }

      def renderWelcome(chutiState: ChutiState): VdomElement = {
        <.div(
          <.h1(s"Bienvenido ${chutiState.user.fold("")(_.name)}!"), // TODO i8n
          <.p(
            s"En cartera tienes ${chutiState.wallet.fold("")(_.amount.toString())} satoshi" // TODO i8n
          )
        )
      }

      def renderInviteExternalDialog: VdomElement =
        Modal().open(s.dlg == Dialog.inviteExternal)(
          ModalHeader()("Invitar amigo externo"), // TODO i8n
          ModalContent()(
            FormField()(
              Label()("Nombre"), // TODO i8n
              Input()
                .required(true)
                .name("Nombre") // TODO i8n
                .value(s.inviteExternalDialogState.fold("")(_.name))
                .onChange { (_: ReactEventFrom[HTMLInputElement], data: InputOnChangeData) =>
                  $.modState(s =>
                    s.copy(inviteExternalDialogState =
                      s.inviteExternalDialogState
                        .map(_.copy(name = data.value.get.asInstanceOf[String]))
                    )
                  )
                }()
            ),
            FormField()(
              Label()("Correo"), // TODO i8n
              Input()
                .required(true)
                .name("Correo") // TODO i8n
                .`type`("email")
                .value(s.inviteExternalDialogState.fold("")(_.email))
                .onChange { (_: ReactEventFrom[HTMLInputElement], data: InputOnChangeData) =>
                  $.modState(s =>
                    s.copy(inviteExternalDialogState =
                      s.inviteExternalDialogState
                        .map(_.copy(email = data.value.get.asInstanceOf[String]))
                    )
                  )
                }()
            )
          ),
          ModalActions()(
            Button()
              .compact(true)
              .basic(true)
              .onClick { (_, _) =>
                $.modState(_.copy(dlg = Dialog.none, inviteExternalDialogState = None))
              }("Cancelar"), // TODO i8n
            p.gameInProgress.value.fold(EmptyVdom) { game =>
              Button()
                .compact(true)
                .basic(true)
                .onClick { (_, _) =>
                  Callback.log(s"Inviting user by email") >>
                    calibanCall[Mutations, Option[Boolean]](
                      Mutations.inviteByEmail(
                        s.inviteExternalDialogState.fold("")(_.name),
                        s.inviteExternalDialogState.fold("")(_.email),
                        game.id.fold(0)(_.value)
                      ),
                      _ =>
                        Toast.success("Invitación mandada!") >> $.modState( // TODO i8n
                          _.copy(dlg = Dialog.none, inviteExternalDialogState = None)
                        )
                    )
                }("Invitar") // TODO i8n
            }
          )
        )

      ChutiState.ctx.consume { chutiState =>
        chutiState.user
          .fold(
            <.div(
              Loader() // key = "cargando",
                .active(true)
                .size(SemanticSIZES.massive)("Cargando") // TODO i8n
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
                    Button()
//                      .key("juegaConQuienSea")
                      .compact(true)
                      .basic(true)
                      .onClick((_, _) =>
                        Callback.log(s"Calling joinRandomGame") >>
                          calibanCallThroughJsonOpt[Mutations, Game](
                            Mutations.joinRandomGame,
                            callback = gameOpt =>
                              Toast.success("Sentado a la mesa!") >> p.gameInProgress.setState( // TODO i8n
                                gameOpt
                              )
                          )
                      )("Juega Con Quien sea"), // TODO i8n
                    Button()
//                      .key("empezarJuegoNuevo")
                      .compact(true)
                      .basic(true)
                      .onClick((_, _) =>
                        $.modState(
                          _.copy(
                            dlg = Dialog.newGame,
                            newGameDialogState = Option(NewGameDialogState())
                          )
                        )
                      )(
                        "Crear Juego Nuevo" // TODO i8n
                      )
                  ).when(
                    p.gameInProgress.value.fold(true)(_.gameStatus == GameStatus.partidoTerminado)
                  ),
                  p.gameInProgress.value.toVdomArray { game =>
                    VdomArray(
                      game.gameStatus match {
                        case status if status.enJuego =>
                          EmptyVdom // Put here any action that should only happen when game is active
                        case GameStatus.esperandoJugadoresInvitados =>
                          VdomArray(
                            if (
                              game.jugadores
                                .exists(_.invited) && game.jugadores.head.id == user.id
                            ) {
                              Button()
//                                .key("cancelarInvitaciones")
                                .compact(true)
                                .basic(true)
                                .onClick { (_, _) =>
                                  calibanCall[Mutations, Option[Boolean]](
                                    Mutations.cancelUnacceptedInvitations(game.id.get.value),
                                    _ => chutiState.onRequestGameRefresh() >> refresh()
                                  )
                                }("Cancelar invitaciones a aquellos que todavía no aceptan") // TODO i8n
                            } else
                              EmptyVdom,
                            if (game.jugadores.head.id == user.id) {
                              VdomArray(
                                Button()
//                                .key("invitarPorCorreo")
                                  .compact(true)
                                  .basic(true)
                                  .onClick((_, _) =>
                                    $.modState(
                                      _.copy(
                                        dlg = Dialog.inviteExternal,
                                        inviteExternalDialogState = Option(InviteExternalDialogState())
                                      )
                                    )
                                  )("Invitar por correo electrónico"), // TODO i8n
                                Button()
                                  //                                .key("invitarPorCorreo")
                                  .compact(true)
                                  .basic(true)
                                  .onClick((_, _) =>
                                    $.modState(
                                      _.copy(
                                        dlg = Dialog.startWithBots
                                      )
                                    )
                                  )("Empezar juego (el resto de los jugadores serán bots)") // TODO i8n
                              )
                            } else
                              EmptyVdom
                          )
                        case _ => EmptyVdom
                      },
                      if (!game.gameStatus.acabado) {
                        val costo: Option[Double] =
                          if (game.gameStatus.enJuego)
                            game.cuentasCalculadas
                              .find(_._1.id == user.id).map(n => (n._2 + game.abandonedPenalty) * game.satoshiPerPoint)
                          else
                            None

                        Button()
//                          .key("abandonarJuego")
                          .compact(true)
                          .basic(true)
                          .onClick((_, _) =>
                            Confirm.confirm(
                              header = Option("Abandonar juego"), // TODO i8n
                              question = s"Estas seguro que quieres abandonar el juego en el que te encuentras? ${costo // TODO i8n
                                  .fold("")(n => s"Te va a costar $n satoshi")}", // TODO i8n
                              onConfirm = Callback.log(s"Abandoning game") >> // TODO i8n
                                calibanCall[Mutations, Option[Boolean]](
                                  Mutations.abandonGame(game.id.get.value),
                                  res =>
                                    if (res.getOrElse(false)) Toast.success("Juego abandonado!") // TODO i8n
                                    else
                                      Toast.error(
                                        "Error abandonando juego!" // TODO i8n
                                      )
                                )
                            )
                          )("Abandona Juego") // TODO i8n
                      } else if (s.invites.isEmpty) {
                        Button()
//                          key("nuevoJuegoMismosJugadores")
                          .compact(true)
                          .basic(true)
                          .onClick((_, _) =>
                            calibanCallThroughJsonOpt[Mutations, Game](
                              Mutations.newGameSameUsers(game.id.get.value),
                              gameOpt =>
                                Toast.success("Juego empezado!") >> p.gameInProgress // TODO i8n
                                  .setState(gameOpt) >> $.modState(
                                  _.copy(dlg = Dialog.none)
                                ) >> refresh()
                            )
                          )("Nuevo partido con los mismos jugadores") // TODO i8n
                      } else
                        EmptyVdom
                    )
                  }
                ),
                p.gameInProgress.value.toVdomArray { game =>
                  <.div(
                    ^.key       := "gameInProgress",
                    ^.className := "gameInProgress",
                    <.h1("Juego en Curso"), // TODO i8n
                    <.div(
                      <.h2(s"Juego #${game.id.fold(0)(_.value)}"), // TODO i8n
                      <.div(game.gameStatus match {
                        case GameStatus.jugando  => <.p("Estamos a medio juego") // TODO i8n
                        case GameStatus.cantando => <.p("Estamos a medio juego (cantando)") // TODO i8n
                        case GameStatus.requiereSopa =>
                          <.p("Estamos a medio juego (esperando la sopa)") // TODO i8n
                        case GameStatus.abandonado       => <.p("Juego abandonado") // TODO i8n
                        case GameStatus.comienzo         => <.p("Juego empezado") // TODO i8n
                        case GameStatus.partidoTerminado => <.p("Juego terminado") // TODO i8n
                        case GameStatus.esperandoJugadoresAzar =>
                          <.p(
                            "Esperando Que otros jugadores se junten para poder empezar, en cuanto se junten cuatro empezamos!" // TODO i8n
                          )
                        case GameStatus.esperandoJugadoresInvitados =>
                          <.span(
                            <.p(
                              "Esperando Que otros jugadores se junten para poder empezar, en cuanto se junten cuatro empezamos!" // TODO i8n
                            ),
                            <.p(
                              s"Tienes que invitar otros ${4 - game.jugadores.size} jugadores" // TODO i8n
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
                              <.td(JugadorState.description(game.jugadorState(jugador)))
                            )
                          }
                        )
                      )
                    )
                  )
                },
                TagMod(
                  <.div(
                    ^.key := "invitaciones",
                    <.h1("Invitaciones"), // TODO i8n
                    <.table(
                      ^.className := "playersTable",
                      <.tbody(
                        s.invites.toVdomArray { game =>
                          <.tr(
                            ^.key := game.id.fold("")(_.toString),
                            <.td(
                              s"Juego con ${game.jugadores.map(_.user.name).mkString(",")}" // TODO i8n
                            ),
                            <.td(
                              Button()
                                .compact(true)
                                .basic(true)
                                .primary(true)
                                .onClick((_, _) => {
                                  calibanCallThroughJsonOpt[Mutations, Game](
                                    Mutations.acceptGameInvitation(
                                      game.id.fold(0)(_.value)
                                    ),
                                    gameOpt => p.gameInProgress.setState(gameOpt)
                                  )
                                })("Aceptar"), // TODO i8n
                              Button()
                                .compact(true)
                                .basic(true)
                                .onClick((_, _) => {
                                  calibanCall[Mutations, Option[Boolean]](
                                    Mutations.declineGameInvitation(
                                      game.id.fold(0)(_.value)
                                    ),
                                    _ =>
                                      Toast.success("Invitación rechazada") >> // TODO i8n
                                        p.gameInProgress.setState(None) >> refresh()
                                  )
                                })("Rechazar") // TODO i8n
                            )
                          )
                        }
                      )
                    )
                  )
                ).when(s.invites.nonEmpty)
              ),
              <.div(
                ^.className := "lobbyCol2",
                if (chutiState.isFirstLogin) renderFirstLoginWelcome(chutiState)
                else renderWelcome(chutiState),
                <.div(
                  ^.className := "users",
                  renderStartWithBotsDialog,
                  renderNewGameDialog,
                  renderInviteExternalDialog,
                  <.div(
                    ^.key := "jugadores",
                    <.h1("Otros usuarios (en linea o amigos)"), // TODO i8n
                    <.table(
                      ^.className := "playersTable",
                      <.tbody(
                        chutiState.usersAndFriends.filter(_.user.id != user.id).toVdomArray { player =>
                          TableRow(
//                              key(player.user.id.fold("")(_.toString)
                          )(
                            TableCell()
                              .width(SemanticWIDTHS.`1`)(
                                if (player.isFriend)
                                  Popup()
                                    .content("Amigo") // TODO i8n
                                    .trigger(
                                      Icon()
                                        .className("icon")
                                        .name(SemanticICONS.star)
                                        .color(SemanticCOLORS.yellow)
                                        .circular(true)
                                        .fitted(true)()
                                    )()
                                else
                                  EmptyVdom,
                                if (player.isLoggedIn)
                                  Popup()
                                    .content("En linea") // TODO i8n
                                    .trigger(
                                      Icon()
                                        .className("icon")
                                        .name(SemanticICONS.`user outline`)
                                        .circular(true)
                                        .fitted(true)()
                                    )()
                                else
                                  EmptyVdom
                              ),
                            TableCell()
                              .width(SemanticWIDTHS.`1`)
                              .align(center)(
                                Dropdown()
                                  .className("menuBurger")
                                  .trigger(
                                    Icon()
                                      .name(SemanticICONS.`ellipsis vertical`)
                                      .fitted(true)()
                                  )(
                                    DropdownMenu()(
                                      (for {
                                        game     <- p.gameInProgress.value
                                        _        <- user.id
                                        playerId <- player.user.id
                                        gameId   <- game.id
                                      } yield
                                        if (
                                          game.gameStatus == GameStatus.esperandoJugadoresInvitados &&
                                          game.jugadores.head.id == user.id &&
                                          !game.jugadores.exists(_.id == player.user.id)
                                        )
                                          DropdownItem()
                                            .onClick { (_, _) =>
                                              calibanCall[Mutations, Option[Boolean]](
                                                Mutations
                                                  .inviteToGame(playerId.value, gameId.value),
                                                res =>
                                                  if (res.getOrElse(false))
                                                    Toast.success("Jugador Invitado!") // TODO i8n
                                                  else Toast.error("Error invitando jugador!") // TODO i8n
                                              )
                                            }("Invitar a jugar"): VdomNode // TODO i8n
                                        else
                                          EmptyVdom).getOrElse(EmptyVdom),
                                      if (player.isFriend)
                                        DropdownItem()
                                          .onClick { (_, _) =>
                                            calibanCall[Mutations, Option[Boolean]](
                                              Mutations.unfriend(player.user.id.get.value),
                                              res =>
                                                if (res.getOrElse(false))
                                                  refresh() >> Toast.success(
                                                    s"Cortalas, ${player.user.name} ya no es tu amigo!" // TODO i8n
                                                  )
                                                else
                                                  Toast.error("Error haciendo amigos!") // TODO i8n
                                            )
                                          }("Ya no quiero ser tu amigo") // TODO i8n
                                      else
                                        DropdownItem()
                                          .onClick { (_, _) =>
                                            calibanCall[Mutations, Option[Boolean]](
                                              Mutations.friend(player.user.id.get.value),
                                              res =>
                                                if (res.getOrElse(false))
                                                  chutiState
                                                    .onRequestGameRefresh() >> refresh() >> Toast
                                                    .success("Un nuevo amiguito!") // TODO i8n
                                                else
                                                  Toast.error("Error haciendo amigos!") // TODO i8n
                                            )
                                          }("Agregar como amigo") // TODO i8n
                                    )
                                  )
                              ),
                            TableCell()
                              .width(SemanticWIDTHS.`14`)(player.user.name)
                          )
                        }
                      )
                    )
                  ).when(chutiState.usersAndFriends.exists(_.user.id != user.id))
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

  private val component = ScalaComponent
    .builder[Props]
    .initialState(State())
    .renderBackend[Backend]
    .componentDidMount(_.backend.init())
    .build

  def apply(
    gameInProgress: StateSnapshot[Option[Game]],
    mode:           StateSnapshot[GameViewMode]
  ): Unmounted[Props, State, Backend] = component(Props(gameInProgress, mode))

}
