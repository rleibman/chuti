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
import chat._
import chuti.CuantasCantas.Canto5
import chuti.Triunfo.{SinTriunfos, TriunfoNumero}
import chuti._
import components.Toast
import game.GameClient.Mutations
import io.circe.generic.auto._
import io.circe.syntax._
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.StateSnapshot
import japgolly.scalajs.react.vdom.html_<^._
import org.scalablytyped.runtime.StringDictionary
import pages.GamePage.Mode.Mode
import pages.LobbyComponent.calibanCall
import typings.semanticUiReact.components._
import typings.semanticUiReact.dropdownItemMod.DropdownItemProps
import typings.semanticUiReact.imageImageMod.ImageProps

import scala.scalajs.js
import scala.scalajs.js.JSConverters._

object GameComponent {
  case class Props(
    chutiState:           ChutiState,
    gameInProgress:       StateSnapshot[Option[Game]],
    onRequestGameRefresh: Callback,
    mode:                 StateSnapshot[Mode]
  )

  object Dialog extends Enumeration {
    type Dialog = Value
    val cuentas, none = Value
  }
  import Dialog._

  case class State(
    currentPlayEvent: Option[PlayEvent] = None,
    privateMessage:   Option[ChatMessage] = None,
    dlg:              Dialog = Dialog.none
  ) {
    def currentEvent[Event <: PlayEvent] = currentPlayEvent.asInstanceOf[Option[Event]]
  }

  class Backend($ : BackendScope[Props, State]) {
    def refresh(): Callback = {
      Callback.empty
    }

    def init(): Callback = {
      Callback.log(s"Initializing") >>
        refresh()
    }

    def modEvent[Event <: PlayEvent](fn: Option[Event] => Event): Callback = $.modState { s =>
      s.copy(currentPlayEvent = Option(fn(s.currentEvent[Event]))) //Hiding the ugliness of the cast
    }

    def play(
      gameId:   GameId,
      eventOpt: Option[PlayEvent]
    ): Callback = {
      eventOpt.fold(Callback.empty) { event =>
        calibanCall[Mutations, Option[Boolean]](
          Mutations.play(gameId.value, event.asJson),
          _ => Toast.success("Listo!")
        )
      }
    }

    def render(
      p: Props,
      s: State
    ): VdomNode = {
      def renderCuentasDialog: VdomArray = {
        p.gameInProgress.value.toVdomArray { game =>
          Modal(key = "cuentasDialog", open = s.dlg == Dialog.cuentas)(
            ModalHeader()("Cuentas"),
            ModalContent()(
              Table()(
                TableHeader()(
                  TableRow(key = "cuentasHeader")(
                    TableHeaderCell()("Jugador"),
                    TableHeaderCell()("Cuentas"),
                    TableHeaderCell()("Total"),
                    TableHeaderCell()("Satoshi")
                  )
                ),
                game.jugadores.zipWithIndex.toVdomArray {
                  case (jugador, jugadorIndex) =>
                    TableRow(key = s"cuenta$jugadorIndex")(
                      TableCell()(jugador.user.name),
                      TableCell()(
                        jugador.cuenta.zipWithIndex.toVdomArray {
                          case (cuenta, cuentaIndex) =>
                            <.span(
                              ^.key       := s"cuenta_num${jugadorIndex}_$cuentaIndex",
                              ^.className := (if (cuenta.esHoyo) "hoyo" else ""),
                              s"${if (cuenta.puntos > 0) "+" else ""} cuenta.puntos"
                            )
                        }
                      ),
                      TableCell()(jugador.cuenta.map(_.puntos).sum),
                      TableCell()(0)
                    )
                }
              )
            ),
            ModalActions()(Button(compact = true, onClick = { (_, _) =>
              $.modState(_.copy(dlg = Dialog.none))
            })("Ok"))
          )
        }
      }

      <.div(
        renderCuentasDialog,
        Button(compact = true, onClick = { (_, _) =>
          p.mode.setState(GamePage.Mode.lobby)
        })("Regresa al lobby"),
        Button(compact = true, onClick = { (_, _) =>
          $.modState(_.copy(dlg = Dialog.cuentas))
        })("Cuentas"),
        p.gameInProgress.value.fold(EmptyVdom) { game =>
          game.jugadores.zipWithIndex.toVdomArray {
            case (jugador, playerIndex) =>
              val actionExpected = game.gameStatus match {
                case GameStatus.cantando =>
                  jugador.mano
                case GameStatus.jugando =>
                  jugador.mano || (game.enJuego.nonEmpty && !game.estrictaDerecha) /*|| estricta derecha y es tu turno */
              }
              val isSelf = p.chutiState.user.fold(false)(_.id == jugador.id)
              Container(
                key = s"playerContainer$playerIndex",
                className =
                  s"jugador$playerIndex ${if (isSelf) " self" else ""}${if (actionExpected) " mano"
                  else ""}"
              )(
                Header(className = "playerName")(jugador.user.name),
                if (isSelf) {
                  <.div(
                    Container(className = "actionBar")(
                      game.gameStatus match {
                        case GameStatus.cantando =>
                          if (jugador.mano && jugador.cuantasCantas.isEmpty) {
                            val cantanteActual =
                              game.jugadores.find(_.cantante).getOrElse(jugador)
                            val min = cantanteActual.cuantasCantas
                              .map(c => CuantasCantas.byPriority(c.prioridad + 1)).getOrElse(
                                Canto5
                              )
                            val cantasOptions = ((if (jugador.turno) CuantasCantas.Casa
                                                  else CuantasCantas.Buenas) +: CuantasCantas
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
                                value = s
                                  .currentEvent[Canta].map(_.cuantasCantas.prioridad).getOrElse(
                                    0
                                  ).toDouble,
                                options = cantasOptions,
                                onChange = { (_, dropDownProps) =>
                                  val value = dropDownProps.value.asInstanceOf[Double].toInt
                                  modEvent[Canta](_ =>
                                    Canta(cuantasCantas = CuantasCantas.byPriority(value))
                                  )
                                }
                              )(),
                              Button(compact = true, onClick = { (_, _) =>
                                play(game.id.get, s.currentEvent[Canta])
                              })("Canta")
                            )
                          } else {
                            EmptyVdom
                          }
                        case GameStatus.jugando =>
                          <.div(
                            "Triunfan",
                            if (jugador.cantante && jugador.mano && jugador.filas.isEmpty) {
                              Dropdown(
                                placeholder = "Triunfo",
                                icon =
                                  s.currentEvent[Pide].fold(EmptyVdom.asInstanceOf[js.Any]) {
                                    e =>
                                      e.triunfo match {
                                        case Some(TriunfoNumero(num)) =>
                                          <.img(^.src := s"images/${num.value}.svg")
                                            .asInstanceOf[js.Any]
                                        case _ => EmptyVdom.asInstanceOf[js.Any]
                                      }
                                  },
                                value = s
                                  .currentEvent[Pide].fold(null: String)(_.triunfo.toString),
                                options = Triunfo.posibilidades
                                  .map(triunfo =>
                                    DropdownItemProps(
                                      StringDictionary = StringDictionary("key" -> triunfo.toString),
                                      image = triunfo match {
                                        case SinTriunfos => null
                                        case TriunfoNumero(num) =>
                                          ImageProps(StringDictionary =
                                            StringDictionary("src" -> s"images/${num.value}.svg")
                                          )
                                      },
                                      value = triunfo.toString,
                                      text = triunfo.toString
                                    )
                                  ).toJSArray
                              )()
                            } else {
                              EmptyVdom
                            },
                            if (jugador.mano) {
                              <.span(
                                Checkbox(
                                  toggle = true,
                                  label = "Estricta Derecha",
                                  checked =
                                    s.currentEvent[Pide].fold(false)(_.estrictaDerecha)
                                )(),
                                Button(compact = true)("Pide")
                              )
                            } else {
                              EmptyVdom
                            }
                          )
                        case _ => EmptyVdom
                      }
                    )
                  )
                } else if (actionExpected) {
                  EmptyVdom //Segment()(Loader(active = true, indeterminate = true)("Esperando"))
                } else {
                  EmptyVdom
                },
                Container(className = "statusBar")(
                  if (jugador.turno) "Le toco cantar, " else "",
                  jugador.cuantasCantas.fold("")(c => s"canto $c")
                ),
                <.div(
                  ^.className := "fichas",
                  jugador.fichas.zipWithIndex.toVdomArray {
                    case (FichaTapada, fichaIndex) =>
                      <.img(
                        ^.key       := s"ficha_${playerIndex}_$fichaIndex",
                        ^.src       := s"images/blank_uia.svg",
                        ^.className := "domino"
                      )
                    case (FichaConocida(arriba, abajo), fichaIndex) =>
                      val selectable = true
                      <.img(
                        if (selectable) ^.cursor.pointer else ^.cursor.auto,
                        ^.key := s"ficha_${playerIndex}_$fichaIndex",
//                        ^.transform := "rotate(45deg)", //TODO allow user to flip the domino
                        ^.src       := s"images/${abajo}_$arriba.svg",
                        ^.className := "domino"
                      )
                  }
                ),
                <.div(
                  ^.className := "filas",
                  jugador.filas.zipWithIndex.toVdomArray {
                    case (fila, filaIndex) =>
                      <.div(
                        ^.key       := s"fila_${playerIndex}_$filaIndex",
                        ^.className := "fila",
                        fila.fichas.zipWithIndex.toVdomArray {
                          case (ficha, fichaIndex) =>
                            if (fila.abierta) {
                              <.img(
                                ^.key       := s"fila_ficha_${playerIndex}_${filaIndex}_$fichaIndex",
                                ^.src       := s"images/${ficha.abajo}_${ficha.arriba}.svg",
                                ^.className := "domino_jugado"
                              )
                            } else {
                              <.img(
                                ^.key       := s"fila_ficha_${playerIndex}_${filaIndex}_$fichaIndex",
                                ^.src       := s"images/blank_uia.svg",
                                ^.className := "domino_jugado"
                              )
                            }
                        }
                      )
                  }
                )
              )
          }
        },
        Container(className = "fichasEnJuego")(
          p.gameInProgress.value.toVdomArray(game =>
            game.enJuego.zipWithIndex.toVdomArray {
              case ((user, ficha), fichaIndex) =>
                <.div(
                  ^.key       := s"enJuego$fichaIndex",
                  ^.className := "fichaEnJuego",
                  game.jugador(Option(user)).user.name,
                  <.img(
                    ^.src       := s"images/${ficha.abajo}_${ficha.arriba}.svg",
                    ^.className := "domino_enjuego"
                  )
                )
            }
          )
        ),
        p.chutiState.user.fold(EmptyVdom)(user =>
          ChatComponent(
            user,
            ChannelId(p.gameInProgress.value.flatMap(_.id).fold(0)(_.value)),
            onPrivateMessage = Option(msg =>
              $.modState(_.copy(privateMessage = Option(msg))) >> Toast.info(
                <.div(s"Tienes un nuevo mensaje!", <.br(), msg.msg)
              ) >> p.onRequestGameRefresh >> refresh()
            )
          )
        )
      )
    }
  }

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
