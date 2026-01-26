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
import auth.*
import chuti.*
import zio.*
import zio.http.*

import java.nio.file.{Files, Paths as JPaths}

object StaticRoutes extends AppRoutes[ChutiEnvironment, ChutiSession, GameError] {

  private def file(
    fileName: String,
    request:  Request
  ): IO[GameError, java.io.File] = {
    JPaths.get(fileName) match {
      case path: java.nio.file.Path if !Files.exists(path) =>
        ZIO.fail(NotFoundError(s"File not found: ${request.path}"))
      case path: java.nio.file.Path => ZIO.succeed(path.toFile.nn)
      case null => ZIO.fail(GameError(s"HttpError.InternalServerError(Could not find file $fileName))"))
    }
  }
  private def file(
    fileName: String
  ): IO[GameError, java.io.File] = {
    JPaths.get(fileName) match {
      case path: java.nio.file.Path if !Files.exists(path) => ZIO.fail(NotFoundError(s"File not found: $fileName"))
      case path: java.nio.file.Path                        => ZIO.succeed(path.toFile.nn)
      case null => ZIO.fail(GameError(s"HttpError.InternalServerError(Could not find file $fileName))"))
    }
  }
  override def unauth: ZIO[ChutiEnvironment, GameError, Routes[ChutiEnvironment, GameError]] =
    ZIO.succeed(
      Routes(
        Method.GET / "playerView" -> handler {
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
        }.flatten,
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
                } yield file
              }.mapError(GameError(_))
        }.flatten
      )
    )

}
