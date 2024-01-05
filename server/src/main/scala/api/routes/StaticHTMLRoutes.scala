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

import api.Chuti.ChutiEnvironment
import api.ChutiSession
import api.auth.Auth.RequestWithSession
import api.config.ConfigurationService
import zio.http.*
import zio.*
import zio.http.codec.HttpCodec.NotFound

import java.nio.file.{Files, Paths as JPaths}

object StaticHTMLRoutes {

  private lazy val authNotRequired: Set[String] = Set(
    "login.html",
    "css/chuti.css",
    "css/app-sui-theme.css",
    "chuti-login-opt-bundle.js",
    "chuti-login-opt-bundle.js.map",
    "css/app.css",
    "images/favicon.ico",
    "images/logo.png",
    "favicon.ico",
    "webfonts/fa-solid-900.woff2",
    "webfonts/fa-solid-900.woff",
    "webfonts/fa-solid-900.ttf"
  )

//  val meHandler: Handler[Any, Nothing, String, Response] = Handler.fromFunction { (request: String) => Response.text(s"Hello $request") }

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

  val unauthRoute: Routes[ConfigurationService, Throwable] = Routes(
    Method.GET / "loginForm" -> handler { (request: Request) =>
      Handler.fromFileZIO {
        for {
          config <- ZIO.service[ConfigurationService]
          staticContentDir = config.config.getString(s"${config.configKey}.staticContentDir").nn
          file <- file(s"$staticContentDir/login.html", request)
        } yield file
      }
    }.flatten,
    Method.ANY / "unauth" / string("somethingElse") -> handler { (somethingElse: String, request: Request) =>
      if (authNotRequired(somethingElse)) {
        Handler.fromFileZIO {
          for {
            config <- ZIO.service[ConfigurationService]
            staticContentDir = config.config.getString(s"${config.configKey}.staticContentDir").nn
            file <- file(s"$staticContentDir/$somethingElse", request)
          } yield file
        }
      } else {
        Handler.error(Status.Unauthorized)
      }
    }.flatten
  )

  val authRoute: Routes[ConfigurationService, Throwable] =
    Routes(
      Method.GET / "index.html" -> handler { (request: Request) =>
        Handler.fromFileZIO {
          for {
            config <- ZIO.service[ConfigurationService]
            staticContentDir = config.config.getString(s"${config.configKey}.staticContentDir").nn
            file <- file(s"$staticContentDir/index.html", request)
          } yield file
        }
      }.flatten,
      Method.GET / string("somethingElse") -> handler { (somethingElse: String, request: Request) =>
        Handler.fromFileZIO {
          if (somethingElse == "/") {
            for {
              config <- ZIO.service[ConfigurationService]
              staticContentDir = config.config.getString(s"${config.configKey}.staticContentDir").nn
              file <- file(s"$staticContentDir/index.html", request)
            } yield file
          } else {
            for {
              config <- ZIO.service[ConfigurationService]
              staticContentDir = config.config.getString(s"${config.configKey}.staticContentDir").nn
              file <- file(s"$staticContentDir/$somethingElse", request)
            } yield file
          }
        }
      }.flatten
    )
}
