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

package util

import java.util.Locale

abstract class LocalizedMessages {

  case class MessageBundle(
    locale: String,
    map:    Map[String, String]
  )
  def bundles: Map[String, MessageBundle]

  def localized(
    key:          String,
    default:      String = ""
  )(using locale: Locale = new Locale("es", "MX")
  ): String = {
    println(s"locale $locale, key = $key")
    (for {
      // TODO, find the most specific match, first by language-country, then by language or use whatever is found.
      bundle <- bundles.get(locale.getLanguage.nn)
      str    <- bundle.map.get(key)
    } yield str).getOrElse(default)
  }

}
