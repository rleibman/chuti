/*
 * Copyright (c) 2024 Roberto Leibman
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

import chuti.{PagedStringSearch, User, UserId, UserWallet}
import japgolly.scalajs.react.AsyncCallback
import zio.json.*

package object service {

  import scala.language.unsafeNulls
  object UserRESTClient extends LiveRESTClient[User, UserId, PagedStringSearch] {

    override val baseUrl: String = s"/api/auth"

    case object UserClientService extends LiveClientService {

      def isFirstLoginToday(): AsyncCallback[Boolean] = RESTOperation[String, Boolean]("get", s"$baseUrl/isFirstLoginToday", None)
      def whoami():            AsyncCallback[Option[User]] = RESTOperation[String, Option[User]]("get", s"$baseUrl/whoami", None)
      def wallet():            AsyncCallback[Option[UserWallet]] = RESTOperation[String, Option[UserWallet]]("get", s"$baseUrl/userWallet", None)
      def changePassword(password: String): AsyncCallback[Boolean] =
        RESTOperation[String, Boolean]("post", s"$baseUrl/changePassword", Option(password))
      def setLocale(languageTag: String): AsyncCallback[Boolean] = RESTOperation[String, Boolean]("put", s"$baseUrl/locale", Option(languageTag))

    }
    override def remoteSystem: UserClientService.type = UserClientService

  }

}
