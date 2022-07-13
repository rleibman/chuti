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
import chuti.{PagedStringSearch, User, UserId}
import dao.quill.QuillRepository
import dao.{CRUDOperations, Repository}
import game.GameService
import io.circe.generic.auto.*
import io.circe.{Decoder, DecodingFailure, Encoder}
import mail.{CourierPostman, Postman}
import routes.*
import zhttp.http.*
import zhttp.http.middleware.HttpMiddleware
import zhttp.service.*
import zhttp.service.server.ServerChannelFactory
import zio.logging.*
import zio.{Clock, Console, *}

import java.util.Locale

object Chuti extends zio.ZIOAppDefault {

  import api.auth.Auth.*

  given Decoder[Locale] =
    Decoder.decodeString.map(s =>
      Locale.forLanguageTag(s) match {
        case l: Locale => l
        case null => throw DecodingFailure(s"invalid locale $s", List.empty)
      }
    )

  given Encoder[Locale] = Encoder.encodeString.contramap(_.toString)

  override type Environment = GameService & ChatService & Config & Repository & Postman & TokenHolder &
    SessionStorage[
      ChutiSession,
      String
    ] & SessionTransport[ChutiSession]
  private val configLayer: ULayer[Config] = ZLayer.succeed(api.config.live)
  private val postmanLayer = ZLayer.fromZIO(for {config <- ZIO.service[Config]} yield CourierPostman.live(config))
  private val uncachedRepository: ULayer[Repository] = configLayer >>> QuillRepository.uncached
  private val repositoryLayer: ULayer[Repository] = uncachedRepository >>> Repository.cached

  import scala.language.unsafeNulls

  final def logRequest: HttpMiddleware[Any, Nothing] =
    Middleware.interceptZIOPatch(req => zio.Clock.nanoTime.map(start => (req.method, req.url, start))) { case (response, (method, url, start)) =>
      for {
        end <- Clock.nanoTime
        _ <- ZIO.logInfo(s"${response.status.asJava.code()} $method ${url.encode} ${(end - start) / 1000000}ms")
      } yield Patch.empty
    }

  val unauthRoute: RHttpApp[Environment & AuthRoutes.OpsService] =
    Seq(
      AuthRoutes.unauthRoute,
      StaticHTMLRoutes.unauthRoute
    ).reduce(_ ++ _)

  def authRoutes(sessionTransport: SessionTransport[ChutiSession]): HttpApp[Environment & AuthRoutes.OpsService & Clock, Nothing] =
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
          Http.succeed(Response.fromHttpError(HttpError.InternalServerError(e.getMessage.nn, Some(e))))
      } @@ sessionTransport.auth

  private val appZIO: URIO[Environment & AuthRoutes.OpsService & Clock, RHttpApp[Environment & AuthRoutes.OpsService & Clock]] = for {
    sessionTransport <- ZIO.service[SessionTransport[ChutiSession]]
  } yield {
    ((
      unauthRoute ++ authRoutes(sessionTransport)
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

//  override def bootstrap: ZLayer[ZIOAppArgs with Scope, Any, Environment] = ZLayer.make[Environment]
  override def run: ZIO[Environment with ZIOAppArgs with Scope, Any, Any] = {
    ZIO.scoped(GameService.make().memoize.flatMap { gameServiceLayer =>
      ChatService.make().memoize.flatMap { chatServiceLayer =>

        (for {
          config <- ZIO.service[Config.Service]
          app <- appZIO
          server = Server.bind(
            config.config.getString(s"${config.configKey}.host").nn,
            config.config.getInt(s"${config.configKey}.port").nn
          ) ++
            Server.enableObjectAggregator(maxRequestSize = 210241024) ++
            Server.app(app)
          started <- ZIO.scoped(server.make.flatMap(start =>
            // Waiting for the server to start
            Console.printLine(s"Server started on port ${start.port}") *> ZIO.never
            // Ensures the server doesn't die after printing
          ))
        } yield started).exitCode
          .provide(
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
