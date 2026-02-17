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

package chuti.api

import chuti.GameError

import java.nio.file.Path

class NotFoundError(
  val path:    Path,
  msg:         String,
  cause:       Option[Throwable] = None,
  isTransient: Boolean = false
) extends GameError(msg, cause, isTransient)

object NotFoundError {

  def apply(
    path:    Path,
    message: String
  ): NotFoundError = new NotFoundError(path, message)

  def apply(
    path:        Path,
    msg:         String,
    cause:       Option[Throwable] = None,
    isTransient: Boolean = false
  ): NotFoundError = new NotFoundError(path, msg, cause, isTransient)

}
