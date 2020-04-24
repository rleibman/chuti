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

object LoggedInUserRepo {
  type LoggedInUserRepo = Has[Service]

  trait Service {
    def addUser(user:    UserId): UIO[Boolean]
    def removeUser(user: UserId): UIO[Boolean]
    def listUsers: UIO[Set[UserId]]
    def clear:     UIO[Boolean]
  }

  val live: Service = new Service {
    private val users = scala.collection.mutable.Set.empty[UserId]
    override def addUser(userId:    UserId): UIO[Boolean] = UIO.succeed(users.add(userId))
    override def removeUser(userId: UserId): UIO[Boolean] = UIO.succeed(users.remove(userId))
    override def listUsers: UIO[Set[UserId]] = UIO.succeed(users.toSet)
    override def clear: UIO[Boolean] = UIO.succeed {
      users.clear()
      true
    }
  }
}

object GameService {
  val god: User = User(
    id = Some(UserId(-666)),
    email = "god@chuti.fun",
    name = "Un-namable",
    created = LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC),
    lastUpdated = LocalDateTime.now(),
    wallet = Double.MaxValue
  )

  type GameLayer = SessionProvider
    with DatabaseProvider with Repository with LoggedInUserRepo.LoggedInUserRepo

  implicit val runtime: zio.Runtime[zio.ZEnv] = zio.Runtime.default

  type GameService = Has[Service]

  trait Service {

    def joinRandomGame(): ZIO[GameLayer, GameException, Game]
    def newGame(): ZIO[GameLayer, GameException, Game]
    def play(gameEvent: GameEvent): ZIO[GameLayer, GameException, Game]
    def getGameForUser: ZIO[GameService with GameLayer, GameException, Option[Game]]
    def getGame(gameId: GameId): ZIO[GameLayer, GameException, Option[Game]]
    def abandonGame(gameId: GameId): ZIO[GameLayer, GameException, Boolean]
    def getFriends: ZIO[GameService with GameLayer, GameException, Seq[UserId]]
    def getInvites: ZIO[GameService with GameLayer, GameException, Seq[Game]]
    def getLoggedInUsers: ZIO[GameService with GameLayer, GameException, Seq[User]]
    def inviteFriend(user: User, friend: User): ZIO[GameService with GameLayer, GameException, Boolean]
    //See if the friend exists
    //If the friend does not exist
    //  Add a temporary user for the friend
    //  Send an invite to the friend to join the server
    //Add a temporary record in the friends table
    //  If the friend exists
    //  Send the friend an invite to become friends by email
    //  Add a temporary record in the friends table
    def acceptFriendship(user:   User,friend: User): ZIO[GameService with GameLayer, GameException, Boolean]
    def unfriend(user:  User,enemy: User): ZIO[GameService with GameLayer, GameException, Boolean]

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
        }.map(_ => true)
    )
  def getGameForUser: ZIO[GameService with GameLayer, GameException, Option[Game]] =
    URIO.accessM(_.get.getGameForUser)
  def getGame(gameId: GameId): ZIO[GameService with GameLayer, GameException, Option[Game]] =
    URIO.accessM(_.get.getGame(gameId))
  def getFriends: ZIO[GameService with GameLayer, GameException, Seq[UserId]] =
    URIO.accessM(_.get.getFriends)
  def getInvites: ZIO[GameService with GameLayer, GameException, Seq[Game]] =
    URIO.accessM(_.get.getInvites)
  def getLoggedInUsers: ZIO[GameService with GameLayer, GameException, Seq[User]] =
    URIO.accessM(_.get.getLoggedInUsers)

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
  ): ZIO[Any, Nothing, EventType] = {
    println(event) //TODO move to log
    for {
      allQueues <- allQueuesRef.get
      sent <- UIO
        .foreach(allQueues)(queue =>
          queue.queue
            .offer(event)
            .onInterrupt(allQueuesRef.update(_.filterNot(_ == queue)))
        )
        .as(event)
    } yield sent
  }

  def make(): ZLayer[Any, Nothing, GameService] = ZLayer.fromEffect {
    for {
      userEventQueues <- Ref.make(List.empty[EventQueue[UserEvent]])
      gameEventQueues <- Ref.make(List.empty[EventQueue[GameEvent]])
    } yield new Service {

      def abandonGame(gameId: GameId): ZIO[GameLayer, GameException, Boolean] = {
        (for {
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          repository <- ZIO.access[Repository](_.get)
          deleted    <- repository.gameOperations.delete(gameId, softDelete = true)
          //TODO abandoning the game incurs a penalty on the user, 10 point plus any points already lost
          _ <- broadcast(userEventQueues, UserEvent(user, UserEventType.AbandonedGame))
        } yield deleted).mapError(GameException.apply)
      }

      def joinRandomGame(): ZIO[GameLayer, GameException, Game] = {
        (for {
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          repository <- ZIO.access[Repository](_.get)
          gameOpt <- repository.gameOperations
            .gamesWaitingForPlayers().mapError(GameException.apply).map(_.headOption)
          saved <- {
            val game1 =
              gameOpt.getOrElse(Game(None, gameStatus = Estado.esperandoJugadoresAzar))
            val game2 = JoinGame(user = user).doEvent(game1)._1
            val game3 = if (game2.canTransition(Estado.jugando)) {
              EmpiezaJuego().doEvent(game2)._1
            } else {
              game2
            }
            repository.gameOperations.upsert(game3)
          }
          _ <- broadcast(userEventQueues, UserEvent(user, UserEventType.JoinedGame))
        } yield saved).mapError(GameException.apply)
      }

      def newGame(): ZIO[GameLayer, GameException, Game] = {
        (for {
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          repository <- ZIO.access[Repository](_.get)
          upserted <- {
            val newGame = Game(
              id = None,
              gameStatus = Estado.esperandoJugadoresInvitados
            )
            val game2 = JoinGame(user = user).doEvent(newGame)._1
            repository.gameOperations.upsert(game2)
          }
          _ <- broadcast(userEventQueues, UserEvent(user, UserEventType.JoinedGame))
        } yield upserted).mapError(GameException.apply)
      }

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
        } yield ???
      }

      override def getGame(gameId: GameId): ZIO[GameLayer, GameException, Option[Game]] = {
        (for {
          repository <- ZIO.access[Repository](_.get)
          game       <- repository.gameOperations.get(gameId)
        } yield game).mapError(GameException.apply)
      }

      override def getGameForUser: ZIO[GameLayer, GameException, Option[Game]] = {
        (for {
          repository <- ZIO.access[Repository](_.get)
          game       <- repository.gameOperations.getGameForUser
        } yield game).mapError(GameException.apply)
      }

      def getFriends: ZIO[GameLayer, GameException, Seq[UserId]] = ???

      def getInvites: ZIO[GameLayer, GameException, Seq[Game]] = ???

      def getLoggedInUsers: ZIO[GameLayer, GameException, Seq[User]] = ???

      def acceptFriendship(user: User, friend: User): ZIO[GameLayer, GameException, Boolean] = ???

      def inviteFriend(user: User, friend: User): ZIO[GameLayer, GameException, Boolean] = ???

      def unfriend(user: User, enemy: User): ZIO[GameLayer, GameException, Boolean] = ???

      override def gameStream(gameId: GameId): ZStream[GameLayer, Nothing, GameEvent] =
        ZStream.unwrap {
          for {
            user  <- ZIO.access[SessionProvider](_.get.session.user)
            queue <- Queue.sliding[GameEvent](requestedCapacity = 100)
            _     <- gameEventQueues.update(EventQueue(user, queue) :: _)
          } yield ZStream.fromQueue(queue).filter(_.gameId == Option(gameId))
        }

      override def userStream: ZStream[GameLayer, Nothing, UserEvent] = ZStream.unwrap {
        for {
          user             <- ZIO.access[SessionProvider](_.get.session.user)
          allUserQueues    <- userEventQueues.get
          loggedInUserRepo <- ZIO.access[LoggedInUserRepo](_.get)
          _                <- UIO.foreach(user.id)(id => loggedInUserRepo.addUser(id))
          _ <- {
            val userEvent = UserEvent(user, UserEventType.Connected)
            UIO.foreach(allUserQueues.filter(_.user != user)) { userQueue =>
              userQueue.queue.offer(userEvent)
            }
          }
          queue <- Queue.sliding[UserEvent](requestedCapacity = 100)
          _     <- userEventQueues.update(EventQueue(user, queue) :: _)
        } yield ZStream.fromQueue(queue).ensuring {
          UIO.foreach(user.id)(id => loggedInUserRepo.removeUser(id)) *>
            broadcast(userEventQueues, UserEvent(user, UserEventType.Disconnected))
        }
      }

    }
  }

}
