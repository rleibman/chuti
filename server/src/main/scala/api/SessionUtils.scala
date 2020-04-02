/*
 * Copyright 2020 Roberto Leibman
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package api

import akka.event.Logging
import akka.event.slf4j.Logger
import akka.http.scaladsl.server.{Directive0, Directive1, Directives}
import chuti.UserId
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import com.softwaremill.session._

import scala.util.Try

trait SessionUtils extends Directives with Config {
  this: HasActorSystem =>
  import actorSystem.dispatcher
  private val log = Logging.getLogger(actorSystem, this)
  implicit def sessionSerializer: SessionSerializer[ChutiSession, String] =
    new SingleValueSessionSerializer[ChutiSession, String](
      _.usuario.id.fold("")(_.value.toString),
      id => Try(???)
    )
  private val sessionConfig = SessionConfig.default(config.getString("chuti.sessionServerSecret"))
  implicit protected val sessionManager: SessionManager[ChutiSession] =
    new SessionManager[ChutiSession](sessionConfig)
  implicit private val refreshTokenStorage: InMemoryRefreshTokenStorage[ChutiSession] =
    (msg: String) => log.info(msg)
  protected def mySetSession(v: ChutiSession): Directive0 = setSession(refreshable, usingCookies, v)
  protected val ensureSession: Directive1[SessionResult[ChutiSession]] =
    session(refreshable, usingCookies)
  protected val myInvalidateSession: Directive0 = invalidateSession(refreshable, usingCookies)
}
