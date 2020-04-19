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

package components
/*
 * Copyright (c) 2019 Roberto Leibman -- All Rights Reserved
 *
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *
 */

package components

import java.time.LocalDate

import app.ChutiState
import japgolly.scalajs.react.React.Context
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{Callback, ReactMouseEventFrom}
import org.scalajs.dom.raw.{HTMLButtonElement, HTMLElement}
import org.scalajs.dom.window
import typings.semanticUiReact.buttonButtonMod.ButtonProps
import typings.semanticUiReact.components.{FormField, FormGroup, Label, Segment}
import typings.semanticUiReact.genericMod.{SemanticSIZES, SemanticWIDTHS}

//import scala.concurrent.Future
import scala.scalajs.js

/**
  * An abstract component trait from which all components in the app should derive. A good
  * place to put in global implicits, common code that should be in all pages, etc.
  */
trait ChutiComponent {
  val chutiContext: Context[ChutiState] = ChutiState.ctx

  def doPrint(
    event: ReactMouseEventFrom[HTMLButtonElement],
    data:  ButtonProps
  ): Callback =
    Callback {
      window.print()
    }

  def paragraphIt(
    str:    String,
    unique: String
  ): VdomArray =
    str
      .split("\n")
      .zipWithIndex
      .toVdomArray {
        case (s, index) =>
          <.p(
            ^.key                     := s"$unique$index",
            ^.marginTop               := 8.px,
            ^.marginBottom            := 8.px,
            ^.dangerouslySetInnerHtml := s
          )
      }

  def formatDate(date: LocalDate): String = {
    //    def this(year: Int, month: Int, date: Int = 1, hours: Int = 0,
    //             minutes: Int = 0, seconds: Int = 0, ms: Int = 0) = this()
    val jsDate = new js.Date(date.getYear, date.getMonthValue - 1, date.getDayOfMonth)
    jsDate.toLocaleDateString()
  }
}
