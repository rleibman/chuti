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
import chuti.{User, UserId}
import com.softwaremill.session.SessionDirectives._
import com.softwaremill.session.SessionOptions._
import com.softwaremill.session._
import dao._
import game.GameService
import scalacache.Cache
import scalacache.caffeine.CaffeineCache
import zio.logging.slf4j.Slf4jLogger
import zio.logging.{Logging, log}
import zio.{Task, ZIO, ZLayer}
import zioslick.RepositoryException

import scala.concurrent.duration._
import scala.util.Try

object SessionUtils {
  implicit val userCache: Cache[Option[User]] = CaffeineCache[Option[User]]
}

trait SessionUtils extends Directives {
  this: HasActorSystem =>
  import scalacache._
  import scalacache.memoization._
  import scalacache.caffeine.CaffeineCache
  import actorSystem.dispatcher
  import scalacache.ZioEffect.modes._
  import SessionUtils._

  private val dbProvider: DatabaseProvider.Service = new MySQLDatabaseProvider() {}
  private val repository: Repository.Service = new LiveRepository() {}

  lazy private val godLayer = ZLayer
    .succeed(dbProvider) ++ SessionProvider
    .layer(ChutiSession(GameService.god)) ++ Slf4jLogger.make((_, str) => str)

  def getUser(id: Int): Task[Option[User]] = memoizeF(Option(1.hour)) {
    repository.userOperations.get(UserId(id)).provideLayer(godLayer).catchSome {
      case e: RepositoryException =>
        ZIO.succeed {
          e.printStackTrace()
          None
        }
    }
  }

  implicit def sessionSerializer: SessionSerializer[ChutiSession, String] =
    new SingleValueSessionSerializer[ChutiSession, String](
      _.user.id.fold("-1")(_.value.toString),
      (id: String) =>
        Try {
          val user = {
            zio.Runtime.default.unsafeRun(getUser(id.toInt))
          }
          if (user.isEmpty)
            throw new Exception("The user was not found!")
          ChutiSession(user.get)
        }
    )

  private val sessionConfig = SessionConfig.default(
    config.live.config.getString(s"${config.live.configKey}.sessionServerSecret")
  )
  implicit protected val sessionManager: SessionManager[ChutiSession] =
    new SessionManager[ChutiSession](sessionConfig)
  implicit private val refreshTokenStorage: InMemoryRefreshTokenStorage[ChutiSession] =
    (msg: String) => log.info(msg)
  protected def mySetSession(v: ChutiSession): Directive0 = setSession(refreshable, usingCookies, v)
  lazy protected val ensureSession: Directive1[SessionResult[ChutiSession]] =
    session(refreshable, usingCookies)
  lazy protected val myInvalidateSession: Directive0 = invalidateSession(refreshable, usingCookies)
}
