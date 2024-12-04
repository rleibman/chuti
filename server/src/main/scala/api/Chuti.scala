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

import api.auth.Auth
import api.auth.Auth.{SessionStorage, SessionTransport}
import api.token.TokenHolder
import chat.ChatService
import chuti.{GameException, PagedStringSearch, User, UserId}
import dao.quill.QuillRepository
import dao.{CRUDOperations, Repository}
import game.GameService
import io.circe.generic.auto.*
import io.circe.{Decoder, DecodingFailure, Encoder}
import mail.{CourierPostman, Postman}
import routes.*
import zio.http.*
import zio.logging.*
import zio.logging.backend.SLF4J
import zio.*

import java.util.concurrent.TimeUnit
import java.util.Locale

object Chuti extends ZIOApp {

  override type Environment = ChutiEnvironment
  override val environmentTag: EnvironmentTag[Environment] = EnvironmentTag[ChutiEnvironment]

  override def bootstrap: ULayer[ChutiEnvironment] = EnvironmentBuilder.live.orDie

  import api.auth.Auth.*

  import scala.language.unsafeNulls

  lazy val unauthRoute: Routes[ChutiEnvironment, Throwable] =
    Seq(
      AuthRoutes.unauthRoute,
      StaticHTMLRoutes.unauthRoute
    ).reduce(_ ++ _) @@ Middleware.debug

  def authRoutes(
    sessionTransport: SessionTransport[ChutiSession]
  ): ZIO[Any, Throwable, Routes[GameService & ChatService & ChutiEnvironment, Throwable]] = {
    for {
      gameRoutes <- GameRoutes.authRoute
      chatRoutes <- ChatRoutes.authRoute
    } yield Seq(
      AuthRoutes.authRoute,
      gameRoutes,
      chatRoutes,
      StaticHTMLRoutes.authRoute
    ).reduce(_ ++ _) @@ sessionTransport.bearerAuthWithContext @@ Middleware.debug

  }

  def mapError(e: Cause[Throwable]): UIO[Response] = {
    lazy val contentTypeJson: Headers = Headers(Header.ContentType(MediaType.application.json).untyped)
    e.squash match {
      case e: GameException =>
        val body =
          s"""{
            "exceptionMessage": ${e.getMessage},
            "stackTrace": [${e.getStackTrace.nn.map(s => s"\"${s.toString}\"").mkString(",")}]
          }"""

        ZIO.logError(body).as(Response.apply(body = Body.fromString(body), status = Status.BadGateway, headers = contentTypeJson))
      case e =>
        val body =
          s"""{
            "exceptionMessage": ${e.getMessage},
            "stackTrace": [${e.getStackTrace.nn.map(s => s"\"${s.toString}\"").mkString(",")}]
          }"""
        ZIO.logError(body).as(Response.apply(body = Body.fromString(body), status = Status.InternalServerError, headers = contentTypeJson))

    }
  }

  lazy private val zapp: ZIO[Environment, Throwable, Routes[Environment & GameService & ChatService, Nothing]] =
    for {
      _                <- ZIO.log("Initializing Routes")
      sessionTransport <- ZIO.service[SessionTransport[ChutiSession]]
      authRoute        <- authRoutes(sessionTransport)
    } yield (
      (unauthRoute ++ authRoute) @@ Middleware.debug
    ).handleErrorCauseZIO(mapError)

  override def run: ZIO[Environment & ZIOAppArgs & Scope, Throwable, ExitCode] = {
    ZIO.scoped(GameService.make().memoize.flatMap { gameServiceLayer =>
      ChatService.make().memoize.flatMap { chatServiceLayer =>
        (for {
          config <- ZIO.serviceWithZIO[ConfigurationService](_.appConfig)
          app    <- zapp
          _      <- ZIO.logInfo(s"Starting application with config $config")
          server <- {
            val serverConfig = ZLayer.succeed(
              Server.Config.default
                .binding(config.chuti.httpConfig.hostName, config.chuti.httpConfig.port)
            )

            Server
              .serve(app)
              .zipLeft(ZIO.logDebug(s"Server Started on ${config.chuti.httpConfig.port}"))
              .tapErrorCause(ZIO.logErrorCause(s"Server on port ${config.chuti.httpConfig.port} has unexpectedly stopped", _))
              .provideSome[Environment](serverConfig, Server.live, gameServiceLayer, chatServiceLayer)
              .foldCauseZIO(
                cause => ZIO.logErrorCause("err when booting server", cause).exitCode,
                _ => ZIO.logError("app quit unexpectedly...").exitCode
              )
          }
        } yield server)
      }
    })
  }

//  def runa: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] = {
//    ZIO.scoped(GameService.make().memoize.flatMap { gameServiceLayer =>
//      ChatService.make().memoize.flatMap { chatServiceLayer =>
//        (for {
//          config <- ZIO.service[Config.Service]
//          app    <- zapp
//          server = Server.bind(
//            config.config.getString(s"${config.configKey}.host").nn,
//            config.config.getInt(s"${config.configKey}.port").nn
//          ) ++
//            Server.enableObjectAggregator(maxRequestSize = 210241024) ++
//            Server.app(app)
//          started <- ZIO.scoped(
//            server.make.flatMap(start =>
//              // Waiting for the server to start
//              Console.printLine(s"Server started on port ${start.port}") *> ZIO.never
//              // Ensures the server doesn't die after printing
//            )
//          )
//        } yield started).exitCode
//          .provide(
//            slf4jLogger,
//            ZLayer.succeed(Clock.ClockLive),
//            gameServiceLayer,
//            chatServiceLayer,
//            configLayer,
//            repositoryLayer,
//            postmanLayer,
//            TokenHolder.liveLayer,
//            Auth.SessionStorage.tokenEncripted[ChutiSession],
//            Auth.SessionTransport.cookieSessionTransport[ChutiSession],
//            ZLayer.fromZIO(ZIO.service[Repository].map(_.userOperations)),
//            ServerChannelFactory.auto,
//            EventLoopGroup.auto()
//          )
//      }
//    })
//  }

}
