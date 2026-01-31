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

package api.routes

import api.*
import api.routes.StaticRoutes.file
import auth.*
import chuti.*
import zio.*
import zio.http.*
import zio.stream.ZStream

import java.nio.file.{Files, Paths as JPaths}

object StaticRoutes extends AppRoutes[ChutiEnvironment, ChutiSession, GameError] {

  private def file(
    fileName: String
  ): IO[GameError, java.io.File] = {
    JPaths.get(fileName) match {
      case path: java.nio.file.Path if !Files.exists(path) => ZIO.fail(NotFoundError(s"File not found: $fileName"))
      case path: java.nio.file.Path                        => ZIO.succeed(path.toFile.nn)
      case null => ZIO.fail(GameError(s"HttpError.InternalServerError(Could not find file $fileName))"))
    }
  }

  private def getCacheHeaders(path: String): Headers = {
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
        Header.Custom("Cache-Control", "public, max-age=31536000, immutable")
      )
    } else if (isJs || isCss) {
      // Cache JS and CSS for 1 day (may change with deployments)
      Headers(
        Header.Custom("Cache-Control", "public, max-age=86400")
      )
    } else {
      // HTML and other files: cache for 5 minutes
      Headers(
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
              .map(response => response.updateHeaders(_ => getCacheHeaders("index.html")))
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
                  cacheHeaders = getCacheHeaders(somethingElse)
                  _ <- ZIO.logDebug(s"Serving static file: $somethingElse with cache headers")
                } yield file
              }.mapError(GameError(_))
              .map(response => response.updateHeaders(_ => getCacheHeaders(somethingElse)))
              .contramap[(Path, Request)](_._2)
        }
      )
    )

}
