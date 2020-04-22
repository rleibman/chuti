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

  type GameLayer = SessionProvider
    with DatabaseProvider with Repository with LoggedInUserRepo.LoggedInUserRepo

  implicit val runtime: zio.Runtime[zio.ZEnv] = zio.Runtime.default

  type GameService = Has[Service]

  trait Service {
    def joinRandomGame(): ZIO[GameLayer, GameException, GameState]
    def newGame():        ZIO[GameLayer, GameException, GameState]
    def play(gameEvent:     GameEvent): ZIO[GameLayer, GameException, GameState]
    def getGameForUser: ZIO[GameService with GameLayer, GameException, Option[GameState]]
    def getGame(gameId:     GameId):    ZIO[GameLayer, GameException, Option[GameState]]
    def abandonGame(gameId: GameId):    ZIO[GameLayer, GameException, Boolean]

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

  def joinRandomGame(): ZIO[GameService with GameLayer, GameException, GameState] =
    URIO.accessM(_.get.joinRandomGame())
  def abandonGame(gameId: GameId): ZIO[GameService with GameLayer, GameException, Boolean] =
    URIO.accessM(_.get.abandonGame(gameId))
  def newGame(): ZIO[GameService with GameLayer, GameException, GameState] =
    URIO.accessM(_.get.newGame())
  def play(gameEvent: Json): ZIO[GameService with GameLayer, GameException, GameState] =
    URIO.accessM(_.get.play {
      val decoder = implicitly[Decoder[GameEvent]]
      decoder.decodeJson(gameEvent) match {
        case Right(event) => event
        case Left(error)  => throw GameException(error)
      }
    })
  def getGameForUser: ZIO[GameService with GameLayer, GameException, Option[GameState]] =
    URIO.accessM(_.get.getGameForUser)
  def getGame(gameId: GameId): ZIO[GameService with GameLayer, GameException, Option[GameState]] =
    URIO.accessM(_.get.getGame(gameId))
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
          deleted    <- repository.gameStateOperations.delete(gameId, softDelete = true)
          //TODO abandoning the game incurs a penalty on the user, 10 point plus any points already lost
          _ <- broadcast(userEventQueues, UserEvent(user, UserEventType.AbandonedGame))
        } yield deleted).mapError(GameException.apply)
      }

      def joinRandomGame(): ZIO[GameLayer, GameException, GameState] = {
        (for {
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          repository <- ZIO.access[Repository](_.get)
          gameOpt <- repository.gameStateOperations
            .gamesWaitingForPlayers().mapError(GameException.apply).map(_.headOption)
          saved <- {
            val game1 =
              gameOpt.getOrElse(GameState(None, gameStatus = Estado.esperandoJugadoresAzar))
            val game2 = JoinGame(user = user).doEvent(game1)._1
            val game3 = if (game2.canTransition(Estado.jugando)) {
              EmpiezaJuego().doEvent(game2)._1
            } else {
              game2
            }
            repository.gameStateOperations.upsert(game3)
          }
          _ <- broadcast(userEventQueues, UserEvent(user, UserEventType.JoinedGame))
        } yield saved).mapError(GameException.apply)
      }

      def newGame(): ZIO[GameLayer, GameException, GameState] = {
        (for {
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          repository <- ZIO.access[Repository](_.get)
          upserted <- {
            val newGame = GameState(
              id = None,
              gameStatus = Estado.esperandoJugadoresInvitados
            )
            val game2 = JoinGame(user = user).doEvent(newGame)._1
            repository.gameStateOperations.upsert(game2)
          }
          _ <- broadcast(userEventQueues, UserEvent(user, UserEventType.JoinedGame))
        } yield upserted).mapError(GameException.apply)
      }

      override def play(gameEvent: GameEvent): ZIO[GameLayer, GameException, GameState] = {
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

      override def getGame(gameId: GameId): ZIO[GameLayer, GameException, Option[GameState]] = {
        (for {
          repository <- ZIO.access[Repository](_.get)
          game       <- repository.gameStateOperations.get(gameId)
        } yield game).mapError(GameException.apply)
      }

      override def getGameForUser: ZIO[GameLayer, GameException, Option[GameState]] = {
        (for {
          repository <- ZIO.access[Repository](_.get)
          game       <- repository.gameStateOperations.getGameForUser
        } yield game).mapError(GameException.apply)
      }

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
