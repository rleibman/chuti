package chuti

import org.scalatest.flatspec.AsyncFlatSpec
import zio.console._
import zio.duration._
import zio.stream.ZStream
import zio.{ Queue, UIO, ZIO, ZQueue }

class ZIOStreamSpec extends AsyncFlatSpec {
  "open a stream and writing to it" should "all work" in {
    val zioQueue: UIO[Queue[String]] = ZQueue.unbounded[String]

    val result = for {
      queue        <- zioQueue
      consumeQueue = ZStream.fromQueue(queue).foreach(e => putStrLn(e.toString))
      //Sleep without blocking threads thanks to ZIO fibers
      feedQueue = ZIO.foreach(Range(1, 1000)) { e =>
        if (e == 999) {
          queue.shutdown.as(true)
        } else {
          ZIO.sleep(2.millis) *> queue.offer(e.toString)
        }

      }
      //run consume and feed in parallel
      _ <- consumeQueue.zipPar(feedQueue)
    } yield ()

    val fut = zio.Runtime.default.unsafeRunToFuture { result }
    fut.map { _ =>
      assert(true)
    }

  }
}
