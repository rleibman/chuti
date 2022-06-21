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

package routes

import api.Chuti.Environment
import api.ChutiSession
import api.auth.Auth.RequestWithSession
import api.config.Config
import zhttp.http.*
import zio.*
import zio.blocking.Blocking

import java.nio.file.{Files, Paths as JPaths}

object StaticHTMLRoutes {

  lazy val authNotRequired: Set[String] = Set(
    "/login.html",
    "/css/chuti.css",
    "/css/app-sui-theme.css",
    "/chuti-login-opt-bundle.js",
    "/chuti-login-opt-bundle.js.map",
    "/css/app.css",
    "/images/favicon.ico",
    "/images/logo.png",
    "/favicon.ico",
    "/webfonts/fa-solid-900.woff2",
    "/webfonts/fa-solid-900.woff",
    "/webfonts/fa-solid-900.ttf"
  )

  private def file(
    fileName: String,
    request:  Request
  ): ZIO[Blocking, HttpError, HttpData] = {
    JPaths.get(fileName) match {
      case path: java.nio.file.Path if !Files.exists(path) => ZIO.fail(HttpError.NotFound(request.path))
      case path: java.nio.file.Path                        => ZIO.succeed(HttpData.fromFile(path.toFile))
    }
  }

  val unauthRoute: Http[Environment & Blocking, Throwable, Request, Response] = Http.collectZIO[Request] {
    case request @ (Method.GET -> !! / "loginForm") =>
      for {
        config <- ZIO.service[Config.Service]
        staticContentDir = config.config.getString(s"${config.configKey}.staticContentDir")
        data <- file(s"$staticContentDir/login.html", request)
      } yield Response(data = data)
    case request @ ((Method.GET | Method.PUT | Method.POST) -> "unauth" /: somethingElse) =>
      if (authNotRequired(somethingElse.toString())) {
        for {
          config <- ZIO.service[Config.Service]
          staticContentDir = config.config.getString(s"${config.configKey}.staticContentDir")
          data <- file(s"$staticContentDir/${somethingElse.toString()}", request)
        } yield Response(data = data)
      } else {
        ZIO.succeed(Response(Status.Unauthorized))
      }
  }

  val authRoute: Http[Environment & Blocking, Throwable, RequestWithSession[ChutiSession], Response] =
    Http.collectZIO[RequestWithSession[ChutiSession]] {
      case request @ Method.GET -> somethingElse if somethingElse == Path("/") =>
        for {
          config <- ZIO.service[Config.Service]
          staticContentDir = config.config.getString(s"${config.configKey}.staticContentDir")
          data <- file(s"$staticContentDir/index.html", request)
        } yield Response(data = data)
      case request @ Method.GET -> !! / "index.html" =>
        for {
          config <- ZIO.service[Config.Service]
          staticContentDir = config.config.getString(s"${config.configKey}.staticContentDir")
          data <- file(s"$staticContentDir/index.html", request)
        } yield Response(data = data)
      case request @ Method.GET -> somethingElse =>
        for {
          config <- ZIO.service[Config.Service]
          staticContentDir = config.config.getString(s"${config.configKey}.staticContentDir")
          data <- file(s"$staticContentDir/$somethingElse", request)
        } yield Response(data = data)
      case request => ZIO.fail(HttpError.NotFound(request.path))
    }

}
