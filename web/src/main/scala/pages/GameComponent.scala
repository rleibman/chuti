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
import chuti.*
import chuti.CuantasCantas.{Canto5, CuantasCantas}
import chuti.Triunfo.{SinTriunfos, TriunfoNumero}
import components.Confirm
import japgolly.scalajs.react.*
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.StateSnapshot
import japgolly.scalajs.react.vdom.html_<^.*
import net.leibman.chuti.react.mod.CSSProperties
import net.leibman.chuti.semanticUiReact.components.*
import net.leibman.chuti.semanticUiReact.distCommonjsElementsImageImageMod.ImageProps
import net.leibman.chuti.semanticUiReact.distCommonjsGenericMod.{
  SemanticCOLORS,
  SemanticICONS,
  SemanticSIZES,
  SemanticShorthandItem
}
import net.leibman.chuti.semanticUiReact.distCommonjsModulesDropdownDropdownItemMod.DropdownItemProps
import net.leibman.chuti.semanticUiReact.semanticUiReactStrings.*
import org.scalajs.dom

import scala.scalajs.js.JSConverters.*

object GameComponent {

  object GameComponentMessages extends LocalizedMessages {

    override def bundles: Map[String, GameComponentMessages.MessageBundle] =
      Map(
        "es" -> MessageBundle("es", Map("GameComponent.listo" -> "Listo!")),
        "en" -> MessageBundle("en", Map("GameComponent.listo" -> "Ready!"))
      )

  }

  case class Props(
    gameInProgress: Option[Game],
    gameViewMode:   StateSnapshot[GameViewMode]
  )

  case class State(
    cuantasCantas:     Option[CuantasCantas] = None,
    estrictaDerecha:   Boolean = false,
    fichaSeleccionada: Option[Ficha] = None,
    triunfo:           Option[Triunfo] = None,
    justDealt:         Set[Int] = Set.empty, // Track which player positions just got dealt
    collectingTo:      Option[Int] = None // Track which player position is collecting tricks
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
      (for {
        _ <- GameClient.game.playSilently(gameId, event)
      } yield clearPlayState()).completeWith(_.get)
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

    def checkForDeals(
      prevGame: Option[Game],
      currGame: Option[Game]
    ): Callback = {
      import scala.scalajs.js.timers

      (prevGame, currGame) match {
        case (Some(prev), Some(curr)) =>
          // Detect which player positions got new deals (went from <7 to 7 tiles)
          val newDeals = (0 until 4).filter { playerIdx =>
            val prevCount = if (playerIdx < prev.jugadores.size) prev.jugadores(playerIdx).fichas.size else 0
            val currCount = if (playerIdx < curr.jugadores.size) curr.jugadores(playerIdx).fichas.size else 0
            prevCount < 7 && currCount == 7
          }.toSet

          if (newDeals.nonEmpty) {
            $.modState(_.copy(justDealt = newDeals)) >> Callback {
              // Clear the justDealt flag after animation completes (1.5 seconds for staggered animation)
              timers.setTimeout(1500) {
                $.modState(_.copy(justDealt = Set.empty)).runNow()
              }
            }
          } else {
            Callback.empty
          }
        case _ => Callback.empty
      }
    }

    def checkForTrickCollection(
      prevGame: Option[Game],
      currGame: Option[Game]
    ): Callback = {
      import scala.scalajs.js.timers

      (prevGame, currGame) match {
        case (Some(prev), Some(curr)) =>
          // Detect when tricks are collected (enJuego goes from 4 to 0)
          val prevInPlay = prev.enJuego.size
          val currInPlay = curr.enJuego.size

          if (prevInPlay == 4 && currInPlay == 0) {
            // Find who won the last trick by checking who got a new fila
            val winnerIdOpt = curr.jugadores
              .find { j =>
                val prevFilas = prev.jugadores.find(_.id == j.id).fold(0)(_.filas.size)
                val currFilas = j.filas.size
                currFilas > prevFilas
              }.map(_.id)

            winnerIdOpt.fold(Callback.empty) { winnerId =>
              // Wait 10 seconds to let players see the tiles before collecting
              Callback {
                timers.setTimeout(10000) {
                  // Now start the collection animation
                  $.modState(_.copy(collectingTo = Some(winnerId.value.toInt))).runNow()
                  // Clear the collectingTo flag after animation completes (1200ms more)
                  timers.setTimeout(1200) {
                    $.modState(_.copy(collectingTo = None)).runNow()
                  }
                }
              }
            }
          } else {
            Callback.empty
          }
        case _ => Callback.empty
      }
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
                      } ${
                        if (jugador.jugadorType != JugadorType.human) "bot-player"
                        else ""
                      } ${
                        if (canPlay && jugador.jugadorType != JugadorType.human) "active-turn"
                        else ""
                      }",
                    jugador.user.name,
                    // Add bot badge and rationale icon
                    if (jugador.jugadorType != JugadorType.human) {
                      TagMod(
                        <.span(^.className := "bot-badge", " 🤖"), {
                          dom.console.log(s"Bot ${jugador.user.name}: lastBotRationale = ${jugador.lastBotRationale}")
                          jugador.lastBotRationale match {
                            case Some(rationale) =>
                              <.span(
                                ^.className := "bot-rationale-icon",
                                ^.title     := rationale,
                                Icon().name(SemanticICONS.`question circle`)()
                              )
                            case None =>
                              EmptyVdom
                          }
                        }
                      )
                    } else EmptyVdom
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
                          .flatMap(c => CuantasCantas.byPriority(c.prioridad + 1)).getOrElse(
                            Canto5
                          )
                        val cantasOptions = (defaultCuantas +: CuantasCantas.posibilidades(min)).map { cuantas =>
                          DropdownItemProps()
                            .setText(cuantas.toString)
                            .set("key", cuantas.prioridad.toString)
                            .setValue(cuantas.prioridad)
                        }.toJSArray

                        <.div(
                          Dropdown
                            .className("cantaDropdown")
                            .compact(true)
                            .fluid(false)
                            .placeholder("Cuantas Cantas?") // TODO i8n
                            .selection(true)
                            .value(s.cuantasCantas.getOrElse(defaultCuantas).prioridad)
                            .options(cantasOptions)
                            .onChange {
                              (
                                _,
                                dropDownProps
                              ) =>
                                val value = dropDownProps.value.asInstanceOf[Int]
                                $.modState(
                                  _.copy(cuantasCantas = CuantasCantas.byPriority(value))
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
                                  game.id,
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
                              play(game.id, Da(ficha = s.fichaSeleccionada.get))
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
                                    game.id,
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
                                    play(game.id, Caete(triunfo = s.triunfo))
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
                              play(game.id, Sopa())
                          }("Sopa") // TODO i8n
                      case JugadorState.esperandoCanto          => EmptyVdom
                      case JugadorState.esperando               => EmptyVdom
                      case JugadorState.partidoTerminado        => EmptyVdom
                      case JugadorState.invitedNotAnswered      => EmptyVdom
                      case JugadorState.waitingOthersAcceptance => EmptyVdom
                      case s => throw RuntimeException(s"Should never, ever get here, invalid state: $s")
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
                              onConfirm = play(game.id, MeRindo())
                            )
                        }("Me Rindo") // TODO i8n
                    } else
                      EmptyVdom
                  )
                } else
                  EmptyVdom,
                <.div(
                  ^.className := s"fichas$playerPosition ${
                      if (s.justDealt.contains(playerPosition)) "just-dealt" else ""
                    }",
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
                  <.div(
                    ^.className := "juegoStatusString",
                    Popup()
                      .style(CSSProperties().set("cursor", "pointer"))
                      .trigger(
                        Icon()
                          .name(SemanticICONS.calculator)
                          .size(SemanticSIZES.large)
                      )
                      .hoverable(true)
                      .position(`bottom center`)(
                        <.div(
                          Table()
                            .compact(true)
                            .size(SemanticSIZES.small)(
                              TableHeader()(
                                TableRow()(
                                  TableHeaderCell()("Jugador"),
                                  TableHeaderCell()("Cuentas"),
                                  TableHeaderCell()("Total")
                                )
                              ),
                              TableBody()(game.cuentasCalculadas.zipWithIndex.toVdomArray {
                                case ((jugador, puntos, satoshi), jugadorIndex) =>
                                  TableRow()
                                    .withKey(s"cuentaPopup$jugadorIndex")
                                    .className(
                                      if (chutiState.user.map(_.id).contains(jugador.id)) "cuentasSelf" else ""
                                    )(
                                      TableCell()(jugador.user.name),
                                      TableCell()(
                                        jugador.cuenta.zipWithIndex.toVdomArray { case (cuenta, cuentaIndex) =>
                                          <.span(
                                            ^.key         := s"cuenta_popup${jugadorIndex}_$cuentaIndex",
                                            ^.className   := (if (cuenta.esHoyo) "hoyo" else ""),
                                            ^.marginRight := 5.px,
                                            s"${if (cuenta.puntos >= 0) "+" else ""}${cuenta.puntos}"
                                          )
                                        },
                                        <.span(
                                          ^.fontSize := "large",
                                          ^.color    := "blue",
                                          if (jugador.fueGanadorDelPartido) "➠" else ""
                                        )
                                      ),
                                      TableCell()(
                                        <.span(^.color := (if (puntos < 0) "#CC0000" else "#000000"), puntos)
                                      )
                                    )
                              })
                            )
                        )
                      ),
                    game.statusString
                  )
                ),
                // Show "Start Next Round" button when requiereSopa, otherwise show tiles in play
                if (game.gameStatus == GameStatus.requiereSopa) {
                  <.div(
                    ^.className := "dominoEnJuego",
                    ^.display.flex,
                    ^.alignItems.center,
                    ^.justifyContent.center,
                    Button
                      .color(SemanticCOLORS.green)
                      .size(SemanticSIZES.huge)
                      .onClick {
                        (
                          _,
                          _
                        ) =>
                          // Any human player can trigger sopa on behalf of the bot who has turno
                          play(game.id, Sopa(firstSopa = game.currentEventIndex == 0))
                      }("Listo para la sopa") // TODO i18n "Start Next Round"
                  )
                } else {
                  game.enJuego.toVdomArray { case (user, ficha) =>
                    val playingJugador = game.jugador(user)
                    val playingPosition =
                      if (chutiState.user.fold(false)(_.id == playingJugador.id))
                        0
                      else if (chutiState.user.fold(false)(game.nextPlayer(_).id == playingJugador.id))
                        1
                      else if (chutiState.user.fold(false)(game.prevPlayer(_).id == playingJugador.id))
                        3
                      else
                        2

                    // Calculate winner position if collecting
                    val collectClass = s.collectingTo
                      .flatMap { winnerIdValue =>
                        game.jugadores.find(_.id.value.toInt == winnerIdValue).map { winner =>
                          val winnerPosition =
                            if (chutiState.user.fold(false)(_.id == winner.id))
                              0
                            else if (chutiState.user.fold(false)(game.nextPlayer(_).id == winner.id))
                              1
                            else if (chutiState.user.fold(false)(game.prevPlayer(_).id == winner.id))
                              3
                            else
                              2
                          s" collectToPlayer$winnerPosition"
                        }
                      }.getOrElse("")

                    VdomArray(
                      <.div(
                        ^.key       := "fichasEnJuegoName",
                        ^.className := "fichasEnJuegoName",
                        playingJugador.user.name
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
                        ^.className := s"dominoEnJuego slideFromPlayer$playingPosition$collectClass"
                      )
                    )
                  }
                }
              )
            } // end else (game is in progress)
          )
        )
      }
    }

  }

  import scala.language.unsafeNulls
  given Reusability[Triunfo] = Reusability.by(_.toString)
  given Reusability[Game] = Reusability.by(game => (game.id.value, game.currentEventIndex))
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
    .backend[Backend](Backend(_))
    .renderPS(_.backend.render(_, _))
    .componentDidUpdate($ =>
      $.backend.checkForDeals($.prevProps.gameInProgress, $.currentProps.gameInProgress) >>
        $.backend.checkForTrickCollection($.prevProps.gameInProgress, $.currentProps.gameInProgress)
    )
    .configure(Reusability.shouldComponentUpdate)
    .build

  def apply(
    gameInProgress: Option[Game],
    mode:           StateSnapshot[GameViewMode]
  ): Unmounted[Props, State, Backend] = component(Props(gameInProgress, mode))

}
