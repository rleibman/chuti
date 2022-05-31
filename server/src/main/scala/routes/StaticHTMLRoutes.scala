package routes

import api.Chuti.Environment
import api.config.Config
import zhttp.http.*
import zio.*
import zio.blocking.Blocking

import java.nio.file.{Files, Paths as JPaths}

object StaticHTMLRoutes {

  val route: Http[Environment & Blocking, Throwable, Request, Response] = {
    Http.collectZIO[Request] { request =>
      for {
        config <- ZIO.service[Config.Service]
        staticContentDir = config.config.getString(s"${config.configKey}.staticContentDir")
        res <- {
          def file(
                    fileName: String,
                    request: Request
                  ): ZIO[Blocking, HttpError, HttpData] = {
            JPaths.get(fileName) match {
              case path: java.nio.file.Path if !Files.exists(path) => ZIO.fail(HttpError.NotFound(request.path))
              case path: java.nio.file.Path => ZIO.succeed(HttpData.fromFile(path.toFile))
            }
          }

          request match {
            case Method.GET -> !! =>
              for {
                data <- file(s"$staticContentDir/index.html", request)
              } yield Response(data = data)
            case Method.GET -> somethingElse =>
              for {
                data <- file(s"$staticContentDir/$somethingElse", request)
              } yield Response(data = data)
            case _ => ZIO.fail(HttpError.NotFound(request.path))
          }

        }} yield res
      }
    }
  //  }

}
