/*
 * Copyright 2020 Roberto Leibman
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package game

import api.ChutiSession
import chuti.{Event, GameState, Jugador, User, UserId}
import dao.{Repository, RepositoryIO, SessionProvider}
import zio.stream.ZStream
import zio.{Queue, Task, UIO, ZIO, ZQueue}
import zioslick.RepositoryException

trait GameServer extends Repository {
  val god: User = User(
    id = Some(UserId(-666)),
    email = "god@chuti.fun",
    name = "Un-namable",
    wallet = Double.MaxValue
  )
  private val zioEventQueue: UIO[Queue[(GameState, Jugador, Event)]] =
    ZQueue.unbounded[(GameState, Jugador, Event)]

  //Game running stuff
  def game(id: Int): RepositoryIO[GameState] = ???

  //Starts the game server, opens and initializes streams, queues and caches, starts database
  def start: RepositoryIO[Unit] = ???

  //Ends the game server, closes down streams and queues, empties caches, shuts down database, etc.
  def end: RepositoryIO[Unit] = ???

  def event(
    game:    GameState,
    jugador: Jugador,
    event:   Event
  ): RepositoryIO[GameState] = ???

  //Game admin stuff
  def addUser(user: User) =
    repository.userOperations.upsert(user)
  def deleteUser(user: User): RepositoryIO[Boolean] =
    user.id.fold(ZIO.succeed(false): RepositoryIO[Boolean])(id =>
      repository.userOperations.delete(id, true)
    )
  def changePassword(
    user:     User,
    password: String
  ): RepositoryIO[Boolean] = ???
  def login(
    user:     User,
    password: String
  ): RepositoryIO[Boolean] = ???
  def inviteFriend(
    user:   User,
    friend: User
  ): RepositoryIO[Boolean] =
    //See if the friend exists
    //If the friend does not exist
    //  Add a temporary user for the friend
    //  Send an invite to the friend to join the server
    //Add a temporary record in the friends table
    //  If the friend exists
    //  Send the friend an invite to become friends by email
    //  Add a temporary record in the friends table
    ???
  def acceptFriendship(
    user:   User,
    friend: User
  ): RepositoryIO[Boolean] =
    ???
  def friends(user: User): RepositoryIO[Seq[User]] = repository.userOperations.friends(user)
  def unfriend(
    user:  User,
    enemy: User
  ): RepositoryIO[Boolean] =
    repository.userOperations.unfriend(user, enemy)
  def newGame(user: User): RepositoryIO[GameState] = {
    val newGame = GameState(id = None, jugadores = List(Jugador(user)))
    repository.gameStateOperations.upsert(newGame)
  }
  def gameEventStream(gameId: Int): ZStream[SessionProvider, Nothing, Event] = ???
}
