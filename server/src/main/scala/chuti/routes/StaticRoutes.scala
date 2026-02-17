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

package chuti.routes

import StaticRoutes.file
import auth.*
import chuti.*
import chuti.api.{ChutiEnvironment, ChutiSession, ConfigurationService, NotFoundError}
import zio.*
import zio.http.*
import zio.stream.ZStream

import java.nio.file.{Files, Paths as JPaths}

object StaticRoutes extends AppRoutes[ChutiEnvironment, ChutiSession, GameError] {

  private def file(
    fileName: String
  ): IO[GameError, java.io.File] = {
    JPaths.get(fileName) match {
      case path: java.nio.file.Path if !Files.exists(path) =>
        ZIO.fail(NotFoundError(path, s"File not found: $fileName"))
      case path: java.nio.file.Path => ZIO.succeed(path.toFile.nn)
      case null => ZIO.fail(GameError(s"HttpError.InternalServerError(Could not find file $fileName))"))
    }
  }

  private def getHeaders(path: String): Headers = {
    // Determine content type
    val contentType = path match {
      case p if p.endsWith(".svg")    => "image/svg+xml"
      case p if p.endsWith(".png")    => "image/png"
      case p if p.endsWith(".jpg")    => "image/jpeg"
      case p if p.endsWith(".jpeg")   => "image/jpeg"
      case p if p.endsWith(".gif")    => "image/gif"
      case p if p.endsWith(".webp")   => "image/webp"
      case p if p.endsWith(".woff")   => "font/woff"
      case p if p.endsWith(".woff2")  => "font/woff2"
      case p if p.endsWith(".ttf")    => "font/ttf"
      case p if p.endsWith(".eot")    => "application/vnd.ms-fontobject"
      case p if p.endsWith(".mp3")    => "audio/mpeg"
      case p if p.endsWith(".wav")    => "audio/wav"
      case p if p.endsWith(".ogg")    => "audio/ogg"
      case p if p.endsWith(".m4a")    => "audio/mp4"
      case p if p.endsWith(".aac")    => "audio/aac"
      case p if p.endsWith(".js")     => "application/javascript"
      case p if p.endsWith(".js.map") => "application/json"
      case p if p.endsWith(".css")    => "text/css"
      case p if p.endsWith(".html")   => "text/html"
      case _                          => "application/octet-stream"
    }

    val isImage = path.endsWith(".png") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
      path.endsWith(".svg") || path.endsWith(".gif") || path.endsWith(".webp")
    val isFont = path.endsWith(".woff") || path.endsWith(".woff2") || path.endsWith(".ttf") || path.endsWith(".eot")
    val isSound = path.endsWith(".mp3") || path.endsWith(".wav") || path.endsWith(".ogg") ||
      path.endsWith(".m4a") || path.endsWith(".aac")
    val isJs = path.endsWith(".js") || path.endsWith(".js.map")
    val isCss = path.endsWith(".css")

    if (isImage || isFont || isSound) {
      // Cache images, fonts, and sounds for 1 year (they rarely change)
      // public: can be cached by browsers and CDNs
      // immutable: tells browser the file will never change at this URL
      Headers(
        Header.ContentType(MediaType.parseCustomMediaType(contentType).get),
        Header.Custom("Cache-Control", "public, max-age=31536000, immutable")
      )
    } else if (isJs || isCss) {
      // Cache JS and CSS for 1 day (may change with deployments)
      Headers(
        Header.ContentType(MediaType.parseCustomMediaType(contentType).get),
        Header.Custom("Cache-Control", "public, max-age=86400")
      )
    } else {
      // HTML and other files: cache for 5 minutes
      Headers(
        Header.ContentType(MediaType.parseCustomMediaType(contentType).get),
        Header.Custom("Cache-Control", "public, max-age=300, must-revalidate")
      )
    }
  }

  override def unauth: ZIO[ChutiEnvironment, GameError, Routes[ChutiEnvironment, GameError]] =
    ZIO.succeed(
      Routes(
        Method.GET / Root -> handler {
          (
            _: Request
          ) =>
            Handler
              .fromFileZIO {
                for {
                  config <- ZIO.serviceWithZIO[ConfigurationService](_.appConfig)
                  staticContentDir = config.chuti.http.staticContentDir
                  file <- file(s"$staticContentDir/index.html")
                } yield file
              }.mapError(GameError(_))
              .map(response => response.updateHeaders(_ => getHeaders("index.html")))
        }.flatten,
        Method.GET / trailing -> handler {
          (
            path: Path,
            _:    Request
          ) =>

            // You might want to restrict the files that could come back, but then again, you may not
            val somethingElse = path.toString
            Handler
              .fromFileZIO {
                for {
                  config <- ZIO.serviceWithZIO[ConfigurationService](_.appConfig)
                  staticContentDir = config.chuti.http.staticContentDir
                  file <- file(s"$staticContentDir/$somethingElse")
                  cacheHeaders = getHeaders(somethingElse)
                } yield file
              }.mapError(GameError(_))
              .map(response => response.updateHeaders(_ => getHeaders(somethingElse)))
              .contramap[(Path, Request)](_._2)
        }
      )
    )

}
