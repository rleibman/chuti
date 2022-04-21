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

package dao.gen

import java.sql.Timestamp

import chuti.{GameId, GameStatus, UserId}
import com.foerstertechnologies.slickmysql.{ExMySQLProfile, MySQLCirceJsonSupport}
import io.circe.Json
import slick.ast.BaseTypedType
import slick.jdbc.JdbcType
import slick.lifted.{MappedProjection, ProvenShape}
// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object ChutiProfile extends ExMySQLProfile with MySQLCirceJsonSupport

object Tables extends {
  val profile = ChutiProfile
} with Tables

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait Tables {
  val profile: ExMySQLProfile & MySQLCirceJsonSupport

  import slick.jdbc.{GetResult => GR}
  import slick.model.ForeignKeyAction

  object MyApi extends profile.API with profile.CirceJsonImplicits
  import MyApi.*

  implicit val estadoType: JdbcType[GameStatus] & BaseTypedType[GameStatus] =
    profile.MappedColumnType.base[GameStatus, String](
      estado => if (estado == null) null else estado.value,
      str => if (str == null) null else GameStatus.withName(str)
    )

  implicit lazy val userIdColumnType: JdbcType[UserId] & BaseTypedType[UserId] =
    MappedColumnType.base[UserId, Int](_.value, UserId)
  implicit lazy val gameIdColumnType: JdbcType[GameId] & BaseTypedType[GameId] =
    MappedColumnType.base[GameId, Int](_.value, GameId)

  val hashPassword: (Rep[String], Rep[Int]) => Rep[String] =
    SimpleFunction.binary[String, Int, String]("SHA2")

  /** DDL for all tables. Call .create to execute. */
  lazy val schema: profile.SchemaDescription =
    FriendsQuery.schema ++ GameQuery.schema ++ GameEventQuery.schema ++ GamePlayersQuery.schema ++ UserQuery.schema ++ UserWalletQuery.schema

  case class FriendsRow(
    one: UserId,
    two: UserId
  )

  implicit def GetResultFriendsRow(
    implicit e0: GR[UserId],
    e1:          GR[Boolean]
  ): GR[FriendsRow] =
    GR { prs =>
      import prs.*
      FriendsRow.tupled((<<[UserId], <<[UserId]))
    }

  class Friends(_tableTag: Tag)
      extends profile.api.Table[FriendsRow](_tableTag, Some("chuti"), "friends") {
    def * : ProvenShape[FriendsRow] =
      (one, two) <> (FriendsRow.tupled, FriendsRow.unapply)

    def ? : MappedProjection[Option[FriendsRow], (Option[UserId], Option[UserId])] =
      (Rep.Some(one), Rep.Some(two)).shaped.<>(
        { r =>
          import r.*
          _1.map(_ => FriendsRow.tupled((_1.get, _2.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    val one: Rep[UserId] = column[UserId]("one")

    val two: Rep[UserId] = column[UserId]("two")

    lazy val userFk1 = foreignKey("friends_user_1", one, UserQuery)(
      r => r.id,
      onUpdate = ForeignKeyAction.Restrict,
      onDelete = ForeignKeyAction.Restrict
    )

    lazy val userFk2 = foreignKey("friends_user_2", two, UserQuery)(
      r => r.id,
      onUpdate = ForeignKeyAction.Restrict,
      onDelete = ForeignKeyAction.Restrict
    )
  }

  lazy val FriendsQuery = new TableQuery(tag => new Friends(tag))

  case class GameRow(
    id:           GameId,
    lastSnapshot: Json,
    gameStatus:   GameStatus,
    created:      Timestamp,
    lastupdated:  Timestamp,
    currentIndex: Int = 0
  )

  implicit def GetResultGameRow(
    implicit e0: GR[GameId],
    e5:          GR[Int],
    e1:          GR[Json],
    e6:          GR[GameStatus],
    e2:          GR[Timestamp]
  ): GR[GameRow] =
    GR { prs =>
      import prs.*
      GameRow.tupled(
        (
          <<[GameId],
          <<[Json],
          <<[GameStatus],
          <<[Timestamp],
          <<[Timestamp],
          <<[Int]
        )
      )
    }

  class GameTable(_tableTag: Tag)
      extends profile.api.Table[GameRow](_tableTag, Some("chuti"), "game") {
    def * : ProvenShape[GameRow] =
      (
        id,
        lastSnapshot,
        gameStatus,
        created,
        lastupdated,
        currentIndex
      ) <> (GameRow.tupled, GameRow.unapply)

    def ? : MappedProjection[Option[
      GameRow
    ], (Option[GameId], Option[Json], Option[GameStatus], Option[Timestamp], Option[Timestamp], Option[Int])] =
      (
        Rep.Some(id),
        Rep.Some(lastSnapshot),
        Rep.Some(gameStatus),
        Rep.Some(created),
        Rep.Some(lastupdated),
        Rep.Some(currentIndex)
      ).shaped.<>(
        { r =>
          import r.*
          _1.map(_ => GameRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    val id: Rep[GameId] = column[GameId]("id", O.AutoInc, O.PrimaryKey)

    val currentIndex: Rep[Int] = column[Int]("current_index", O.Default(0))

    val lastSnapshot: Rep[Json] =
      column[Json](
        "lastSnapshot",
        O.Length(1073741824, varying = true),
        O.SqlType(typeName = "JSON")
      )

    val gameStatus: Rep[GameStatus] =
      column[GameStatus]("status", O.Length(1073741824, varying = true))

    val created: Rep[Timestamp] = column[Timestamp]("created")

    val lastupdated: Rep[Timestamp] = column[Timestamp]("lastUpdated")
  }

  lazy val GameQuery = new TableQuery(tag => new GameTable(tag))

  case class GameEventRow(
    gameId:       GameId,
    currentIndex: Int = 0,
    eventData:    String
  )

  implicit def GetResultGameEventRow(
    implicit e0: GR[GameId],
    e3:          GR[Int],
    e1:          GR[String]
  ): GR[GameEventRow] =
    GR { prs =>
      import prs.*
      GameEventRow.tupled((<<[GameId], <<[Int], <<[String]))
    }

  class GameEvent(_tableTag: Tag)
      extends profile.api.Table[GameEventRow](_tableTag, Some("chuti"), "game_event") {
    def * : ProvenShape[GameEventRow] =
      (gameId, currentIndex, eventData) <> (GameEventRow.tupled, GameEventRow.unapply)

    def ? : MappedProjection[Option[GameEventRow], (Option[GameId], Option[Int], Option[String])] =
      (Rep.Some(gameId), Rep.Some(currentIndex), Rep.Some(eventData)).shaped.<>(
        { r =>
          import r.*
          _1.map(_ => GameEventRow.tupled((_1.get, _2.get, _3.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    val gameId: Rep[GameId] = column[GameId]("game_id")

    val currentIndex: Rep[Int] = column[Int]("current_index", O.Default(0))

    val eventData: Rep[String] = column[String]("event_data", O.Length(1073741824, varying = true))

    val pk = primaryKey("game_event_PK", (gameId, currentIndex))

    lazy val gameFk = foreignKey("game_event_game", gameId, GameQuery)(
      r => r.id,
      onUpdate = ForeignKeyAction.Restrict,
      onDelete = ForeignKeyAction.Restrict
    )
  }

  lazy val GameEventQuery = new TableQuery(tag => new GameEvent(tag))

  case class GamePlayersRow(
    userId:  UserId,
    gameId:  GameId,
    order:   Int,
    invited: Boolean
  )

  implicit def GetResultGamePlayersRow(
    implicit e0: GR[GameId],
    e1:          GR[UserId],
    e2:          GR[Int],
    e3:          GR[Boolean]
  ): GR[GamePlayersRow] =
    GR { prs =>
      import prs.*
      GamePlayersRow.tupled((<<[UserId], <<[GameId], <<[Int], <<[Boolean]))
    }

  class GamePlayers(_tableTag: Tag)
      extends profile.api.Table[GamePlayersRow](_tableTag, Some("chuti"), "game_players") {
    def * : ProvenShape[GamePlayersRow] =
      (userId, gameId, order, invited) <> (GamePlayersRow.tupled, GamePlayersRow.unapply)

    def ? : MappedProjection[Option[
      GamePlayersRow
    ], (Option[UserId], Option[GameId], Option[Int], Option[Boolean])] =
      (Rep.Some(userId), Rep.Some(gameId), Rep.Some(order), Rep.Some(invited)).shaped.<>(
        { r =>
          import r.*
          _1.map(_ => GamePlayersRow.tupled((_1.get, _2.get, _3.get, _4.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    val userId: Rep[UserId] = column[UserId]("user_id")

    val gameId: Rep[GameId] = column[GameId]("game_id")

    val order: Rep[Int] = column[Int]("order")

    val invited: Rep[Boolean] = column[Boolean]("invited", O.Default(false))

    val pk = primaryKey("game_players_PK", (userId, gameId))

    lazy val gameFk = foreignKey("game_players_game", gameId, GameQuery)(
      r => r.id,
      onUpdate = ForeignKeyAction.Restrict,
      onDelete = ForeignKeyAction.Restrict
    )

    lazy val userFk = foreignKey("game_players_user", userId, UserQuery)(
      r => r.id,
      onUpdate = ForeignKeyAction.Restrict,
      onDelete = ForeignKeyAction.Restrict
    )
  }

  lazy val GamePlayersQuery = new TableQuery(tag => new GamePlayers(tag))

  case class UserRow(
    id:          UserId,
    name:        String,
    email:       String,
    created:     Timestamp,
    active:      Boolean = false,
    deleted:     Boolean = false,
    deleteddate: Option[Timestamp] = None
  )

  implicit def GetResultUserRow(
    implicit e0: GR[UserId],
    e1:          GR[String],
    e2:          GR[Timestamp],
    e3:          GR[Option[Timestamp]],
    e4:          GR[Boolean]
  ): GR[UserRow] =
    GR { prs =>
      import prs.*
      UserRow.tupled(
        (
          <<[UserId],
          <<[String],
          <<[String],
          <<[Timestamp],
          <<[Boolean],
          <<[Boolean],
          <<?[Timestamp]
        )
      )
    }

  class UserTable(_tableTag: Tag)
      extends profile.api.Table[UserRow](_tableTag, Some("chuti"), "user") {
    def * : ProvenShape[UserRow] =
      (
        id,
        name,
        email,
        created,
        active,
        deleted,
        deleteddate
      ) <> (UserRow.tupled, UserRow.unapply)

    def ? : MappedProjection[Option[
      UserRow
    ], (Option[UserId], Option[String], Option[String], Option[Timestamp], Option[Boolean], Option[Boolean], Option[Timestamp])] =
      (
        Rep.Some(id),
        Rep.Some(name),
        Rep.Some(email),
        Rep.Some(created),
        Rep.Some(active),
        Rep.Some(deleted),
        deleteddate
      ).shaped.<>(
        { r =>
          import r.*
          _1.map(_ =>
            UserRow.tupled(
              (_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7)
            )
          )
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    val id: Rep[UserId] = column[UserId]("id", O.AutoInc, O.PrimaryKey)

    val name: Rep[String] = column[String]("name")

    val email: Rep[String] =
      column[String]("email", O.Length(255, varying = true))

    val created: Rep[Timestamp] = column[Timestamp]("created")

    val deleted: Rep[Boolean] = column[Boolean]("deleted", O.Default(false))

    val active: Rep[Boolean] = column[Boolean]("active", O.Default(false))

    val deleteddate: Rep[Option[Timestamp]] =
      column[Option[Timestamp]]("deletedDate", O.Default(None))

    val index1 = index("email", email, unique = true)
  }

  lazy val UserQuery = new TableQuery(tag => new UserTable(tag))

  case class UserWalletRow(
    user:   UserId,
    amount: BigDecimal
  )

  implicit def GetResultUserWalletRow(
    implicit e0: GR[UserId],
    e1:          GR[BigDecimal]
  ): GR[UserWalletRow] =
    GR { prs =>
      import prs.*
      UserWalletRow.tupled((<<[UserId], <<[BigDecimal]))
    }

  class UserWalletTable(_tableTag: Tag)
      extends profile.api.Table[UserWalletRow](_tableTag, Some("chuti"), "userWallet") {
    def * : ProvenShape[UserWalletRow] =
      (userId, amount) <> (UserWalletRow.tupled, UserWalletRow.unapply)

    def ? : MappedProjection[Option[UserWalletRow], (Option[UserId], Option[BigDecimal])] =
      (Rep.Some(userId), Rep.Some(amount)).shaped.<>(
        { r =>
          import r.*
          _1.map(_ => UserWalletRow.tupled((_1.get, _2.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    val userId: Rep[UserId] = column[UserId]("userId", O.PrimaryKey)

    val amount: Rep[BigDecimal] = column[BigDecimal]("amount")

    lazy val userFk1 = foreignKey("wallet_user_1", userId, UserQuery)(
      r => r.id,
      onUpdate = ForeignKeyAction.Restrict,
      onDelete = ForeignKeyAction.Restrict
    )

  }

  lazy val UserWalletQuery = new TableQuery(tag => new UserWalletTable(tag))

  case class UserLogRow(
    user: UserId,
    time: Timestamp
  )

  implicit def GetResultUserLogRow(
    implicit e0: GR[UserId],
    e1:          GR[Timestamp]
  ): GR[UserLogRow] =
    GR { prs =>
      import prs.*
      UserLogRow.tupled((<<[UserId], <<[Timestamp]))
    }

  class UserLogTable(_tableTag: Tag)
      extends profile.api.Table[UserLogRow](_tableTag, Some("chuti"), "userLog") {
    def * : ProvenShape[UserLogRow] =
      (userId, time) <> (UserLogRow.tupled, UserLogRow.unapply)

    def ? : MappedProjection[Option[UserLogRow], (Option[UserId], Option[Timestamp])] =
      (Rep.Some(userId), Rep.Some(time)).shaped.<>(
        { r =>
          import r.*
          _1.map(_ => UserLogRow.tupled((_1.get, _2.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    val userId: Rep[UserId] = column[UserId]("userId")

    val time: Rep[Timestamp] = column[Timestamp]("time")

    lazy val userFk1 = foreignKey("wallet_user_1", userId, UserQuery)(
      r => r.id,
      onUpdate = ForeignKeyAction.Restrict,
      onDelete = ForeignKeyAction.Restrict
    )

  }

  lazy val UserLogQuery = new TableQuery(tag => new UserLogTable(tag))

  case class TokenRow(
    tok:          String,
    tokenPurpose: String,
    expireTime:   Timestamp,
    userId:       UserId
  )

  implicit def GetResultTokenRow(
    implicit e0: GR[String],
    e1:          GR[Timestamp],
    e2:          GR[Long],
    e3:          GR[UserId]
  ): GR[TokenRow] =
    GR { prs =>
      import prs.*
      TokenRow.tupled((<<[String], <<[String], <<[Timestamp], <<[UserId]))
    }

  class TokenTable(_tableTag: Tag)
      extends profile.api.Table[TokenRow](_tableTag, Some("chuti"), "token") {
    def * : ProvenShape[TokenRow] =
      (tok, tokenPurpose, expireTime, userId) <> (TokenRow.tupled, TokenRow.unapply)

    def ? : MappedProjection[Option[
      TokenRow
    ], (Option[String], Option[String], Option[Timestamp], Option[UserId])] =
      (
        Rep.Some(tok),
        Rep.Some(tokenPurpose),
        Rep.Some(expireTime),
        Rep.Some(userId)
      ).shaped.<>(
        { r =>
          import r.*
          _1.map(_ => TokenRow.tupled((_1.get, _2.get, _3.get, _4.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    val tok:          Rep[String] = column[String]("tok")
    val tokenPurpose: Rep[String] = column[String]("tokenPurpose")
    val expireTime:   Rep[Timestamp] = column[Timestamp]("expireTime")
    val userId:       Rep[UserId] = column[UserId]("userId")

    lazy val userFk1 = foreignKey("token_user_id", userId, UserQuery)(
      r => r.id,
      onUpdate = ForeignKeyAction.Restrict,
      onDelete = ForeignKeyAction.Restrict
    )
  }

  lazy val TokenQuery = new TableQuery(tag => new TokenTable(tag))
}
