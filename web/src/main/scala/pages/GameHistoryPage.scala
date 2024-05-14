/*
 * Copyright (c) 2024 Roberto Leibman
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package pages

import java.time.format.DateTimeFormatter
import app.ChutiState
import caliban.client.scalajs.ScalaJSClientAdapter
import chuti.*
import game.GameClient.*
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{BackendScope, Callback, ScalaComponent}
import net.leibman.chuti.semanticUiReact.components.{Container, Table, TableBody, TableCell, TableHeader, TableHeaderCell, TableRow}
import zio.json.*

import java.time.ZoneId
import java.util.Locale

object GameHistoryPage extends ChutiPage with ScalaJSClientAdapter {

  private val df = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm").nn.withLocale(Locale.US).nn.withZone(ZoneId.systemDefault()).nn
  case class State(games: Seq[Game] = Seq.empty)

  class Backend($ : BackendScope[Unit, State]) {

    def init: Callback = {
      calibanCall[Queries, Option[List[GameAsJson]]](
        Queries.getHistoricalUserGames,
        jsonGames => {
          $.modState(
            _.copy(games = jsonGames.toList.flatten.map(_.fromJson[Game] match {
              case Right(game) => game
              case Left(error) => throw RuntimeException(error)
            }))
          )
        }
      )
    }

    def render(s: State): VdomElement =
      ChutiState.ctx.consume { chutiState =>
        <.div(
          s.games.toVdomArray(game =>
            <.div(
              ^.key := s"${game.id}",
              Container()(
                Table()(
                  TableHeader()(
                    TableRow(
//                      key = "cuentasHeader1"
                    )(
                      TableHeaderCell().colSpan(4)(
                        s"Juego empezo en: ${df.format(game.created)}. ${game.satoshiPerPoint} Satoshi per punto" // TODO i8n
                      )
                    ),
                    TableRow(
//                      key = "cuentasHeader2"
                    )(
                      TableHeaderCell()("Jugador"), // TODO i8n
                      TableHeaderCell()("Cuentas"), // TODO i8n
                      TableHeaderCell()("Total"), // TODO i8n
                      TableHeaderCell()("Satoshi") // TODO i8n
                    )
                  ),
                  TableBody()(game.cuentasCalculadas.zipWithIndex.toVdomArray { case ((jugador, puntos, satoshi), jugadorIndex) =>
                    TableRow()
//                        key = s"cuenta$jugadorIndex",
                      .className(
                        if (jugador.id == chutiState.user.flatMap(_.id)) "cuentasSelf" else ""
                      )(
                        TableCell()(jugador.user.name),
                        TableCell()(
                          jugador.cuenta.zipWithIndex.toVdomArray { case (cuenta, cuentaIndex) =>
                            <.span(
                              ^.key       := s"cuenta_num${jugadorIndex}_$cuentaIndex",
                              ^.className := (if (cuenta.esHoyo) "hoyo" else ""),
                              s"${if (cuenta.puntos >= 0) "+" else ""} ${cuenta.puntos}"
                            )
                          },
                          <.span(
                            ^.fontSize := "large",
                            ^.color    := "blue",
                            if (jugador.ganadorDePartido) "➠" else ""
                          )
                        ),
                        TableCell()(
                          <.span(^.color := (if (puntos < 0) "#CC0000" else "#000000"), puntos)
                        ),
                        TableCell()(
                          <.span(
                            <.span(
                              ^.color := (if (satoshi < 0) "#CC0000" else "#000000"),
                              satoshi
                            )
                              .when(game.gameStatus == GameStatus.partidoTerminado)
                          )
                        )
                      )
                  })
                )
              )
            )
          )
        )
      }

  }

  private val component = ScalaComponent
    .builder[Unit]
    .initialState(State())
    .renderBackend[Backend]
    .componentDidMount(_.backend.init)
    .build

  def apply(): Unmounted[Unit, State, Backend] = component()

}
