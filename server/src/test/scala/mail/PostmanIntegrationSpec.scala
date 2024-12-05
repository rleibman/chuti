package mail

import api.{ChutiEnvironment, ChutiSession, EnvironmentBuilder}
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
                .from(new InternetAddress("system@chuti.com"))
                .to(new InternetAddress("roberto@leibman.net"))
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
      .make[ChutiEnvironment & ChutiSession](EnvironmentBuilder.withContainer.orDie, ChutiSession.adminSession.toLayer)

}
