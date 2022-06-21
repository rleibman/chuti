package mail

import api.config
import api.token.TokenHolder
import chuti.User
import courier.{Envelope, Text}
import zio.*
import zio.test.Assertion.*
import zio.test.environment.*
import zio.test.{DefaultRunnableSpec, *}

import javax.mail.internet.InternetAddress

object PostmanIntegrationSpec extends DefaultRunnableSpec {

  override def spec: Spec[TestEnvironment, TestFailure[Throwable], TestSuccess] =
    suite("PostmanIntegrationSpec")(
      testM("sending an email") {
//        System.setProperty("mail.smtp.localhost", "magrathea2.leibmanland.com")
//        System.setProperty("mail.smtp.localaddress", "magrathea2.leibmanland.com")
        val zio = for {
          postman <- ZIO.service[Postman.Service]
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
          postman   <- ZIO.service[Postman.Service]
          envelope  <- postman.registrationEmail(User(id = None, email = "roberto@leibman.net", name = "Roberto"))
          delivered <- postman.deliver(envelope).fork
        } yield delivered

        zio.as(assert(true)(equalTo(true)))
      }
    ).provideCustomLayer(
      ZLayer.succeed(CourierPostman.live(config.live)) ++
        ZLayer.succeed(TokenHolder.tempCache)
    )

}
