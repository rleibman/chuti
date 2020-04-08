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

import java.sql.{SQLException, Timestamp}

import api.ChutiSession
import chuti.{GameState, User, UserId}
import dao.Repository.{GameStateOperations, UserOperations}
import dao.gen.Tables
import dao.gen.Tables._
import game.GameServer
import slick.SlickException
import slick.dbio.DBIO
import slick.jdbc.MySQLProfile.api._
import zio.{Task, ZIO}
import zioslick.RepositoryException

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

trait LiveRepository extends Repository with SlickToModelInterop {

  implicit val dbExecutionContext: ExecutionContext = zio.Runtime.default.platform.executor.asEC
  implicit def fromDBIO[A](dbio: DBIO[A]): RepositoryIO[A] =
    for {
      db <- ZIO.accessM[DatabaseProvider](_.get.db)
      ret <- ZIO.fromFuture(implicit ec => db.run(dbio)).mapError {
        case e: SlickException => RepositoryException("Slick Repository Error", Some(e))
        case e: SQLException   => RepositoryException("SQL Repository Error", Some(e))
      }
    } yield ret

  implicit def fromDBIO[A](fn: ChutiSession => DBIO[A]): RepositoryIO[A] =
    for {
      session <- ZIO.access[SessionProvider](_.get.session)
      db      <- ZIO.accessM[DatabaseProvider](_.get.db)
      ret <- ZIO.fromFuture(implicit ec => db.run(fn(session))).mapError {
        case e: SlickException => RepositoryException("Slick Repository Error", Some(e))
        case e: SQLException   => RepositoryException("SQL Repository Error", Some(e))
      }
    } yield ret

  override def repository: Repository.Service = new Repository.Service {
    override val userOperations: Repository.UserOperations = new UserOperations {
      override def unfriend(enemy: User): RepositoryIO[Boolean] = { session: ChutiSession =>
        val rowOpt = for {
          one <- session.user.id
          two <- enemy.id
        } yield { FriendsRow(one.value, two.value) }
        DBIO
          .sequence(
            rowOpt.toSeq
              .map(row =>
                FriendsQuery
                  .filter(r =>
                    (r.one === row.one && r.two === row.two) || (r.one === row.two && r.two === row.one)
                  ).delete
              ).map(_.map(_ > 0))
          ).map(_.headOption.getOrElse(false))
      }

      override def friend(friend: User): RepositoryIO[Boolean] = { session: ChutiSession =>
        val rowOpt = for {
          one <- session.user.id
          two <- friend.id
        } yield { FriendsRow(one.value, two.value) }
        DBIO
          .sequence(rowOpt.toSeq.map(row => FriendsQuery += row).map(_.map(_ > 0))).map(
            _.headOption.getOrElse(false)
          )
      }

      override def friends: RepositoryIO[Seq[User]] = { session: ChutiSession =>
        DBIO
          .sequence(
            session.user.id.toSeq
              .map { id =>
                UserQuery
                  .join(FriendsQuery.filter(f => f.one === id.value || f.two === id.value)).on {
                    (a, b) =>
                      a.id === b.one || a.id === b.two
                  }.map(_._1).result
              }
          ).map(_.flatten.map(UserRow2User))
      }

      override def upsert(user: User): RepositoryIO[User] = { session: ChutiSession =>
        if (session.user != GameServer.god && session.user.id != user.id) {
          throw RepositoryException(s"${session.user} Not authorized")
        }
        (UserQuery returning UserQuery.map(_.id) into ((_, id) => user.copy(id = Some(UserId(id)))))
          .insertOrUpdate(User2UserRow(user))
          .map(_.getOrElse(user))
      }

      def get(pk: UserId): RepositoryIO[Option[User]] =
        fromDBIO {
          UserQuery
            .filter(u => u.id === pk.value && !u.deleted).result.headOption.map(
              _.map(UserRow2User)
            )
        }

      override def delete(
        pk:         UserId,
        softDelete: Boolean
      ): RepositoryIO[Boolean] = { session: ChutiSession =>
        if (session.user != GameServer.god && session.user.id.fold(false)(_ != pk)) {
          throw RepositoryException(s"${session.user} Not authorized")
        }
        if (softDelete) {
          val q = for {
            u <- UserQuery if u.id === pk.value
          } yield (u.deleted, u.deleteddate)
          q.update(true, Option(new Timestamp(System.currentTimeMillis()))).map(_ > 0)
        } else {
          UserQuery.filter(_.id === pk.value).delete.map(_ > 0)
        }
      }

      override def search(search: Option[PagedStringSearch]): RepositoryIO[Seq[User]] = {
        search
          .fold(UserQuery: Query[Tables.User, Tables.UserRow, Seq]) { s =>
            UserQuery
              .filter(u => u.email.like(s"%${s.text}%"))
              .drop(s.pageSize * s.pageIndex)
              .take(s.pageSize)
          }.result.map(_.map(UserRow2User))
      }

      override def count(search: Option[PagedStringSearch]): RepositoryIO[Long] =
        search
          .fold(UserQuery: Query[Tables.User, Tables.UserRow, Seq]) { s =>
            UserQuery
              .filter(u => u.email.like(s"%${s.text}%"))
          }.length.result.map(_.toLong)

      override def login(
        email:    String,
        password: String
      ): RepositoryIO[Option[User]] = {
        (for {
          userOpt <- UserQuery
            .filter(user =>
              !user.deleted && user.email === email && user.hashedpassword === hashPassword(
                password,
                512
              )
            )
            .result
            .headOption
          _ <- DBIO.sequence(userOpt.toSeq.map { user =>
            val q = for {
              u <- UserQuery if u.id === user.id && !u.deleted
            } yield u.lastloggedin
            q.update(Option(new Timestamp(System.currentTimeMillis())))
          })
        } yield userOpt.map(UserRow2User)).transactionally
      }

      override def userByEmail(email: String): RepositoryIO[Option[User]] =
        UserQuery
          .filter(u => u.email === email && !u.deleted).result.headOption.map(_.map(UserRow2User))

      override def changePassword(
        user:        User,
        newPassword: String
      ): RepositoryIO[Boolean] = { session: ChutiSession =>
        if (session.user != GameServer.god && session.user.id != user.id) {
          throw RepositoryException(s"${session.user} Not authorized")
        }
        user.id.fold(DBIO.successful(false))(id =>
          sqlu"UPDATE user SET hashedPassword=SHA2($newPassword, 512) WHERE id = ${id.value}"
            .map(_ > 0)
        )
      }
    }
    override val gameStateOperations: Repository.GameStateOperations = new GameStateOperations {
      override def upsert(e: GameState): RepositoryIO[GameState] = ???

      override def get(pk: Int): RepositoryIO[Option[GameState]] = ???

      override def delete(
        pk:         Int,
        softDelete: Boolean
      ): RepositoryIO[Boolean] = ???

      override def search(search: Option[EmptySearch]): RepositoryIO[Seq[GameState]] =
        ???

      override def count(search: Option[EmptySearch]): RepositoryIO[Long] = ???

    }
  }
}
