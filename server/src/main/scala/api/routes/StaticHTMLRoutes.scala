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
