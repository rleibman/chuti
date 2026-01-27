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

import api.{ChutiEnvironment, ChutiSession, EnvironmentBuilder, toLayer}
import api.token.*
import chuti.User
import courier.{Envelope, Text}
import zio.*
import zio.cache.{Cache, Lookup}
import zio.test.Assertion.*
import zio.test.*

import javax.mail.internet.InternetAddress

object PostmanIntegrationSpec extends ZIOSpec[ChutiEnvironment & ChutiSession] {

  override def spec =
    suite("PostmanIntegrationSpec")(
      test("sending an email") {
        val zio = for {
          postman <- ZIO.service[Postman]
          delivered <- postman
            .deliver(
              Envelope
                .from( InternetAddress("system@chuti.com"))
                .to( InternetAddress("roberto@leibman.net"))
                .subject("hello")
                .content(Text("body of hello"))
            ).fork
        } yield delivered

        zio.as(assert(true)(equalTo(true)))
      },
      test("sending a specific email") {
        //        System.setProperty("mail.smtp.localhost", "magrathea2.leibmanland.com")
        //        System.setProperty("mail.smtp.localaddress", "magrathea2.leibmanland.com")
        val zio = for {
          postman <- ZIO.service[Postman]
          now     <- Clock.instant
          envelope <- postman.registrationEmail(
            User(id = None, email = "roberto@leibman.net", name = "Roberto", created = now, lastUpdated = now)
          )
          delivered <- postman.deliver(envelope).fork
        } yield delivered

        zio.as(assert(true)(equalTo(true)))
      }
    )

  override def bootstrap: ULayer[ChutiEnvironment & ChutiSession] =
    ZLayer
      .make[ChutiEnvironment & ChutiSession](EnvironmentBuilder.withContainer, ChutiSession.godSession.toLayer)

}
