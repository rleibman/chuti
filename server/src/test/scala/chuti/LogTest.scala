package chuti

import java.util.UUID

import zio.clock.Clock
import zio.duration._
import zio.logging._
import zio.logging.slf4j._
import zio.{App, UIO, ZIO, ZLayer}

case class SomethingElse(string: String) {
  def foo: ZIO[Logging, Nothing, Unit] = {
    log.info(s"Hello from SomethingElse $string")
  }
}

object LogTest extends App {

  private val userAnnonation = LogAnnotation[Option[User]](
    name = "user",
    initialValue = None,
    combine = (_, newValue) => newValue,
    render = _.fold("None")(_.email)
  )

  private val logLayer: ZLayer[Any, Nothing, Logging] = Slf4jLogger.makeWithAnnotationsAsMdc(List(userAnnonation), logFormat = {
    case (ctx, str) =>
      s"${ctx(LogAnnotation.CorrelationId)} ${ctx(userAnnonation)}: $str"
  })
  private val users = List(
    Option(User(Option(UserId(1)), "yoyo1@example.com", "yoyo1")),
    Option(User(Option(UserId(2)), "yoyo2@example.com", "yoyo2"))
  )

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    val somethingElse = SomethingElse("Yoyo")
    (for {
      _             <- log.info("Start...")
      correlationId <- UIO(Some(UUID.randomUUID()))
      _             <- somethingElse.foo
      _ <- ZIO.foreachPar(users) { uId =>
        log.locally(
          _.annotate(userAnnonation, uId).annotate(LogAnnotation.CorrelationId, correlationId)
        ) {
          log.info("Starting operation") *>
            ZIO.sleep(500.millis) *>
            log.info("Stopping operation")
        }
      }
    } yield 0).provideSomeLayer[Clock](logLayer)
  }
}
