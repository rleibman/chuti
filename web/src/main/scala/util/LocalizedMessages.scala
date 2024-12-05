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

package util

import java.util.Locale
import scala.annotation.nowarn

abstract class LocalizedMessages {

  case class MessageBundle(
    locale: String,
    map:    Map[String, String]
  )
  def bundles: Map[String, MessageBundle]

  @nowarn
  def localized(
    key:          String,
    default:      String = ""
  )(using locale: Locale = Locale("es", "MX")
  ): String = {
    println(s"locale $locale, key = $key")
    (for {
      // TODO, find the most specific match, first by language-country, then by language or use whatever is found.
      bundle <- bundles.get(locale.getLanguage.nn)
      str    <- bundle.map.get(key)
    } yield str).getOrElse(default)
  }

}
