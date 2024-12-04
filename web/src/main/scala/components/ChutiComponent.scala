/*
 * Copyright (c) 2024 Roberto Leibman
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package components

import java.time.LocalDate

import scala.scalajs.js

/** An abstract component trait from which all components in the app should derive. A good place to put in global implicits, common code that should
  * be in all pages, etc.
  */
trait ChutiComponent {

  def formatDate(date: LocalDate): String = {
    //    def this(year: Int, month: Int, date: Int = 1, hours: Int = 0,
    //             minutes: Int = 0, seconds: Int = 0, ms: Int = 0) = this()
    val jsDate = new js.Date(date.getYear, date.getMonthValue - 1, date.getDayOfMonth)
    jsDate.toLocaleDateString()
  }

}
