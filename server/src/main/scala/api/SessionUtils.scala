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

import akka.http.scaladsl.server.{Directive0, Directive1, Directives}
import chuti.UserId
import com.softwaremill.session.SessionDirectives.*
import com.softwaremill.session.SessionOptions.*
import com.softwaremill.session.*
import dao.*
import dao.slick.{MySQLDatabaseProvider, SlickRepository}
import scalacache.Cache
import scalacache.ZioEffect.modes.*
import scalacache.caffeine.CaffeineCache
import zio.clock.Clock
import zio.logging.{Logging, log}
import zio.logging.slf4j.Slf4jLogger
import zio.{Task, ZIO, ZLayer}

import scala.concurrent.duration.*
import scala.util.Try

object SessionUtils {

  implicit val sessionCache: Cache[Option[ChutiSession]] = CaffeineCache[Option[ChutiSession]]
  def updateSession(session: ChutiSession): Task[Unit] = {
    println(s"updating session $session")
    sessionCache.put(session.user.id)(Option(session)).unit
  }
  def removeFromCache(userIdOpt: Option[UserId]): Task[Any] =
    Task.foreach(userIdOpt)(userId => sessionCache.remove(userId.value))
}

trait SessionUtils extends Directives {
  this: HasActorSystem =>
  import SessionUtils.*
  import actorSystem.dispatcher
  import scalacache.memoization.*

  lazy private val godLayer =
    ((Slf4jLogger.make((_, b) => b) ++ ZLayer.succeed(
      config.live
    )) >>> MySQLDatabaseProvider.liveLayer >>> SlickRepository.live) ++
      SessionProvider.layer(ChutiSession(chuti.god)) ++
      Slf4jLogger.make((_, str) => str) ++
      Clock.live

  def getChutiSession(id: Int): Task[Option[ChutiSession]] =
    memoizeF(Option(1.hour)) {
      val me: Task[Option[ChutiSession]] = (for {
        repository <- ZIO.service[Repository.Service]
        userOpt <- repository.userOperations.get(UserId(id)).provideLayer(godLayer).catchSome {
          case e: RepositoryException =>
            ZIO.succeed {
              e.printStackTrace()
              None
            }
        }
      } yield userOpt.map(u => ChutiSession(u))).provideLayer(godLayer)
      me
    }

  implicit def sessionSerializer: SessionSerializer[ChutiSession, String] =
    new SingleValueSessionSerializer[ChutiSession, String](
      _.user.id.fold("-1")(_.value.toString),
      (id: String) =>
        Try {
          zio.Runtime.default
            .unsafeRun(getChutiSession(id.toInt))
            .getOrElse(throw new Exception(s"The user $id was not found!"))
        }
    )

  private val sessionConfig = SessionConfig.default(
    config.live.config.getString(s"${config.live.configKey}.sessionServerSecret")
  )
  implicit protected val sessionManager: SessionManager[ChutiSession] =
    new SessionManager[ChutiSession](sessionConfig)
  implicit private val refreshTokenStorage: InMemoryRefreshTokenStorage[ChutiSession] =
    (msg: String) => zio.Runtime.default.unsafeRun(log.info(msg).provideLayer(Logging.console()))
  protected def mySetSession(v: ChutiSession): Directive0 = setSession(refreshable, usingCookies, v)
  lazy protected val ensureSession: Directive1[SessionResult[ChutiSession]] =
    session(refreshable, usingCookies)
  lazy protected val myInvalidateSession: Directive0 = {
    invalidateSession(refreshable, usingCookies)
  }
}
