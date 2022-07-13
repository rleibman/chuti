package db.quill

import api.ChutiSession
import api.config.Config
import chuti.{User, UserId}
import dao.SessionContext
import dao.quill.QuillRepository
import db.quill.ChutiContainer.ChutiContainer
import zio.clock.Clock
import zio.logging.*
import zio.logging.slf4j.Slf4jLogger
import zio.random.Random
import zio.test.DefaultRunnableSpec
import zio.{ULayer, URIO, ZIO, ZLayer}

import java.time.{Instant, ZoneId, ZoneOffset}

abstract class QuillSpec extends DefaultRunnableSpec {

  protected val password: String = "testPassword123"
  protected val satan: User = // A user with no permissions
    User(
      id = Some(UserId(999)),
      email = "Satan@hell.com",
      name = "Lucifer Morningstar",
      created = Instant.now().nn,
      lastUpdated = Instant.now().nn
    )

  protected val loggingLayer:    ULayer[Logging] = Slf4jLogger.make((_, b) => b)
  protected val baseConfigLayer: ULayer[Config] = ZLayer.succeed(api.config.live)
  protected val containerLayer:  ULayer[ChutiContainer] = loggingLayer >>> ChutiContainer.containerLayer.orDie
  protected val configLayer = (containerLayer ++ baseConfigLayer) >>> ChutiContainer.configLayer
  protected val quillLayer = QuillRepository.uncached
  protected val godSession:              ULayer[SessionContext] = SessionContext.live(ChutiSession(chuti.god))
  protected val satanSession:            ULayer[SessionContext] = SessionContext.live(ChutiSession(satan))
  protected def userSession(user: User): ULayer[SessionContext] = SessionContext.live(ChutiSession(user))
  protected val now:                     Instant = java.time.Instant.parse("2022-03-11T00:00:00.00Z").nn
  protected val clockLayer: ULayer[Clock] =
    ZLayer.succeed(java.time.Clock.fixed(now, ZoneId.from(ZoneOffset.UTC).nn).nn) >>> Clock.javaClock

  //  val fullGodLayer: ULayer[Config & Repository  & SessionContext ] =
  //    configLayer ++ quillLayer ++ loggingLayer ++ godSession ++ clockLayer ++ Random.live

  protected val testUserZIO: UIO[User] = {
    for {
      now <- Clock.instant
      str <- random.nextString(5)
    } yield User(None, email = s"$str@example.com", name = "Frank Lloyd Wright", created = now, lastUpdated = now)
  }

}
