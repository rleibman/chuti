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
