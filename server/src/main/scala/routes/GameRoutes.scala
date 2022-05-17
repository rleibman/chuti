package routes

import api.Chuti.Environment
import api.config.Config
import caliban.ZHttpAdapter
import game.{GameApi, GameService}
import dao.SessionProvider
import zhttp.http.*
import zio.ZIO
import zio.stream.ZStream
import zio.duration.*

object GameRoutes {

  import GameService.*

  val route: ZIO[Environment & SessionProvider, Throwable, Http[Environment & SessionProvider, Throwable, Request, Response]] = for {
    config <- ZIO.service[Config.Service]
  } yield {
    val staticContentDir =
      config.config.getString(s"${config.configKey}.staticContentDir")

    Http.route[Request] {
      case _ -> !! / "api" / "game" =>
        ZHttpAdapter.makeHttpService(interpreter)
      case _ -> !! / "api" / "game" / "ws" =>
        ZHttpAdapter.makeWebSocketService(interpreter, skipValidation = false, keepAliveTime = Option(5.minutes))
      case _ -> !! / "api" / "game" / "schema" =>
        Http.fromData(HttpData.fromString(GameApi.api.render))
      case _ -> !! / "api" / "game" / "graphiql" =>
        Http.fromStream(ZStream.fromResource(s"$staticContentDir/graphiql.html"))
    }
  }

}
