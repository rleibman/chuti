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
import io.circe.Decoder
import io.circe.parser.parse
import io.circe.syntax.*
import io.netty.handler.codec.http.QueryStringDecoder
import zhttp.http.{HeaderNames, HeaderValues, Headers, HttpError, Request, Response, Status}
import zio.{Cause, ZIO}
import zio.logging.Logging

import scala.jdk.CollectionConverters.*
import java.util.Locale

package object util {

  implicit class RequestExt(request: Request) {

    val formData: ZIO[Any, Throwable, Map[String, String]] = {
      for {
        str <- request.bodyAsString
        _ <- ZIO
          .fail(
            HttpError.BadRequest(
              s"Trying to retrieve form data from a non-form post (content type = ${request.headerValue(HeaderNames.contentType)})"
            )
          )
          .when(!request.headerValue(HeaderNames.contentType).contains(HeaderValues.applicationXWWWFormUrlencoded.toString))
      } yield str
        .split("&")
        .map(_.split("="))
        .collect { case Array(k: String, v: String) =>
          QueryStringDecoder.decodeComponent(k) -> QueryStringDecoder.decodeComponent(v)
        }
        .toMap
    }

    def queryParams: Map[String, List[String]] = request.url.queryParams

    /** Uses https://datatracker.ietf.org/doc/html/rfc7231#section-5.3.5
      */
    def preferredLocale(
      availableLocales: NonEmptyList[Locale],
      forceLanguage:    Option[String]
    ): Locale = {
      val range = Locale.LanguageRange.parse(
        forceLanguage.getOrElse(request.headerValue(HeaderNames.acceptLanguage).getOrElse(availableLocales.head.toLanguageTag))
      )

      Locale.lookup(range, availableLocales.toList.asJava)
    }

    def bodyAs[E: Decoder]: ZIO[Logging, Throwable, E] =
      for {
        body <- request.bodyAsString
        obj <- ZIO
          .fromEither(parse(body).flatMap(_.as[E]))
          .tapError { e =>
            Logging.throwable(s"Error parsing body.\n$body", e)
          }
          .mapError(e => HttpError.BadRequest(e.getMessage))
      } yield obj

  }

  object ResponseExt {

    def json(value: io.circe.Json): Response = Response.json(value.noSpaces)

    def json[A: io.circe.Encoder](value: A): Response = Response.json(value.asJson.noSpaces)

    def seeOther(location: String): Response = Response(Status.SeeOther, Headers.location(location))

  }

}
