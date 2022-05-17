package routes

import api.Chuti.Environment
import api.config.Config
import zhttp.http.*
import zio.*
import zio.blocking.Blocking
import zio.stream.ZStream

import java.nio.file.{Files, Paths as JPaths}

object StaticHTMLRoutes {

  val route: ZIO[Environment, Throwable, Http[Environment, Throwable, Request, Response]] = {
    for {
      config   <- ZIO.service[Config.Service]
      blocking <- ZIO.service[Blocking.Service]
    } yield {
      def file(
        fileName: String,
        request:  Request
      ): IO[HttpError, HttpData] = {
        JPaths.get(fileName) match {
          case path: java.nio.file.Path if !Files.exists(path) => ZIO.fail(HttpError.NotFound(request.path))
          case path: java.nio.file.Path =>
            ZIO.succeed(HttpData.fromStream {
              ZStream.fromFile(path).provideLayer(ZLayer.succeed(blocking))
            })
        }
      }

      Http.collectZIO[Request] { request =>
        val staticContentDir: String = config.config.getString(s"${config.configKey}.staticContentDir")
        (request match {
          case Method.GET -> !! =>
            for {
              data <- file(s"$staticContentDir/index.html", request)
            } yield Response(data = data)
          case Method.GET -> somethingElse =>
            for {
              data <- file(s"$staticContentDir/$somethingElse", request)
            } yield Response(data = data)
          case _ => ZIO.fail(HttpError.NotFound(request.path))
        })
      }
    }
  }

}
