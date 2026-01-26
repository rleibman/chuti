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

import scala.language.unsafeNulls

object GameError {

  def apply(cause: Throwable): GameError =
    cause match {
      case e: GameError => e
      case e => new GameError(cause.getMessage, Some(cause))
    }
  def apply(message: String): GameError = new GameError(message)

  def apply(
    msg:         String,
    cause:       Option[Throwable] = None,
    isTransient: Boolean = false
  ): GameError = {
    cause match {
      case Some(e: GameError) => e
      case e                  => new GameError(msg, e, isTransient)
    }
  }

}

class GameError(
  val msg:         String,
  val cause:       Option[Throwable] = None,
  val isTransient: Boolean = false
) extends Exception(msg, cause.orNull) {}

