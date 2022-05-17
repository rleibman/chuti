package api

import api.config.Config
import api.token.TokenHolder
import chat.ChatService
import chat.ChatService.ChatService
import dao.Repository
import dao.quill.QuillRepository
import game.GameService
import game.GameService.GameService
import mail.CourierPostman
import mail.Postman.Postman
import zhttp.http.*
import zhttp.service.*
import zhttp.service.server.ServerChannelFactory
import zio.*
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.Console
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger
import zio.magic.*
import zio.stream.ZStream

import java.nio.file.Paths as JPaths
import scala.util.Try

object Chuti extends zio.App {
  private type Environment = Blocking & Console & Clock & GameService & ChatService & Logging & Config & Repository & Postman & TokenHolder
  private val configLayer: ULayer[Config] = ZLayer.succeed(api.config.live)
  private val postmanLayer: URLayer[Config, Postman] = (for {
    config <- ZIO.service[Config.Service]
  } yield CourierPostman.live(config)).toLayer
  private val loggingLayer: ULayer[Logging] = Slf4jLogger.make((_, b) => b)

  private val helloWorldRouteZIO: ZIO[Environment, Throwable, Http[Environment, Throwable, Request, Response]] = ???
  private val htmlRouteZIO: ZIO[Environment, Throwable, Http[Environment, Throwable, Request, Response]] = {
    for {
      config <- ZIO.service[Config.Service]
      blocking <- ZIO.service[Blocking.Service]
    } yield {
      def file(fileName: String): HttpData =
        HttpData.fromStream {
          JPaths.get(fileName) match {
            case path: java.nio.file.Path => ZStream.fromFile(path).provideLayer(ZLayer.succeed(blocking))
            case _ => ZStream.fail(new Exception("path is Null!!"))
          }
        }

      Http.collect[Request] { request =>
        val staticContentDir: String = config.config.getString(s"${config.configKey}.staticContentDir")
        request match {
          case Method.GET -> !! => Response(data = file(s"$staticContentDir/index.html"))
          case Method.GET -> !! / somethingElse => Response(data = file(somethingElse))
        }
      }
    }
  }
  private val apiRouteZIO: ZIO[Environment, Throwable, Http[Environment, Throwable, Request, Response]] = ???
  private val unauthRouteZIO: ZIO[Environment, Throwable, Http[Environment, Throwable, Request, Response]] = ???

  private val appZIO: ZIO[Environment, Throwable, Http[Environment, Throwable, Request, Response]] = for {
    unauthRoute <- unauthRouteZIO
    helloWorldRoute <- helloWorldRouteZIO
    htmlRoute <- htmlRouteZIO
    apiRoute <- apiRouteZIO
  } yield unauthRoute ++ helloWorldRoute ++ apiRoute ++ htmlRoute

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    GameService.make().memoize.use { gameServiceLayer =>
      ChatService.make().memoize.use { chatServiceLayer =>
        (for {
          config <- ZIO.service[Config.Service]
          app <- appZIO
          server = Server.bind(config.config.getString(s"${config.configKey}.host"), config.config.getInt(s"${config.configKey}.port")) ++
            Server.enableObjectAggregator(maxRequestSize = 210241024) ++
            Server.app(app)
          started <- server.make
            .use(start =>
              // Waiting for the server to start
              console.putStrLn(s"Server started on port ${start.port}")

                // Ensures the server doesn't die after printing
                *> ZIO.never,
            )
        } yield started)
          .exitCode
          .injectCustom(
            QuillRepository.live,
            TokenHolder.dbLayer,
            loggingLayer,
            postmanLayer,
            configLayer,
            gameServiceLayer,
            chatServiceLayer,
            ServerChannelFactory.auto,
            EventLoopGroup.auto())
      }
    }
  }
}
