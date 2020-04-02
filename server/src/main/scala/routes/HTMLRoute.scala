/*
 * Copyright 2020 Roberto Leibman
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package routes

import akka.http.scaladsl.server.directives.ContentTypeResolver
import akka.http.scaladsl.server.{Directives, Route}
import api.Config

/**
  * A route used to spit out static content
  */
trait HTMLRoute extends Directives with Config {
  val staticContentDir: String = config.getString("chuti.staticContentDir")

  override def getFromDirectory(
    directoryName: String
  )(
    implicit resolver: ContentTypeResolver
  ): Route =
    extractUnmatchedPath { unmatchedPath =>
      getFromFile(s"$staticContentDir/$unmatchedPath")
    }

  def htmlRoute: Route = extractLog { log =>
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
