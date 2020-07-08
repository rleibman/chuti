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

package web

import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.stream.scaladsl._
import api.Api
import api.config.Config
import api.token.TokenHolder
import chat.ChatService
import core.{Core, CoreActors}
import dao.{MySQLDatabaseProvider, Repository, SlickRepository}
import game.GameService
import mail.CourierPostman
import mail.Postman.Postman
import zio.logging.slf4j.Slf4jLogger
import zio.logging.{Logging, log}
import zio.{ULayer, ZIO, ZLayer}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

// $COVERAGE-OFF$ This is actual code that we can't test, so we shouldn't report on it
/**
  * Provides the web server (spray-can) for the REST api in ``Api``, using the actor system
  * defined in ``Core``.
  *
  * You may sometimes wish to construct separate ``ActorSystem`` for the web server machinery.
  * However, for this simple application, we shall use the same ``ActorSystem`` for the
  * entire application.
  *
  * Benefits of separate ``ActorSystem`` include the ability to use completely different
  * configuration, especially when it comes to the threading model.
  */
trait Web {
  this: Api with CoreActors with Core =>

  private val akkaLog: LoggingAdapter = akka.event.Logging.getLogger(actorSystem, this)

  private val config: Config.Service = api.config.live

  private val serverSource: Source[Http.IncomingConnection, Future[Http.ServerBinding]] =
    Http()
      .bind(
        interface = config.config.getString(s"${config.configKey}.host"),
        port = config.config.getInt(s"${config.configKey}.port")
      )

  private val shutdownDeadline: FiniteDuration = 30.seconds

  private val binding: ZIO[Any, Throwable, Http.ServerBinding] = {
    ZLayer.succeed(CourierPostman.live(config))
    val configLayer: ULayer[Config] = ZLayer.succeed(config)
    val postmanLayer: ZLayer[Config, Nothing, Postman] = ZLayer.fromEffect {
      for {
        config <- ZIO.service[Config.Service]
      } yield CourierPostman.live(config)
    }
    val loggingLayer: ULayer[Logging] = Slf4jLogger.make((_, b) => b)
    val repositoryLayer
      : ULayer[Repository] = (configLayer ++ loggingLayer) >>> MySQLDatabaseProvider.liveLayer >>> SlickRepository.live

    GameService.make().memoize.use { gameServiceLayer =>
      ChatService.make().memoize.use { chatServiceLayer =>
        val fullLayer = zio.ZEnv.live ++
          (loggingLayer >>> chatServiceLayer) ++
          gameServiceLayer ++
          loggingLayer ++
          configLayer ++
          repositoryLayer ++
          (configLayer >>> postmanLayer) ++
          ZLayer.succeed(TokenHolder.live)
        (for {
          _      <- log.info("Initializing Routes")
          routes <- routes
          _      <- log.info("Initializing Binding")
          binding <- {
            ZIO.fromFuture { implicit ec =>
              serverSource
                .to(Sink.foreach { connection => // foreach materializes the source
                  akkaLog.debug("Accepted new connection from " + connection.remoteAddress)
                  // ... and then actually handle the connection
                  try {
                    connection.flow.joinMat(routes)(Keep.both).run()
                    ()
                  } catch {
                    case NonFatal(e) =>
                      akkaLog.error(e, "Could not materialize handling flow for {}", connection)
                      throw e
                  }
                }).run().map { b =>
                  sys.addShutdownHook {
                    b.terminate(hardDeadline = shutdownDeadline)
                      .onComplete { _ =>
                        actorSystem.terminate()
                        akkaLog.info("Termination completed")
                      }
                    akkaLog.info("Received termination signal")
                  }
                  b
                }

            }
          }
        } yield binding).provideLayer(fullLayer)
      }
    }
  }

  def start: Future[Nothing] = {
    implicit val ec: ExecutionContext = actorSystem.dispatcher
    lazy val webRuntime = zio.Runtime.default
    webRuntime.unsafeRunToFuture(binding).failed.map { ex =>
      akkaLog.error("server binding error:", ex)
      actorSystem.terminate()
      sys.exit(1)
    }
  }
}
// $COVERAGE-ON$
