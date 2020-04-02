/*
 * Copyright 2020 Roberto Leibman
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package dao

import api.ChutiSession
import chuti.{GameState, User, UserId}
import zio.ZIO
import zioslick.RepositoryException

/**
  * This trait defines all of the Model's database methods.
  */
//@accessible
//@mockable
trait Repository {

  def repository: Repository.Service
}

object Repository {
  trait UserOperations extends CRUDOperations[User, UserId, EmptySearch[User]] {
    def unfriend(
      user:  User,
      enemy: User
    ): RepositoryIO[Boolean]
    def friends(user: User): RepositoryIO[Seq[User]]
  }
  trait GameStateOperations extends CRUDOperations[GameState, Int, EmptySearch[GameState]] {}

  trait Service {
    val gameStateOperations: GameStateOperations

    val userOperations: UserOperations
  }
}
