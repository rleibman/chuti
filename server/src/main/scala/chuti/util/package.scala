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

package chuti.util

import chuti.GameError
import io.netty.handler.codec.http.QueryStringDecoder
import zio.http.*
import zio.*
import zio.http.Body.ContentType
import zio.http.Header.HeaderType
import zio.http.Status.BadRequest
import zio.json.*
import zio.json.ast.Json
import zio.logging.*
import zio.prelude.NonEmptyList

import java.net.MalformedURLException
import java.util.Locale
import scala.jdk.CollectionConverters.*

  extension (request: Request) {

    def formData: ZIO[Any, Throwable, Map[String, String]] = {
      import scala.language.unsafeNulls

      val contentTypeStr = request.header(Header.ContentType).map(_.renderedValue).getOrElse("text/plain")

      for {
        str <- request.body.asString
        _ <- ZIO
          .fail(
            GameError(s"Trying to retrieve form data from a non-form post (content type = ${request.header(Header.ContentType)})")
          )
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
        forceLanguage
          .orElse(request.header(Header.AcceptLanguage).map(_.renderedValue)).getOrElse(
            availableLocales.head.toLanguageTag
          )
      )

      Locale.lookup(range, availableLocales.toList.asJava).nn
    }

    def bodyAs[E: JsonDecoder]: ZIO[Any, Throwable, E] =
      for {
        body <- request.body.asString
        obj <- ZIO
          .fromEither(body.fromJson[E])
          .tapError { e =>
            ZIO.logError(s"Error parsing body.\n$e\n$body")
          }
          .mapError(GameError.apply)
      } yield obj

  }

  object ResponseExt {

    def json(value: Json): Response = Response.json(value.toString)

    def json[A: JsonEncoder](value: A): Response = Response.json(value.toJson)

    def seeOther(location: String): ZIO[Any, GameError, Response] =
      for {
        url <- ZIO.fromEither(URL.decode(location)).mapError(GameError.apply)
      } yield Response(Status.SeeOther, Headers(Header.Location(url)))

  }


