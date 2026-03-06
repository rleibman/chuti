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
import japgolly.scalajs.react.*
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^.*

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

  class Backend($ : BackendScope[Unit, State]) {

    def renderBuildInfo: VdomElement =
      <.div(
        <.h2("Versiones"),
        <.table(
          <.tbody(
            <.tr(<.td("Chuti"), <.td(BuildInfo.version)),
            <.tr(<.td("Scala"), <.td(BuildInfo.scalaVersion)),
            <.tr(<.td("Sbt"), <.td(BuildInfo.sbtVersion)),
          ),
        ),
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
                    ^.width := 50.px,
                  ),
                ),
              ),
              <.td("Scala"),
              <.td("Functional and object oriented language"),
            ),
            <.tr(
              <.td(
                <.a(
                  ^.href := "http://www.zio.dev",
                  <.img(
                    ^.src   := "https://zio.dev/img/navbar_brand.png",
                    ^.width := 50.px,
                  ),
                ),
              ),
              <.td("ZIO"),
              <.td("Type-safe, composable asynchronous and concurrent programming for Scala"),
            ),
            <.tr(
              <.td(
                <.a(
                  ^.href := "https://ghostdogpr.github.io/caliban",
                  <.img(
                    ^.src   := "https://ghostdogpr.github.io/caliban/caliban.svg",
                    ^.width := 50.px,
                  ),
                ),
              ),
              <.td("Caliban"),
              <.td("Caliban is a purely functional library for creating GraphQL backends in Scala."),
            ),
            <.tr(
              <.td(
                <.a(
                  ^.href := "http://www.scala-js.org",
                  <.img(
                    ^.src   := "https://www.scala-js.org/assets/img/scala-js-logo.svg",
                    ^.width := 50.px,
                  ),
                ),
              ),
              <.td("Scala.js"),
              <.td(
                "Scala.js is a compiler that compiles Scala source code to equivalent Javascript code",
              ),
            ),
            <.tr(
              <.td(
                <.a(
                  ^.href := "https://reactjs.org",
                  <.img(
                    ^.src   := "https://upload.wikimedia.org/wikipedia/commons/a/a7/React-icon.svg",
                    ^.width := 50.px,
                  ),
                ),
              ),
              <.td("React.js"),
              <.td("React is a JavaScript library for building user interfaces."),
            ),
            <.tr(
              <.td(
                <.a(
                  ^.href := "https://react.semantic-ui.com",
                  <.img(^.src := "https://semantic-ui.com/images/logo.png", ^.width := 50.px),
                ),
              ),
              <.td("Semantic-UI"),
              <.td(
                "User interface is the language of the web. Good looking web component library.",
              ),
            ),
            <.tr(
              <.td(<.a(^.href := "https://scalablytyped.org/docs/readme.html", "ScalablyTyped")),
              <.td("ScalablyTyped"),
              <.td(
                "Showcasing the most amazing ScalablyTyped project, with over 8000 Scala.Js wrappers of javascript projects",
              ),
            ),
            <.tr(
              <.td(
                <.a(
                  ^.href := "https://mariadb.org",
                  <.img(
                    ^.src   := "https://mariadb.com/wp-content/uploads/2019/11/mariadb-logo-vert_blue-transparent.png",
                    ^.width := 50.px,
                  ),
                ),
              ),
              <.td("MariaDB"),
              <.td("Open source relational database, used for game and user data persistence."),
            ),
            <.tr(
              <.td(
                <.a(
                  ^.href := "https://getquill.io",
                  <.img(
                    ^.src   := "https://getquill.io/img/quill.png",
                    ^.width := 50.px,
                  ),
                ),
              ),
              <.td("Quill"),
              <.td("Compile-time Language Integrated Queries for Scala."),
            ),
            <.tr(
              <.td(
                <.a(
                  ^.href := "https://docs.langchain4j.dev",
                  "LangChain4j",
                ),
              ),
              <.td("LangChain4j"),
              <.td("Java library for integrating large language models, used for AI-powered bots."),
            ),
          ),
        ),
      )

    def render(): VdomElement = {
      <.div(
        <.h1("Chuti.fun"),
        <.p("Copyright ©2025, Roberto Leibman"),
        renderBuildInfo,
        renderTechnologiesUsed,
        <.div(^.dangerouslySetInnerHtml := agradecimientos),
      )
    }

  }

  private val component = ScalaComponent
    .builder[Unit]
    .initialState(State())
    .backend[Backend](Backend(_))
    .render(_.backend.render())
    .build

  def apply(): Unmounted[Unit, State, Backend] = component()

}
