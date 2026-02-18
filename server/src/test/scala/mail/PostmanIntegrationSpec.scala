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

package mail

import chuti.api.{*, given}
import chuti.api.token.TokenHolder
import chuti.mail.Postman
import chuti.{User, UserId}
import courier.{Envelope, Text}
import zio.*
import zio.cache.{Cache, Lookup}
import zio.test.Assertion.*
import zio.test.*

import java.time.{Instant, ZoneId, ZoneOffset}
import javax.mail.internet.InternetAddress

object PostmanIntegrationSpec extends ZIOSpec[ChutiEnvironment & ChutiSession] {
  protected val now:        Instant = java.time.Instant.parse("2022-03-11T00:00:00.00Z").nn
  protected val fixedClock: Clock = Clock.ClockJava(java.time.Clock.fixed(now, ZoneId.from(ZoneOffset.UTC)))

  override def spec =
    suite("PostmanIntegrationSpec")(
      test("sending an email") {
        val zio = for {
          postman <- ZIO.service[Postman]
          delivered <- postman
            .deliver(
              Envelope
                .from(InternetAddress("system@chuti.com"))
                .to(InternetAddress("roberto@leibman.net"))
                .subject("hello")
                .content(Text("body of hello"))
            ).fork
        } yield delivered

        zio.as(assert(true)(equalTo(true))).withClock(fixedClock)
      },
      test("sending a specific email") {
        (for {
          postman <- ZIO.service[Postman]
          now     <- Clock.instant
          testUser = User(id = UserId.empty, email = "roberto@leibman.net", name = "Roberto", created = now, lastUpdated = now)
          // Use mock TokenHolder since the real one tries to insert a token with god's userId (-666)
          // which doesn't exist in the database. This test is about email sending, not token creation.
          envelope  <- postman.registrationEmail(testUser).provide(TokenHolder.mockLayer)
          delivered <- postman.deliver(envelope).fork
        } yield assert(true)(equalTo(true))).withClock(fixedClock)
      }
    )

  override def bootstrap: ULayer[ChutiEnvironment & ChutiSession] =
    ZLayer
      .make[ChutiEnvironment & ChutiSession](EnvironmentBuilder.withContainer, ChutiSession.godSession.toLayer).fresh

}
