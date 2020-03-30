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

package api

import akka.event.Logging
import akka.event.slf4j.Logger
import akka.http.scaladsl.server.{ Directive0, Directive1, Directives }
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
  private val sessionConfig                                                           = SessionConfig.default(config.getString("chuti.sessionServerSecret"))
  protected implicit val sessionManager: SessionManager[ChutiSession]                 = new SessionManager[ChutiSession](sessionConfig)
  private implicit val refreshTokenStorage: InMemoryRefreshTokenStorage[ChutiSession] = (msg: String) => log.info(msg)
  protected def mySetSession(v: ChutiSession): Directive0                             = setSession(refreshable, usingCookies, v)
  protected val ensureSession: Directive1[SessionResult[ChutiSession]]                = session(refreshable, usingCookies)
  protected val myInvalidateSession: Directive0                                       = invalidateSession(refreshable, usingCookies)
}
