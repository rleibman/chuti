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

import chuti.{GameStatus, GameId, UserId}
import com.foerstertechnologies.slickmysql.{ExMySQLProfile, MySQLCirceJsonSupport}
import io.circe.Json
import slick.ast.BaseTypedType
import slick.driver.JdbcProfile
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
  val profile: ExMySQLProfile with MySQLCirceJsonSupport

  import slick.model.ForeignKeyAction

  import slick.jdbc.{GetResult => GR}

  object MyApi extends profile.API with profile.CirceJsonImplicits
  import MyApi._

  implicit val estadoType: JdbcType[GameStatus] with BaseTypedType[GameStatus] =
    profile.MappedColumnType.base[GameStatus, String](
      { estado =>
        if (estado == null) null else estado.value
      }, { str =>
        if (str == null) null else GameStatus.withName(str)
      }
    )

  implicit lazy val userIdColumnType: JdbcType[UserId] with BaseTypedType[UserId] =
    MappedColumnType.base[UserId, Int](_.value, UserId)
  implicit lazy val gameIdColumnType: JdbcType[GameId] with BaseTypedType[GameId] =
    MappedColumnType.base[GameId, Int](_.value, GameId)

  val hashPassword: (Rep[String], Rep[Int]) => Rep[String] =
    SimpleFunction.binary[String, Int, String]("SHA2")

  /** DDL for all tables. Call .create to execute. */
  lazy val schema
    : profile.SchemaDescription = FriendsQuery.schema ++ GameQuery.schema ++ GameEventQuery.schema ++ GamePlayersQuery.schema ++ UserQuery.schema

  /** Entity class storing rows of table Friends
    *  @param one Database column one SqlType(INT)
    *  @param two Database column two SqlType(INT) */
  case class FriendsRow(
    one: UserId,
    two: UserId,
    confirmed: Boolean
  )

  /** GetResult implicit for fetching FriendsRow objects using plain SQL queries */
  implicit def GetResultFriendsRow(implicit e0: GR[UserId], e1: GR[Boolean]): GR[FriendsRow] = GR { prs =>
    import prs._
    FriendsRow.tupled((<<[UserId], <<[UserId], << [Boolean]))
  }

  /** Table description of table friends. Objects of this class serve as prototypes for rows in queries. */
  class Friends(_tableTag: Tag)
      extends profile.api.Table[FriendsRow](_tableTag, Some("chuti"), "friends") {
    def * : ProvenShape[FriendsRow] = (one, two, confirmed) <> (FriendsRow.tupled, FriendsRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? : MappedProjection[Option[FriendsRow], (Option[UserId], Option[UserId], Option[Boolean])] =
      (Rep.Some(one), Rep.Some(two), Rep.Some(confirmed)).shaped.<>(
        { r =>
          import r._; _1.map(_ => FriendsRow.tupled((_1.get, _2.get, _3.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column one SqlType(INT) */
    val one: Rep[UserId] = column[UserId]("one")

    /** Database column two SqlType(INT) */
    val two: Rep[UserId] = column[UserId]("two")

    /** Database column confirmed SqlType(TINYINT), Default(0) */
    val confirmed: Rep[Boolean] = column[Boolean]("confirmed", O.Default(false))

    /** Foreign key referencing User (database name friends_user_1) */
    lazy val userFk1 = foreignKey("friends_user_1", one, UserQuery)(
      r => r.id,
      onUpdate = ForeignKeyAction.Restrict,
      onDelete = ForeignKeyAction.Restrict
    )

    /** Foreign key referencing User (database name friends_user_2) */
    lazy val userFk2 = foreignKey("friends_user_2", two, UserQuery)(
      r => r.id,
      onUpdate = ForeignKeyAction.Restrict,
      onDelete = ForeignKeyAction.Restrict
    )
  }

  /** Collection-like TableQuery object for table Friends */
  lazy val FriendsQuery = new TableQuery(tag => new Friends(tag))

  /** Entity class storing rows of table Game
    *  @param id Database column id SqlType(INT), AutoInc, PrimaryKey
    *  @param currentIndex Database column current_index SqlType(INT), Default(0)
    *  @param startState Database column start_state SqlType(JSON), Length(1073741824,true)
    *  @param gameStatus Database column game_state SqlType(JSON), Length(1073741824,true)
    *  @param created Database column created SqlType(TIMESTAMP)
    *  @param lastupdated Database column lastUpdated SqlType(TIMESTAMP)
    *  @param deleted Database column deleted SqlType(TINYINT), Default(0)
    *  @param deleteddate Database column deletedDate SqlType(TIMESTAMP), Default(None) */
  case class GameRow(
                      id:           GameId,
                      startState:   Json, //TODO rename startState to lastSnapshot
                      gameStatus:   GameStatus,
                      created:      java.sql.Timestamp,
                      lastupdated:  java.sql.Timestamp,
                      currentIndex: Int = 0,
                      deleted:      Boolean = false, //TODO Remove this, gameStatus takes care of it
                      deleteddate:  Option[java.sql.Timestamp] = None //TODO remove, lastUpdated will take care of it.
  )

  /** GetResult implicit for fetching GameRow objects using plain SQL queries */
  implicit def GetResultGameRow(
                                 implicit e0: GR[GameId],
                                 e5:          GR[Int],
                                 e1:          GR[Json],
                                 e6:          GR[GameStatus],
                                 e2:          GR[java.sql.Timestamp],
                                 e3:          GR[Boolean],
                                 e4:          GR[Option[java.sql.Timestamp]]
  ): GR[GameRow] = GR { prs =>
    import prs._
    GameRow.tupled(
      (
        <<[GameId],
        <<[Json],
        <<[GameStatus],
        <<[java.sql.Timestamp],
        <<[java.sql.Timestamp],
        <<[Int],
        <<[Boolean],
        <<?[java.sql.Timestamp]
      )
    )
  }

  /** Table description of table game. Objects of this class serve as prototypes for rows in queries. */
  class Game(_tableTag: Tag) extends profile.api.Table[GameRow](_tableTag, Some("chuti"), "game") {
    def * : ProvenShape[GameRow] =
      (id, startState, gameStatus, created, lastupdated, currentIndex, deleted, deleteddate) <> (GameRow.tupled, GameRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ?
      : MappedProjection[Option[GameRow], (Option[GameId], Option[Json], Option[GameStatus], Option[Timestamp], Option[Timestamp], Option[Int], Option[Boolean], Option[Timestamp])] =
      (
        Rep.Some(id),
        Rep.Some(startState),
        Rep.Some(gameStatus),
        Rep.Some(created),
        Rep.Some(lastupdated),
        Rep.Some(currentIndex),
        Rep.Some(deleted),
        deleteddate
      ).shaped.<>(
        { r =>
          import r._
          _1.map(_ => GameRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column id SqlType(INT), AutoInc, PrimaryKey */
    val id: Rep[GameId] = column[GameId]("id", O.AutoInc, O.PrimaryKey)

    /** Database column current_index SqlType(INT), Default(0) */
    val currentIndex: Rep[Int] = column[Int]("current_index", O.Default(0))

    /** Database column start_state SqlType(JSON), Length(1073741824,true) */
    val startState: Rep[Json] =
      column[Json](
        "start_state",
        O.Length(1073741824, varying = true),
        O.SqlType(typeName = "JSON")
      )

    /** Database column game_state SqlType(TEXT), Length(1073741824,true) */
    val gameStatus
      : Rep[GameStatus] = column[GameStatus]("game_state", O.Length(1073741824, varying = true)) //TODO rename to status

    /** Database column created SqlType(TIMESTAMP) */
    val created: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created")

    /** Database column lastUpdated SqlType(TIMESTAMP) */
    val lastupdated: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("lastUpdated")

    /** Database column deleted SqlType(TINYINT), Default(0) */
    val deleted: Rep[Boolean] = column[Boolean]("deleted", O.Default(false))

    /** Database column deletedDate SqlType(TIMESTAMP), Default(None) */
    val deleteddate: Rep[Option[java.sql.Timestamp]] =
      column[Option[java.sql.Timestamp]]("deletedDate", O.Default(None))
  }

  /** Collection-like TableQuery object for table Game */
  lazy val GameQuery = new TableQuery(tag => new Game(tag))

  /** Entity class storing rows of table GameEvent
    *  @param gameId Database column game_id SqlType(INT)
    *  @param currentIndex Database column current_index SqlType(INT), Default(0)
    *  @param eventData Database column event_data SqlType(JSON), Length(1073741824,true) */
  case class GameEventRow(
    gameId:       GameId,
    currentIndex: Int = 0,
    eventData:    String
  )

  /** GetResult implicit for fetching GameEventRow objects using plain SQL queries */
  implicit def GetResultGameEventRow(
    implicit e0: GR[GameId],
    e3:          GR[Int],
    e1:          GR[String]
  ): GR[GameEventRow] = GR { prs =>
    import prs._
    GameEventRow.tupled((<<[GameId], <<[Int], <<[String]))
  }

  /** Table description of table game_event. Objects of this class serve as prototypes for rows in queries. */
  class GameEvent(_tableTag: Tag)
      extends profile.api.Table[GameEventRow](_tableTag, Some("chuti"), "game_event") {
    def * : ProvenShape[GameEventRow] =
      (gameId, currentIndex, eventData) <> (GameEventRow.tupled, GameEventRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ? : MappedProjection[Option[GameEventRow], (Option[GameId], Option[Int], Option[String])] =
      (Rep.Some(gameId), Rep.Some(currentIndex), Rep.Some(eventData)).shaped.<>(
        { r =>
          import r._; _1.map(_ => GameEventRow.tupled((_1.get, _2.get, _3.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column game_id SqlType(INT) */
    val gameId: Rep[GameId] = column[GameId]("game_id")

    /** Database column current_index SqlType(INT), Default(0) */
    val currentIndex: Rep[Int] = column[Int]("current_index", O.Default(0))

    /** Database column event_data SqlType(JSON), Length(1073741824,true) */
    val eventData: Rep[String] = column[String]("event_data", O.Length(1073741824, varying = true))

    /** Primary key of GameEvent (database name game_event_PK) */
    val pk = primaryKey("game_event_PK", (gameId, currentIndex))

    /** Foreign key referencing Game (database name game_event_game) */
    lazy val gameFk = foreignKey("game_event_game", gameId, GameQuery)(
      r => r.id,
      onUpdate = ForeignKeyAction.Restrict,
      onDelete = ForeignKeyAction.Restrict
    )
  }

  /** Collection-like TableQuery object for table GameEvent */
  lazy val GameEventQuery = new TableQuery(tag => new GameEvent(tag))

  /** Entity class storing rows of table GamePlayers
    *  @param userId Database column user_id SqlType(INT)
    *  @param gameId Database column game_id SqlType(INT) */
  case class GamePlayersRow(
    userId: UserId,
    gameId: GameId,
    order:  Int,
    invited: Boolean
  )

  /** GetResult implicit for fetching GamePlayersRow objects using plain SQL queries */
  implicit def GetResultGamePlayersRow(
    implicit e0: GR[GameId],
    e1:          GR[UserId],
    e2:          GR[Int],
    e3: GR[Boolean]
  ): GR[GamePlayersRow] = GR { prs =>
    import prs._
    GamePlayersRow.tupled((<<[UserId], <<[GameId], <<[Int], <<[Boolean]))
  }

  /** Table description of table game_players. Objects of this class serve as prototypes for rows in queries. */
  class GamePlayers(_tableTag: Tag)
      extends profile.api.Table[GamePlayersRow](_tableTag, Some("chuti"), "game_players") {
    def * : ProvenShape[GamePlayersRow] =
      (userId, gameId, order, invited) <> (GamePlayersRow.tupled, GamePlayersRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ?
      : MappedProjection[Option[GamePlayersRow], (Option[UserId], Option[GameId], Option[Int], Option[Boolean])] =
      (Rep.Some(userId), Rep.Some(gameId), Rep.Some(order), Rep.Some(invited)).shaped.<>(
        { r =>
          import r._; _1.map(_ => GamePlayersRow.tupled((_1.get, _2.get, _3.get, _4.get)))
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column user_id SqlType(INT) */
    val userId: Rep[UserId] = column[UserId]("user_id")

    /** Database column game_id SqlType(INT) */
    val gameId: Rep[GameId] = column[GameId]("game_id")

    val order: Rep[Int] = column[Int]("order")

    /** Database column invited SqlType(TINYINT), Default(0) */
    val invited: Rep[Boolean] = column[Boolean]("invited", O.Default(false))

    /** Primary key of GameEvent (database name game_event_PK) */
    val pk = primaryKey("game_players_PK", (userId, gameId))

    /** Foreign key referencing Game (database name game_players_game) */
    lazy val gameFk = foreignKey("game_players_game", gameId, GameQuery)(
      r => r.id,
      onUpdate = ForeignKeyAction.Restrict,
      onDelete = ForeignKeyAction.Restrict
    )

    /** Foreign key referencing User (database name game_players_user) */
    lazy val userFk = foreignKey("game_players_user", userId, UserQuery)(
      r => r.id,
      onUpdate = ForeignKeyAction.Restrict,
      onDelete = ForeignKeyAction.Restrict
    )
  }

  /** Collection-like TableQuery object for table GamePlayers */
  lazy val GamePlayersQuery = new TableQuery(tag => new GamePlayers(tag))

  /** Entity class storing rows of table User
    *  @param id Database column id SqlType(INT), AutoInc, PrimaryKey
    *  @param hashedpassword Database column hashedPassword SqlType(TEXT)
    *  @param name Database column name SqlType(TEXT)
    *  @param created Database column created SqlType(TIMESTAMP)
    *  @param lastupdated Database column lastUpdated SqlType(TIMESTAMP)
    *  @param lastloggedin Database column lastLoggedIn SqlType(TIMESTAMP), Default(None)
    *  @param email Database column email SqlType(VARCHAR), Length(255,true), Default(None)
    *  @param deleted Database column deleted SqlType(TINYINT), Default(0)
    *  @param deleteddate Database column deletedDate SqlType(TIMESTAMP), Default(None) */
  case class UserRow(
    id:             UserId,
    hashedpassword: String,
    name:           String,
    email:          String,
    created:        java.sql.Timestamp,
    lastupdated:    java.sql.Timestamp,
    lastloggedin:   Option[java.sql.Timestamp] = None,
    deleted:        Boolean = false,
    deleteddate:    Option[java.sql.Timestamp] = None
  )

  /** GetResult implicit for fetching UserRow objects using plain SQL queries */
  implicit def GetResultUserRow(
    implicit e0: GR[UserId],
    e1:          GR[String],
    e2:          GR[java.sql.Timestamp],
    e3:          GR[Option[java.sql.Timestamp]],
    e4:          GR[Boolean]
  ): GR[UserRow] = GR { prs =>
    import prs._
    UserRow.tupled(
      (
        <<[UserId],
        <<[String],
        <<[String],
        <<[String],
        <<[java.sql.Timestamp],
        <<[java.sql.Timestamp],
        <<?[java.sql.Timestamp],
        <<[Boolean],
        <<?[java.sql.Timestamp]
      )
    )
  }

  /** Table description of table user. Objects of this class serve as prototypes for rows in queries. */
  class User(_tableTag: Tag) extends profile.api.Table[UserRow](_tableTag, Some("chuti"), "user") {
    def * : ProvenShape[UserRow] =
      (
        id,
        hashedpassword,
        name,
        email,
        created,
        lastupdated,
        lastloggedin,
        deleted,
        deleteddate
      ) <> (UserRow.tupled, UserRow.unapply)

    /** Maps whole row to an option. Useful for outer joins. */
    def ?
      : MappedProjection[Option[UserRow], (Option[UserId], Option[String], Option[String], Option[String], Option[Timestamp], Option[Timestamp], Option[Timestamp], Option[Boolean], Option[Timestamp])] =
      (
        Rep.Some(id),
        Rep.Some(hashedpassword),
        Rep.Some(name),
        Rep.Some(email),
        Rep.Some(created),
        Rep.Some(lastupdated),
        lastloggedin,
        Rep.Some(deleted),
        deleteddate
      ).shaped.<>(
        { r =>
          import r._
          _1.map(_ =>
            UserRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7, _8.get, _9))
          )
        },
        (_: Any) => throw new Exception("Inserting into ? projection not supported.")
      )

    /** Database column id SqlType(INT), AutoInc, PrimaryKey */
    val id: Rep[UserId] = column[UserId]("id", O.AutoInc, O.PrimaryKey)

    /** Database column hashedPassword SqlType(TEXT) */
    val hashedpassword: Rep[String] = column[String]("hashedPassword")

    /** Database column name SqlType(TEXT) */
    val name: Rep[String] = column[String]("name")

    /** Database column email SqlType(VARCHAR), Length(255,true), Default(None) */
    val email: Rep[String] =
      column[String]("email", O.Length(255, varying = true))

    /** Database column created SqlType(TIMESTAMP) */
    val created: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created")

    /** Database column lastUpdated SqlType(TIMESTAMP) */
    val lastupdated: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("lastUpdated")

    /** Database column lastLoggedIn SqlType(TIMESTAMP), Default(None) */
    val lastloggedin: Rep[Option[java.sql.Timestamp]] =
      column[Option[java.sql.Timestamp]]("lastLoggedIn", O.Default(None))

    /** Database column deleted SqlType(TINYINT), Default(0) */
    val deleted: Rep[Boolean] = column[Boolean]("deleted", O.Default(false))

    /** Database column deletedDate SqlType(TIMESTAMP), Default(None) */
    val deleteddate: Rep[Option[java.sql.Timestamp]] =
      column[Option[java.sql.Timestamp]]("deletedDate", O.Default(None))

    /** Uniqueness Index over (email) (database name email) */
    val index1 = index("email", email, unique = true)
  }

  /** Collection-like TableQuery object for table User */
  lazy val UserQuery = new TableQuery(tag => new User(tag))
}
