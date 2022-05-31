package routes

import api.Auth.RequestWithSession
import api.Chuti.Environment
import api.ChutiSession
import api.config.Config
import caliban.ZHttpAdapter
import chat.{ChatApi, ChatService}
import dao.SessionProvider
import zhttp.http.*
import zio.ZIO
import zio.duration.*

object ChatRoutes {

  import ChatService.*

  val authRoute: Http[Environment, Throwable, RequestWithSession[ChutiSession], Response] = {
    Http.route[RequestWithSession[ChutiSession]] {
      case _ -> !! / "api" / "chat" =>
        Http.collectHttp[RequestWithSession[ChutiSession]] { req =>
          ZHttpAdapter.makeHttpService(interpreter)
            .provideSomeLayer[Environment, SessionProvider, Throwable](SessionProvider.layer(req.session))
        }
      case _ -> !! / "api" / "chat" / "ws" =>
        Http.collectHttp[RequestWithSession[ChutiSession]] { req =>
          ZHttpAdapter.makeWebSocketService(interpreter, skipValidation = false, keepAliveTime = Option(5.minutes))
            .provideSomeLayer[Environment, SessionProvider, Throwable](SessionProvider.layer(req.session))
        }
      case _ -> !! / "api" / "chat" / "schema" =>
        Http.fromData(HttpData.fromString(ChatApi.api.render))
      case _ -> !! / "api" / "chat" / "graphiql" =>
        Http.fromFileZIO(
          for {
            config <- ZIO.service[Config.Service]
            staticContentDir = config.config.getString(s"${config.configKey}.staticContentDir")
          } yield new java.io.File(s"$staticContentDir/graphiql.html")
        )
    }
  }

}
