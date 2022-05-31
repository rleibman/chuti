package api

import api.Auth.SecretKey
import api.config.Config
import api.token.TokenHolder
import chat.ChatService
import chat.ChatService.ChatService
import dao.Repository
import dao.quill.QuillRepository
import game.GameService
import game.GameService.GameService
import io.circe.Decoder
import io.circe.generic.auto.*
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
import zio.magic.*

import java.util.Locale


object Chuti extends zio.App {
  import Auth.*
  implicit val localeDecoder: Decoder[Locale] = Decoder.decodeString.map(Locale.forLanguageTag)

  private val secretKey: URIO[Config, SecretKey] = for {
    config <- ZIO.service[Config.Service]
  } yield SecretKey(config.config.getString(s"${config.configKey}.sessionServerSecret"))


  type Environment = Blocking & Console & Clock & GameService & ChatService & Logging & Config & Repository & Postman & TokenHolder
  private val configLayer: ULayer[Config] = ZLayer.succeed(api.config.live)
  private val postmanLayer: URLayer[Config, Postman] = (for {
    config <- ZIO.service[Config.Service]
  } yield CourierPostman.live(config)).toLayer
  private val loggingLayer: ULayer[Logging] = Slf4jLogger.make((_, b) => b)
  private val repositoryLayer = QuillRepository.live

  final def logRequest: HttpMiddleware[Logging & Clock, Nothing] = Middleware.interceptZIOPatch(req => zio.clock.nanoTime.map(start => (req.method, req.url, start))) {
    case (response, (method, url, start)) =>
      for {
        end <- clock.nanoTime
        _ <- Logging.info(s"${response.status.asJava.code()} ${method} ${url.encode} ${(end - start) / 1000000}ms")
      } yield Patch.empty
  }

  val unauthRoute: RHttpApp[Environment] =
    Seq(
      AuthRoutes.unauthRoute,
      StaticHTMLRoutes.route
    ).reduce(_ ++ _)

  def authRoutes(key: SecretKey): HttpApp[Environment, Nothing] =
    (Seq(
      AuthRoutes.authRoute,
      GameRoutes.authRoute,
      ChatRoutes.authRoute
    )
      .reduce(_ ++ _)
      .catchAll {
        case e: HttpError => Http.succeed(Response.fromHttpError(e))
        case e: Throwable => Http.succeed(Response.fromHttpError(HttpError.InternalServerError(e.getMessage, Some(e))))
      }) @@ Auth.auth[ChutiSession](key)

  private val appZIO: URIO[Environment, RHttpApp[Environment]] = for {
    key <- secretKey
  } yield {
    ((authRoutes(key) ++ unauthRoute) @@ logRequest)
      .tapErrorZIO {
        e: Throwable => Logging.throwable(s"Error", e)
      }
      .catchSome {
        case e: HttpError => Http.succeed(Response.fromHttpError(e))
        case e: Throwable => Http.succeed(Response.fromHttpError(HttpError.InternalServerError(e.getMessage, Some(e))))
      }
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    GameService.make().memoize.use { gameServiceLayer =>
      ChatService.make().memoize.use { chatServiceLayer =>
        (for {
          config <- ZIO.service[Config.Service]
          app <- appZIO
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
            repositoryLayer,
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
