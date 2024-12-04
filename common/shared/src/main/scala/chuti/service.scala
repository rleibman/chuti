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

package chuti

import zio.json.*

object UpdateInvitedUserRequest {

  given JsonDecoder[UpdateInvitedUserRequest] = DeriveJsonDecoder.gen[UpdateInvitedUserRequest]
  given JsonEncoder[UpdateInvitedUserRequest] = DeriveJsonEncoder.gen[UpdateInvitedUserRequest]

}

case class UpdateInvitedUserRequest(
  user:     User,
  password: String,
  token:    String
)

object UpdateInvitedUserResponse {

  given JsonDecoder[UpdateInvitedUserResponse] = DeriveJsonDecoder.gen[UpdateInvitedUserResponse]
  given JsonEncoder[UpdateInvitedUserResponse] = DeriveJsonEncoder.gen[UpdateInvitedUserResponse]

}

case class UpdateInvitedUserResponse(error: Option[String])

object UserCreationRequest {

  given JsonDecoder[UserCreationRequest] = DeriveJsonDecoder.gen[UserCreationRequest]
  given JsonEncoder[UserCreationRequest] = DeriveJsonEncoder.gen[UserCreationRequest]

}

case class UserCreationRequest(
  user:     User,
  password: String
)

object UserCreationResponse {

  given JsonDecoder[UserCreationResponse] = DeriveJsonDecoder.gen[UserCreationResponse]
  given JsonEncoder[UserCreationResponse] = DeriveJsonEncoder.gen[UserCreationResponse]

}

case class UserCreationResponse(error: Option[String])

trait Search

trait PagedSearch {

  def pageIndex: Int
  def pageSize:  Int

}

case class EmptySearch() extends Search

object PagedStringSearch {

  given JsonDecoder[PagedStringSearch] = DeriveJsonDecoder.gen[PagedStringSearch]
  given JsonEncoder[PagedStringSearch] = DeriveJsonEncoder.gen[PagedStringSearch]

}

case class PagedStringSearch(
  text:      String,
  pageIndex: Int = 0,
  pageSize:  Int = 0
) extends Search
