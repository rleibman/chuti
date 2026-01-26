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

import _root_.util.LocalizedMessages
import app.{ChutiState, GameViewMode}
import chuti.CuantasCantas.{Canto5, CuantasCantas}
import chuti.Triunfo.{SinTriunfos, TriunfoNumero}
import chuti.{*, given}
import components.{Confirm, Toast}
import caliban.client.scalajs.given
import caliban.client.scalajs.GameClient.Mutations
import japgolly.scalajs.react.*
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.StateSnapshot
import japgolly.scalajs.react.vdom.html_<^.{^, *}
import net.leibman.chuti.semanticUiReact.components.*
import net.leibman.chuti.semanticUiReact.distCommonjsElementsImageImageMod.ImageProps
import net.leibman.chuti.semanticUiReact.distCommonjsGenericMod.{
  SemanticCOLORS,
  SemanticICONS,
  SemanticSIZES,
  SemanticShorthandItem
}
import net.leibman.chuti.semanticUiReact.distCommonjsModulesDropdownDropdownItemMod.DropdownItemProps
import pages.LobbyComponent.calibanCall
import zio.json.*
import scala.scalajs.js.JSConverters.*

object GameComponent {

  object GameComponentMessages extends LocalizedMessages {

    override def bundles: Map[String, GameComponentMessages.MessageBundle] =
      Map(
        "es" -> MessageBundle("es", Map("GameComponent.listo" -> "Listo!")),
        "en" -> MessageBundle("en", Map("GameComponent.listo" -> "Ready!"))
      )

  }
  import GameComponentMessages.*

  case class Props(
    gameInProgress: Option[Game],
    gameViewMode:   StateSnapshot[GameViewMode]
  )

  case class State(
    cuantasCantas:     Option[CuantasCantas] = None,
    estrictaDerecha:   Boolean = false,
    fichaSeleccionada: Option[Ficha] = None,
    triunfo:           Option[Triunfo] = None
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
        Mutations.play(gameId.value, event.toJsonAST.toOption.get),
        _ => clearPlayState() >> Toast.success(localized("GameComponent.listo"))
      )
    }

    def moveFichaRight(
      chutiState: ChutiState,
      jugador:    Jugador,
      ficha:      FichaConocida
    ): Callback =
      chutiState.modGameInProgress(
        game => {
          val fichas = game.jugador(jugador.id).fichas
          val index = fichas.indexOf(ficha)
          val newFichas = fichas.updated(index + 1, fichas(index)).updated(index, fichas(index + 1))
          game.copy(jugadores = game.modifiedJugadores(_.id == jugador.id, _.copy(fichas = newFichas)))
        },
        chutiState.playSound("sounds/moveRight.mp3")
      )

    def moveFichaLeft(
      chutiState: ChutiState,
      jugador:    Jugador,
      ficha:      FichaConocida
    ): Callback =
      chutiState.modGameInProgress(
        game => {
          val fichas = game.jugador(jugador.id).fichas
          val index = fichas.indexOf(ficha)
          val newFichas = fichas.updated(index - 1, fichas(index)).updated(index, fichas(index - 1))
          game.copy(jugadores = game.modifiedJugadores(_.id == jugador.id, _.copy(fichas = newFichas)))
        },
        chutiState.playSound("sounds/moveLeft.mp3")
      )

    def render(
      p: Props,
      s: State
    ): VdomNode = {
      ChutiState.ctx.consume { chutiState =>
        <.div(
          ^.key       := "gameTable",
          ^.className := "gameTable",
          p.gameInProgress.fold(EmptyVdom) { game =>
            game.jugadores.zipWithIndex.toVdomArray { case (jugador, playerIndex) =>
              val jugadorState: JugadorState = game.jugadorState(jugador)
              val puedeRendirse = jugador.cantante &&
                game.jugadores.flatMap(_.filas).size <= 1 &&
                (jugadorState == JugadorState.esperando ||
                  jugadorState == JugadorState.pidiendoInicial ||
                  jugadorState == JugadorState.pidiendo ||
                  jugadorState == JugadorState.dando)
              val isSelf = chutiState.user.fold(false)(_.id == jugador.id)
              val canPlay =
                (jugadorState != JugadorState.esperando && jugadorState != JugadorState.esperandoCanto) &&
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
                    ^.className := s"playerName ${
                        if (canPlay) "canPlay"
                        else ""
                      }",
                    jugador.user.name
                  ),
                  <.div(
                    ^.className := "userStatus",
                    if (jugador.turno) "Le toco cantar. " else "", // TODO i8n
                    jugador.cuantasCantas
                      .fold("")(c =>
                        s"Canto ${
                            if (jugador.turno && (c.numFilas == 4 || c.numFilas < 0)) CuantasCantas.Casa // TODO i8n
                            else c
                          }"
                      ),
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
                        val cantasOptions = (defaultCuantas +: CuantasCantas.posibilidades(min)).map { cuantas =>
                          DropdownItemProps()
                            .setText(cuantas.toString)
                            .set("key", cuantas.prioridad.toString)
                            .setValue(cuantas.prioridad.toString)
                        }.toJSArray

                        <.div(
                          Dropdown
                            .className("cantaDropdown")
                            .compact(true)
                            .fluid(false)
                            .placeholder("Cuantas Cantas?") // TODO i8n
                            .selection(true)
                            .value(s.cuantasCantas.getOrElse(defaultCuantas).prioridad.toDouble)
                            .options(cantasOptions)
                            .onChange {
                              (
                                _,
                                dropDownProps
                              ) =>
                                val value = dropDownProps.value.asInstanceOf[Double].toInt
                                $.modState(
                                  _.copy(cuantasCantas = Option(CuantasCantas.byPriority(value)))
                                )
                            }(),
                          Button
                            .compact(true)
                            .basic(true)
                            .onClick {
                              (
                                _,
                                _
                              ) =>
                                play(
                                  game.id.get,
                                  Canta(s.cuantasCantas.getOrElse(defaultCuantas))
                                )
                            }("Canta") // TODO i8n
                        )
                      case JugadorState.dando =>
                        Button
                          .compact(true)
                          .basic(true)
                          .disabled(s.fichaSeleccionada.isEmpty)
                          .onClick {
                            (
                              _,
                              _
                            ) =>
                              play(game.id.get, Da(ficha = s.fichaSeleccionada.get))
                          }("Dá") // TODO i8n
                      case jugadorState @ (JugadorState.pidiendo | JugadorState.pidiendoInicial) =>
                        <.span(
                          if (jugadorState == JugadorState.pidiendoInicial) {
                            <.span(
                              Label()("Triunfan"), // TODO i8n
                              <.div(
                                ^.className := "triunfoNum",
                                s.triunfo match {
                                  case Some(TriunfoNumero(num)) =>
                                    <.img(
                                      ^.verticalAlign := "middle",
                                      ^.src           := s"images/${num.value}.svg",
                                      ^.height        := 28.px
                                    )
                                  case Some(SinTriunfos) => <.span("Sin Triunfos") // TODO i8n
                                  case _                 => EmptyVdom
                                }
                              ),
                              Dropdown
                                .className("triunfoDropdown")
                                .text("")
                                .labeled(true)
                                .placeholder("Triunfo") // TODO i8n
                                .value(s.triunfo.fold("")(_.toString))
                                .onChange {
                                  (
                                    _,
                                    dropDownProps
                                  ) =>
                                    val value = dropDownProps.value.asInstanceOf[String]
                                    $.modState(_.copy(triunfo = Option(Triunfo(value))))
                                }
                                .options(
                                  Triunfo.posibilidades.map { triunfo =>
                                    val props = DropdownItemProps()
                                      .set("key", triunfo.toString)
                                      .setValue(triunfo.toString)
                                      .setText(triunfo.toString)

                                    triunfo match {
                                      case SinTriunfos => props.setImageNull
                                      case TriunfoNumero(num) =>
                                        props.setImage(
                                          ImageProps()
                                            .setHref(s"images/${num.value}.svg")
                                            .asInstanceOf[SemanticShorthandItem[ImageProps]]
                                        )
                                    }

                                  }.toJSArray
                                )()
                            )
                          } else
                            EmptyVdom,
                          <.span(
                            Checkbox
                              .toggle(true)
                              .label("Estricta Derecha") // TODO i8n
                              .checked(s.estrictaDerecha)
                              .onChange {
                                (
                                  _,
                                  checkBoxProps
                                ) =>
                                  $.modState(
                                    _.copy(estrictaDerecha = checkBoxProps.checked.getOrElse(false))
                                  )
                              }(),
                            Button
                              .compact(true)
                              .basic(true)
                              .disabled(
                                s.fichaSeleccionada.isEmpty ||
                                  (s.triunfo.isEmpty && (jugador.cantante && jugador.mano && jugador.filas.isEmpty))
                              )
                              .onClick {
                                (
                                  _,
                                  _
                                ) =>
                                  play(
                                    game.id.get,
                                    Pide(
                                      ficha = s.fichaSeleccionada.get,
                                      triunfo = s.triunfo,
                                      estrictaDerecha = s.estrictaDerecha
                                    )
                                  )
                              }("Pide"), // TODO i8n
                            if (
                              s.triunfo.nonEmpty && game
                                .copy(triunfo = s.triunfo).puedesCaerte(jugador)
                            ) {
                              Button
                                .compact(true)
                                .basic(true)
                                .primary(true)
                                .onClick {
                                  (
                                    _,
                                    _
                                  ) =>
                                    play(game.id.get, Caete(triunfo = s.triunfo))
                                }("Cáete") // TODO i8n
                            } else
                              EmptyVdom
                          )
                        )
                      case JugadorState.haciendoSopa =>
                        Button
                          .compact(true)
                          .basic(true)
                          .onClick {
                            (
                              _,
                              _
                            ) =>
                              play(game.id.get, Sopa())
                          }("Sopa") // TODO i8n
                      case JugadorState.esperandoCanto   => EmptyVdom
                      case JugadorState.esperando        => EmptyVdom
                      case JugadorState.partidoTerminado => EmptyVdom
                      case _                             => throw new RuntimeException("Should never, ever get here")
                    },
                    if (puedeRendirse) {
                      Button
                        .compact(true)
                        .basic(true)
                        .onClick {
                          (
                            _,
                            _
                          ) =>
                            Confirm.confirm(
                              question = "Estas seguro que te quieres rendir?", // TODO i8n
                              onConfirm = play(game.id.get, MeRindo())
                            )
                        }("Me Rindo") // TODO i8n
                    } else
                      EmptyVdom
                  )
                } else
                  EmptyVdom,
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
                          ^.transform := (if (chutiState.isFlipped(ficha)) "rotate(180deg)"
                                          else "none"),
                          ^.src := s"images/${abajo}_${arriba}x150.png",
                          ^.onClick --> {
                            if (selectable) {
                              $.modState(copyMe => {
                                val triunfo =
                                  if (ficha.esMula && copyMe.triunfo.isEmpty)
                                    Option(TriunfoNumero(ficha.arriba))
                                  else copyMe.triunfo
                                copyMe.copy(fichaSeleccionada = Option(ficha), triunfo = triunfo)
                              })
                            } else
                              Callback.empty
                          },
                          ^.className := s"domino$playerPosition ${
                              if (s.fichaSeleccionada.fold(false)(_ == ficha)) "selected"
                              else ""
                            }"
                        ),
                        <.div(
                          ^.className := "domino0FlipAction",
                          ^.onClick --> {
                            chutiState.flipFicha(ficha) >> chutiState.playSound(
                              "sounds/flip.mp3"
                            ) >> Callback.log(s"flip $ficha")
                          },
                          Icon
                            .name(SemanticICONS.`sync alternate`)
                            .fitted(true)
                            .circular(true)
                            .size(SemanticSIZES.small)
                            .color(SemanticCOLORS.blue)
                            .inverted(true)()
                        ),
                        <.div(
                          ^.className := "domino0MoveRightAction",
                          ^.onClick --> {
                            moveFichaRight(chutiState, jugador, ficha) >> Callback.log(
                              s"moveFichaRight $ficha"
                            )
                          },
                          Icon
                            .name(SemanticICONS.`arrow right`)
                            .fitted(true)
                            .circular(true)
                            .size(SemanticSIZES.small)
                            .color(SemanticCOLORS.blue)
                            .inverted(true)()
                        ).when(fichaIndex < jugador.fichas.size - 1),
                        <.div(
                          ^.className := "domino0MoveLeftAction",
                          ^.onClick --> {
                            moveFichaLeft(chutiState, jugador, ficha) >> Callback.log(
                              s"moveFichaLeft $ficha"
                            )
                          },
                          Icon
                            .name(SemanticICONS.`arrow left`)
                            .fitted(true)
                            .circular(true)
                            .size(SemanticSIZES.small)
                            .color(SemanticCOLORS.blue)
                            .inverted(true)()
                        ).when(fichaIndex > 0)
                      )
                  }
                ),
                <.div(
                  ^.className := s"filas$playerPosition",
                  jugador.filas.zipWithIndex.toVdomArray { case (fila, filaIndex) =>
                    val abierta =
                      fila.fichas.size < 4 || fila.index == 0 || (fila.index == (game.jugadores
                        .flatMap(_.filas).size - 1) && game.enJuego.isEmpty)
                    val fichaGanadora =
                      if (!abierta) None
                      else Option(game.fichaGanadora(fila.fichas.head, fila.fichas.tail))
                    <.div(
                      ^.key       := s"fila_${playerIndex}_$filaIndex",
                      ^.className := s"filaFichas$playerPosition",
                      fila.fichas.zipWithIndex.toVdomArray { case (ficha, fichaIndex) =>
                        if (abierta) {
                          <.div(
                            ^.key       := s"fila_ficha_${playerIndex}_${filaIndex}_$fichaIndex",
                            ^.className := s"dominoJugado${playerPosition}Container",
                            <.img(
                              ^.src := s"images/${ficha.abajo}_${ficha.arriba}x75.png",
                              ^.className := s"dominoJugado$playerPosition${
                                  if (fichaGanadora.fold(false)(_ == ficha)) " fichaGanadora"
                                  else ""
                                }"
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
            if (game.gameStatus == GameStatus.partidoTerminado || game.gameStatus == GameStatus.abandonado) {
              <.div(
                ^.key       := "fichasEnJuego",
                ^.className := "fichasEnJuego",
                <.div(
                  ^.className := "juegoStatus",
                  <.div(^.className := "triunfan"),
                  <.div(
                    ^.className := "juegoStatusString",
                    <.p(game.statusString),
                    <.p(chutiState.ultimoBorlote.fold("")(_.toString))
                  )
                ),
                <.div(^.className := "fichasEnJuegoName"),
                <.div(
                  ^.className := "dominoEnJuego",
                  Button
                    .basic(true)
                    .onClick {
                      (
                        _,
                        _
                      ) =>
                        chutiState
                          .onGameViewModeChanged(GameViewMode.lobby)
                    }("Regresa al Lobby") // TODO i8n
                )
              )
            } else {
              <.div(
                ^.className := "fichasEnJuego",
                <.div(
                  ^.className := "juegoStatus",
                  game.triunfo match {
                    case Some(SinTriunfos) => <.div(^.className := "triunfan", "Sin Triunfos") // TODO i8n
                    case Some(TriunfoNumero(num)) =>
                      <.div(
                        ^.className := "triunfan",
                        "Triunfan", // TODO i8n
                        <.img(^.src := s"images/${num.value}.svg", ^.height := 28.px)
                      )
                    case None => <.div()
                  },
                  <.div(^.className := "juegoStatusString", game.statusString)
                ),
                game.enJuego.toVdomArray { case (user, ficha) =>
                  VdomArray(
                    <.div(
                      ^.key       := "fichasEnJuegoName",
                      ^.className := "fichasEnJuegoName",
                      game.jugador(Option(user)).user.name
                    ),
                    <.img(
                      ^.key := "dominoEnJuego",
                      ^.transform := (
                        if (
                          game.triunfo match {
                            case Some(TriunfoNumero(num)) =>
                              ficha.es(
                                num
                              ) && ficha.abajo == num && ficha.arriba.value > ficha.abajo.value
                            case _ => false
                          }
                        )
                          "rotate(180deg)"
                        else
                          "none"
                      ),
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

  import scala.language.unsafeNulls
  given Reusability[Triunfo] = Reusability.by(_.toString)
  given Reusability[Game] = Reusability.by(game => (game.id.map(_.value), game.currentEventIndex))
  given Reusability[CuantasCantas] = Reusability.by(_.toString)
  given Reusability[Ficha] = Reusability.by(_.toString)
  given Reusability[State] = Reusability.derive[State]
  given Reusability[Props] = Reusability.derive[Props]

  private val component = ScalaComponent
    .builder[Props]
    .initialStateFromProps(p =>
      State(
        triunfo = p.gameInProgress.flatMap(_.triunfo),
        estrictaDerecha = p.gameInProgress.fold(false)(_.estrictaDerecha)
      )
    )
    .renderBackend[Backend]
    .configure(Reusability.shouldComponentUpdate)
    .build

  def apply(
    gameInProgress: Option[Game],
    mode:           StateSnapshot[GameViewMode]
  ): Unmounted[Props, State, Backend] = component(Props(gameInProgress, mode))

}
