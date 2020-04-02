/*
 * Copyright 2020 Roberto Leibman
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package dao

import java.sql.SQLException

import api.ChutiSession
import chuti.{GameState, User, UserId}
import dao.Repository.{GameStateOperations, UserOperations}
import slick.SlickException
import slick.basic.BasicBackend
import slick.dbio.DBIO
import zio.{Has, ZIO}
import zioslick.RepositoryException
import scala.language.implicitConversions

trait LiveRepository extends Repository with SlickToModelInterop {

  implicit def fromDBIO[A](dbio: DBIO[A]): RepositoryIO[A] =
    for {
      session <- ZIO.access[SessionProvider](_.get.session)
      db      <- ZIO.accessM[DatabaseProvider](_.get.db)
      ret <- ZIO.fromFuture(implicit ec => db.run(dbio)).mapError {
        case e: SlickException => RepositoryException("Slick Repository Error", Some(e))
        case e: SQLException   => RepositoryException("SQL Repository Error", Some(e))
      }
    } yield ret

  override def repository: Repository.Service = new Repository.Service {
    override val userOperations: Repository.UserOperations = new UserOperations {
      override def unfriend(
        user:  User,
        enemy: User
      ): RepositoryIO[Boolean] = ???

      override def friends(user: User): RepositoryIO[Seq[User]] = ???

      override def upsert(e: User): RepositoryIO[User] = {
        val dbio: DBIO[User] = ???
        fromDBIO(dbio)
      }

      override def get(pk: UserId): RepositoryIO[Option[User]] = ???

      override def delete(
        pk:         UserId,
        softDelete: Boolean
      ): RepositoryIO[Boolean] = ???

      override def search(search: Option[EmptySearch[User]]): RepositoryIO[Seq[User]] = ???

      override def count(search: Option[EmptySearch[User]]): RepositoryIO[Long] = ???
    }
    override val gameStateOperations: Repository.GameStateOperations = new GameStateOperations {
      override def upsert(e: GameState): RepositoryIO[GameState] = ???

      override def get(pk: Int): RepositoryIO[Option[GameState]] = ???

      override def delete(
        pk:         Int,
        softDelete: Boolean
      ): RepositoryIO[Boolean] = ???

      override def search(search: Option[EmptySearch[GameState]]): RepositoryIO[Seq[GameState]] =
        ???

      override def count(search: Option[EmptySearch[GameState]]): RepositoryIO[Long] = ???

    }
  }
}
