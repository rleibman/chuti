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

import chuti.BuildInfo
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._

object AboutPage extends ChutiPage {
  case class State()

  val agradecimientos: String =
    s"""
   |<div>
   |<h2>Agradecimientos</h2>
   |<p>Un gran agradecimiento a mis amigos con los que jugué chuti en la Ibero, y que me ayudaron a acordarme como se juega:
   |Wilfrido Cruz, Francisco Briseño, Alfredo Galindo, Hiram Garcia, Juan Carlos Escobar, Juan Carlos Garfias, Paul Uribe.</p>
   |<p>... Y a todos los demás con los que no platico por Whatsapp a diario (pero que también fueron clientes).</p>
   |</div>
   |""".stripMargin

  class Backend($ : BackendScope[_, State]) {
    def renderBuildInfo: VdomElement =
      <.div(
        <.h2("Versiones"),
        <.table(
          <.tbody(
            <.tr(<.td("Chuti"), <.td(BuildInfo.version)),
            <.tr(<.td("Scala"), <.td(BuildInfo.scalaVersion)),
            <.tr(<.td("Sbt"), <.td(BuildInfo.sbtVersion))
          )
        )
      )
    def renderTechnologiesUsed: VdomElement =
      <.div(
        <.h2("Powered by"),
        <.table(
          <.tbody(
            <.tr(
              <.td(
                <.a(
                  ^.href := "http://www.scala-lang.org",
                  <.img(
                    ^.src   := "https://www.scala-lang.org/resources/img/scala-logo.png",
                    ^.width := 50.px
                  )
                )
              ),
              <.td("Scala"),
              <.td("Functional and object oriented language")
            ),
            <.tr(
              <.td(
                <.a(
                  ^.href := "http://www.zio.dev",
                  <.img(
                    ^.src   := "https://zio.dev/img/navbar_brand.png",
                    ^.width := 50.px
                  )
                )
              ),
              <.td("ZIO"),
              <.td("Type-safe, composable asynchronous and concurrent programming for Scala")
            ),
            <.tr(
              <.td(
                <.a(
                  ^.href := "https://ghostdogpr.github.io/caliban",
                  <.img(
                    ^.src   := "https://ghostdogpr.github.io/caliban/caliban.svg",
                    ^.width := 50.px
                  )
                )
              ),
              <.td("Caliban"),
              <.td("Caliban is a purely functional library for creating GraphQL backends in Scala.")
            ),
            <.tr(
              <.td(
                <.a(
                  ^.href := "http://akka.io",
                  <.img(
                    ^.src   := "https://akka.io/resources/images/akka_full_color.svg",
                    ^.width := 50.px
                  )
                )
              ),
              <.td("Akka-http"),
              <.td(" Modern, fast, asynchronous, streaming-first HTTP server and client.")
            ),
            <.tr(
              <.td(
                <.a(
                  ^.href := "http://scala-slick.org",
                  <.img(
                    ^.src   := "https://scala-slick.org/resources/images/slick-logo.png",
                    ^.width := 50.px
                  )
                )
              ),
              <.td("Scala Slick"),
              <.td("Slick is a modern database query and access library for Scala.")
            ),
            <.tr(
              <.td(
                <.a(
                  ^.href := "http://www.mysql.com",
                  <.img(
                    ^.src   := "https://www.mysql.com/common/logos/logo-mysql-170x115.png",
                    ^.width := 50.px
                  )
                )
              ),
              <.td("MySql Database"),
              <.td("MySQL is an open-source relational database management system.")
            ),
            <.tr(
              <.td(
                <.a(
                  ^.href := "http://www.scala-js.org",
                  <.img(
                    ^.src   := "https://www.scala-js.org/assets/img/scala-js-logo.svg",
                    ^.width := 50.px
                  )
                )
              ),
              <.td("Scala.js"),
              <.td(
                "Scala.js is a compiler that compiles Scala source code to equivalent Javascript code"
              )
            ),
            <.tr(
              <.td(
                <.a(
                  ^.href := "https://reactjs.org",
                  <.img(
                    ^.src   := "https://upload.wikimedia.org/wikipedia/commons/a/a7/React-icon.svg",
                    ^.width := 50.px
                  )
                )
              ),
              <.td("React.js"),
              <.td("React is a JavaScript library for building user interfaces.")
            ),
            <.tr(
              <.td(
                <.a(
                  ^.href := "https://react.semantic-ui.com",
                  <.img(^.src := "https://semantic-ui.com/images/logo.png", ^.width := 50.px)
                )
              ),
              <.td("Semantic-UI"),
              <.td(
                "User interface is the language of the web. Good looking web component library."
              )
            ),
            <.tr(
              <.td(<.a(^.href := "https://scalablytyped.org/docs/readme.html", "ScalablyTyped")),
              <.td("ScalablyTyped"),
              <.td(
                "Showcasing the most amazing ScalablyTyped project, with over 8000 Scala.Js wrappers of javascript projects"
              )
            )
          )
        )
      )

    def render(S: State): VdomElement = {
      <.div(
        <.h1("Chuti.fun"),
        <.p("Copyright ©2020, Roberto Leibman"),
        renderBuildInfo,
        renderTechnologiesUsed,
        <.div(^.dangerouslySetInnerHtml := agradecimientos)
      )
    }
  }

  private val component = ScalaComponent
    .builder[Unit]
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply(): Unmounted[Unit, State, Backend] = component()

}
