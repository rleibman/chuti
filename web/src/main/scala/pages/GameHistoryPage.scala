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

import java.time.format.DateTimeFormatter

import app.ChutiState
import caliban.client.scalajs.ScalaJSClientAdapter
import chuti._
import game.GameClient.Queries
import io.circe.generic.auto._
import io.circe.{Decoder, Json}
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, ScalaComponent}
import typings.semanticUiReact.components.{
  Container,
  Table,
  TableBody,
  TableCell,
  TableHeader,
  TableHeaderCell,
  TableRow
}

object GameHistoryPage extends ChutiPage with ScalaJSClientAdapter {
  private val df = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")
  case class State(games: Seq[Game] = Seq.empty)

  class Backend($ : BackendScope[_, State]) {
    private val gameDecoder = implicitly[Decoder[Game]]

    def init: Callback = {
      calibanCall[Queries, Option[List[Json]]](
        Queries.getHistoricalUserGames,
        jsonGames => {
          $.modState(
            _.copy(games =
              jsonGames.toList.flatten.map(json =>
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

    def render(s: State): VdomElement =
      ChutiState.ctx.consume { chutiState =>
        <.div(
          s.games.toVdomArray(game =>
            <.div(
              ^.key := s"${game.id}",
              Container()(
                Table()(
                  TableHeader()(
                    TableRow(key = "cuentasHeader1")(
                      TableHeaderCell(colSpan = 4)(
                        s"Juego empezo en: ${df.format(game.created)}. ${game.satoshiPerPoint} Satoshi per punto"
                      )
                    ),
                    TableRow(key = "cuentasHeader2")(
                      TableHeaderCell()("Jugador"),
                      TableHeaderCell()("Cuentas"),
                      TableHeaderCell()("Total"),
                      TableHeaderCell()("Satoshi")
                    )
                  ),
                  TableBody()(game.cuentasCalculadas.zipWithIndex.toVdomArray {
                    case ((jugador, puntos, satoshi), jugadorIndex) =>
                      TableRow(
                        key = s"cuenta$jugadorIndex",
                        className =
                          if (jugador.id == chutiState.user.flatMap(_.id)) "cuentasSelf" else ""
                      )(
                        TableCell()(jugador.user.name),
                        TableCell()(
                          jugador.cuenta.zipWithIndex.toVdomArray {
                            case (cuenta, cuentaIndex) =>
                              <.span(
                                ^.key       := s"cuenta_num${jugadorIndex}_$cuentaIndex",
                                ^.className := (if (cuenta.esHoyo) "hoyo" else ""),
                                s"${if (cuenta.puntos >= 0) "+" else ""} ${cuenta.puntos}"
                              )
                          }
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
