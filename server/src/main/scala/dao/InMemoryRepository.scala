/*
 * Copyright (c) 2024 Roberto Leibman
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package dao

import api.token.*
import chuti.*
import dao.Repository.TokenOperations
import zio.logging.*
import zio.*

import java.time.Instant
import java.util.UUID

object InMemoryRepository {

  private val now = Instant.now().nn

  val user1: User =
    User(Option(UserId(1)), "yoyo1@example.com", "yoyo1", created = now, lastUpdated = now)
  val user2: User =
    User(Option(UserId(2)), "yoyo2@example.com", "yoyo2", created = now, lastUpdated = now)
  val user3: User =
    User(Option(UserId(3)), "yoyo3@example.com", "yoyo3", created = now, lastUpdated = now)
  val user4: User =
    User(Option(UserId(4)), "yoyo4@example.com", "yoyo4", created = now, lastUpdated = now)

  def fromGames(games: Seq[Game]): ULayer[Repository] =
    ZLayer.fromZIO(for {
      games <- Ref.make(games.map(g => g.id.get -> g).toMap)
      users <- Ref.make(
        Map(
          UserId(1) -> user1,
          UserId(2) -> user2,
          UserId(3) -> user3,
          UserId(4) -> user4
        )
      )
      tokens  <- Ref.make(Map.empty[Token, (User, TokenPurpose, Instant)])
      friends <- Ref.make(Seq.empty[(UserId, UserId)])
      wallets <- Ref.make(Map.empty[UserId, UserWallet])
    } yield InMemoryRepository(games, users, tokens, friends, wallets))

  val make: ULayer[Repository] = ZLayer.fromZIO(for {
    games <- Ref.make(Map.empty[GameId, Game])
    users <- Ref.make(
      Map(
        UserId(1) -> user1,
        UserId(2) -> user2,
        UserId(3) -> user3,
        UserId(4) -> user4
      )
    )
    tokens  <- Ref.make(Map.empty[Token, (User, TokenPurpose, Instant)])
    friends <- Ref.make(Seq.empty[(UserId, UserId)])
    wallets <- Ref.make(Map.empty[UserId, UserWallet])
  } yield InMemoryRepository(games, users, tokens, friends, wallets))

}

case class InMemoryRepository(
  games:     Ref[Map[GameId, Game]],
  users:     Ref[Map[UserId, User]],
  tokens:    Ref[Map[Token, (User, TokenPurpose, Instant)]],
  friendSeq: Ref[Seq[(UserId, UserId)]],
  wallets:   Ref[Map[UserId, UserWallet]]
) extends Repository {

  import InMemoryRepository.*

  override val gameOperations: Repository.GameOperations = new Repository.GameOperations {

    override def getHistoricalUserGames: RepositoryIO[Seq[Game]] = ???

    override def gameInvites: RepositoryIO[Seq[Game]] = ???

    override def gamesWaitingForPlayers(): RepositoryIO[Seq[Game]] =
      games.get.map(_.collect { case (_, game) if game.jugadores.size < game.numPlayers => game }.toSeq)

    override def getGameForUser: RepositoryIO[Option[Game]] =
      for {
        user <- ZIO.service[SessionContext].map(_.session.user)
        res  <- games.get.map(_.find(_._2.jugadores.exists(_.id == user.id)).map(_._2))
      } yield res

    override def upsert(game: Game): RepositoryIO[Game] =
      for {
        id <- zio.Random.nextInt
        newGame = game.copy(id = game.id.orElse(Some(GameId(id))))
        _ <- games.update(map => map + (newGame.id.get -> newGame))
      } yield newGame

    override def get(pk: GameId): RepositoryIO[Option[Game]] = games.get.map(_.get(pk))

    override def delete(
      pk:         GameId,
      softDelete: Boolean
    ): RepositoryIO[Boolean] = games.update(_ - pk).as(true)

    override def search(search: Option[EmptySearch]): RepositoryIO[Seq[Game]] = games.get.map(_.values.toSeq)

    override def count(search: Option[EmptySearch]): RepositoryIO[Long] = games.get.map(_.size.toLong)

    override def updatePlayers(game: Game): RepositoryIO[Game] = upsert(game)

    override def userInGame(id: GameId): RepositoryIO[Boolean] = ZIO.succeed(true)

  }

  override val userOperations: Repository.UserOperations = new Repository.UserOperations {

    override def login(
      email:    String,
      password: String
    ): ZIO[Any, RepositoryError, Option[User]] =
      users.get.map(_.collectFirst { case (_, user) if user.email == email && password == "password" => user })

    override def userByEmail(email: String): RepositoryIO[Option[User]] =
      users.get.map(_.collectFirst { case (_, user) if user.email == email => user })

    override def changePassword(
      user:     User,
      password: String
    ): RepositoryIO[Boolean] = ZIO.succeed(true)

    override def unfriend(enemy: User): RepositoryIO[Boolean] = ZIO.succeed(true)

    override def friend(friend: User): RepositoryIO[Boolean] = ZIO.succeed(true)

    override def friends: RepositoryIO[Seq[User]] =
      for {
        user <- ZIO.service[SessionContext].map(_.session.user)
        fs <- friendSeq.get
          .map(_.collect {
            case (u1, u2) if user.id.contains(u1) => u2
            case (u2, u1) if user.id.contains(u1) => u2
          }).map(_.toSet)
        ret <- users.get.map(_.collect { case (id, user) if fs.contains(id) => user })
      } yield ret.toSeq

    override def getWallet: RepositoryIO[Option[UserWallet]] =
      for {
        user <- ZIO.service[SessionContext].map(_.session.user)
        w    <- wallets.get.map(_.collectFirst { case (id, wallet) if user.id.contains(id) => wallet })
      } yield w

    override def updateWallet(userWallet: UserWallet): RepositoryIO[UserWallet] =
      wallets.update(ws => ws + (userWallet.userId -> userWallet)).as(userWallet)

    override def upsert(user: User): RepositoryIO[User] =
      for {
        id <- zio.Random.nextInt
        newUser = user.copy(id = user.id.orElse(Some(UserId(id))))
        _ <- users.update(map => map + (newUser.id.get -> newUser))
      } yield newUser

    override def get(pk: UserId): RepositoryIO[Option[User]] = users.get.map(_.get(pk))

    override def delete(
      pk:         UserId,
      softDelete: Boolean
    ): RepositoryIO[Boolean] = users.update(_ - pk).as(true)

    override def search(search: Option[PagedStringSearch]): RepositoryIO[Seq[User]] = users.get.map(_.values.toSeq)

    override def count(search: Option[PagedStringSearch]): RepositoryIO[Long] = users.get.map(_.size)

    override def getWallet(userId: UserId): RepositoryIO[Option[UserWallet]] =
      wallets.get.map(_.collectFirst { case (id, wallet) if id == userId => wallet })

    override def firstLogin: RepositoryIO[Option[Instant]] = ZIO.clock.flatMap(_.instant.map(Some.apply))

  }
  override val tokenOperations: Repository.TokenOperations = new TokenOperations {

    override def validateToken(
      token:   Token,
      purpose: TokenPurpose
    ): RepositoryIO[Option[User]] =
      tokens.modify(toks =>
        (
          toks.collectFirst {
            case (tok, (user, purp, _)) if tok.tok == token.tok && purpose == purp => user
          },
          toks - token
        )
      )

    override def createToken(
      user:    User,
      purpose: TokenPurpose,
      ttl:     Option[Duration]
    ): RepositoryIO[Token] = {
      val token = Token(UUID.randomUUID().toString)

      for {
        clock   <- ZIO.clock
        instant <- ttl.fold(ZIO.succeed(Instant.MAX.nn))(d => clock.instant.map(_.plusMillis(d.toMillis).nn))
        _       <- tokens.update(_ + (token -> (user, purpose, instant)))
      } yield token
    }

    override def peek(
      token:   Token,
      purpose: TokenPurpose
    ): RepositoryIO[Option[User]] =
      tokens.get.map(_.collectFirst {
        case (tok, (user, purp, _)) if tok.tok == token.tok && purpose == purp => user
      })

    override def cleanup: RepositoryIO[Boolean] = tokens.update(_ => Map.empty).as(true) // It should really only cleanup those that are ttld out

  }

}
