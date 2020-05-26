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

import api.ChutiSession
import api.token.TokenHolder
import caliban.{CalibanError, GraphQLInterpreter}
import chuti._
import dao.{DatabaseProvider, Repository, SessionProvider}
import game.LoggedInUserRepo.LoggedInUserRepo
import io.circe.generic.auto._
import io.circe.{Decoder, Json}
import mail.Postman.Postman
import zio._
import zio.logging.{Logging, log}
import zio.stream.ZStream

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
  lazy val godLayer: ULayer[SessionProvider] = {
    SessionProvider.layer(ChutiSession(GameService.god))
  }

  val god: User = User(
    id = Some(UserId(-666)),
    email = "god@chuti.fun",
    name = "Un-namable",
    created = LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC),
    lastUpdated = LocalDateTime.now()
  )

  type GameLayer = SessionProvider
    with DatabaseProvider with Repository with LoggedInUserRepo with Postman with Logging
    with TokenHolder

  implicit val runtime: zio.Runtime[zio.ZEnv] = zio.Runtime.default

  type GameService = Has[GameService.Service]

  trait Service {
    val userQueue: Ref[List[EventQueue[UserEvent]]]
    val gameQueue: Ref[List[EventQueue[GameEvent]]]

    def broadcastGameEvent(gameEvent: GameEvent): ZIO[GameLayer, GameException, GameEvent]
    def joinRandomGame(): ZIO[GameLayer, GameException, Game]
    def newGame():        ZIO[GameLayer, GameException, Game]
    def play(
      gameId:    GameId,
      playEvent: PlayEvent
    ):                  ZIO[GameLayer, GameException, Game]
    def getGameForUser: ZIO[GameLayer, GameException, Option[Game]]
    def getGame(gameId:     GameId): ZIO[GameLayer, GameException, Option[Game]]
    def abandonGame(gameId: GameId): ZIO[GameLayer, GameException, Boolean]
    def getFriends:       ZIO[GameLayer, GameException, Seq[UserId]]
    def getGameInvites:   ZIO[GameLayer, GameException, Seq[Game]]
    def getLoggedInUsers: ZIO[GameLayer, GameException, Seq[User]]
    def inviteToGame(
      userId: UserId,
      gameId: GameId
    ): ZIO[GameLayer, GameException, Boolean]
    def inviteFriend(friend:          User):   ZIO[GameLayer, GameException, Boolean]
    def acceptGameInvitation(gameId:  GameId): ZIO[GameLayer, GameException, Game]
    def declineGameInvitation(gameId: GameId): ZIO[GameLayer, GameException, Boolean]
    def acceptFriendship(friend:      User):   ZIO[GameLayer, GameException, Boolean]
    def unfriend(enemy:               User):   ZIO[GameLayer, GameException, Boolean]

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
  def play(
    gameId:    GameId,
    playEvent: Json
  ): ZIO[GameService with GameLayer, GameException, Boolean] =
    URIO.accessM(
      _.get
        .play(
          gameId, {
            val decoder = implicitly[Decoder[PlayEvent]]
            decoder.decodeJson(playEvent) match {
              case Right(event) => event
              case Left(error)  => throw GameException(error)
            }
          }
        ).as(true)
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
  ): ZIO[Logging, Nothing, EventType] = {
    for {
      _         <- log.info(s"Broadcasting event $event")
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
            if (!game.jugadores.exists(_.id == user.id)) {
              throw GameException("Ese usuario no esta jugando matarile-rile-ron")
            } else {
              val (gameAfterApply, appliedEvent) =
                game.applyEvent(Option(user), AbandonGame())
              //God needs to save this user, because the user has already left the game
              repository.gameOperations
                .upsert(gameAfterApply).map((_, appliedEvent)).provideSomeLayer[
                  DatabaseProvider with Logging
                ](godLayer)
            }
          }
          players <- ZIO.foreach(savedOpt) {
            case (game, event) =>
              repository.gameOperations.updatePlayers(game).map((_, event))
          }
          _ <- ZIO.foreach(players) {
            case (game, _)
                if game.jugadores.isEmpty &&
                  (game.gameStatus == GameStatus.esperandoJugadoresInvitados ||
                    game.gameStatus == GameStatus.esperandoJugadoresAzar) =>
              repository.gameOperations
                .delete(game.id.get, false).provideSomeLayer[DatabaseProvider with Logging](
                  godLayer
                )
            case (game, _) if game.jugadores.isEmpty =>
              repository.gameOperations
                .delete(game.id.get, true).provideSomeLayer[DatabaseProvider with Logging](godLayer)
            case (game, event) => ZIO.succeed(game)
          }
          _ <- ZIO.foreach(players) { game =>
            //TODO make sure that current losses in this game are also assigned to the user
            //TODO change player status, and update players in LoggedIn Players and in database, invalidate db cache
            repository.userOperations
              .upsert(
                user
                  .copy(
                    userStatus = UserStatus.Idle
                  )
              )
          }
          walletOpt <- repository.userOperations.getWallet
          _ <- ZIO.foreach(gameOpt.flatMap(g => walletOpt.map(w => (g, w)))) {
            case (game, wallet)
                if game.gameStatus == GameStatus.jugando | game.gameStatus == GameStatus.cantando =>
              repository.userOperations.updateWallet(
                wallet.copy(amount = wallet.amount - (game.abandonedPenalty / game.pointsPerDollar))
              )
            case _ => ZIO.succeed(true)
          }
          _ <- ZIO.foreach(savedOpt) {
            case (_, appliedEvent) =>
              broadcast(gameEventQueues, appliedEvent)
          }
          _ <- broadcast(userEventQueues, UserEvent(user, UserEventType.AbandonedGame))
        } yield savedOpt.nonEmpty).mapError(GameException.apply)

      def broadcastGameEvent(gameEvent: GameEvent): ZIO[GameLayer, GameException, GameEvent] = {
        broadcast(gameEventQueues, gameEvent)
      }

      def joinRandomGame(): ZIO[GameLayer, GameException, Game] =
        (for {
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          repository <- ZIO.access[Repository](_.get)
          gameOpt <- repository.gameOperations
            .gamesWaitingForPlayers().bimap(GameException.apply, _.headOption)
          newOrRetrieved <- gameOpt.fold(
            //The game has no players, so god needs to save it
            repository.gameOperations
              .upsert(Game(None, gameStatus = GameStatus.esperandoJugadoresAzar)).provideSomeLayer[
                DatabaseProvider with Logging
              ](godLayer)
          )(game => ZIO.succeed(game))
          afterApply <- {
            //TODO, refactor with acceptGameInvitation
            val (joined, joinGame) = newOrRetrieved.applyEvent(Option(user), JoinGame())
            val (started, startGame: GameEvent) =
              if (joined.canTransitionTo(GameStatus.requiereSopa)) {
                joined.copy(gameStatus = GameStatus.requiereSopa).applyEvent(Option(user), Sopa())
                //TODO change player status, and update players in LoggedIn Players and in database, invalidate db cache
              } else {
                joined.applyEvent(Option(user), NoOp())
              }
            repository.gameOperations.upsert(started).map((_, joinGame, startGame))
          }
          _ <- repository.gameOperations.updatePlayers(afterApply._1)
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
              gameStatus = GameStatus.esperandoJugadoresInvitados
            )
            val (game2, _) = newGame.applyEvent(Option(user), JoinGame())
            repository.gameOperations.upsert(game2)
          }
          _ <- repository.gameOperations.updatePlayers(upserted)
          _ <- ZIO.foreach(upserted.jugadores.find(_.user.id == user.id))(j =>
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
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          repository <- ZIO.access[Repository](_.get)
          postman    <- ZIO.access[Postman](_.get)
          //See if the friend exists
          friendOpt <- repository.userOperations.userByEmail(friend.email)
          //If the friend does not exist
          //  Add a temporary user for the friend
          savedFriend <- friendOpt.fold(repository.userOperations.upsert(friend))(f =>
            ZIO.succeed(f)
          )
          // Send an invite to the friend to join the server, or just to become friends if the user already exists
          envelope <- friendOpt.fold(postman.inviteNewFriendEmail(user, savedFriend))(f =>
            postman.inviteExistingUserFriendEmail(user, f)
          )
          _ <- postman.deliver(envelope)
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
      ): ZIO[GameLayer, GameException, Boolean] = {
        (for {
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          repository <- ZIO.access[Repository](_.get)
          postman    <- ZIO.access[Postman](_.get)
          gameOpt    <- repository.gameOperations.get(gameId)
          invitedOpt <- repository.userOperations.get(userId)
          afterInvitation <- ZIO.foreach(gameOpt) { game =>
            if (invitedOpt.isEmpty)
              throw GameException(s"User $userId does not exist")
            if (!game.jugadores.exists(_.user.id == user.id))
              throw GameException(s"User $userId is not in this game, he can't invite anyone")
            val (withInvite, invitation) =
              game.applyEvent(Option(user), InviteToGame(invited = invitedOpt.get))
            repository.gameOperations.upsert(withInvite).map((_, invitation))
          }
          _ <- ZIO.foreach(afterInvitation)(g => repository.gameOperations.updatePlayers(g._1))
          envelopeOpt <- ZIO.foreach(invitedOpt.flatMap(u => afterInvitation.map(g => (u, g._1)))) {
            case (invited, game) =>
              postman.inviteToGameEmail(user, invited, game)
          }
          _ <- ZIO.foreach(envelopeOpt)(envelope => postman.deliver(envelope))
          _ <- ZIO.foreach(afterInvitation) {
            case (_, event) =>
              broadcast(gameEventQueues, event)
          }
        } yield true).mapError(GameException.apply)
      }

      def acceptGameInvitation(gameId: GameId): ZIO[GameLayer, GameException, Game] =
        (for {
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          repository <- ZIO.access[Repository](_.get)
          gameOpt    <- repository.gameOperations.get(gameId)
          //NOTE, this should never really create a new game
          newOrRetrieved <- gameOpt.fold(
            repository.gameOperations
              .upsert(Game(None, gameStatus = GameStatus.esperandoJugadoresInvitados))
          )(game => ZIO.succeed(game))
          afterApply <- {
            //TODO, refactor with acceptGameInvitation
            val (joined, joinGame) = newOrRetrieved.applyEvent(Option(user), JoinGame())
            val (started, startGame: GameEvent) =
              if (joined.canTransitionTo(GameStatus.requiereSopa)) {
                joined.copy(gameStatus = GameStatus.requiereSopa).applyEvent(Option(user), Sopa())
                //TODO change player status, and update players in LoggedIn Players and in database, invalidate db cache
              } else {
                joined.applyEvent(Option(user), NoOp())
              }
            repository.gameOperations.upsert(started).map((_, joinGame, startGame))
          }
          _ <- repository.gameOperations.updatePlayers(afterApply._1)
          _ <- ZIO.foreach(afterApply._1.jugadores.find(_.user.id == user.id))(j =>
            repository.userOperations.upsert(j.user)
          )
          _ <- broadcast(gameEventQueues, afterApply._2)
          _ <- broadcast(gameEventQueues, afterApply._3)
          _ <- broadcast(userEventQueues, UserEvent(user, UserEventType.JoinedGame))
        } yield afterApply._1).mapError(GameException.apply)

      def declineGameInvitation(gameId: GameId): ZIO[GameLayer, GameException, Boolean] =
        (for {
          repository <- ZIO.access[Repository](_.get)
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          gameOpt    <- repository.gameOperations.get(gameId)
          afterEvent <- ZIO.foreach(gameOpt) { game =>
            if (game.gameStatus.enJuego)
              throw GameException(
                s"User $user tried to decline an invitation for a game that had already started"
              )
            if (!game.jugadores.exists(_.user.id == user.id))
              throw GameException(s"User ${user.id} is not even in this game")
            val (afterEvent, declinedEvent) = game.applyEvent(Option(user), DeclineInvite())
            repository.gameOperations.upsert(afterEvent).map((_, declinedEvent))
          }
          _ <- ZIO.foreach(afterEvent)(g => repository.gameOperations.updatePlayers(g._1))
          _ <- ZIO.foreach(afterEvent) {
            case (_, event) =>
              broadcast(gameEventQueues, event)
          }
        } yield true).mapError(GameException.apply)

      def checkPlayTransition(
        user:      User,
        game:      Game,
        playEvent: GameEvent
      ): (Game, Seq[GameEvent]) = {
        def applyHoyoTecnico(
          game:  Game,
          razon: String
        ): (Game, Seq[GameEvent]) = {
          val a = game.applyEvent(Option(user), HoyoTecnico(razon))
          val b = a._1.applyEvent(Option(user), BorloteEvent(Borlote.HoyoTecnico))
          (b._1, Seq(a._2, b._2))
        }
        playEvent match {
          case e: Da if e.hoyoTecnico.isDefined => applyHoyoTecnico(game, e.hoyoTecnico.get)
          case e: Pide if e.hoyoTecnico.isDefined => applyHoyoTecnico(game, e.hoyoTecnico.get)
          case e: PideInicial if e.hoyoTecnico.isDefined =>
            applyHoyoTecnico(game, e.hoyoTecnico.get)
          case e: Da if e.ficha == Game.campanita =>
            val a = game.applyEvent(Option(user), BorloteEvent(Borlote.CampanitaSeJuega))
            (a._1, Seq(a._2))
          case _ =>
            val (regalosGame, regalosBorlote) =
              if (game.jugadores.filter(!_.cantante).count(_.filas.size == 1) == 3) {
                val a = game.applyEvent(Option(user), BorloteEvent(Borlote.SantaClaus))
                (a._1, Seq(a._2))
              } else if (game.jugadores.filter(!_.cantante).exists(_.filas.size == 3)) {
                val a = game.applyEvent(Option(user), BorloteEvent(Borlote.ElNiñoDelCumpleaños))
                (a._1, Seq(a._2))
              } else
                (game, Seq.empty)

            val (helechoGame, helechoBorlote) =
              if (game.jugadores.find(!_.cantante).fold(false)(_.filas.size == 4) && game.jugadores
                    .exists(_.filas.size < 4)) {
                val a = game.applyEvent(Option(user), BorloteEvent(Borlote.Helecho))
                (a._1, Seq(a._2))
              } else {
                (regalosGame, regalosBorlote)
              }

            if (helechoGame.jugadores.exists(_.fichas.isEmpty)) {
              //Ya se acabo el juego, a nadie le quedan fichas
              val a = helechoGame.applyEvent(Option(user), TerminaJuego())
              (a._1, helechoBorlote :+ a._2)
            } else {
              (helechoGame, helechoBorlote)
            }
        }
      }

      override def play(
        gameId:    GameId,
        playEvent: PlayEvent
      ): ZIO[GameLayer, GameException, Game] = {
        (for {
          repository <- ZIO.access[Repository](_.get)
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          gameOpt    <- repository.gameOperations.get(gameId)
          played <- gameOpt.fold(throw GameException("Game not found")) { game =>
            if (!game.jugadores.exists(_.user.id == user.id)) {
              throw GameException("This user isn't playing in this game!!")
            }
            val (played, event) = game.applyEvent(Option(user), playEvent)

            val (transitioned, transitionEvents) = checkPlayTransition(user, played, event)

            repository.gameOperations.upsert(transitioned).map((_, event +: transitionEvents))
          }
          _ <- ZIO.foreach(played._2)(broadcast(gameEventQueues, _))
        } yield played._1).mapError(GameException.apply)
      }

      override def gameStream(gameId: GameId): ZStream[GameLayer, Nothing, GameEvent] =
        ZStream.unwrap {
          for {
            user  <- ZIO.access[SessionProvider](_.get.session.user)
            queue <- Queue.sliding[GameEvent](requestedCapacity = 100)
            _     <- gameEventQueues.update(EventQueue(user, queue) :: _)
            after <- gameEventQueues.get
            _     <- log.info(s"GameStream started, queues have ${after.length} entries")
          } yield ZStream
            .fromQueue(queue)
            .tap(event => log.debug(event.toString))
            .filter {
              case InviteToGame(invited, eventGameId, _, _) =>
                (eventGameId == Option(gameId)) || invited.id == user.id
              case event => event.gameId == Option(gameId)
            }
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
          _     <- log.info(s"UserStream started, queues have ${after.length} entries")
        } yield ZStream.fromQueue(queue).ensuring {
          UIO.foreach(user.id)(id => loggedInUserRepo.removeUser(id)) *>
            broadcast(userEventQueues, UserEvent(user, UserEventType.Disconnected))
        }
      }

    }
  }

}
