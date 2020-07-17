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

package scalacache

import zio.Task

import scala.util.control.NonFatal

object ZioEffect {

  object modes {

    /**
      * A mode that wraps results of async operations in `zio.Task`.
      */
    implicit val task: Mode[Task] = new Mode[Task] {
      val M: Async[Task] = AsyncForZIOTask
    }

  }

  val AsyncForZIOTask: Async[Task] = new Async[Task] {

    def pure[A](a: A): Task[A] = Task.succeed(a)

    def map[A, B](fa: Task[A])(f: (A) => B): Task[B] = fa.map(f)

    def flatMap[A, B](fa: Task[A])(f: (A) => Task[B]): Task[B] = fa.flatMap(f)

    def raiseError[A](t: Throwable): Task[A] = Task.fail(t)

    def handleNonFatal[A](fa: => Task[A])(f: Throwable => A): Task[A] =
      fa.catchSome {
        case NonFatal(e) => Task.succeed(f(e))
      }

    def delay[A](thunk: => A): Task[A] = Task.effect(thunk)

    def suspend[A](thunk: => Task[A]): Task[A] = Task.flatten(Task.effect(thunk))

    def async[A](register: (Either[Throwable, A] => Unit) => Unit): Task[A] =
      Task.effectAsync { (kk: Task[A] => Unit) =>
        register { e =>
          kk(e match {
            case Left(t)  => Task.fail(t)
            case Right(r) => Task.succeed(r)
          })
        }
      }

  }
}
