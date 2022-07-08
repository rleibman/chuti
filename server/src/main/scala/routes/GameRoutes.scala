/*
 * Copyright 2020 Roberto Leibman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package routes

import api.Chuti.Environment
import api.ChutiSession
import api.auth.Auth.RequestWithSession
import api.config.Config
import caliban.ZHttpAdapter
import dao.SessionContext
import game.{GameApi, GameService}
import zhttp.http.*
import zio.ZIO
import zio.duration.*

object GameRoutes {

  import GameService.*

  val authRoute: Http[Environment, Throwable, RequestWithSession[ChutiSession], Response] = {
    Http.collectHttp[RequestWithSession[ChutiSession]] {
      case _ -> !! / "api" / "game" =>
        Http.collectHttp { req =>
          ZHttpAdapter
            .makeHttpService(interpreter)
            .provideSomeLayer[Environment, SessionContext, Throwable](SessionContext.live(req.session.get))
        }
      case _ -> !! / "api" / "game" / "ws" =>
        Http.collectHttp { req =>
          ZHttpAdapter
            .makeWebSocketService(interpreter, skipValidation = false, keepAliveTime = Option(5.minutes))
            .provideSomeLayer[Environment, SessionContext, Throwable](SessionContext.live(req.session.get))
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
