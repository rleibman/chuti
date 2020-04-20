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
import zio.stream.ZStream
import zio.{Has, URIO, ZLayer, _}
import zioslick.RepositoryException

object GameService {
  type GameLayer = SessionProvider with DatabaseProvider with Repository

  implicit val runtime: zio.Runtime[zio.ZEnv] = zio.Runtime.default

  type GameService = Has[Service]

  trait Service {
    def joinRandomGame(): ZIO[GameLayer, RepositoryException, GameState]
    def newGame():        ZIO[GameLayer, RepositoryException, GameState]
    def play(gameEvent:    GameEvent): URIO[GameLayer, GameState]
    def getGame(gameId:    GameId):    URIO[GameLayer, GameState]
    def abandonGame(gameId: GameId): ZIO[GameLayer, RepositoryException, Boolean]
    def gameStream(gameId: GameId):    ZStream[GameLayer, RepositoryException, GameEvent]
    def userStream: ZStream[GameLayer, RepositoryException, UserEvent]
  }

  lazy val interpreter: GraphQLInterpreter[ZEnv with GameLayer, CalibanError] =
    runtime.unsafeRun(
      GameService
        .make()
        .memoize
        .use(layer =>
          GameApi.api.interpreter.map(_.provideSomeLayer[ZEnv with GameLayer](layer))
        )
    )

  def joinRandomGame(): ZIO[GameService with GameLayer, RepositoryException, GameState] =
    URIO.accessM(_.get.joinRandomGame())
  def abandonGame(gameId: GameId): ZIO[GameService with GameLayer, RepositoryException, Boolean] =
    URIO.accessM(_.get.abandonGame(gameId))
  def newGame(): ZIO[GameService with GameLayer, RepositoryException, GameState] =
    URIO.accessM(_.get.newGame())
  def play(gameEvent: GameEvent): ZIO[GameService with GameLayer, RepositoryException, GameState] =
    URIO.accessM(_.get.play(gameEvent))
  def getGame(gameId: GameId): ZIO[GameService with GameLayer, RepositoryException, GameState] =
    URIO.accessM(_.get.getGame(gameId))
  def gameStream(gameId: GameId): ZStream[GameService with GameLayer, RepositoryException, GameEvent] =
    ZStream.accessStream(_.get.gameStream(gameId))
  def userStream: ZStream[GameService with GameLayer, RepositoryException, UserEvent] =
    ZStream.accessStream(_.get.userStream)

  case class UserEventQueue(
    user:  User,
    queue: Queue[UserEvent]
  )

  case class GameEventQueue(
    user:  User,
    queue: Queue[GameEvent]
  )

  def make(): ZLayer[Any, Nothing, GameService] = ZLayer.fromEffect {
    for {
      userEventQueue <- Ref.make(List.empty[UserEventQueue])
      gameEventQueue <- Ref.make(List.empty[GameEventQueue])
    } yield new Service {
      def abandonGame(gameId: GameId): ZIO[GameLayer, Nothing, Boolean] = ???

      def joinRandomGame(): ZIO[GameLayer, Nothing, GameState] = ???

      def newGame(): ZIO[GameLayer, RepositoryException, GameState] = {
        for {
          user  <- ZIO.access[SessionProvider](_.get.session.user)
          repository  <- ZIO.access[Repository](_.get)
          upserted <- {
            val newGame = GameState(id = None, jugadores = List(Jugador(user)))
            repository.gameStateOperations.upsert(newGame)
          }
        } yield upserted
      }

      override def play(gameEvent: GameEvent): URIO[GameLayer, GameState] = ???

      override def getGame(gameId: GameId): URIO[GameLayer, GameState] = ???

      override def gameStream(gameId: GameId): ZStream[GameLayer, Nothing, GameEvent] =
        ZStream.unwrap {
          for {
            user  <- ZIO.access[SessionProvider](_.get.session.user)
            queue <- Queue.sliding[GameEvent](requestedCapacity = 100)
            _     <- gameEventQueue.update(GameEventQueue(user, queue) :: _)
          } yield ZStream.fromQueue(queue).filter(_.gameId == gameId)
        }

      override def userStream: ZStream[GameLayer, Nothing, UserEvent] = ZStream.unwrap {
        for {
          user  <- ZIO.access[SessionProvider](_.get.session.user)
          queue <- Queue.sliding[UserEvent](requestedCapacity = 100)
          _     <- userEventQueue.update(UserEventQueue(user, queue) :: _)
        } yield ZStream.fromQueue(queue)
      }

    }
  }

}
