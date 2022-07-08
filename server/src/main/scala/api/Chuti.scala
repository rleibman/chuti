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
import api.config.Config
import api.token.TokenHolder
import chat.ChatService
import chat.ChatService.ChatService
import chuti.{PagedStringSearch, User, UserId}
import dao.quill.QuillRepository
import dao.{CRUDOperations, Repository}
import game.GameService
import game.GameService.GameService
import io.circe.generic.auto.*
import io.circe.{Decoder, Encoder}
import mail.CourierPostman
import mail.Postman.Postman
import routes.*
import zhttp.http.*
import zhttp.http.middleware.HttpMiddleware
import zhttp.service.*
import zhttp.service.server.ServerChannelFactory
import zio.*
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger

import java.util.Locale

object Chuti extends zio.App {

  import api.auth.Auth.*

  given Decoder[Locale] = Decoder.decodeString.map(Locale.forLanguageTag)
  given Encoder[Locale] = Encoder.encodeString.contramap(_.toString)

  type Environment = Blocking & Console & Clock & GameService & ChatService & Logging & Config & Repository & Postman & TokenHolder &
    SessionStorage[
      ChutiSession,
      String
    ] & SessionTransport[ChutiSession]
  private val configLayer: ULayer[Config] = ZLayer.succeed(api.config.live)
  private val postmanLayer: URLayer[Config, Postman] = (for {
    config <- ZIO.service[Config.Service]
  } yield CourierPostman.live(config)).toLayer
  private val loggingLayer: ULayer[Logging] = Slf4jLogger.make((_, b) => b)
  private val repositoryLayer = QuillRepository.cached
  private val authOpsLayer: ZLayer[Repository, Nothing, Has[CRUDOperations[User, UserId, PagedStringSearch]]] =
    ZIO.service[Repository.Service].map(_.userOperations).toLayer

  final def logRequest: HttpMiddleware[Logging & Clock, Nothing] =
    Middleware.interceptZIOPatch(req => zio.clock.nanoTime.map(start => (req.method, req.url, start))) { case (response, (method, url, start)) =>
      for {
        end <- clock.nanoTime
        _   <- Logging.info(s"${response.status.asJava.code()} $method ${url.encode} ${(end - start) / 1000000}ms")
      } yield Patch.empty
    }

  val unauthRoute: RHttpApp[Environment & AuthRoutes.OpsService] =
    Seq(
      AuthRoutes.unauthRoute,
      StaticHTMLRoutes.unauthRoute
    ).reduce(_ ++ _)

  def authRoutes(sessionTransport: SessionTransport.Service[ChutiSession]): HttpApp[Environment & AuthRoutes.OpsService, Nothing] =
    Seq(
      AuthRoutes.authRoute,
      GameRoutes.authRoute,
      ChatRoutes.authRoute,
      StaticHTMLRoutes.authRoute
    )
      .reduce(_ ++ _)
      .catchAll {
        case e: HttpError =>
          e.printStackTrace()
          Http.succeed(Response.fromHttpError(e))
        case e: Throwable =>
          e.printStackTrace()
          Http.succeed(Response.fromHttpError(HttpError.InternalServerError(e.getMessage, Some(e))))
      } @@ sessionTransport.auth

  private val appZIO: URIO[Environment & AuthRoutes.OpsService, RHttpApp[Environment & AuthRoutes.OpsService]] = for {
    sessionTransport <- ZIO.service[SessionTransport.Service[ChutiSession]]
  } yield {
    ((
      unauthRoute ++ authRoutes(sessionTransport)
    ) @@ logRequest)
      .tapErrorZIO {
        Logging.throwable(s"Error", _)
      }
      .catchSome {
        case e: HttpError =>
          e.printStackTrace()
          Http.succeed(Response.fromHttpError(e))
        case e: Throwable =>
          e.printStackTrace()
          Http.succeed(Response.fromHttpError(HttpError.InternalServerError(e.getMessage, Some(e))))
      }
  }

  val clockLayer = Clock.live
  val sessionStorageLayer = Auth.SessionStorage.tokenEncripted[ChutiSession]

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    GameService.make().memoize.use { gameServiceLayer =>
      ChatService.make().memoize.use { chatServiceLayer =>
        val fullLayer: ULayer[Environment & AuthRoutes.OpsService & EventLoopGroup & ServerChannelFactory] = ???
        (for {
          config <- ZIO.service[Config.Service]
          app    <- appZIO
          server = Server.bind(
            config.config.getString(s"${config.configKey}.host"),
            config.config.getInt(s"${config.configKey}.port")
          ) ++
            Server.enableObjectAggregator(maxRequestSize = 210241024) ++
            Server.app(app)
          started <- server.make
            .use(start =>
              // Waiting for the server to start
              console.putStrLn(s"Server started on port ${start.port}") *> ZIO.never
              // Ensures the server doesn't die after printing
            )
        } yield started).exitCode
          .provideCustomLayer(fullLayer)
//            (configLayer >>> repositoryLayer) ++
//              TokenHolder.mockLayer ++
//              loggingLayer ++
//              (configLayer >>> postmanLayer) ++
//              configLayer ++
//              gameServiceLayer ++
//              (loggingLayer >>> chatServiceLayer) ++
//              ServerChannelFactory.auto ++
//              EventLoopGroup.auto() ++
//              ((configLayer >>> repositoryLayer) >>> authOpsLayer) ++
//              (clockLayer >>> sessionStorageLayer) ++
//              ((clockLayer >>> sessionStorageLayer) >>> Auth.SessionTransport.cookieSessionTransport[ChutiSession])
      }
    }
  }

}
