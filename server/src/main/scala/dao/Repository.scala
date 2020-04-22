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
    def friend(friend:  User): RepositoryIO[Boolean]
    def friends: RepositoryIO[Seq[User]]
  }
  trait GameStateOperations extends CRUDOperations[GameState, GameId, EmptySearch] {
    def gamesWaitingForPlayers(): RepositoryIO[Seq[GameState]]
    def getGameForUser: RepositoryIO[Option[GameState]]
  }

  trait Service {
    val gameStateOperations: GameStateOperations

    val userOperations: UserOperations
  }
}
