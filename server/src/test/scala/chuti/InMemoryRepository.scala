package chuti

import dao.{Repository, RepositoryIO}
import zio.Task

class InMemoryRepository(loadedGames: Seq[Game]) extends Repository.Service {
  private val games =
    scala.collection.mutable.Map[GameId, Game](loadedGames.map(game => game.id.get -> game): _*)
  override val gameOperations: Repository.GameOperations = new Repository.GameOperations {
    override def gameInvites:              RepositoryIO[Seq[Game]] = ???
    override def gamesWaitingForPlayers(): RepositoryIO[Seq[Game]] = ???
    override def getGameForUser:           RepositoryIO[Option[Game]] = ???
    override def upsert(e: Game): RepositoryIO[Game] = {
      val id = e.id.getOrElse(GameId(games.size + 1))
      Task.succeed {
        games.put(id, e.copy(id = Option(id)))
        games(id)
      }
    }
    override def get(pk: GameId): RepositoryIO[Option[Game]] = Task.succeed(games.get(pk))
    override def delete(
                         pk:         GameId,
                         softDelete: Boolean
                       ): RepositoryIO[Boolean] = ???
    override def search(search: Option[EmptySearch]): RepositoryIO[Seq[Game]] =
      Task.succeed(games.values.toSeq)
    override def count(search: Option[EmptySearch]): RepositoryIO[Long] = Task.succeed(games.size)
  }

  private val users = Map(
    UserId(1) -> User(
      Option(UserId(1)),
      "yoyo1@example.com",
      "yoyo1",
      userStatus = UserStatus.Idle
    ),
    UserId(2) -> User(
      Option(UserId(2)),
      "yoyo2@example.com",
      "yoyo2",
      userStatus = UserStatus.Idle
    ),
    UserId(3) -> User(
      Option(UserId(3)),
      "yoyo3@example.com",
      "yoyo3",
      userStatus = UserStatus.Idle
    ),
    UserId(4) -> User(
      Option(UserId(4)),
      "yoyo4@example.com",
      "yoyo4",
      userStatus = UserStatus.Idle
    )
  )

  override val userOperations: Repository.UserOperations = new Repository.UserOperations {
    override def login(
                        email:    String,
                        password: String
                      ): RepositoryIO[Option[User]] = ???

    override def userByEmail(email: String): RepositoryIO[Option[User]] = ???

    override def changePassword(
                                 user:     User,
                                 password: String
                               ): RepositoryIO[Boolean] = ???

    override def unfriend(enemy: User): RepositoryIO[Boolean] = ???

    override def friend(
                         friend:    User,
                         confirmed: Boolean
                       ): RepositoryIO[Boolean] = ???

    override def friends: RepositoryIO[Seq[User]] = ???

    override def getWallet: RepositoryIO[Option[UserWallet]] = ???

    override def updateWallet(userWallet: UserWallet): RepositoryIO[Boolean] = ???

    override def upsert(e: User): RepositoryIO[User] = ???

    override def get(pk: UserId): RepositoryIO[Option[User]] = Task.succeed(users.get(pk))

    override def delete(
                         pk:         UserId,
                         softDelete: Boolean
                       ): RepositoryIO[Boolean] = ???

    override def search(search: Option[PagedStringSearch]): RepositoryIO[Seq[User]] = ???

    override def count(search: Option[PagedStringSearch]): RepositoryIO[Long] = ???
  }
}
