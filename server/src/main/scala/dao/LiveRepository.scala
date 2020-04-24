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
import chuti.{EmptySearch, Estado, GameId, Game, PagedStringSearch, User, UserId}
import dao.Repository.{GameOperations, UserOperations}
import dao.gen.Tables
import dao.gen.Tables._
import game.GameService
import slick.SlickException
import slick.dbio.DBIO
import slick.jdbc.MySQLProfile.api._
import zio.ZIO
import zioslick.RepositoryException

import scala.concurrent.ExecutionContext
import scala.language.implicitConversions

trait LiveRepository extends Repository.Service with SlickToModelInterop {

  implicit val dbExecutionContext: ExecutionContext = zio.Runtime.default.platform.executor.asEC
  implicit def fromDBIO[A](dbio: DBIO[A]): RepositoryIO[A] =
    for {
      db <- ZIO.accessM[DatabaseProvider](_.get.db)
      ret <- ZIO.fromFuture(implicit ec => db.run(dbio)).mapError {
        case e: SlickException =>
          e.printStackTrace()
          RepositoryException("Slick Repository Error", Some(e))
        case e: SQLException =>
          e.printStackTrace()
          RepositoryException("SQL Repository Error", Some(e))
      }
    } yield ret

  implicit def fromDBIO[A](fn: ChutiSession => DBIO[A]): RepositoryIO[A] =
    for {
      session <- ZIO.access[SessionProvider](_.get.session)
      db      <- ZIO.accessM[DatabaseProvider](_.get.db)
      ret <- ZIO.fromFuture(implicit ec => db.run(fn(session))).mapError {
        case e: SlickException =>
          e.printStackTrace()
          RepositoryException("Slick Repository Error", Some(e))
        case e: SQLException =>
          e.printStackTrace()
          RepositoryException("SQL Repository Error", Some(e))
      }
    } yield ret

  override val userOperations: Repository.UserOperations = new UserOperations {
    override def unfriend(enemy: User): RepositoryIO[Boolean] = { session: ChutiSession =>
      val rowOpt = for {
        one <- session.user.id
        two <- enemy.id
      } yield { FriendsRow(one, two) }
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
      } yield { FriendsRow(one, two) }
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
                .join(FriendsQuery.filter(f => f.one === id || f.two === id)).on { (a, b) =>
                  a.id === b.one || a.id === b.two
                }.map(_._1).result
            }
        ).map(_.flatten.map(UserRow2User))
    }

    override def upsert(user: User): RepositoryIO[User] = { session: ChutiSession =>
      if (session.user != GameService.god && session.user.id != user.id) {
        throw RepositoryException(s"${session.user} Not authorized")
      }
      (UserQuery returning UserQuery.map(_.id) into ((_, id) => user.copy(id = Some(id))))
        .insertOrUpdate(User2UserRow(user))
        .map(_.getOrElse(user))
    }

    def get(pk: UserId): RepositoryIO[Option[User]] =
      fromDBIO {
        UserQuery
          .filter(u => u.id === pk && !u.deleted).result.headOption.map(
            _.map(UserRow2User)
          )
      }

    override def delete(
      pk:         UserId,
      softDelete: Boolean
    ): RepositoryIO[Boolean] = { session: ChutiSession =>
      if (session.user != GameService.god && session.user.id.fold(false)(_ != pk)) {
        throw RepositoryException(s"${session.user} Not authorized")
      }
      if (softDelete) {
        val q = for {
          u <- UserQuery if u.id === pk
        } yield (u.deleted, u.deleteddate)
        q.update(true, Option(new Timestamp(System.currentTimeMillis()))).map(_ > 0)
      } else {
        UserQuery.filter(_.id === pk).delete.map(_ > 0)
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
      if (session.user != GameService.god && session.user.id != user.id) {
        throw RepositoryException(s"${session.user} Not authorized")
      }
      user.id.fold(DBIO.successful(false))(id =>
        sqlu"UPDATE user SET hashedPassword=SHA2($newPassword, 512) WHERE id = ${id.value}"
          .map(_ > 0)
      )
    }
  }
  override val gameOperations: Repository.GameOperations = new GameOperations {
    override def upsert(game: Game): RepositoryIO[Game] = { session: ChutiSession =>
      if (session.user != GameService.god && !game.jugadores.exists(_.user.id == session.user.id)) {
        throw RepositoryException(s"${session.user} Not authorized")
      }
      for {
        upserted <- (GameQuery returning GameQuery.map(_.id) into (
          (
            _,
            id
          ) => game.copy(id = Some(id))
        )).insertOrUpdate(Game2GameRow(game))
          .map(_.getOrElse(game))
        _ <- DBIO.sequence(game.jugadores.zipWithIndex.map {
          case (player, index) =>
            GamePlayersQuery.insertOrUpdate(
              GamePlayersRow(
                player.user.id.getOrElse(UserId(0)),
                upserted.id.getOrElse(GameId(0)),
                index
              )
            )
        })
      } yield game
    }

    override def get(pk: GameId): RepositoryIO[Option[Game]] =
      GameQuery
        .filter(_.id === pk)
        .result
        .map(_.headOption.map(row => GameRow2Game(row)))

    override def delete(
      pk:         GameId,
      softDelete: Boolean
    ): RepositoryIO[Boolean] = { session: ChutiSession =>
      if (session.user != GameService.god) {
        throw RepositoryException(s"${session.user} Not authorized")
      }
      if (softDelete) {
        val q = for {
          g <- GameQuery if g.id === pk
        } yield (g.deleted, g.deleteddate)
        q.update(true, Option(new Timestamp(System.currentTimeMillis()))).map(_ > 0)
      } else {
        GameQuery.filter(_.id === pk).delete.map(_ > 0)
      }

    }

    override def search(search: Option[EmptySearch]): RepositoryIO[Seq[Game]] =
      ???

    override def count(search: Option[EmptySearch]): RepositoryIO[Long] = ???

    override def gamesWaitingForPlayers(): RepositoryIO[Seq[Game]] =
      GameQuery
        .filter(g => g.gameStatus === (Estado.esperandoJugadoresAzar: Estado) && !g.deleted)
        .result
        .map(_.map(row => GameRow2Game(row)))

    override def getGameForUser: RepositoryIO[Option[Game]] = { session: ChutiSession =>
      GamePlayersQuery
        .filter(_.userId === session.user.id.getOrElse(UserId(-1)))
        .join(GameQuery.filter(!_.deleted)).on(_.gameId === _.id)
        .result
        .map(_.headOption.map(row => GameRow2Game(row._2)))
    }
  }
}
