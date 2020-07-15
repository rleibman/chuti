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

package router

import app.{ChutiState, GameViewMode, GlobalDialog}
import chat.ChatComponent
import chuti.{ChannelId, GameStatus}
import components.Toast
import components.components.ChutiComponent
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{BackendScope, Callback, ScalaComponent}
import org.scalajs.dom._
import pages._
import typings.semanticUiReact.components._
import typings.semanticUiReact.genericMod.SemanticICONS

object AppRouter extends ChutiComponent {

  sealed trait AppPage

  case object GameAppPage extends AppPage

  case object RulesAppPage extends AppPage

  case object UserSettingsAppPage extends AppPage

  case object AboutAppPage extends AppPage

  object DialogRenderer {
    class Backend($ : BackendScope[_, _]) {

      def render(): VdomElement = ChutiState.ctx.consume { chutiState =>
        def renderCuentasDialog: VdomArray = {
          chutiState.gameInProgress.toVdomArray { game =>
            Modal(key = "cuentasDialog", open = chutiState.currentDialog == GlobalDialog.cuentas)(
              ModalHeader()(s"Cuentas (${game.satoshiPerPoint} Satoshi per punto)"),
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
                  TableBody()(game.cuentasCalculadas.zipWithIndex.toVdomArray {
                    case ((jugador, puntos, satoshi), jugadorIndex) =>
                      TableRow(key = s"cuenta$jugadorIndex")(
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
                            <.span(^.color := (if (satoshi < 0) "#CC0000" else "#000000"), satoshi)
                              .when(game.gameStatus == GameStatus.partidoTerminado)
                          )
                        )
                      )
                  })
                ),
                if (game.gameStatus == GameStatus.partidoTerminado) {
                  <.div(
                    s"Partido terminado, ${game.ganadorDePartido.fold("")(_.user.name)} gano el partido."
                  )
                } else {
                  EmptyVdom
                }
              ),
              ModalActions()(Button(compact = true, basic = true, onClick = { (_, _) =>
                chutiState.showDialog(GlobalDialog.none)
              })("Ok"))
            )
          }
        }
        VdomArray(renderCuentasDialog)
      }
    }
    private val component = ScalaComponent
      .builder[Unit]("content")
      .renderBackend[Backend]
      .build
    def apply() = component()
  }

  private def layout(
    page:       RouterCtl[AppPage],
    resolution: Resolution[AppPage]
  ): VdomElement = {
    assert(page != null)
    ChutiState.ctx.consume { chutiState =>
      def renderMenu = {
        VdomArray(
          <.div(
            ^.key       := "menu",
            ^.height    := 100.pct,
            ^.className := "no-print headerMenu",
            Menu(
              attached = false,
              compact = true,
              text = true,
              borderless = true
            )(
              Dropdown(
                item = true,
//                simple = true,
                compact = true,
                text = "☰ Menu"
              )(
                DropdownMenu()(
                  chutiState.gameInProgress
                    .filter(g => g.gameStatus.enJuego || g.gameStatus == GameStatus.partidoTerminado
                    ).map { _ =>
                      VdomArray(
                        MenuItem(
                          key = "menuEntrarAlJuego",
                          onClick = { (e, _) =>
                            chutiState
                              .onGameViewModeChanged(GameViewMode.game) >> page
                              .setEH(GameAppPage)(e)
                          }
                        )("Entrar al Juego"),
                        MenuItem(key = "menuCuentas", onClick = { (_, _) =>
                          chutiState.showDialog(GlobalDialog.cuentas)
                        })("Cuentas")
                      )
                    },
                  MenuItem(
                    key = "menuLobby",
                    onClick = { (e, _) =>
                      chutiState
                        .onGameViewModeChanged(GameViewMode.lobby) >> page.setEH(GameAppPage)(e)
                    }
                  )("Lobby"),
                  Divider()(),
                  MenuItem(onClick = { (e, _) =>
                    page.setEH(RulesAppPage)(e)
                  })("Reglas de Chuti"),
                  MenuItem(onClick = { (_, _) =>
                    Callback {
                      document.location.href = "/api/auth/doLogout"
                    }
                  })("Cerrar sesión"),
                  MenuItem(onClick = { (e, _) =>
                    page.setEH(UserSettingsAppPage)(e)
                  })("Administración de usuario"),
                  Divider()(),
                  MenuItem(onClick = { (_, _) =>
                    chutiState.toggleSound
                  })(
                    Icon(name =
                      if (chutiState.muted) SemanticICONS.`volume up`
                      else SemanticICONS.`volume off`
                    )(),
                    if (chutiState.muted) "Con Sonido" else "Sin Sonido"
                  ),
                  Divider()(),
                  MenuItem(onClick = { (e, _) =>
                    page.setEH(AboutAppPage)(e)
                  })("Acerca de Chuti")
                )
              )
            )
          ),
          <.div(
            ^.key       := "user",
            ^.className := "user",
            s"${chutiState.user.fold("")(u => s"Hola ${u.name}!")}"
          )
        )
      }

      <.div(
        ^.className := "innerContent",
        <.div(^.className := "header", renderMenu, DialogRenderer()),
        resolution.render(),
        chutiState.user.fold(EmptyVdom) { user =>
          val channelId = chutiState.gameInProgress.fold(ChannelId.lobbyChannel)(game =>
            chutiState.gameViewMode match {
              case app.GameViewMode.lobby => ChannelId.lobbyChannel
              case GameViewMode.game      => game.channelId.getOrElse(ChannelId.lobbyChannel)
              case app.GameViewMode.none  => ChannelId.lobbyChannel
            }
          )
          ChatComponent(
            user,
            channelId,
            onPrivateMessage = { msg =>
              Toast.info(<.div(s"Tienes un nuevo mensaje!", <.br(), msg.msg)) >> chutiState.onRequestGameRefresh
            }
          )
        }
      )
    }
  }

  private val config: RouterConfig[AppPage] = RouterConfigDsl[AppPage].buildConfig { dsl =>
    import dsl._

    (trimSlashes
      | staticRoute("#game", GameAppPage) ~> renderR(_ => GamePage())
      | staticRoute("#rules", RulesAppPage) ~> renderR(_ => RulesPage())
      | staticRoute("#userSettings", UserSettingsAppPage) ~> renderR(_ => UserSettingsPage()))
      .notFound(redirectToPage(GameAppPage)(SetRouteVia.HistoryReplace))
      .renderWith(layout)
  }
  private val baseUrl: BaseUrl = BaseUrl.fromWindowOrigin_/

  val router: Router[AppPage] = Router.apply(baseUrl, config)
}
