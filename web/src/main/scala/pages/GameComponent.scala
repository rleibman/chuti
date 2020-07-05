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

import app.GameViewMode.GameViewMode
import app.{ChutiState, GameViewMode}
import chat._
import chuti.CuantasCantas.{Canto5, CuantasCantas}
import chuti.JugadorState.JugadorState
import chuti.Triunfo.{SinTriunfos, TriunfoNumero}
import chuti._
import components.{Confirm, Toast}
import game.GameClient.Mutations
import io.circe.generic.auto._
import io.circe.syntax._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.StateSnapshot
import japgolly.scalajs.react.vdom.html_<^._
import org.scalablytyped.runtime.StringDictionary
import pages.LobbyComponent.calibanCall
import typings.semanticUiReact.components._
import typings.semanticUiReact.dropdownItemMod.DropdownItemProps
import typings.semanticUiReact.imageImageMod.ImageProps

import scala.scalajs.js.JSConverters._

object GameComponent {
  case class Props(
    gameInProgress: StateSnapshot[Option[Game]],
    gameViewMode:   StateSnapshot[GameViewMode]
  )

  case class State(
    cuantasCantas:     Option[CuantasCantas] = None,
    triunfo:           Option[Triunfo] = None,
    estrictaDerecha:   Boolean = false,
    fichaSeleccionada: Option[Ficha] = None,
    privateMessage:    Option[ChatMessage] = None
  )

  class Backend($ : BackendScope[Props, State]) {
    def refresh(): Callback = {
      Callback.empty
    }

    def init(): Callback = {
      Callback.log(s"Initializing GameComponent") >>
        refresh()
    }

    def clearPlayState(): Callback =
      $.modState(
        _.copy(
          cuantasCantas = None,
          triunfo = None,
          estrictaDerecha = false,
          fichaSeleccionada = None
        )
      )

    def play(
      gameId: GameId,
      event:  PlayEvent
    ): Callback = {
      calibanCall[Mutations, Option[Boolean]](
        Mutations.play(gameId.value, event.asJson),
        _ => clearPlayState() >> Toast.success("Listo!")
      )
    }

    def render(
      p: Props,
      s: State
    ): VdomNode = {

      ChutiState.ctx.consume { chutiState =>
        VdomArray(
          <.div(
            ^.className := "gameTable",
            p.gameInProgress.value.fold(EmptyVdom) { game =>
              game.jugadores.zipWithIndex.toVdomArray {
                case (jugador, playerIndex) =>
                  val jugadorState: JugadorState = game.jugadorState(jugador)
                  val puedeRendirse = jugador.cantante &&
                    game.jugadores.flatMap(_.filas).size <= 1 &&
                    (jugadorState == JugadorState.esperando ||
                      jugadorState == JugadorState.pidiendoInicial ||
                      jugadorState == JugadorState.pidiendo ||
                      jugadorState == JugadorState.dando)
                  println(s"jugadorState = $jugadorState, puedeRendirse = $puedeRendirse")
                  val isSelf = chutiState.user.fold(false)(_.id == jugador.id)
                  val canPlay = jugadorState != JugadorState.esperando && jugadorState != JugadorState.esperandoCanto

                  val playerPosition =
                    if (isSelf)
                      0
                    else if (chutiState.user.fold(false)(game.nextPlayer(_).id == jugador.id))
                      1
                    else if (chutiState.user.fold(false)(game.prevPlayer(_).id == jugador.id))
                      3
                    else
                      2

                  <.div(
                    ^.key       := s"playerContainer$playerPosition",
                    ^.className := s"jugador$playerPosition ${if (isSelf) " self" else ""}",
                    <.div(
                      ^.className := s"statusBar$playerPosition",
                      <.div(
                        ^.className := s"playerName ${if (canPlay) "canPlay"
                        else ""}",
                        jugador.user.name
                      ),
                      <.div(
                        ^.className := "userStatus",
                        if (jugador.turno) "Le toco cantar. " else "",
                        jugador.cuantasCantas
                          .fold("")(c => s"Canto $c"),
                        jugador.statusString
                      )
                    ),
                    if (isSelf) {
                      <.div(
                        ^.className := "jugadorActionBar",
                        jugadorState match {
                          case JugadorState.cantando =>
                            val defaultCuantas =
                              if (jugador.cantante) CuantasCantas.Casa else CuantasCantas.Buenas
                            val cantanteActual =
                              game.jugadores.find(_.cantante).getOrElse(jugador)
                            val min = cantanteActual.cuantasCantas
                              .map(c => CuantasCantas.byPriority(c.prioridad + 1)).getOrElse(
                                Canto5
                              )
                            val cantasOptions = (defaultCuantas +: CuantasCantas
                              .posibilidades(min)).map { cuantas =>
                              DropdownItemProps(
                                StringDictionary = StringDictionary("key" -> cuantas.prioridad),
                                value = cuantas.prioridad,
                                text = cuantas.toString
                              )
                            }.toJSArray

                            <.div(
                              Dropdown(
                                className = "cantaDropdown",
                                compact = true,
                                fluid = false,
                                placeholder = "Cuantas Cantas?",
                                selection = true,
                                value = s.cuantasCantas.getOrElse(defaultCuantas).prioridad.toDouble,
                                options = cantasOptions,
                                onChange = { (_, dropDownProps) =>
                                  val value = dropDownProps.value.asInstanceOf[Double].toInt
                                  $.modState(
                                    _.copy(cuantasCantas = Option(CuantasCantas.byPriority(value)))
                                  )
                                }
                              )(),
                              Button(
                                compact = true,
                                basic = true,
                                onClick = { (_, _) =>
                                  play(
                                    game.id.get,
                                    Canta(s.cuantasCantas.getOrElse(defaultCuantas))
                                  )
                                }
                              )("Canta")
                            )
                          case JugadorState.dando =>
                            Button(
                              compact = true,
                              basic = true,
                              disabled = s.fichaSeleccionada.isEmpty,
                              onClick = { (_, _) =>
                                play(game.id.get, Da(ficha = s.fichaSeleccionada.get))
                              }
                            )("Dá")
                          case p @ (JugadorState.pidiendo | JugadorState.pidiendoInicial) =>
                            <.span(
                              if (p == JugadorState.pidiendoInicial) {
                                <.span(
                                  Label()("Triunfan"),
                                  s.triunfo match {
                                    case Some(TriunfoNumero(num)) =>
                                      <.img(^.src := s"images/${num.value}.svg", ^.height := 28.px)
                                    case Some(SinTriunfos) => <.span("Sin Triunfos")
                                    case _                 => EmptyVdom
                                  },
                                  Dropdown(
                                    labeled = true,
                                    placeholder = "Triunfo",
                                    value = s.triunfo.fold("")(_.toString),
                                    onChange = { (_, dropDownProps) =>
                                      val value = dropDownProps.value.asInstanceOf[String]
                                      $.modState(_.copy(triunfo = Option(Triunfo(value))))
                                    },
                                    options = Triunfo.posibilidades
                                      .map(triunfo =>
                                        DropdownItemProps(
                                          StringDictionary =
                                            StringDictionary("key" -> triunfo.toString),
                                          image = triunfo match {
                                            case SinTriunfos => null
                                            case TriunfoNumero(num) =>
                                              ImageProps(StringDictionary = StringDictionary(
                                                "src" -> s"images/${num.value}.svg"
                                              )
                                              )
                                          },
                                          value = triunfo.toString,
                                          text = triunfo.toString
                                        )
                                      ).toJSArray
                                  )()
                                )
                              } else {
                                EmptyVdom
                              },
                              <.span(
                                Checkbox(
                                  toggle = true,
                                  label = "Estricta Derecha",
                                  checked = s.estrictaDerecha,
                                  onChange = { (_, checkBoxProps) =>
                                    $.modState(
                                      _.copy(estrictaDerecha =
                                        checkBoxProps.checked.getOrElse(false)
                                      )
                                    )
                                  }
                                )(),
                                Button(
                                  compact = true,
                                  basic = true,
                                  disabled = s.fichaSeleccionada.isEmpty ||
                                    (s.triunfo.isEmpty && (jugador.cantante && jugador.mano && jugador.filas.isEmpty)),
                                  onClick = { (_, _) =>
                                    play(
                                      game.id.get,
                                      Pide(
                                        ficha = s.fichaSeleccionada.get,
                                        triunfo = s.triunfo,
                                        estrictaDerecha = s.estrictaDerecha
                                      )
                                    )
                                  }
                                )("Pide"),
                                if (p == JugadorState.pidiendo && game.puedesCaerte(jugador)) {
                                  Button(compact = true, basic = true, onClick = { (_, _) =>
                                    play(game.id.get, Caete())
                                  })("Cáete")
                                } else {
                                  EmptyVdom
                                }
                              )
                            )
                          case JugadorState.haciendoSopa =>
                            Button(compact = true, basic = true, onClick = { (_, _) =>
                              play(game.id.get, Sopa())
                            })("Sopa")
                          case JugadorState.esperandoCanto   => EmptyVdom
                          case JugadorState.esperando        => EmptyVdom
                          case JugadorState.partidoTerminado => EmptyVdom
                        },
                        if (puedeRendirse) {
                          Button(
                            compact = true,
                            basic = true,
                            onClick = { (_, _) =>
                              Confirm.confirm(
                                question = "Estas seguro que te quieres rendir?",
                                onConfirm = play(game.id.get, MeRindo())
                              )
                            }
                          )("Me Rindo")
                        } else {
                          EmptyVdom
                        }
                      )
                    } else {
                      EmptyVdom
                    },
                    <.div(
                      ^.className := s"fichas$playerPosition",
                      jugador.fichas.zipWithIndex.toVdomArray {
                        case (FichaTapada, fichaIndex) =>
                          <.div(
                            ^.className := s"domino${playerPosition}Container",
                            <.img(
                              ^.key       := s"ficha_${playerIndex}_$fichaIndex",
                              ^.src       := s"images/backx150.png",
                              ^.className := s"domino$playerPosition"
                            )
                          )
                        case (ficha @ FichaConocida(arriba, abajo), fichaIndex) =>
                          val selectable = canPlay
                          <.div(
                            ^.className := s"domino${playerPosition}Container",
                            <.img(
                              if (selectable) ^.cursor.pointer else ^.cursor.auto,
                              ^.key := s"ficha_${playerIndex}_$fichaIndex",
//                            ^.transform := "rotate(180deg)", //TODO allow user to flip the domino
                              ^.src := s"images/${abajo}_${arriba}x150.png",
                              ^.onClick --> {
                                if (selectable)
                                  $.modState(_.copy(fichaSeleccionada = Option(ficha)))
                                else
                                  Callback.empty
                              },
                              ^.className := s"domino$playerPosition ${if (s.fichaSeleccionada.fold(false)(_ == ficha)) "selected"
                              else ""}"
                            )
                          )
                      }
                    ),
                    <.div(
                      ^.className := s"filas$playerPosition",
                      jugador.filas.zipWithIndex.toVdomArray {
                        case (fila, filaIndex) =>
                          <.div(
                            ^.key       := s"fila_${playerIndex}_$filaIndex",
                            ^.className := s"filaFichas$playerPosition",
                            fila.fichas.zipWithIndex.toVdomArray {
                              case (ficha, fichaIndex) =>
                                val abierta = fila.index <= 0
                                if (abierta) {
                                  <.div(
                                    ^.className := s"dominoJugado${playerPosition}Container",
                                    <.img(
                                      ^.key       := s"fila_ficha_${playerIndex}_${filaIndex}_$fichaIndex",
                                      ^.src       := s"images/${ficha.abajo}_${ficha.arriba}x75.png",
                                      ^.className := s"dominoJugado$playerPosition"
                                    )
                                  )
                                } else {
                                  <.div(
                                    ^.className := s"dominoJugadoContainer$playerPosition",
                                    <.img(
                                      ^.key       := s"fila_ficha_${playerIndex}_${filaIndex}_$fichaIndex",
                                      ^.src       := s"images/backx75.png",
                                      ^.className := s"dominoJugado$playerPosition"
                                    )
                                  )
                                }
                            }
                          )
                      }
                    )
                  )
              }
            },
            p.gameInProgress.value.toVdomArray(game =>
              if (game.gameStatus == GameStatus.partidoTerminado) {
                <.div(
                  ^.className := "fichasEnJuego",
                  <.div(
                    ^.className := "juegoStatus",
                    <.div(^.className := "triunfan"),
                    <.div(^.className := "juegoStatusString", game.statusString)
                  ),
                  <.div(^.className := "fichasEnJuegoName"),
                  <.div(
                    ^.className := "dominoEnJuego",
                    Button(basic = true, onClick = { (_, _) =>
                      chutiState
                        .onGameViewModeChanged(GameViewMode.lobby)
                    })("Regresa al Lobby")
                  )
                )
              } else {
                <.div(
                  ^.className := "fichasEnJuego",
                  <.div(
                    ^.className := "juegoStatus",
                    game.triunfo match {
                      case Some(SinTriunfos) => <.div(^.className := "triunfan", "Sin Triunfos")
                      case Some(TriunfoNumero(num)) =>
                        <.div(
                          ^.className := "triunfan",
                          "Triunfan",
                          <.img(^.src := s"images/${num.value}.svg", ^.height := 28.px)
                        )
                      case None => <.div()
                    },
                    <.div(^.className := "juegoStatusString", game.statusString)
                  ),
                  game.enJuego.toVdomArray {
                    case (user, ficha) =>
                      VdomArray(
                        <.div(
                          ^.className := "fichasEnJuegoName",
                          game.jugador(Option(user)).user.name
                        ),
                        <.img(
                          ^.src       := s"images/${ficha.abajo}_${ficha.arriba}x150.png",
                          ^.className := "dominoEnJuego"
                        )
                      )
                  }
                )
              }
            )
          ),
          chutiState.user.fold(EmptyVdom)(user =>
            ChatComponent(
              user,
              ChannelId(p.gameInProgress.value.flatMap(_.id).fold(0)(_.value)),
              onPrivateMessage = Option(msg =>
                $.modState(_.copy(privateMessage = Option(msg))) >> Toast.info(
                  <.div(s"Tienes un nuevo mensaje!", <.br(), msg.msg)
                ) >> chutiState.onRequestGameRefresh >> refresh()
              )
            )
          )
        )
      }
    }
  }

  private val component = ScalaComponent
    .builder[Props]
    .initialStateFromProps(_ => State())
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.init())
    .build

  def apply(
    gameInProgress: StateSnapshot[Option[Game]],
    mode:           StateSnapshot[GameViewMode]
  ): Unmounted[Props, State, Backend] =
    component(Props(gameInProgress, mode))
}
