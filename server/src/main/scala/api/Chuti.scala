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

package api

import api.auth.Auth
import api.auth.Auth.SessionStorage
import api.config.ConfigurationService
import api.routes.*
import api.routes.AuthRoutes.OpsService
import api.token.TokenHolder
import chat.ChatService
import chuti.{PagedStringSearch, User, UserId}
import dao.quill.QuillRepository
import dao.{CRUDOperations, Repository}
import game.GameService
import mail.{CourierPostman, Postman}
import util.ResponseExt
import zio.http.*
import zio.http.middleware.HttpMiddleware
import zhttp.service.*
import zhttp.service.server.ServerChannelFactory
import zio.logging.*
import zio.logging.backend.SLF4J
import zio.*

import java.util.Locale
import zio.json.*

object Chuti extends zio.ZIOAppDefault {

  lazy private val slf4jLogger = SLF4J.slf4j(LogFormat.line |-| LogFormat.cause)

  import api.auth.Auth.*
  import util.*

  type ChutiEnvironment = GameService & ChatService & ConfigurationService & Repository & Postman & TokenHolder &
    SessionStorage[
      ChutiSession,
      String
    ] & SessionTransport[ChutiSession]

  lazy private val configLayer: ULayer[ConfigurationService] = ZLayer.succeed(api.config.live)
  lazy private val postmanLayer = ZLayer.fromZIO(for { config <- ZIO.service[ConfigurationService]} yield CourierPostman.live(config))
  lazy private val uncachedRepository: ULayer[Repository] = configLayer >>> QuillRepository.uncached
  lazy private val repositoryLayer:    ULayer[Repository] = uncachedRepository >>> Repository.cached

  import scala.language.unsafeNulls

  final def logRequest: HttpMiddleware[Any, Nothing] =
    Middleware.interceptZIOPatch(req => zio.Clock.nanoTime.map(start => (req.method, req.url, start))) { case (response, (method, url, start)) =>
      for {
        end <- Clock.nanoTime
        _   <- ZIO.logInfo(s"${response.status.asJava.code()} $method ${url.encode} ${(end - start) / 1000000}ms")
      } yield Patch.empty
    }

  lazy val unauthRoute: RHttpApp[ChutiEnvironment & AuthRoutes.OpsService] =
    Seq(
      AuthRoutes.unauthRoute,
      StaticHTMLRoutes.unauthRoute
    ).reduce(_ ++ _)

  def authRoutes(sessionTransport: SessionTransport[ChutiSession]): HttpApp[ChutiEnvironment & AuthRoutes.OpsService, Nothing] =
    Seq(
      AuthRoutes.authRoute,
      GameRoutes.authRoute,
      ChatRoutes.route,
      StaticHTMLRoutes.authRoute
    )
      .reduce(_ ++ _)
      .catchAll {
        case e: HttpError =>
          e.printStackTrace()
          Http.succeed(Response.fromHttpError(e))
        case e: Throwable =>
          e.printStackTrace()
          Http.succeed(Response.fromHttpError(HttpError.InternalServerError(e.getMessage.nn, Some(e))))
      } @@ sessionTransport.auth

  lazy private val appZIO: URIO[ChutiEnvironment & AuthRoutes.OpsService, RHttpApp[ChutiEnvironment & AuthRoutes.OpsService]] = {
    for {
      sessionTransport <- ZIO.service[SessionTransport[ChutiSession]]
    } yield {
      ((
        unauthRoute
          ++
            authRoutes(sessionTransport)
      ) @@ logRequest)
        .tapErrorZIO { e =>
          ZIO.logErrorCause(s"Error", Cause.die(e))
        }
        .catchSome {
          case e: HttpError =>
            e.printStackTrace()
            Http.succeed(Response.fromHttpError(e))
          case e: Throwable =>
            e.printStackTrace()
            Http.succeed(Response.fromHttpError(HttpError.InternalServerError(e.getMessage.nn, Some(e))))
        }
    }
  }

  override def run: ZIO[Environment & ZIOAppArgs & Scope, Any, Any] = {
    ZIO.scoped(GameService.make().memoize.flatMap { gameServiceLayer =>
      ChatService.make().memoize.flatMap { chatServiceLayer =>
        (for {
          config <- ZIO.service[ConfigurationService]
          app    <- appZIO
          server = Server.bind(
            config.config.getString(s"${config.configKey}.host").nn,
            config.config.getInt(s"${config.configKey}.port").nn
          ) ++
            Server.enableObjectAggregator(maxRequestSize = 210241024) ++
            Server.app(app)
          started <- ZIO.scoped(
            server.make.flatMap(start =>
              // Waiting for the server to start
              ZIO.logInfo(s"Server started on port ${start.port}") *> ZIO.never
              // Ensures the server doesn't die after printing
            )
          )
        } yield started).exitCode
          .provide(
            slf4jLogger,
            ZLayer.succeed(Clock.ClockLive),
            gameServiceLayer,
            chatServiceLayer,
            configLayer,
            repositoryLayer,
            postmanLayer,
            TokenHolder.liveLayer,
            Auth.SessionStorage.tokenEncripted[ChutiSession],
            Auth.SessionTransport.cookieSessionTransport[ChutiSession],
            ZLayer.fromZIO(ZIO.service[Repository].map(_.userOperations)),
            ServerChannelFactory.auto,
            EventLoopGroup.auto()
          )
      }
    })
  }

}
