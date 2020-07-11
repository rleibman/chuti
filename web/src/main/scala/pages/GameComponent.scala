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
import japgolly.scalajs.react.extra.{ReusabilityOverlay, StateSnapshot}
import japgolly.scalajs.react.vdom.html_<^._
import org.scalablytyped.runtime.StringDictionary
import pages.LobbyComponent.calibanCall
import typings.semanticUiReact.components._
import typings.semanticUiReact.dropdownItemMod.DropdownItemProps
import typings.semanticUiReact.imageImageMod.ImageProps

import scala.scalajs.js.JSConverters._

object GameComponent {
  case class Props(
    gameInProgress: Option[Game],
    gameViewMode:   StateSnapshot[GameViewMode]
  )

  case class State(
    cuantasCantas:     Option[CuantasCantas] = None,
    estrictaDerecha:   Boolean = false,
    fichaSeleccionada: Option[Ficha] = None,
    privateMessage:    Option[ChatMessage] = None
  )

  class Backend($ : BackendScope[Props, State]) {
    def clearPlayState(): Callback =
      $.modState(
        _.copy(
          cuantasCantas = None,
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
        <.div(
          ^.key       := "gameTable",
          ^.className := "gameTable",
          p.gameInProgress.fold(EmptyVdom) { game =>
            game.jugadores.zipWithIndex.toVdomArray {
              case (jugador, playerIndex) =>
                val jugadorState: JugadorState = game.jugadorState(jugador)
                val puedeRendirse = jugador.cantante &&
                  game.jugadores.flatMap(_.filas).size <= 1 &&
                  (jugadorState == JugadorState.esperando ||
                    jugadorState == JugadorState.pidiendoInicial ||
                    jugadorState == JugadorState.pidiendo ||
                    jugadorState == JugadorState.dando)
                val isSelf = chutiState.user.fold(false)(_.id == jugador.id)
                val canPlay = (jugadorState != JugadorState.esperando && jugadorState != JugadorState.esperandoCanto) &&
                  ((!game.estrictaDerecha) || jugador.fichas.size > game
                    .prevPlayer(jugador).fichas.size)

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
                        case jugadorState @ (JugadorState.pidiendo |
                            JugadorState.pidiendoInicial) =>
                          <.span(
                            if (jugadorState == JugadorState.pidiendoInicial) {
                              <.span(
                                Label()("Triunfan"),
                                game.triunfo match {
                                  case Some(TriunfoNumero(num)) =>
                                    <.img(^.src := s"images/${num.value}.svg", ^.height := 28.px)
                                  case Some(SinTriunfos) => <.span("Sin Triunfos")
                                  case _                 => EmptyVdom
                                },
                                Dropdown(
                                  labeled = true,
                                  placeholder = "Triunfo",
                                  value = game.triunfo.fold("")(_.toString),
                                  onChange = { (_, dropDownProps) =>
                                    val value = dropDownProps.value.asInstanceOf[String]
                                    chutiState.modGameInProgress(
                                      _.copy(triunfo = Option(Triunfo(value)))
                                    )
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
                                    _.copy(estrictaDerecha = checkBoxProps.checked.getOrElse(false))
                                  )
                                }
                              )(),
                              Button(
                                compact = true,
                                basic = true,
                                disabled = s.fichaSeleccionada.isEmpty ||
                                  (game.triunfo.isEmpty && (jugador.cantante && jugador.mano && jugador.filas.isEmpty)),
                                onClick = { (_, _) =>
                                  play(
                                    game.id.get,
                                    Pide(
                                      ficha = s.fichaSeleccionada.get,
                                      triunfo = game.triunfo,
                                      estrictaDerecha = s.estrictaDerecha
                                    )
                                  )
                                }
                              )("Pide"),
                              if (game.triunfo.nonEmpty && game.puedesCaerte(jugador)) {
                                Button(compact = true, basic = true, onClick = { (_, _) =>
                                  play(game.id.get, Caete(triunfo = game.triunfo))
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
                          ^.key       := s"ficha_${playerIndex}_$fichaIndex",
                          ^.className := s"domino${playerPosition}Container",
                          <.img(
                            ^.src       := s"images/backx150.png",
                            ^.className := s"domino$playerPosition"
                          )
                        )
                      case (ficha @ FichaConocida(arriba, abajo), fichaIndex) =>
                        val selectable = canPlay
                        <.div(
                          ^.key       := s"ficha_${playerIndex}_$fichaIndex",
                          ^.className := s"domino${playerPosition}Container",
                          <.img(
                            if (selectable) ^.cursor.pointer else ^.cursor.auto,
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
                              val abierta = fila.fichas.size < 4 || fila.index == 0 || (fila.index == (game.jugadores
                                .flatMap(_.filas).size - 1) && game.enJuego.isEmpty)
                              if (abierta) {
                                <.div(
                                  ^.key       := s"fila_ficha_${playerIndex}_${filaIndex}_$fichaIndex",
                                  ^.className := s"dominoJugado${playerPosition}Container",
                                  <.img(
                                    ^.src       := s"images/${ficha.abajo}_${ficha.arriba}x75.png",
                                    ^.className := s"dominoJugado$playerPosition"
                                  )
                                )
                              } else {
                                <.div(
                                  ^.key       := s"fila_ficha_${playerIndex}_${filaIndex}_$fichaIndex",
                                  ^.className := s"dominoJugado${playerPosition}Container",
                                  <.img(
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
          p.gameInProgress.toVdomArray(game =>
            if (game.gameStatus == GameStatus.partidoTerminado) {
              <.div(
                ^.key       := "fichasEnJuego",
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
                        ^.key       := "fichasEnJuegoName",
                        ^.className := "fichasEnJuegoName",
                        game.jugador(Option(user)).user.name
                      ),
                      <.img(
                        ^.key       := "dominoEnJuego",
                        ^.src       := s"images/${ficha.abajo}_${ficha.arriba}x150.png",
                        ^.className := "dominoEnJuego"
                      )
                    )
                }
              )
            }
          )
        )
      }
    }
  }

  implicit val triunfoReuse: Reusability[Triunfo] = Reusability.by(_.toString)
  implicit val gameReuse: Reusability[Game] =
    Reusability.by(game => (game.id.map(_.value), game.currentEventIndex, game.triunfo))
  implicit private val stateReuse: Reusability[State] = Reusability.always
  implicit private val propsReuse: Reusability[Props] = Reusability.derive[Props]

  private val component = ScalaComponent
    .builder[Props]
    .initialStateFromProps(_ => State())
    .renderBackend[Backend]
    .configure(Reusability.shouldComponentUpdateAndLog("GameComponent"))
    .configure(ReusabilityOverlay.install)
    .build

  def apply(
    gameInProgress: Option[Game],
    mode:           StateSnapshot[GameViewMode]
  ): Unmounted[Props, State, Backend] =
    component(Props(gameInProgress, mode))
}
