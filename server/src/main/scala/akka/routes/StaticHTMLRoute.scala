package akka.routes

import akka.http.scaladsl.server.directives.ContentTypeResolver
import akka.http.scaladsl.server.{Directives, Route}
import api.config
import zio.{UIO, ZIO}

/** A route used to spit out static content
  */
trait StaticHTMLRoute extends Directives {

  val staticContentDir: String =
    config.live.config.getString(s"${config.live.configKey}.staticContentDir")

  override def getFromDirectory(
    directoryName:     String
  )(implicit resolver: ContentTypeResolver
  ): Route = extractUnmatchedPath(unmatchedPath => getFromFile(s"$staticContentDir/$unmatchedPath"))

  def htmlRoute: UIO[Route] =
    ZIO.succeed {
      extractLog { log =>
        pathEndOrSingleSlash {
          get {
            log.info("GET /")
            log.debug(s"GET $staticContentDir/index.html")
            getFromFile(s"$staticContentDir/index.html")
          }
        } ~
          get {
            extractUnmatchedPath { path =>
              log.debug(s"GET $path")
              encodeResponse {
                getFromDirectory(staticContentDir)
              }
            }
          }
      }
    }

}
