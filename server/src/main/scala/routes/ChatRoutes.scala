package routes

import api.Chuti.Environment
import api.config.Config
import caliban.ZHttpAdapter
import chat.{ChatApi, ChatService}
import dao.SessionProvider
import zhttp.http.*
import zio.ZIO
import zio.stream.ZStream
import zio.duration.*

object ChatRoutes {

  import ChatService.*

  val route: ZIO[Environment & SessionProvider, Throwable, Http[Environment & SessionProvider, Throwable, Request, Response]] = for {
    config <- ZIO.service[Config.Service]
  } yield {
    val staticContentDir =
      config.config.getString(s"${config.configKey}.staticContentDir")

    Http.route[Request] {
      case _ -> !! / "api" / "chat" =>
        ZHttpAdapter.makeHttpService(interpreter)
      case _ -> !! / "api" / "chat" / "ws" =>
        ZHttpAdapter.makeWebSocketService(interpreter, skipValidation = false, keepAliveTime = Option(5.minutes))
      case _ -> !! / "api" / "chat" / "schema" =>
        Http.fromData(HttpData.fromString(ChatApi.api.render))
      case _ -> !! / "api" / "chat" / "graphiql" =>
        Http.fromStream(ZStream.fromResource(s"$staticContentDir/graphiql.html"))
    }
  }

}
