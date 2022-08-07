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
