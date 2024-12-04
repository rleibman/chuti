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

import cats.data.NonEmptyList
import chuti.GameException
import io.circe.Decoder
import io.circe.parser.parse
import io.circe.syntax.*
import io.netty.handler.codec.http.QueryStringDecoder
import zio.http.*
import zio.*
import zio.http.Body.ContentType
import zio.http.Header.HeaderType
import zio.http.Status.BadRequest
import zio.logging.*

import java.net.MalformedURLException
import java.util.Locale
import scala.jdk.CollectionConverters.*

package object util {

  extension (request: Request) {

    def formData: ZIO[Any, Throwable, Map[String, String]] = {
      import scala.language.unsafeNulls

      val contentTypeStr = request.header(Header.ContentType).map(_.renderedValue).getOrElse("text/plain")

      for {
        str <- request.body.asString
        _ <- ZIO
          .fail(GameException(s"Trying to retrieve form data from a non-form post (content type = ${request.header(Header.ContentType)})"))
          .when(!contentTypeStr.contains(MediaType.application.`x-www-form-urlencoded`.subType))
      } yield str
        .split("&").map(_.split("="))
        .collect { case Array(k: String, v: String) =>
          QueryStringDecoder.decodeComponent(k) -> QueryStringDecoder.decodeComponent(v)
        }
        .toMap
    }

    /** Uses https://datatracker.ietf.org/doc/html/rfc7231#section-5.3.5
      */
    def preferredLocale(
      availableLocales: NonEmptyList[Locale],
      forceLanguage:    Option[String]
    ): Locale = {
      val range = Locale.LanguageRange.parse(
        forceLanguage.orElse(request.header(Header.AcceptLanguage).map(_.renderedValue)).getOrElse(availableLocales.head.toLanguageTag)
      )

      Locale.lookup(range, availableLocales.toList.asJava).nn
    }

    def bodyAs[E: Decoder]: ZIO[Any, Throwable, E] =
      for {
        body <- request.body.asString
        obj <- ZIO
          .fromEither(parse(body).flatMap(_.as[E]))
          .tapError { e =>
            ZIO.logErrorCause(s"Error parsing body.\n$body", Cause.die(e))
          }
          .mapError(GameException.apply)
      } yield obj

  }

  object ResponseExt {

    def json(value: io.circe.Json): Response = Response.json(value.noSpaces)

    def json[A: io.circe.Encoder](value: A): Response = Response.json(value.asJson.noSpaces)

    def seeOther(location: String): ZIO[Any, GameException, Response] =
      for {
        url <- ZIO.fromEither(URL.decode(location)).mapError(GameException.apply)
      } yield Response(Status.SeeOther, Headers(Header.Location(url)))

  }

}
