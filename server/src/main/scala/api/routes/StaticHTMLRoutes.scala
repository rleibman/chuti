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

import api.{ChutiEnvironment, ChutiSession, ConfigurationService}
import zio.*
import zio.http.*

import java.nio.file.{Files, Paths as JPaths}

object StaticHTMLRoutes {

  lazy private val authNotRequired: Set[String] = Set(
    "login.html",
    "css/chuti.css",
    "css/app-sui-theme.css",
    "chuti-login-opt-bundle.js",
    "chuti-web-opt-bundle.js",
    "css/app.css",
    "images/favicon.ico",
    "images/logo.png",
    "favicon.ico",
    "webfonts/fa-solid-900.woff2",
    "webfonts/fa-solid-900.woff",
    "webfonts/fa-solid-900.ttf"
  )

  private def file(
    fileName: String,
    request:  Request
  ): IO[Exception, java.io.File] = {
    JPaths.get(fileName) match {
      case path: java.nio.file.Path if !Files.exists(path) => ZIO.fail(Exception(s"NotFound(${request.path})"))
      case path: java.nio.file.Path                        => ZIO.succeed(path.toFile.nn)
      case null => ZIO.fail(Exception(s"HttpError.InternalServerError(Could not find file $fileName))"))
    }
  }

  val unauthRoute: Routes[ChutiEnvironment, Throwable] = Routes(
    Method.GET / "loginForm" -> handler { (request: Request) =>
      Handler.fromFileZIO {
        for {
          staticContentDir <- ZIO
            .serviceWithZIO[ConfigurationService](_.appConfig).map(_.chuti.httpConfig.staticContentDir)
          data <- file(s"$staticContentDir/login.html", request)
        } yield data
      }
    }.flatten,
    Method.ANY / "unauth" / trailing -> handler {
      (
        path:    Path,
        request: Request
      ) =>
        val somethingElse = path.toString

        if (authNotRequired(somethingElse)) {
          Handler.fromFileZIO {
            for {
              staticContentDir <- ZIO
                .serviceWithZIO[ConfigurationService](_.appConfig).map(_.chuti.httpConfig.staticContentDir)
              data <- file(s"$staticContentDir/$somethingElse", request)
            } yield data
          }
        } else {
          Handler.error(Status.Unauthorized)
        }
    }.flatten
  )

  val authRoute: Routes[ChutiEnvironment & ChutiSession, Throwable] =
    Routes(
      Method.GET / "index.html" -> handler { (request: Request) =>
        Handler.fromFileZIO {
          for {
            config <- ZIO.serviceWithZIO[ConfigurationService](_.appConfig)
            staticContentDir = config.chuti.httpConfig.staticContentDir
            file <- file(s"$staticContentDir/index.html", request)
          } yield file
        }
      }.flatten,
      Method.GET / "" -> handler { (request: Request) =>
        Handler.fromFileZIO {
          for {
            config <- ZIO.serviceWithZIO[ConfigurationService](_.appConfig)
            staticContentDir = config.chuti.httpConfig.staticContentDir
            file <- file(s"$staticContentDir/index.html", request)
          } yield file
        }
      }.flatten
//      Method.GET / trailing -> handler {
//        (
//          path:    Path,
//          request: Request
//        ) =>
//          val somethingElse = path.toString
//          Handler.fromFileZIO {
//            if (somethingElse == "/" || somethingElse.isEmpty) {
//              for {
//                config <- ZIO.serviceWithZIO[ConfigurationService](_.appConfig)
//                staticContentDir = config.chuti.httpConfig.staticContentDir
//                file <- file(s"$staticContentDir/index.html", request)
//              } yield file
//            } else {
//              for {
//                config <- ZIO.serviceWithZIO[ConfigurationService](_.appConfig)
//                staticContentDir = config.chuti.httpConfig.staticContentDir
//                file <- file(s"$staticContentDir/$somethingElse", request)
//              } yield file
//            }
//          }
//      }.flatten
    )

}
