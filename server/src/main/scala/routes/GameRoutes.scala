package routes

import api.Auth.RequestWithSession
import api.Chuti.Environment
import api.ChutiSession
import api.config.Config
import caliban.ZHttpAdapter
import dao.SessionProvider
import game.{GameApi, GameService}
import zhttp.http.*
import zio.ZIO
import zio.duration.*

object GameRoutes {

  import GameService.*

  val authRoute: Http[Environment, Throwable, RequestWithSession[ChutiSession], Response] = {
    Http.route[RequestWithSession[ChutiSession]] {
      case _ -> !! / "api" / "game" =>
        Http.collectHttp { req =>
          ZHttpAdapter.makeHttpService(interpreter)
            .provideSomeLayer[Environment, SessionProvider, Throwable](SessionProvider.layer(req.session))
        }
      case _ -> !! / "api" / "game" / "ws" =>
        Http.collectHttp { req =>
          ZHttpAdapter.makeWebSocketService(interpreter, skipValidation = false, keepAliveTime = Option(5.minutes))
            .provideSomeLayer[Environment, SessionProvider, Throwable](SessionProvider.layer(req.session))
        }
      case _ -> !! / "api" / "game" / "schema" =>
        Http.fromData(HttpData.fromString(GameApi.api.render))
      case _ -> !! / "api" / "game" / "graphiql" =>
        Http.fromFileZIO(
          for {
            config <- ZIO.service[Config.Service]
            staticContentDir = config.config.getString(s"${config.configKey}.staticContentDir")
          } yield new java.io.File(s"$staticContentDir/graphiql.html")
        )
    }
  }

}
