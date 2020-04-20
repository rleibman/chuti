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

package dao

import api.ChutiSession
import zio.{Has, Layer, ZLayer}

object SessionProvider {
  trait Session {
    def session: ChutiSession
  }
  def live(s: ChutiSession): Session = {
    new Session {
      val session: ChutiSession = s
    }
  }
  def layer(session: ChutiSession): Layer[Nothing, Has[Session]] = ZLayer.succeed(live(session))
}
