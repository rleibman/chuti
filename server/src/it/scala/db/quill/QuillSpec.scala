package db.quill

import api.ChutiSession
import api.config.Config
import chuti.{User, UserId}
import dao.SessionContext
import dao.quill.QuillRepository
import zio.clock.Clock
import zio.logging.Logging
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
  protected val containerLayer = ChutiContainer.containerLayer.orDie
  protected val configLayer = (containerLayer ++ baseConfigLayer) >>> ChutiContainer.configLayer
  protected val quillLayer = QuillRepository.cached
  protected val godSession:              ULayer[SessionContext] = SessionContext.live(ChutiSession(chuti.god))
  protected val satanSession:            ULayer[SessionContext] = SessionContext.live(ChutiSession(satan))
  protected def userSession(user: User): ULayer[SessionContext] = SessionContext.live(ChutiSession(user))
  protected val now:                     Instant = java.time.Instant.parse("2022-03-11T00:00:00.00Z").nn
  protected val clockLayer: ULayer[Clock] =
    ZLayer.succeed(java.time.Clock.fixed(now, ZoneId.from(ZoneOffset.UTC).nn).nn) >>> Clock.javaClock

  //  val fullGodLayer: ULayer[Config & Repository & Logging & SessionContext & Clock & Random] =
  //    configLayer ++ quillLayer ++ loggingLayer ++ godSession ++ clockLayer ++ Random.live

  protected val testUserZIO: URIO[Random & Clock, User] = {
    for {
      random <- ZIO.service[Random.Service]
      now    <- ZIO.service[Clock.Service].flatMap(_.instant)
      str    <- random.nextString(5)
    } yield User(None, email = s"$str@example.com", name = "Frank Lloyd Wright", created = now, lastUpdated = now)
  }

}
