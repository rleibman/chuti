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

import app.ChutiState
import components.components.ChutiComponent
import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.extra.router._
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom._
import pages._
import typings.semanticUiReact.components._

object AppRouter extends ChutiComponent {

  case class State()

  sealed trait AppPage

  case object GameAppPage extends AppPage

  case object RulesAppPage extends AppPage

  case object UserSettingsAppPage extends AppPage

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
                  chutiState.menuProviders.flatMap(_()).toVdomArray {
                    case (item, action) =>
                      MenuItem(onClick = { (_, _) =>
                        action
                      })(item)
                  },
                  Divider()(),
                  MenuItem(onClick = { (_, _) =>
                    Callback.alert("en construcción") //TODO write this
                  })("Reglas de Chuti"),
                  MenuItem(onClick = { (_, _) =>
                    Callback {
                      document.location.href = "/api/auth/doLogout"
                    }
                  })("Cerrar sesión"),
                  MenuItem(onClick = { (_, _) =>
                    Callback.alert("en construcción") //TODO write this
                  })("Administración de usuario"),
                  MenuItem(onClick = { (_, _) =>
                    Callback.alert("en construcción") //TODO write this
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
        <.div(^.className := "header", renderMenu),
        resolution.render()
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
