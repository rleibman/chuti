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

import chuti.{PagedStringSearch, User, UserId}
import io.circe.generic.auto._
import japgolly.scalajs.react.AsyncCallback
import util.Config

package object service extends Config {

  object UserRESTClient extends LiveRESTClient[User, UserId, PagedStringSearch] {
    override val baseUrl: String = s"/api/auth"

    case object UserClientService extends LiveClientService {
      def whoami(): AsyncCallback[Option[User]] =
        RESTOperation[String, Option[User]]("get", s"$baseUrl/whoami", None)
      def changePassword(password: String): AsyncCallback[Boolean] =
        RESTOperation[String, Boolean]("post", s"$baseUrl/changePassword", Option(password))
    }
    override def remoteSystem = UserClientService
  }
}
