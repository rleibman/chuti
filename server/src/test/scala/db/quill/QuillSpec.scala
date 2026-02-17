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

package db.quill

import chuti.api.{*, given}
import chuti.db.ZIORepository
import chuti.db.quill.QuillRepository
import chuti.util.ChutiContainer
import chuti.{User, UserId}
import db.quill.QuillUserSpec.now
import zio.*
import zio.logging.*
import zio.logging.backend.SLF4J
import zio.test.*

import java.time.{Instant, ZoneId, ZoneOffset}

abstract class QuillSpec extends ZIOSpec[ChutiEnvironment] {

  override def bootstrap:   ULayer[ChutiEnvironment] = EnvironmentBuilder.withContainer
  protected val now:        Instant = java.time.Instant.parse("2022-03-11T00:00:00.00Z").nn
  protected val fixedClock: Clock = Clock.ClockJava(java.time.Clock.fixed(now, ZoneId.from(ZoneOffset.UTC)))

  protected val password: String = "testPassword123"
  protected val satan: User = // A user with no permissions
    User(
      id = UserId(999),
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
    } yield User(
      UserId.empty,
      email = s"$str@example.com",
      name = "Frank Lloyd Wright",
      created = now,
      lastUpdated = now
    )
  }

  //  protected val clockLayer: ULayer[Clock] =
  //    ZLayer.succeed(java.time.Clock.fixed(now, ZoneId.from(ZoneOffset.UTC).nn).nn) >>> Clock.JavaClock

}
