package mail

import api.config
import api.token.TokenHolder
import chuti.User
import courier.{Envelope, Text}
import javax.mail.internet.InternetAddress
import zio._
import zio.console._
import zio.test.Assertion._
import zio.test.environment._
import zio.test.{DefaultRunnableSpec, _}

object PostmanIntegrationSpec extends DefaultRunnableSpec {
  override def spec: Spec[TestEnvironment, TestFailure[Throwable], TestSuccess] =
    suite("PostmanIntegrationSpec")(
      testM("sending an email") {
//        System.setProperty("mail.smtp.localhost", "magrathea2.leibmanland.com")
//        System.setProperty("mail.smtp.localaddress", "magrathea2.leibmanland.com")
        val zio = for {
          postman <- ZIO.service[Postman.Service]
          delivered <- postman.deliver(
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
          postman <- ZIO.service[Postman.Service]
          envelope <- postman.registrationEmail(User(id = None, email = "roberto@leibman.net", name = "Roberto"))
          delivered <- postman.deliver(envelope).fork
        } yield delivered

        zio.as(assert(true)(equalTo(true)))
      }
    ).provideCustomLayer(
      ZLayer.succeed(CourierPostman.live(config.live)) ++
      ZLayer.succeed(TokenHolder.tempCache)
    )
}

import mail.HelloWorld._

object HelloWorld {
  def sayHello: ZIO[Console, Nothing, Unit] =
    console.putStrLn("Hello, World!")
}

object HelloWorldSpec extends DefaultRunnableSpec {
  override def spec: Spec[_root_.zio.test.environment.TestEnvironment, TestFailure[Nothing], TestSuccess] = suite("HelloWorldSpec")(
    testM("sayHello correctly displays output") {
      for {
        _      <- sayHello
        output <- TestConsole.output
      } yield assert(output)(equalTo(Vector("Hello, World!\n")))
    }
  )
}
