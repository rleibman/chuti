package mail

import api.config
import api.token.*
import chuti.User
import courier.{Envelope, Text}
import zio.*
import zio.cache.{Cache, Lookup}
import zio.clock.*
import zio.test.Assertion.*
import zio.test.environment.*
import zio.test.{DefaultRunnableSpec, *}

import javax.mail.internet.InternetAddress

object PostmanIntegrationSpec extends DefaultRunnableSpec {

  val tokenLayer: ULayer[TokenHolder] = ???

  override def spec: Spec[TestEnvironment, TestFailure[Throwable], TestSuccess] =
    suite("PostmanIntegrationSpec")(
      testM("sending an email") {
//        System.setProperty("mail.smtp.localhost", "magrathea2.leibmanland.com")
//        System.setProperty("mail.smtp.localaddress", "magrathea2.leibmanland.com")
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
      testM("sending a specific email") {
        //        System.setProperty("mail.smtp.localhost", "magrathea2.leibmanland.com")
        //        System.setProperty("mail.smtp.localaddress", "magrathea2.leibmanland.com")
        val zio = for {
          postman   <- ZIO.service[Postman]
          now       <- Clock.instant
          envelope  <- postman.registrationEmail(User(id = None, email = "roberto@leibman.net", name = "Roberto", created = now, lastUpdated = now))
          delivered <- postman.deliver(envelope).fork
        } yield delivered

        zio.as(assert(true)(equalTo(true)))
      }
    ).provideCustomLayer(
      ZLayer.succeed(CourierPostman.live(config.live)) ++ tokenLayer
    )

}
