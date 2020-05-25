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

import components.components.ChutiComponent
import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom._
import pages._
import typings.semanticUiReact.components.{Button, Sidebar, SidebarPushable, SidebarPusher}
import typings.semanticUiReact.semanticUiReactStrings.push

object AppRouter extends ChutiComponent {

  case class State()

  sealed trait AppPage

  case object GameAppPage extends AppPage

  case object LobbyAppPage extends AppPage

  case object RulesAppPage extends AppPage

  case object UserSettingsAppPage extends AppPage

  private def layout(
    page:       RouterCtl[AppPage],
    resolution: Resolution[AppPage]
  ) = {
    assert(page != null)
    chutiContext.consume { chutiState =>
      def renderMenu =
        <.div(
          ^.height    := 100.pct,
          ^.className := "no-print"
        )

      <.div(
        ^.height := 100.pct,
          SidebarPushable()(
          Sidebar(animation = push, visible = chutiState.sidebarVisible)(renderMenu),
          SidebarPusher()(
            <.div(
              ^.height   := 100.pct,
              ^.maxWidth := 1500.px,
              ^.padding  := 5.px,
              Button(onClick = { (_, _) => Callback {
                document.location.href = "/api/auth/doLogout"
              }})("Log Out"),
              resolution.render()
            )
          )
        )
      )
    }
  }

  private val config: RouterConfig[AppPage] = RouterConfigDsl[AppPage].buildConfig { dsl =>
    import dsl._
    (trimSlashes
      | staticRoute("#game", GameAppPage) ~> renderR(_ => GamePage())
      | staticRoute("#lobby", LobbyAppPage) ~> renderR(_ =>
        chutiContext.consume { chutiState =>
          LobbyPage(chutiState)
        }
      )
      | staticRoute("#rules", RulesAppPage) ~> renderR(_ => RulesPage())
      | staticRoute("#userSettings", UserSettingsAppPage) ~> renderR(_ => UserSettingsPage()))
      .notFound(redirectToPage(LobbyAppPage)(SetRouteVia.HistoryReplace))
      .renderWith(layout)
  }
  private val baseUrl: BaseUrl = BaseUrl.fromWindowOrigin_/

  val router: Router[AppPage] = Router.apply(baseUrl, config)
}
