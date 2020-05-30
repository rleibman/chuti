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

package app

import chuti.User
import components.components.ChutiComponent
import components.{Confirm, Toast}
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import router.AppRouter
import service.UserRESTClient

import scala.util.{Failure, Success}

/**
  * This is a helper class meant to load initial app state, scalajs-react normally
  * suggests (and rightfully so) that the router should be the main content of the app,
  * but having a middle piece that loads app state makes some sense, that way the router is in charge of routing and
  * presenting the app menu.
  */
object Content extends ChutiComponent {

  case class State(chutiState: ChutiState = ChutiState())

  class Backend($ : BackendScope[_, State]) {
    val onToggleSidebar: Callback = {
      $.modState(s =>
        s.copy(chutiState = s.chutiState.copy(sidebarVisible = !s.chutiState.sidebarVisible))
      )
    }

    def onUserChanged(userOpt: Option[User]): Callback =
      userOpt.fold(Callback.empty) { user =>
        UserRESTClient.remoteSystem
          .upsert(user)
          .completeWith {
            case Success(u) =>
              (Toast.success("User successfully saved ") >>
                $.modState(s => s.copy(chutiState = s.chutiState.copy(user = Some(u)))))
            case Failure(t) =>
              t.printStackTrace()
              Callback.empty //TODO do something else here
          }
      }

    def render(s: State): VdomElement =
      chutiContext.provide(s.chutiState) {
        <.div(^.height := 100.pct, Confirm.render(), Toast.render(), AppRouter.router())
      }

    def refresh(s: State): Callback =
      $.modState(s =>
        s.copy(chutiState = s.chutiState.copy(onToggleSidebar = Some(onToggleSidebar)))
      ) >>
        UserRESTClient.remoteSystem.whoami().completeWith {
          case Success(user) =>
            $.modState(s =>
              s.copy(
                chutiState = s.chutiState.copy(onUserChanged = Some(onUserChanged), user = user)
              )
            )
          case Failure(e) =>
            e.printStackTrace()
            Callback.empty
          //TODO do something more with this here
        }
  }

  private val component = ScalaComponent
    .builder[Unit]("content")
    .initialState(State())
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.refresh($.state))
    .build

  def apply(): Unmounted[Unit, State, Backend] = component()
}
