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

import auth.AuthClient
import chat.*
import chuti.{ChutiState, GameStatus, GameViewMode, GlobalDialog}
import components.*
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.router.*
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{BackendScope, Callback, ScalaComponent}
import net.leibman.chuti.semanticUiReact.components.*
import net.leibman.chuti.semanticUiReact.distCommonjsCollectionsMenuMenuMod.MenuProps
import net.leibman.chuti.semanticUiReact.distCommonjsGenericMod.SemanticICONS
import pages.*

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import scala.annotation.unused

object AppRouter extends ChutiComponent {

  private val df =
    DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm").nn.withLocale(Locale.US).nn.withZone(ZoneId.systemDefault()).nn

  sealed trait AppPage

  case object GameAppPage extends AppPage

  case object GameHistoryAppPage extends AppPage

  case object RulesAppPage extends AppPage

  case object UserSettingsAppPage extends AppPage

  case object AboutAppPage extends AppPage

  case object ChangeLogAppPage extends AppPage

  object DialogRenderer {

    class Backend(@unused $ : BackendScope[Unit, Unit]) {

      def render(): VdomElement =
        ChutiState.ctx.consume { chutiState =>
          import chutiState.ChutiMessages.*
          given locale: Locale = chutiState.locale

          def renderCuentasDialog: VdomArray = {
            chutiState.gameInProgress.toVdomArray { game =>
              Modal()
                //                key = "cuentasDialog",
                .open(chutiState.currentDialog == GlobalDialog.cuentas)(
                  ModalHeader()(
                    s"Juego empezo en: ${df.format(game.created)}. ${game.satoshiPerPoint} Satoshi per punto" // TODO I8n
                  ),
                  ModalContent()(
                    Table()(
                      TableHeader()(
                        TableRow(
//                        key("cuentasHeader"
                        )(
                          TableHeaderCell()(localized("Chuti.jugador")),
                          TableHeaderCell()(localized("Chuti.cuentas")),
                          TableHeaderCell()(localized("Chuti.total")),
                          TableHeaderCell()(localized("Chuti.satoshi"))
                        )
                      ),
                      TableBody()(game.cuentasCalculadas.zipWithIndex.toVdomArray {
                        case ((jugador, puntos, satoshi), jugadorIndex) =>
                          TableRow()
//                          key(s"cuenta$jugadorIndex",
                            .className(if (chutiState.user.map(_.id).contains(jugador.id)) "cuentasSelf" else "")(
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
                                  if (jugador.fueGanadorDelPartido) "➠" else ""
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
                    ),
                    if (game.gameStatus == GameStatus.partidoTerminado) {
                      <.div(
                        s"Partido terminado, ${game.ganadorDePartido.fold("")(_.user.name)} gano el partido." // TODO I8n
                      )
                    } else
                      EmptyVdom,
                    <.div( // TODO I8n
                      <.h1("Puntuacion"),
                      <.ul(
                        <.li("Si ganas, cada uno te da 1 punto (o sea, recibes 3 puntos)"),
                        <.li(
                          "Si ganas con chuti, cada jugador te da 2 puntos (o sea, recibes 6 puntos)"
                        ),
                        <.li(
                          "Si alguien quedó en negativos por tener varios hoyos, producto de estar cante y cante, le da dos puntos al ganador."
                        ),
                        <.li(
                          "Si alguien quedó en cero, le da un punto al ganador."
                        ),
                        <.li(
                          "Por cada hoyo que tienes, le das un punto a cada uno de los otros jugadores (o sea, pierdes 3 puntos)"
                        )
                      )
                    )
                  ),
                  ModalActions()(
                    Button()
                      .compact(true)
                      .basic(true)
                      .onClick {
                        (
                          _,
                          _
                        ) =>
                          chutiState.showDialog(GlobalDialog.none)
                      }("Ok") // TODO I8n
                  )
                )
            }
          }

          VdomArray(renderCuentasDialog)
        }

    }

    private val component = ScalaComponent
      .builder[Unit]("content")
      .backend[Backend](Backend(_))
      .render(_.backend.render())
      .build

    def apply(): Unmounted[Unit, Unit, Backend] = component()

  }

  private def layout(
    page:       RouterCtl[AppPage],
    resolution: Resolution[AppPage]
  ): VdomElement = {
    ChutiState.ctx.consume { chutiState =>
      import chutiState.ChutiMessages.*
      given locale: Locale = chutiState.locale
      def renderMenu = {
        VdomArray(
          <.div(
            ^.key       := "menu",
            ^.height    := 100.pct,
            ^.className := "no-print headerMenu",
            Menu.withProps(
              MenuProps()
                .setAttached(false)
                .setCompact(true)
                .setText(true)
                .setBorderless(true)
            )
          )(
            Dropdown()
              .item(true)
              //                simple(true)
              .compact(true)
              .text(
                "☰ Menu" // TODO I8n
              )(
                DropdownMenu()(
                  chutiState.gameInProgress
                    .filter(g => g.gameStatus.enJuego || g.gameStatus == GameStatus.partidoTerminado).map { _ =>
                      VdomArray(
                        // Only show "Entrar al juego" if not already in game mode
                        if (chutiState.gameViewMode != GameViewMode.game)
                          MenuItem()
//                          key("menuEntrarAlJuego",
                            .onClick {
                              (
                                e,
                                _
                              ) =>
                                chutiState
                                  .onGameViewModeChanged(GameViewMode.game) >> page
                                  .setEH(GameAppPage)(e)
                            }(localized("Chuti.entrarAlJuego"))
                        else EmptyVdom,
                        MenuItem()
//                          key("menuCuentas",
                          .onClick {
                            (
                              _,
                              _
                            ) =>
                              chutiState.showDialog(GlobalDialog.cuentas)
                          }("Cuentas") // TODO I8n
                      )
                    },
                  MenuItem()
//                    key("menuLobby",
                    .onClick {
                      (
                        e,
                        _
                      ) =>
                        chutiState
                          .onGameViewModeChanged(GameViewMode.lobby) >> page.setEH(GameAppPage)(e)
                    }("Lobby"), // TODO I8n
                  MenuItem()
//                    key("history",
                    .onClick {
                      (
                        e,
                        _
                      ) =>
                        page.setEH(GameHistoryAppPage)(e)
                    }("Historia de juegos"), // TODO I8n
                  Divider()(),
                  MenuItem().onClick(
                    (
                      e,
                      _
                    ) => page.setEH(RulesAppPage)(e)
                  )("Reglas de Chuti"), // TODO I8n
                  MenuItem().onClick {
                    (
                      _,
                      _
                    ) => AuthClient.logout().completeWith(_ => Callback.empty)
                  }("Cerrar sesión"), // TODO I8n`
                  MenuItem().onClick(
                    (
                      e,
                      _
                    ) => page.setEH(UserSettingsAppPage)(e)
                  )(
                    "Administración de usuario" // TODO I8n
                  ),
                  Divider()(),
                  MenuItem().onClick(
                    (
                      _,
                      _
                    ) => chutiState.toggleSound
                  )(
                    Icon().name(
                      if (chutiState.muted) SemanticICONS.`volume up`
                      else SemanticICONS.`volume off`
                    )(),
                    if (chutiState.muted) "Con Sonido" else "Sin Sonido" // TODO I8n
                  ),
                  Divider()(),
                  MenuItem().onClick(
                    (
                      e,
                      _
                    ) => page.setEH(ChangeLogAppPage)(e)
                  )("ChangeLog"), // TODO I8n
                  MenuItem().onClick(
                    (
                      e,
                      _
                    ) => page.setEH(AboutAppPage)(e)
                  )(
                    "Acerca de chuti.fun" // TODO I8n
                  )
                )
              )
          ),
          <.div(
            ^.key       := "user",
            ^.className := "user",
            s"${chutiState.user.fold("")(u => s"Hola ${u.name}!")}" // TODO I8n
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
              case GameViewMode.lobby => ChannelId.lobbyChannel
              case GameViewMode.game  => game.channelId.orElse(ChannelId.lobbyChannel)
              case GameViewMode.none  => ChannelId.lobbyChannel
            }
          )
          ChatComponent(
            user,
            channelId,
            onPrivateMessage = { msg =>
              Toast.info(
                <.div(s"Tienes un nuevo mensaje!", <.br(), msg.msg) // TODO I8n
              ) >> chutiState.onRequestGameRefresh()
            },
            onMessage = _ => chutiState.playSound("sounds/message.mp3")
          )
        }
      )
    }
  }

  private val config: RouterConfig[AppPage] = RouterConfigDsl[AppPage].buildConfig { dsl =>
    import dsl.*

    (trimSlashes
      | staticRoute("#game", GameAppPage) ~> renderR(_ => GamePage())
      | staticRoute("#history", GameHistoryAppPage) ~> renderR(_ => GameHistoryPage())
      | staticRoute("#rules", RulesAppPage) ~> renderR(_ => RulesPage())
      | staticRoute("#userSettings", UserSettingsAppPage) ~> renderR(_ => UserSettingsPage())
      | staticRoute("#about", AboutAppPage) ~> renderR(_ => AboutPage())
      | staticRoute("#changeLog", ChangeLogAppPage) ~> renderR(_ => ChangeLogPage()))
      .notFound(redirectToPage(GameAppPage)(SetRouteVia.HistoryReplace))
      .renderWith(layout)
  }
  private val baseUrl: BaseUrl = BaseUrl.fromWindowOrigin_/

  val router: Router[AppPage] = Router.apply(baseUrl, config)

}
