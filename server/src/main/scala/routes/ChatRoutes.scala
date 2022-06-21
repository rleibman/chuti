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
import chat.{ChatApi, ChatService}
import dao.SessionProvider
import zhttp.http.*
import zio.ZIO
import zio.duration.*

object ChatRoutes {

  import ChatService.*

  val authRoute: Http[Environment, Throwable, RequestWithSession[ChutiSession], Response] = {
    Http.collectHttp[RequestWithSession[ChutiSession]] {
      case _ -> !! / "api" / "chat" =>
        Http.collectHttp[RequestWithSession[ChutiSession]] { req =>
          ZHttpAdapter
            .makeHttpService(interpreter)
            .provideSomeLayer[Environment, SessionProvider, Throwable](SessionProvider.layer(req.session.get))
        }
      case _ -> !! / "api" / "chat" / "ws" =>
        Http.collectHttp[RequestWithSession[ChutiSession]] { req =>
          ZHttpAdapter
            .makeWebSocketService(interpreter, skipValidation = false, keepAliveTime = Option(5.minutes))
            .provideSomeLayer[Environment, SessionProvider, Throwable](SessionProvider.layer(req.session.get))
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
