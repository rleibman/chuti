package db.quill

import api.*
import chuti.{User, UserId}
import dao.Repository
import dao.quill.QuillRepository
import db.quill.QuillUserSpec.now
import util.ChutiContainer
import zio.*
import zio.logging.*
import zio.logging.backend.SLF4J
import zio.test.*

import java.time.{Instant, ZoneId, ZoneOffset}

abstract class QuillSpec extends ZIOSpec[ChutiEnvironment] {

  override def bootstrap:   ULayer[ChutiEnvironment] = EnvironmentBuilder.withContainer.orDie
  protected val now:        Instant = java.time.Instant.parse("2022-03-11T00:00:00.00Z").nn
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

  protected val godSession:   ULayer[ChutiSession] = ChutiSession(chuti.god).toLayer
  protected val satanSession: ULayer[ChutiSession] = ChutiSession(satan).toLayer

  protected def userSession(user: User): ULayer[ChutiSession] = ChutiSession(user).toLayer

  protected val testUserZIO: UIO[User] = {
    for {
      now <- Clock.instant
      str <- Random.nextString(5)
    } yield User(None, email = s"$str@example.com", name = "Frank Lloyd Wright", created = now, lastUpdated = now)
  }

  //  protected val clockLayer: ULayer[Clock] =
  //    ZLayer.succeed(java.time.Clock.fixed(now, ZoneId.from(ZoneOffset.UTC).nn).nn) >>> Clock.JavaClock

}
