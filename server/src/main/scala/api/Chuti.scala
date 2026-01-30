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

package api

import api.routes.*
import api.token.TokenHolder
import auth.{AuthServer, Session}
import chat.ChatService
import chuti.*
import dao.{RepositoryError, ZIORepository}
import game.GameService
import mail.Postman
import zio.*
import zio.http.*
import zio.logging.backend.SLF4J

import java.io.{PrintWriter, StringWriter}

object Chuti extends ZIOApp {

  override type Environment = ChutiEnvironment
  override val environmentTag: EnvironmentTag[Environment] = EnvironmentTag[ChutiEnvironment]

  // Configure ZIO to use SLF4J (logback) instead of console logging
  // This routes all ZIO logs through logback, which writes to both file and console
  override def bootstrap: ULayer[ChutiEnvironment] =
    Runtime.removeDefaultLoggers >>> SLF4J.slf4j >>> EnvironmentBuilder.live

  object TestRoutes extends AppRoutes[ChutiEnvironment, ChutiSession, GameError] {

    /** These routes represent the api, the are intended to be used thorough ajax-type calls they require a session
      */
    override def api: ZIO[
      ChutiEnvironment,
      GameError,
      Routes[ChutiEnvironment & ChutiSession, GameError]
    ] =
      ZIO.succeed(
        Routes(
          Method.GET / "api" / "test" -> handler((_: Request) => Handler.html(s"<html>Test API Ok!</html>")).flatten
        )
      )

    override def unauth: ZIO[ChutiEnvironment, GameError, Routes[ChutiEnvironment, GameError]] =
      ZIO.succeed(
        Routes(
          Method.GET / "unauth" / "unauthtest.html" -> handler((_: Request) =>
            Handler.html(s"<html>Test Unauth Ok!</html>")
          ).flatten
        )
      )

  }

  object AllTogether extends AppRoutes[ChutiEnvironment, ChutiSession, GameError] {

    private val routes: Seq[AppRoutes[ChutiEnvironment, ChutiSession, GameError]] =
      Seq(
        ChatRoutes,
        GameRoutes,
        TestRoutes,
        StaticRoutes
      )

    override def api: ZIO[
      ChutiEnvironment,
      GameError,
      Routes[ChutiEnvironment & ChutiSession, GameError]
    ] = ZIO.foreach(routes)(_.api).map(_.reduce(_ ++ _) @@ Middleware.debug)

    override def unauth: ZIO[ChutiEnvironment, GameError, Routes[ChutiEnvironment, GameError]] =
      ZIO.foreach(routes)(_.unauth).map(_.reduce(_ ++ _) @@ Middleware.debug)

  }

  def mapError(original: Cause[Throwable]): UIO[Response] = {
    lazy val contentTypeJson: Headers = Headers(Header.ContentType(MediaType.application.json).untyped)

    val squashed = original.squash
    val sw = StringWriter()
    val pw = PrintWriter(sw)
    squashed.printStackTrace(pw)

    val body = "Error in Chuti"
    // We really don't want details

    val status = squashed match {
      case _: NotFoundError                    => Status.NotFound
      case e: RepositoryError if e.isTransient => Status.BadGateway
      case _: GameError                        => Status.InternalServerError
      case _ => Status.InternalServerError
    }
    ZIO
      .logErrorCause("Error in Chuti", original).as(
        Response.apply(body = Body.fromString(body), status = status, headers = contentTypeJson)
      )
  }

  lazy val zapp: ZIO[ChutiEnvironment, GameError, Routes[ChutiEnvironment, Nothing]] = for {
    _                <- ZIO.log("Initializing Routes")
    authServer       <- ZIO.service[AuthServer[User, UserId, ConnectionId]]
    authServerApi    <- authServer.authRoutes
    authServerUnauth <- authServer.unauthRoutes
    unauth           <- AllTogether.unauth
    api              <- AllTogether.api
  } yield (
    ((api ++ authServerApi) @@ authServer.bearerSessionProvider) ++
      authServerUnauth ++ unauth
  )
    .handleErrorCauseZIO(mapError)

  override def run: ZIO[Environment & ZIOAppArgs & Scope, GameError, Unit] = {
    // Configure thread count using CLI
    for {
      config <- ZIO.serviceWithZIO[ConfigurationService](_.appConfig)
      // Run Flyway migrations first, before anything else
      _ <- FlywayMigration.runMigrations
      // Resume any stuck games after restart
      gameService <- ZIO.service[GameService]
      resumedCount <- gameService
        .resumeStuckGames()
        .catchAll(error => ZIO.logError(s"Error resuming stuck games: ${error.msg}").as(0))
      _ <- ZIO.logInfo(s"Resumed $resumedCount active games")
//      rateLimiter <- ZIO.service[RateLimiter]
//      _           <- RateLimiter.cleanupSchedule(rateLimiter)
//      _           <- ZIO.logInfo("Rate limiter cleanup schedule started")
      app <- zapp
      _   <- ZIO.logInfo(s"Starting application with config $config")
      server <- {
        // Configure server with request streaming enabled for large uploads
        val serverConfig = ZLayer.succeed(
          Server.Config.default
            .binding(config.chuti.http.hostName, config.chuti.http.port)
            .copy(requestStreaming = Server.RequestStreaming.Enabled)
        )

        Server
          .serve(app)
          .zipLeft(ZIO.logDebug(s"Server Started on ${config.chuti.http.port}"))
          .tapErrorCause(ZIO.logErrorCause(s"Server on port ${config.chuti.http.port} has unexpectedly stopped", _))
          .provideSome[Environment](serverConfig, Server.live)
          .foldCauseZIO(
            cause => ZIO.logErrorCause("err when booting server", cause),
            _ => ZIO.logError("app quit unexpectedly...")
          )
      }
    } yield server
  }

}
