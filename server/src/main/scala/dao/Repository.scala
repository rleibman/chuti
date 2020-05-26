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

import chuti._

object Repository {
  trait UserOperations extends CRUDOperations[User, UserId, PagedStringSearch] {
    def login(
      email:    String,
      password: String
    ): RepositoryIO[Option[User]]

    def userByEmail(email: String): RepositoryIO[Option[User]]

    def changePassword(
      user:     User,
      password: String
    ): RepositoryIO[Boolean]

    def unfriend(enemy: User): RepositoryIO[Boolean]
    def friend(
      friend:    User,
      confirmed: Boolean
    ):           RepositoryIO[Boolean]
    def friends: RepositoryIO[Seq[User]]

    def getWallet: RepositoryIO[Option[UserWallet]]
    def updateWallet(userWallet: UserWallet): RepositoryIO[Boolean]
  }
  trait GameOperations extends CRUDOperations[Game, GameId, EmptySearch] {
    def updatePlayers(game: Game): RepositoryIO[Game]
    def gameInvites:              RepositoryIO[Seq[Game]]
    def gamesWaitingForPlayers(): RepositoryIO[Seq[Game]]
    def getGameForUser:           RepositoryIO[Option[Game]]
  }

  trait Service {
    val gameOperations: GameOperations

    val userOperations: UserOperations
  }
}
