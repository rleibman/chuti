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

package game

import java.time.{LocalDateTime, ZoneOffset}

import caliban.{CalibanError, GraphQLInterpreter}
import chuti._
import dao.{DatabaseProvider, Repository, SessionProvider}
import game.LoggedInUserRepo.LoggedInUserRepo
import io.circe.generic.auto._
import io.circe.{Decoder, Json}
import zio.stream.ZStream
import zio._
import zio.console.Console
import zioslick.RepositoryException

object LoggedInUserRepo {
  type LoggedInUserRepo = Has[Service]

  trait Service {
    def addUser(user:      User):   UIO[Boolean]
    def removeUser(userId: UserId): UIO[Boolean]
    def userMap: UIO[Map[UserId, User]]
    def clear:   UIO[Boolean]
  }

  val live: Service = new Service {
    private val users = scala.collection.mutable.Map.empty[UserId, User]
    override def addUser(user: User): UIO[Boolean] =
      UIO.succeed(users.put(user.id.getOrElse(UserId(0)), user).nonEmpty)
    override def removeUser(userId: UserId): UIO[Boolean] =
      UIO.succeed(users.remove(userId).nonEmpty)
    override def userMap: UIO[Map[UserId, User]] = UIO.succeed(users.toMap)
    override def clear: UIO[Boolean] = UIO.succeed {
      users.clear()
      true
    }
  }
}

object GameService {
  //TODO filter hidden data from the game, we should never send to a user game information from the other users (e.g. the value of their tiles)

  val god: User = User(
    id = Some(UserId(-666)),
    email = "god@chuti.fun",
    name = "Un-namable",
    created = LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC),
    lastUpdated = LocalDateTime.now(),
    wallet = Double.MaxValue
  )

  type GameLayer = Console
    with SessionProvider with DatabaseProvider with Repository with LoggedInUserRepo

  implicit val runtime: zio.Runtime[zio.ZEnv] = zio.Runtime.default

  type GameService = Has[Service]

  trait Service {
    val userQueue: Ref[List[EventQueue[UserEvent]]]
    val gameQueue: Ref[List[EventQueue[GameEvent]]]

    def joinRandomGame(): ZIO[GameLayer, GameException, Game]
    def newGame():        ZIO[GameLayer, GameException, Game]
    def play(gameEvent: GameEvent): ZIO[GameLayer, GameException, Game]
    def getGameForUser: ZIO[GameService with GameLayer, GameException, Option[Game]]
    def getGame(gameId:     GameId): ZIO[GameLayer, GameException, Option[Game]]
    def abandonGame(gameId: GameId): ZIO[GameLayer, GameException, Boolean]
    def getFriends:       ZIO[GameService with GameLayer, GameException, Seq[UserId]]
    def getGameInvites:   ZIO[GameService with GameLayer, GameException, Seq[Game]]
    def getLoggedInUsers: ZIO[GameService with GameLayer, GameException, Seq[User]]
    def inviteToGame(
      userId: UserId,
      gameId: GameId
    ): ZIO[GameService with GameLayer, GameException, Boolean]
    def inviteFriend(friend:         User):   ZIO[GameService with GameLayer, GameException, Boolean]
    def acceptGameInvitation(gameId: GameId): ZIO[GameService with GameLayer, GameException, Game]
    def declineGameInvitation(
      gameId: GameId
    ): ZIO[GameService with GameLayer, GameException, Boolean]
    def acceptFriendship(friend: User): ZIO[GameService with GameLayer, GameException, Boolean]
    def unfriend(enemy:          User): ZIO[GameService with GameLayer, GameException, Boolean]

    def gameStream(gameId: GameId): ZStream[GameLayer, GameException, GameEvent]
    def userStream: ZStream[GameLayer, GameException, UserEvent]
  }

  lazy val interpreter: GraphQLInterpreter[ZEnv with GameLayer, CalibanError] =
    runtime.unsafeRun(
      GameService
        .make()
        .memoize
        .use(layer => GameApi.api.interpreter.map(_.provideSomeLayer[ZEnv with GameLayer](layer)))
    )

  def joinRandomGame(): ZIO[GameService with GameLayer, GameException, Game] =
    URIO.accessM(_.get.joinRandomGame())
  def abandonGame(gameId: GameId): ZIO[GameService with GameLayer, GameException, Boolean] =
    URIO.accessM(_.get.abandonGame(gameId))
  def newGame(): ZIO[GameService with GameLayer, GameException, Game] =
    URIO.accessM(_.get.newGame())
  def play(gameEvent: Json): ZIO[GameService with GameLayer, GameException, Boolean] =
    URIO.accessM(
      _.get
        .play {
          val decoder = implicitly[Decoder[GameEvent]]
          decoder.decodeJson(gameEvent) match {
            case Right(event) => event
            case Left(error)  => throw GameException(error)
          }
        }.as(true)
    )
  def getGameForUser: ZIO[GameService with GameLayer, GameException, Option[Game]] =
    URIO.accessM(_.get.getGameForUser)
  def getGame(gameId: GameId): ZIO[GameService with GameLayer, GameException, Option[Game]] =
    URIO.accessM(_.get.getGame(gameId))
  def getFriends: ZIO[GameService with GameLayer, GameException, Seq[UserId]] =
    URIO.accessM(_.get.getFriends)
  def inviteToGame(
    userId: UserId,
    gameId: GameId
  ): ZIO[GameService with GameLayer, GameException, Boolean] =
    URIO.accessM(_.get.inviteToGame(userId, gameId))
  def getGameInvites: ZIO[GameService with GameLayer, GameException, Seq[Game]] =
    URIO.accessM(_.get.getGameInvites)
  def getLoggedInUsers: ZIO[GameService with GameLayer, GameException, Seq[User]] =
    URIO.accessM(_.get.getLoggedInUsers)
  def acceptGameInvitation(gameId: GameId): ZIO[GameService with GameLayer, GameException, Game] =
    URIO.accessM(_.get.acceptGameInvitation(gameId))
  def declineGameInvitation(
    gameId: GameId
  ): ZIO[GameService with GameLayer, GameException, Boolean] =
    URIO.accessM(_.get.declineGameInvitation(gameId))

  def gameStream(gameId: GameId): ZStream[GameService with GameLayer, GameException, GameEvent] =
    ZStream.accessStream(_.get.gameStream(gameId))
  def userStream: ZStream[GameService with GameLayer, GameException, UserEvent] =
    ZStream.accessStream(_.get.userStream)

  case class EventQueue[EventType](
    user:  User,
    queue: Queue[EventType]
  )

  private def broadcast[EventType](
    allQueuesRef: Ref[List[EventQueue[EventType]]],
    event:        EventType
  ): ZIO[Console, Nothing, EventType] = {
    for {
      _         <- console.putStrLn(s"Broadcasting event $event")
      allQueues <- allQueuesRef.get
      sent <- UIO
        .foreach(allQueues) { queue =>
          queue.queue
            .offer(event)
            .onInterrupt(allQueuesRef.update(_.filterNot(_ == queue)))
        }
        .as(event)
    } yield sent
  }

  def make(): ZLayer[Any, Nothing, GameService] = ZLayer.fromEffect {
    for {
      userEventQueues <- Ref.make(List.empty[EventQueue[UserEvent]])
      gameEventQueues <- Ref.make(List.empty[EventQueue[GameEvent]])
    } yield new Service {
      override val userQueue: Ref[List[EventQueue[UserEvent]]] = userEventQueues
      override val gameQueue: Ref[List[EventQueue[GameEvent]]] = gameEventQueues

      def abandonGame(gameId: GameId): ZIO[GameLayer, GameException, Boolean] =
        (for {
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          repository <- ZIO.access[Repository](_.get)
          gameOpt    <- repository.gameOperations.get(gameId)
          savedOpt <- ZIO.foreach(gameOpt) { game =>
            val (gameAfterApply, appliedEvent) = game.applyEvent(AbandonGame(user))
            repository.gameOperations.upsert(gameAfterApply).map((_, appliedEvent))
          }
          //TODO abandoning the game incurs a penalty on the user, 10 point plus any points already lost
          _ <- ZIO.foreach(savedOpt) {
            case (_, appliedEvent) =>
              broadcast(gameEventQueues, appliedEvent)
          }
          _ <- broadcast(userEventQueues, UserEvent(user, UserEventType.AbandonedGame))
        } yield savedOpt.nonEmpty).mapError(GameException.apply)

      def joinRandomGame(): ZIO[GameLayer, GameException, Game] =
        (for {
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          repository <- ZIO.access[Repository](_.get)
          gameOpt <- repository.gameOperations
            .gamesWaitingForPlayers().bimap(GameException.apply, _.headOption)
          afterApply <- {
            val newOrRetrieved =
              gameOpt.getOrElse(Game(None, gameStatus = Estado.esperandoJugadoresAzar))
            val (joined, joinGame) = newOrRetrieved.applyEvent(JoinGame(user = user))
            val (started, startGame: GameEvent) = if (joined.canTransition(Estado.jugando)) {
              joined.applyEvent(EmpiezaJuego())
              //TODO change player status, and update players in LoggedIn Players and in database, invalidate db cache
            } else {
              (joined, NoOp())
            }
            repository.gameOperations.upsert(started).map((_, joinGame, startGame))
          }
          _ <- ZIO.foreach(afterApply._1.jugadores.find(_.user.id == user.id))(j =>
            repository.userOperations.upsert(j.user)
          )
          _ <- broadcast(gameEventQueues, afterApply._2)
          _ <- broadcast(gameEventQueues, afterApply._3)
          _ <- broadcast(userEventQueues, UserEvent(user, UserEventType.JoinedGame))
        } yield afterApply._1).mapError(GameException.apply)

      def newGame(): ZIO[GameLayer, GameException, Game] =
        (for {
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          repository <- ZIO.access[Repository](_.get)
          upserted <- {
            val newGame = Game(
              id = None,
              gameStatus = Estado.esperandoJugadoresInvitados
            )
            val (game2, _) = newGame.applyEvent(JoinGame(user = user))
            repository.gameOperations.upsert(game2)
          }
          savedUsers <- ZIO.foreach(upserted.jugadores.find(_.user.id == user.id))(j =>
            repository.userOperations.upsert(j.user)
          )
          _ <- broadcast(userEventQueues, UserEvent(user, UserEventType.JoinedGame))
        } yield upserted).mapError(GameException.apply)

      override def getGame(gameId: GameId): ZIO[GameLayer, GameException, Option[Game]] =
        (for {
          repository <- ZIO.access[Repository](_.get)
          game       <- repository.gameOperations.get(gameId)
        } yield game).mapError(GameException.apply)

      override def getGameForUser: ZIO[GameLayer, GameException, Option[Game]] =
        (for {
          repository <- ZIO.access[Repository](_.get)
          game       <- repository.gameOperations.getGameForUser
        } yield game).mapError(GameException.apply)

      def getFriends: ZIO[GameLayer, GameException, Seq[UserId]] =
        (for {
          repository <- ZIO.access[Repository](_.get)
          friends    <- repository.userOperations.friends.map(_.flatMap(_.id.toSeq))
        } yield friends).mapError(GameException.apply)

      def getGameInvites: ZIO[GameLayer, GameException, Seq[Game]] =
        (for {
          repository  <- ZIO.access[Repository](_.get)
          gameInvites <- repository.gameOperations.gameInvites
        } yield gameInvites).mapError(GameException.apply)

      def getLoggedInUsers: ZIO[GameLayer, GameException, Seq[User]] =
        for {
          loggedInUserRepo <- ZIO.access[LoggedInUserRepo](_.get)
          loggedInUsers    <- loggedInUserRepo.userMap.map(_.values.take(20).toSeq)
        } yield loggedInUsers

      def acceptFriendship(friend: User): ZIO[GameLayer, GameException, Boolean] =
        (for {
          repository <- ZIO.access[Repository](_.get)
          friends    <- repository.userOperations.friend(friend, confirmed = true)
        } yield friends).mapError(GameException.apply)

      def inviteFriend(friend: User): ZIO[GameLayer, GameException, Boolean] =
        (for {
          repository <- ZIO.access[Repository](_.get)
          //See if the friend exists
          friendOpt <- repository.userOperations.userByEmail(friend.email)
          //If the friend does not exist
          //  Add a temporary user for the friend
          savedFriend <- friendOpt.fold(repository.userOperations.upsert(friend))(f =>
            ZIO.succeed(f)
          )
          //  TODO Send an invite to the friend to join the server, or just to become friends if the user already exists
          emailSent <- ZIO.succeed(true)
          //Add a temporary record in the friends table
          friendRecord <- repository.userOperations.friend(savedFriend, confirmed = false)
        } yield friendRecord).mapError(GameException.apply)

      def unfriend(enemy: User): ZIO[GameLayer, GameException, Boolean] =
        (for {
          repository <- ZIO.access[Repository](_.get)
          friends    <- repository.userOperations.unfriend(enemy)
        } yield friends).mapError(GameException.apply)

      def inviteToGame(
        userId: UserId,
        gameId: GameId
      ): ZIO[GameService with GameLayer, GameException, Boolean] = {
        (for {
          repository <- ZIO.access[Repository](_.get)
          gameOpt    <- repository.gameOperations.get(gameId)
          userOpt    <- repository.userOperations.get(userId)
          _ <- ZIO.foreach(gameOpt) { game =>
            if (userOpt.isEmpty)
              throw GameException(s"User $userId does not exist")
            if (game.jugadores.exists(_.user.id == Option(userId)))
              throw GameException(s"User $userId is already in game")
            if (game.jugadores.length == game.size)
              throw GameException("The game is already full")
            val withInvite =
              game.copy(jugadores = game.jugadores ++ userOpt.map(Jugador(_, invited = true)))
            repository.gameOperations.upsert(withInvite)
          }
          //TODO convert to send gameEvent
        } yield true).mapError(GameException.apply)
      }

      def acceptGameInvitation(gameId: GameId): ZIO[GameLayer, GameException, Game] =
        (for {
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          repository <- ZIO.access[Repository](_.get)
          gameOpt    <- repository.gameOperations.get(gameId)
          afterApply <- {
            val newOrRetrieved =
              gameOpt.getOrElse(Game(None, gameStatus = Estado.esperandoJugadoresAzar))
            val (joined, joinGame) = newOrRetrieved.applyEvent(JoinGame(user = user))
            val (started, startGame: GameEvent) = if (joined.canTransition(Estado.jugando)) {
              joined.applyEvent(EmpiezaJuego())
              //TODO change player status, and update players in LoggedIn Players and in database, invalidate db cache
            } else {
              (joined, NoOp)
            }
            repository.gameOperations.upsert(started).map((_, joinGame, startGame))
          }
          _ <- ZIO.foreach(afterApply._1.jugadores.find(_.user.id == user.id))(j => repository.userOperations.upsert(j.user))
          _ <- broadcast(gameEventQueues, afterApply._2)
          _ <- broadcast(gameEventQueues, afterApply._3)
          _ <- broadcast(userEventQueues, UserEvent(user, UserEventType.JoinedGame))
        } yield afterApply._1).mapError(GameException.apply)

      def declineGameInvitation(gameId: GameId): ZIO[GameLayer, GameException, Boolean] =
        (for {
          repository <- ZIO.access[Repository](_.get)
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          gameOpt    <- repository.gameOperations.get(gameId)
          _ <- ZIO.foreach(gameOpt) { game =>
            if (game.gameStatus.enJuego)
              throw GameException(
                s"User $user tried to decline an invitation for a game that had already started"
              )
            if (!game.jugadores.exists(_.user.id == user.id))
              throw GameException(s"User ${user.id} is not even in this game")
            val withoutUser =
              game.copy(jugadores = game.jugadores.filter(_.user.id != user.id))
            repository.gameOperations.upsert(withoutUser)
          }
          //TODO convert to send gameEvent
        } yield true).mapError(GameException.apply)

      override def play(gameEvent: GameEvent): ZIO[GameLayer, GameException, Game] = {
        //TODO
        //Check if the move is allowed
        //Perform the move on the GameState
        //Save the game state (background)
        //broadcast the move
        //check change to game status?
        for {
          user <- ZIO.access[SessionProvider](_.get.session.user)
          sent <- broadcast(gameEventQueues, gameEvent)
        } yield ??? //TODO write this
      }

      override def gameStream(gameId: GameId): ZStream[GameLayer, Nothing, GameEvent] =
        ZStream.unwrap {
          for {
            user  <- ZIO.access[SessionProvider](_.get.session.user)
            queue <- Queue.sliding[GameEvent](requestedCapacity = 100)
            _     <- gameEventQueues.update(EventQueue(user, queue) :: _)
            after <- gameEventQueues.get
            _     <- console.putStrLn(s"GameStream started, queues have ${after.length} entries")
          } yield ZStream.fromQueue(queue).filter(a=> a.gameId == Option(gameId))
        }

      override def userStream: ZStream[GameLayer, Nothing, UserEvent] = ZStream.unwrap {
        for {
          user             <- ZIO.access[SessionProvider](_.get.session.user)
          allUserQueues    <- userEventQueues.get
          loggedInUserRepo <- ZIO.access[LoggedInUserRepo](_.get)
          _                <- loggedInUserRepo.addUser(user)
          _ <- {
            val userEvent = UserEvent(user, UserEventType.Connected)
            UIO.foreach(allUserQueues.filter(_.user != user)) { userQueue =>
              userQueue.queue.offer(userEvent)
            }
          }
          queue <- Queue.sliding[UserEvent](requestedCapacity = 100)
          _     <- userEventQueues.update(EventQueue(user, queue) :: _)
          after <- userEventQueues.get
          _     <- console.putStrLn(s"UserStream started, queues have ${after.length} entries")
        } yield ZStream.fromQueue(queue).ensuring {
          UIO.foreach(user.id)(id => loggedInUserRepo.removeUser(id)) *>
            broadcast(userEventQueues, UserEvent(user, UserEventType.Disconnected))
        }
      }

    }
  }

}
