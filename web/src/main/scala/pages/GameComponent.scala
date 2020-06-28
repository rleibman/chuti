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

  object JugadorState extends Enumeration {
    type JugadorState = Value
    val dando, cantando, pidiendoInicial, pidiendo, esperando, haciendoSopa = Value
  }
  import JugadorState._

  object Dialog extends Enumeration {
    type Dialog = Value
    val cuentas, none = Value
  }
  import Dialog._

  case class State(
    cuantasCantas:     Option[CuantasCantas] = None,
    triunfo:           Option[Triunfo] = None,
    estrictaDerecha:   Boolean = false,
    fichaSeleccionada: Option[Ficha] = None,
    privateMessage:    Option[ChatMessage] = None,
    dlg:               Dialog = Dialog.none
  ) {}

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
                              s"${if (cuenta.puntos > 0) "+" else ""} ${cuenta.puntos}"
                            )
                        }
                      ),
                      TableCell()(jugador.cuenta.map(_.puntos).sum),
                      TableCell()(0)
                    )
                }
              )
            ),
            ModalActions()(Button(compact = true, basic = true, onClick = { (_, _) =>
              $.modState(_.copy(dlg = Dialog.none))
            })("Ok"))
          )
        }
      }

      ChutiState.ctx.consume { chutiState =>
        <.div(
          renderCuentasDialog,
          Container(className = "navBar")(
            //TODO mueve cuentas al menu
            Button(compact = true, basic = true, onClick = { (_, _) =>
              $.modState(_.copy(dlg = Dialog.cuentas))
            })("Cuentas")
          ),
          p.gameInProgress.value.fold(EmptyVdom) { game =>
            game.jugadores.zipWithIndex.toVdomArray {
              case (jugador, playerIndex) =>
                val jugadorState: JugadorState = {
                  game.gameStatus match {
                    case GameStatus.cantando =>
                      if (jugador.mano)
                        JugadorState.cantando
                      else
                        JugadorState.esperando
                    case GameStatus.jugando =>
                      if (jugador.cantante && jugador.filas.isEmpty && game.enJuego.isEmpty)
                        JugadorState.pidiendoInicial
                      else if (jugador.mano && game.enJuego.isEmpty)
                        JugadorState.pidiendo
                      else if (game.enJuego.nonEmpty && !game.enJuego
                                 .exists(_._1 == jugador.id.get)) //TODO estricta derecha
                        JugadorState.dando
                      else
                        JugadorState.esperando
                    case GameStatus.requiereSopa =>
                      if (jugador.turno)
                        JugadorState.haciendoSopa
                      else
                        JugadorState.esperando
                  }
                }
                val isSelf = chutiState.user.fold(false)(_.id == jugador.id)

                val playerPosition =
                  if (isSelf)
                    0
                  else if (chutiState.user.fold(false)(game.nextPlayer(_).id == jugador.id))
                    1
                  else if (chutiState.user.fold(false)(game.prevPlayer(_).id == jugador.id))
                    3
                  else
                    2

                Container(
                  key = s"playerContainer$playerPosition",
                  className = s"jugador$playerPosition ${if (isSelf) " self" else ""}"
                )(
                  Container(className = "statusBarEven")(
                    <.div(
                      ^.className := s"playerName ${if (jugadorState != JugadorState.esperando) "canPlay"
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
                    Container(className = "jugadorActionBar")(
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
                                    _.copy(estrictaDerecha = checkBoxProps.checked.getOrElse(false))
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
                              if (game.jugadores.flatMap(_.filas).size < 2) {
                                Button(compact = true, basic = true, onClick = { (_, _) =>
                                  play(game.id.get, MeRindo())
                                })("Me Rindo")
                              } else {
                                EmptyVdom
                              },
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
                        case JugadorState.esperando => EmptyVdom
                      }
                    )
                  } else {
                    EmptyVdom
                  },
                  <.div(
                    ^.className := "fichas",
                    jugador.fichas.zipWithIndex.toVdomArray {
                      case (FichaTapada, fichaIndex) =>
                        <.img(
                          ^.key       := s"ficha_${playerIndex}_$fichaIndex",
                          ^.src       := s"images/backx150.png",
                          ^.className := "domino"
                        )
                      case (ficha @ FichaConocida(arriba, abajo), fichaIndex) =>
                        val selectable = true
                        <.img(
                          if (selectable) ^.cursor.pointer else ^.cursor.auto,
                          ^.key := s"ficha_${playerIndex}_$fichaIndex",
//                        ^.transform := "rotate(180deg)", //TODO allow user to flip the domino
                          ^.src := s"images/${abajo}_${arriba}x150.png",
                          ^.onClick --> { $.modState(_.copy(fichaSeleccionada = Option(ficha))) },
                          ^.className := s"domino ${if (s.fichaSeleccionada.fold(false)(_ == ficha)) "selected"
                          else ""}"
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
                              val abierta = fila.index <= 0
                              if (abierta) {
                                <.img(
                                  ^.key       := s"fila_ficha_${playerIndex}_${filaIndex}_$fichaIndex",
                                  ^.src       := s"images/${ficha.abajo}_${ficha.arriba}x75.png",
                                  ^.className := "domino_jugado"
                                )
                              } else {
                                <.img(
                                  ^.key       := s"fila_ficha_${playerIndex}_${filaIndex}_$fichaIndex",
                                  ^.src       := s"images/backx75.png",
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
          p.gameInProgress.value.toVdomArray(game =>
            Container(className = "fichasEnJuego")(
              game.triunfo match {
                case Some(SinTriunfos) => "Sin Triunfos"
                case Some(TriunfoNumero(num)) =>
                  <.span("Triunfan", <.img(^.src := s"images/${num.value}.svg", ^.height := 28.px))
                case None => EmptyVdom
              },
              game.statusString,
              game.enJuego.zipWithIndex.toVdomArray {
                case ((user, ficha), fichaIndex) =>
                  <.div(
                    ^.key       := s"enJuego$fichaIndex",
                    ^.className := "fichasEnJuegoInner",
                    <.div(^.className := "fichasEnJuegoName", game.jugador(Option(user)).user.name),
                    <.img(
                      ^.src       := s"images/${ficha.abajo}_${ficha.arriba}x150.png",
                      ^.className := "domino_enjuego"
                    )
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
