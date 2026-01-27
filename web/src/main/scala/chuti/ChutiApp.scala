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

package chuti

import auth.{AuthClient, LoginRouter, OAuthProviderUI}
import japgolly.scalajs.react.*
import japgolly.scalajs.react.vdom.html_<^.*
import org.scalajs.dom

import scala.scalajs.js
import scala.scalajs.js.annotation.JSExport

object ChutiApp {

  /** Google logo SVG (official colors)
    */
  private val googleIcon: VdomNode = {
    import japgolly.scalajs.react.vdom.svg_<^.{< as svgTag, ^ as svgAttr}

    svgTag.svg(
      svgAttr.width   := "20",
      svgAttr.height  := "20",
      svgAttr.viewBox := "0 0 48 48",
      svgTag.path(
        svgAttr.fill := "#EA4335",
        svgAttr.d := "M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"
      ),
      svgTag.path(
        svgAttr.fill := "#4285F4",
        svgAttr.d := "M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"
      ),
      svgTag.path(
        svgAttr.fill := "#FBBC05",
        svgAttr.d := "M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"
      ),
      svgTag.path(
        svgAttr.fill := "#34A853",
        svgAttr.d := "M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"
      )
    )
  }

  private val oauthProviders = List(
    OAuthProviderUI(
      provider = "google",
      icon = Some(googleIcon),
      label = "Sign in with Google",
      className = Some("auth-oauth-button-google")
    )
  )

  val component = ScalaFnComponent
    .withHooks[Unit]
    .useState(None: Option[User])
    .useEffectOnMountBy {
      (
        _,
        userOpt
      ) =>
        def isPlayerView: Boolean = dom.window.location.pathname.startsWith("/playerView")
        if (isPlayerView) {
          Callback.log("player view, no auth expected")
        } else {
          Callback.log("DMScreenApp mounting, checking authentication...") >>
            AuthClient
              .whoami[User, ConnectionId](Some(ClientRepository.connectionId))
              .flatTap {
                case Some(user) => Callback.log(s"User authenticated: ${user.email}").asAsyncCallback
                case None       => Callback.log("No user authenticated, showing login").asAsyncCallback
              }
              .map(me => userOpt.modState(_ => me))
              .completeWith(_.get)
        }
    }
    .render {
      (
        _,
        userOpt
      ) =>

        val queryString:  String = dom.window.location.search
        val searchParams: Map[String, String] = dom.URLSearchParams(queryString).iterator.map(t => (t._1, t._2)).toMap

        userOpt.value.fold(
          LoginRouter(Some(ClientRepository.connectionId), oauthProviders)
        )(user => Content())
    }

  @JSExport
  def main(args: Array[String]): Unit = {
    js.Dynamic.global.document.title =
      if (org.scalajs.dom.window.location.protocol == "https:") "DMScreen" else "DMScreen (Local)"
    val container = dom.document.getElementById("content")
    val root = ReactDOMClient.createRoot(container)
    root.render(component())
    ()

  }

}
