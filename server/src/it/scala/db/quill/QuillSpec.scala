package db.quill

import api.ChutiSession
import api.config.Config
import chuti.{User, UserId}
import dao.quill.QuillRepository
import dao.{Repository, SessionContext}
import db.quill.ChutiContainer
import db.quill.QuillUserSpec.now
import zio.*
import zio.logging.*
import zio.logging.backend.SLF4J
import zio.test.*

import java.time.{Instant, ZoneId, ZoneOffset}

abstract class QuillSpec extends ZIOSpecDefault {
  protected val now: Instant = java.time.Instant.parse("2022-03-11T00:00:00.00Z").nn
  protected val fixedClock: Clock = Clock.ClockJava(java.time.Clock.fixed(now, ZoneId.from(ZoneOffset.UTC).nn).nn)

  protected val password: String = "testPassword123"
  protected val satan: User = // A user with no permissions
    User(
      id = Some(UserId(999)),
      email = "Satan@hell.com",
      name = "Lucifer Morningstar",
      created = Instant.now().nn,
      lastUpdated = Instant.now().nn
    )

  protected val loggingLayer: ULayer[Unit] = SLF4J.slf4j(zio.LogLevel.Debug, LogFormat.line |-| LogFormat.cause)

  protected val baseConfigLayer: ULayer[Config] = ZLayer.succeed(api.config.live)
  protected val containerLayer: ULayer[ChutiContainer] = ChutiContainer.containerLayer.orDie
  protected val configLayer: URLayer[ChutiContainer, Config] = baseConfigLayer >>> ChutiContainer.configLayer
  protected val quillLayer: URLayer[Config, Repository] = QuillRepository.uncached
  protected val godSession: ULayer[SessionContext] = SessionContext.live(ChutiSession(chuti.god))
  protected val satanSession: ULayer[SessionContext] = SessionContext.live(ChutiSession(satan))

  protected def userSession(user: User): ULayer[SessionContext] = SessionContext.live(ChutiSession(user))

  protected val testUserZIO: UIO[User] = {
    for {
      now <- Clock.instant
      str <- Random.nextString(5)
    } yield User(None, email = s"$str@example.com", name = "Frank Lloyd Wright", created = now, lastUpdated = now)
  }

  //  protected val clockLayer: ULayer[Clock] =
  //    ZLayer.succeed(java.time.Clock.fixed(now, ZoneId.from(ZoneOffset.UTC).nn).nn) >>> Clock.JavaClock


}
