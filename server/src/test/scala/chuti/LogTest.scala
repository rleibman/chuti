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

import dao.InMemoryRepository.now
import zio.logging.*
import zio.logging.slf4j.*
import zio.{Clock, ExitCode, UIO, ZIO, ZIOAppDefault, ZLayer, *}

import java.time.Instant
import java.util.UUID

case class SomethingElse(string: String) {

  def foo: ZIO[Any, Nothing, Unit] = {
    ZIO.logInfo(s"Hello from SomethingElse $string")
  }

}

object LogTest extends ZIOAppDefault {

  private val userAnnonation = LogAnnotation[Option[User]](
    name = "user",
    initialValue = None,
    combine = (_, newValue) => newValue,
    render = _.fold("None")(_.email)
  )

  private val logLayer: ZLayer[Any, Nothing, Logging] = Slf4jLogger.makeWithAnnotationsAsMdc(
    List(userAnnonation),
    logFormat = { case (ctx, str) =>
      s"${ctx(LogAnnotation.CorrelationId)} ${ctx(userAnnonation)}: $str"
    }
  )
  private val now = Instant.now.nn
  private val users = List(
    User(Option(UserId(1)), "yoyo1@example.com", "yoyo1", created = now, lastUpdated = now),
    User(Option(UserId(2)), "yoyo2@example.com", "yoyo2", created = now, lastUpdated = now)
  )

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, ExitCode] = {
    val somethingElse = SomethingElse("Yoyo")
    (for {
      _ <- ZIO.logInfo("Start...")
      _ <- somethingElse.foo
      _ <- ZIO.foreachParDiscard(users) { user =>
        log.locally(_.annotate(userAnnonation, Option(user)).annotate(LogAnnotation.CorrelationId, Option(UUID.randomUUID().nn))) {
          ZIO.logInfo("Starting operation") *>
            ZIO.sleep(500.millis) *>
            ZIO.logInfo("Stopping operation")
        }
      }
    } yield ExitCode.success).provideSomeLayer[Clock](logLayer)
  }

}
