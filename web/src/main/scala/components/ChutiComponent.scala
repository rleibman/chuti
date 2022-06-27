/*
 * Copyright (c) 2019 Roberto Leibman -- All Rights Reserved
 *
 *  Unauthorized copying of this file, via any medium is strictly prohibited
 *  Proprietary and confidential
 *
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
