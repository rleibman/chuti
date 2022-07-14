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

package chuti

import org.scalatest.flatspec.AsyncFlatSpec
import zio.Console.*
import zio.stream.ZStream
import zio.*

class ZIOStreamSpec extends AsyncFlatSpec {

  "open a stream and writing to it" should "all work" in {
    val zioQueue: UIO[Queue[String]] = Queue.unbounded[String]

    val result = for {
      queue <- zioQueue
      consumeQueue = ZStream.fromQueue(queue).foreach(e => printLine(e.toString))
      // Sleep without blocking threads thanks to ZIO fibers
      feedQueue = ZIO.foreach(Range(1, 1000)) { e =>
        if (e == 999)
          queue.shutdown.as(true)
        else
          ZIO.sleep(2.millis) *> queue.offer(e.toString)

      }
      // run consume and feed in parallel
      _ <- consumeQueue.zipPar(feedQueue)
    } yield ()

    val fut = Unsafe.unsafe(zio.Runtime.default.unsafe.runToFuture(result))
    fut.map { _ =>
      assert(true)
    }

  }

}
