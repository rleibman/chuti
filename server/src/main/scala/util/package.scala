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

import cats.data.NonEmptyList
import io.netty.handler.codec.http.QueryStringDecoder
import zio.http.*
import zio.*
import zio.logging.*
import zio.json.*

import java.util.Locale
import scala.jdk.CollectionConverters.*

package object util {

  extension (request: Request) {

    def formData: ZIO[Any, Throwable, Map[String, String]] = {
      import scala.language.unsafeNulls

      for {
        str <- request.body.asString
        _ <- ZIO
          .fail(
            Exception(
              s"Trying to retrieve form data from a non-form post (content type = ${request.header(Header.ContentType)})"
            )
          )
          .when(!request.header(Header.ContentType).contains(MediaType.application.`x-www-form-urlencoded`.toString))
      } yield str
        .split("&").map(_.split("="))
        .collect { case Array(k: String, v: String) =>
          QueryStringDecoder.decodeComponent(k) -> QueryStringDecoder.decodeComponent(v)
        }
        .toMap
    }

    def queryParams: QueryParams = request.url.queryParams

    /** Uses https://datatracker.ietf.org/doc/html/rfc7231#section-5.3.5
      */
    def preferredLocale(
      availableLocales: NonEmptyList[Locale],
      forceLanguage:    Option[String]
    ): Locale = {
      val range = Locale.LanguageRange.parse(
        forceLanguage.getOrElse(request.header(Header.AcceptLanguage).map(_.renderedValue).getOrElse(availableLocales.head.toLanguageTag))
      )

      Locale.lookup(range, availableLocales.toList.asJava).nn
    }

    def bodyAs[E: JsonDecoder]: ZIO[Any, Throwable, E] =
      for {
        body <- request.body.asString
        obj <- ZIO
          .fromEither(body.fromJson[E])
          .mapError(e => Exception(e))
          .tapError { e =>
            ZIO.logErrorCause(s"Error parsing body.\n$body", Cause.die(e))
          }
      } yield obj

  }

  object ResponseExt {

    def json[A: JsonEncoder](value: A): Response = Response.json(value.toJson)
    
  }

  given JsonDecoder[Locale] =
    JsonDecoder.string.mapOrFail(s =>
      Locale.forLanguageTag(s) match {
        case l: Locale => Right(l)
        case null => Left(s"invalid locale $s")
      }
    )

  given JsonEncoder[Locale] = JsonEncoder.string.contramap(_.toString)

}
