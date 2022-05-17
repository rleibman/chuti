package akka.routes

import akka.HasActorSystem
import akka.http.scaladsl.server.{Directives, Route}
import api.config.Config
import caliban.AkkaHttpAdapter
import chat.*
import dao.{Repository, SessionProvider}
import zio.clock.Clock
import zio.console.Console
import zio.duration.*
import zio.logging.Logging
import zio.{RIO, ZIO}
import sttp.tapir.json.circe.*

import scala.concurrent.ExecutionContextExecutor

trait ChatRoute extends Directives with HasActorSystem {

  implicit lazy val executionContext: ExecutionContextExecutor = actorSystem.dispatcher
  import ChatService.*

  def route: RIO[Console & Clock & ChatService & Repository & SessionProvider & Logging & Config, Route] = {
    for {
      config <- ZIO.service[Config.Service]
      runtime <-
        ZIO
          .runtime[
            Console & Clock & ChatService & Repository & SessionProvider & Logging & Config
          ]
    } yield {
      val staticContentDir =
        config.config.getString(s"${config.configKey}.staticContentDir")

      implicit val r: zio.Runtime[Console & Clock & ChatService & Repository & SessionProvider & Logging & Config] = runtime

      pathPrefix("chat") {
        pathEndOrSingleSlash {
          AkkaHttpAdapter.makeHttpService(
            interpreter
          )
        } ~
          path("schema") {
            get(complete(ChatApi.api.render))
          } ~
          path("ws") {
            AkkaHttpAdapter.makeWebSocketService(
              interpreter,
              skipValidation = false,
              keepAliveTime = Option(5.minutes)
            )
          } ~ path("graphiql") {
            getFromFile(s"$staticContentDir/graphiql.html")
          }
      }
    }
  }

}
