package dao.gen
// AUTO-GENERATED Slick data model
/** Stand-alone Slick data model for immediate use */
object Tables extends {
  val profile = slick.jdbc.MySQLProfile
} with Tables

/** Slick data model trait for extension, choice of backend or usage in the cake pattern. (Make sure to initialize this late.) */
trait Tables {
  val profile: slick.jdbc.JdbcProfile
  import profile.api._
  import slick.model.ForeignKeyAction
  // NOTE: GetResult mappers for plain SQL are only generated for tables where Slick knows how to map the types of all columns.
  import slick.jdbc.{GetResult => GR}

  /** DDL for all tables. Call .create to execute. */
  lazy val schema: profile.SchemaDescription = Friends.schema ++ Game.schema ++ GameEvent.schema ++ GamePlayers.schema ++ User.schema
  @deprecated("Use .schema instead of .ddl", "3.0")
  def ddl = schema

  /** Entity class storing rows of table Friends
   *  @param one Database column one SqlType(INT)
   *  @param two Database column two SqlType(INT) */
  case class FriendsRow(one: Int, two: Int)
  /** GetResult implicit for fetching FriendsRow objects using plain SQL queries */
  implicit def GetResultFriendsRow(implicit e0: GR[Int]): GR[FriendsRow] = GR{
    prs => import prs._
    FriendsRow.tupled((<<[Int], <<[Int]))
  }
  /** Table description of table friends. Objects of this class serve as prototypes for rows in queries. */
  class Friends(_tableTag: Tag) extends profile.api.Table[FriendsRow](_tableTag, Some("chuti"), "friends") {
    def * = (one, two) <> (FriendsRow.tupled, FriendsRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(one), Rep.Some(two))).shaped.<>({r=>import r._; _1.map(_=> FriendsRow.tupled((_1.get, _2.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column one SqlType(INT) */
    val one: Rep[Int] = column[Int]("one")
    /** Database column two SqlType(INT) */
    val two: Rep[Int] = column[Int]("two")

    /** Foreign key referencing User (database name friends_user_1) */
    lazy val userFk1 = foreignKey("friends_user_1", one, User)(r => r.id, onUpdate=ForeignKeyAction.Restrict, onDelete=ForeignKeyAction.Restrict)
    /** Foreign key referencing User (database name friends_user_2) */
    lazy val userFk2 = foreignKey("friends_user_2", two, User)(r => r.id, onUpdate=ForeignKeyAction.Restrict, onDelete=ForeignKeyAction.Restrict)
  }
  /** Collection-like TableQuery object for table Friends */
  lazy val Friends = new TableQuery(tag => new Friends(tag))

  /** Entity class storing rows of table Game
   *  @param id Database column id SqlType(INT), AutoInc, PrimaryKey
   *  @param currentIndex Database column current_index SqlType(INT), Default(0)
   *  @param startState Database column start_state SqlType(JSON), Length(1073741824,true)
   *  @param gameState Database column game_state SqlType(JSON), Length(1073741824,true)
   *  @param created Database column created SqlType(TIMESTAMP)
   *  @param lastupdated Database column lastUpdated SqlType(TIMESTAMP)
   *  @param deleted Database column deleted SqlType(TINYINT), Default(0)
   *  @param deleteddate Database column deletedDate SqlType(TIMESTAMP), Default(None) */
  case class GameRow(id: Int, currentIndex: Int = 0, startState: String, gameState: String, created: java.sql.Timestamp, lastupdated: java.sql.Timestamp, deleted: Byte = 0, deleteddate: Option[java.sql.Timestamp] = None)
  /** GetResult implicit for fetching GameRow objects using plain SQL queries */
  implicit def GetResultGameRow(implicit e0: GR[Int], e1: GR[String], e2: GR[java.sql.Timestamp], e3: GR[Byte], e4: GR[Option[java.sql.Timestamp]]): GR[GameRow] = GR{
    prs => import prs._
    GameRow.tupled((<<[Int], <<[Int], <<[String], <<[String], <<[java.sql.Timestamp], <<[java.sql.Timestamp], <<[Byte], <<?[java.sql.Timestamp]))
  }
  /** Table description of table game. Objects of this class serve as prototypes for rows in queries. */
  class Game(_tableTag: Tag) extends profile.api.Table[GameRow](_tableTag, Some("chuti"), "game") {
    def * = (id, currentIndex, startState, gameState, created, lastupdated, deleted, deleteddate) <> (GameRow.tupled, GameRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(id), Rep.Some(currentIndex), Rep.Some(startState), Rep.Some(gameState), Rep.Some(created), Rep.Some(lastupdated), Rep.Some(deleted), deleteddate)).shaped.<>({r=>import r._; _1.map(_=> GameRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7.get, _8)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(INT), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column current_index SqlType(INT), Default(0) */
    val currentIndex: Rep[Int] = column[Int]("current_index", O.Default(0))
    /** Database column start_state SqlType(JSON), Length(1073741824,true) */
    val startState: Rep[String] = column[String]("start_state", O.Length(1073741824,varying=true))
    /** Database column game_state SqlType(JSON), Length(1073741824,true) */
    val gameState: Rep[String] = column[String]("game_state", O.Length(1073741824,varying=true))
    /** Database column created SqlType(TIMESTAMP) */
    val created: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created")
    /** Database column lastUpdated SqlType(TIMESTAMP) */
    val lastupdated: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("lastUpdated")
    /** Database column deleted SqlType(TINYINT), Default(0) */
    val deleted: Rep[Byte] = column[Byte]("deleted", O.Default(0))
    /** Database column deletedDate SqlType(TIMESTAMP), Default(None) */
    val deleteddate: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("deletedDate", O.Default(None))
  }
  /** Collection-like TableQuery object for table Game */
  lazy val Game = new TableQuery(tag => new Game(tag))

  /** Entity class storing rows of table GameEvent
   *  @param gameId Database column game_id SqlType(INT)
   *  @param currentIndex Database column current_index SqlType(INT), Default(0)
   *  @param eventData Database column event_data SqlType(JSON), Length(1073741824,true) */
  case class GameEventRow(gameId: Int, currentIndex: Int = 0, eventData: String)
  /** GetResult implicit for fetching GameEventRow objects using plain SQL queries */
  implicit def GetResultGameEventRow(implicit e0: GR[Int], e1: GR[String]): GR[GameEventRow] = GR{
    prs => import prs._
    GameEventRow.tupled((<<[Int], <<[Int], <<[String]))
  }
  /** Table description of table game_event. Objects of this class serve as prototypes for rows in queries. */
  class GameEvent(_tableTag: Tag) extends profile.api.Table[GameEventRow](_tableTag, Some("chuti"), "game_event") {
    def * = (gameId, currentIndex, eventData) <> (GameEventRow.tupled, GameEventRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(gameId), Rep.Some(currentIndex), Rep.Some(eventData))).shaped.<>({r=>import r._; _1.map(_=> GameEventRow.tupled((_1.get, _2.get, _3.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column game_id SqlType(INT) */
    val gameId: Rep[Int] = column[Int]("game_id")
    /** Database column current_index SqlType(INT), Default(0) */
    val currentIndex: Rep[Int] = column[Int]("current_index", O.Default(0))
    /** Database column event_data SqlType(JSON), Length(1073741824,true) */
    val eventData: Rep[String] = column[String]("event_data", O.Length(1073741824,varying=true))

    /** Primary key of GameEvent (database name game_event_PK) */
    val pk = primaryKey("game_event_PK", (gameId, currentIndex))

    /** Foreign key referencing Game (database name game_event_game) */
    lazy val gameFk = foreignKey("game_event_game", gameId, Game)(r => r.id, onUpdate=ForeignKeyAction.Restrict, onDelete=ForeignKeyAction.Restrict)
  }
  /** Collection-like TableQuery object for table GameEvent */
  lazy val GameEvent = new TableQuery(tag => new GameEvent(tag))

  /** Entity class storing rows of table GamePlayers
   *  @param userId Database column user_id SqlType(INT)
   *  @param gameId Database column game_id SqlType(INT) */
  case class GamePlayersRow(userId: Int, gameId: Int)
  /** GetResult implicit for fetching GamePlayersRow objects using plain SQL queries */
  implicit def GetResultGamePlayersRow(implicit e0: GR[Int]): GR[GamePlayersRow] = GR{
    prs => import prs._
    GamePlayersRow.tupled((<<[Int], <<[Int]))
  }
  /** Table description of table game_players. Objects of this class serve as prototypes for rows in queries. */
  class GamePlayers(_tableTag: Tag) extends profile.api.Table[GamePlayersRow](_tableTag, Some("chuti"), "game_players") {
    def * = (userId, gameId) <> (GamePlayersRow.tupled, GamePlayersRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(userId), Rep.Some(gameId))).shaped.<>({r=>import r._; _1.map(_=> GamePlayersRow.tupled((_1.get, _2.get)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column user_id SqlType(INT) */
    val userId: Rep[Int] = column[Int]("user_id")
    /** Database column game_id SqlType(INT) */
    val gameId: Rep[Int] = column[Int]("game_id")

    /** Foreign key referencing Game (database name game_players_game) */
    lazy val gameFk = foreignKey("game_players_game", gameId, Game)(r => r.id, onUpdate=ForeignKeyAction.Restrict, onDelete=ForeignKeyAction.Restrict)
    /** Foreign key referencing User (database name game_players_user) */
    lazy val userFk = foreignKey("game_players_user", userId, User)(r => r.id, onUpdate=ForeignKeyAction.Restrict, onDelete=ForeignKeyAction.Restrict)
  }
  /** Collection-like TableQuery object for table GamePlayers */
  lazy val GamePlayers = new TableQuery(tag => new GamePlayers(tag))

  /** Entity class storing rows of table User
   *  @param id Database column id SqlType(INT), AutoInc, PrimaryKey
   *  @param username Database column username SqlType(TEXT)
   *  @param hashedpassword Database column hashedPassword SqlType(TEXT)
   *  @param name Database column name SqlType(TEXT)
   *  @param created Database column created SqlType(TIMESTAMP)
   *  @param lastupdated Database column lastUpdated SqlType(TIMESTAMP)
   *  @param lastloggedin Database column lastLoggedIn SqlType(TIMESTAMP), Default(None)
   *  @param email Database column email SqlType(VARCHAR), Length(255,true), Default(None)
   *  @param deleted Database column deleted SqlType(TINYINT), Default(0)
   *  @param deleteddate Database column deletedDate SqlType(TIMESTAMP), Default(None) */
  case class UserRow(id: Int, username: String, hashedpassword: String, name: String, created: java.sql.Timestamp, lastupdated: java.sql.Timestamp, lastloggedin: Option[java.sql.Timestamp] = None, email: Option[String] = None, deleted: Byte = 0, deleteddate: Option[java.sql.Timestamp] = None)
  /** GetResult implicit for fetching UserRow objects using plain SQL queries */
  implicit def GetResultUserRow(implicit e0: GR[Int], e1: GR[String], e2: GR[java.sql.Timestamp], e3: GR[Option[java.sql.Timestamp]], e4: GR[Option[String]], e5: GR[Byte]): GR[UserRow] = GR{
    prs => import prs._
    UserRow.tupled((<<[Int], <<[String], <<[String], <<[String], <<[java.sql.Timestamp], <<[java.sql.Timestamp], <<?[java.sql.Timestamp], <<?[String], <<[Byte], <<?[java.sql.Timestamp]))
  }
  /** Table description of table user. Objects of this class serve as prototypes for rows in queries. */
  class User(_tableTag: Tag) extends profile.api.Table[UserRow](_tableTag, Some("chuti"), "user") {
    def * = (id, username, hashedpassword, name, created, lastupdated, lastloggedin, email, deleted, deleteddate) <> (UserRow.tupled, UserRow.unapply)
    /** Maps whole row to an option. Useful for outer joins. */
    def ? = ((Rep.Some(id), Rep.Some(username), Rep.Some(hashedpassword), Rep.Some(name), Rep.Some(created), Rep.Some(lastupdated), lastloggedin, email, Rep.Some(deleted), deleteddate)).shaped.<>({r=>import r._; _1.map(_=> UserRow.tupled((_1.get, _2.get, _3.get, _4.get, _5.get, _6.get, _7, _8, _9.get, _10)))}, (_:Any) =>  throw new Exception("Inserting into ? projection not supported."))

    /** Database column id SqlType(INT), AutoInc, PrimaryKey */
    val id: Rep[Int] = column[Int]("id", O.AutoInc, O.PrimaryKey)
    /** Database column username SqlType(TEXT) */
    val username: Rep[String] = column[String]("username")
    /** Database column hashedPassword SqlType(TEXT) */
    val hashedpassword: Rep[String] = column[String]("hashedPassword")
    /** Database column name SqlType(TEXT) */
    val name: Rep[String] = column[String]("name")
    /** Database column created SqlType(TIMESTAMP) */
    val created: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("created")
    /** Database column lastUpdated SqlType(TIMESTAMP) */
    val lastupdated: Rep[java.sql.Timestamp] = column[java.sql.Timestamp]("lastUpdated")
    /** Database column lastLoggedIn SqlType(TIMESTAMP), Default(None) */
    val lastloggedin: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("lastLoggedIn", O.Default(None))
    /** Database column email SqlType(VARCHAR), Length(255,true), Default(None) */
    val email: Rep[Option[String]] = column[Option[String]]("email", O.Length(255,varying=true), O.Default(None))
    /** Database column deleted SqlType(TINYINT), Default(0) */
    val deleted: Rep[Byte] = column[Byte]("deleted", O.Default(0))
    /** Database column deletedDate SqlType(TIMESTAMP), Default(None) */
    val deleteddate: Rep[Option[java.sql.Timestamp]] = column[Option[java.sql.Timestamp]]("deletedDate", O.Default(None))

    /** Uniqueness Index over (email) (database name email) */
    val index1 = index("email", email, unique=true)
  }
  /** Collection-like TableQuery object for table User */
  lazy val User = new TableQuery(tag => new User(tag))
}
