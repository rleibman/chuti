package chuti

import courier.Envelope
import mail.Postman
import mail.Postman.Postman
import zio.ZIO

class MockPostman extends Postman.Service {
  override def deliver(email: Envelope): ZIO[Postman, Throwable, Unit] = ZIO.succeed(())
}
