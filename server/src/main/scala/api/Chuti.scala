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
import routes.*
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

object Chuti extends zio.App {

  type Environment = Blocking & Console & Clock & GameService & ChatService & Logging & Config & Repository & Postman & TokenHolder
  private val configLayer: ULayer[Config] = ZLayer.succeed(api.config.live)
  private val postmanLayer: URLayer[Config, Postman] = (for {
    config <- ZIO.service[Config.Service]
  } yield CourierPostman.live(config)).toLayer
  private val loggingLayer: ULayer[Logging] = Slf4jLogger.make((_, b) => b)

//  val logRequest: Http[Environment, Throwable, Request, Response] = Http.collectZIO[Request](req => Logging.info(s"${req.method.toString()} request to ${req.url.encode}").ignore)
//
//
  private val appZIO: ZIO[Environment, Throwable, Http[Environment, Throwable, Request, Response]] = for {
    allRoutes <- ZIO.collectAll(
      Seq(
        //      AuthRoutes.route,
        //      ChatRoutes.route,
        //      CRUDRoutes.route,
        //      GameRoutes.route,
        //      ModelRoutes.route,
        StaticHTMLRoutes.route
      )
    )
  } yield allRoutes
    .reduce(_ ++ _)
    .contramapZIO { request: Request => Logging.info(request.toString()).as(request) }
    .tapErrorZIO(Logging.throwable(s"Error", _))
    .catchSome { case e: HttpError => Http.succeed(Response.fromHttpError(e)) }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    GameService.make().memoize.use { gameServiceLayer =>
      ChatService.make().memoize.use { chatServiceLayer =>
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
              console.putStrLn(s"Server started on port ${start.port}")

              // Ensures the server doesn't die after printing
                *> ZIO.never
            )
        } yield started).exitCode
          .injectCustom(
            QuillRepository.live,
            TokenHolder.mockLayer,
            loggingLayer,
            postmanLayer,
            configLayer,
            gameServiceLayer,
            chatServiceLayer,
            ServerChannelFactory.auto,
            EventLoopGroup.auto()
          )
      }
    }
  }

}
