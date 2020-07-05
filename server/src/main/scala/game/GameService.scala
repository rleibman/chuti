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
import chat.ChatService.ChatService
import chat._
import chuti._
import dao.{Repository, SessionProvider}
import game.UserConnectionRepo.UserConnectionRepo
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Decoder, Json}
import mail.Postman
import mail.Postman.Postman
import zio._
import zio.clock.Clock
import zio.console.Console
import zio.logging.{Logging, log}
import zio.stream.ZStream
import zioslick.RepositoryException

//TODO consider getting rid of userConnectionRepo, and use the userEventQueues instead, they seem to be doing the same thing
object UserConnectionRepo {
  type UserConnectionRepo = Has[Service]

  trait Service {
    def addConnection(
      connectionId: ConnectionId,
      user:         User
    ): UIO[Boolean]

    def removeConnection(connectionId: ConnectionId): UIO[Boolean]

    def connectionMap: UIO[Map[ConnectionId, User]]

    def clear: UIO[Boolean]
  }

  private val connections = scala.collection.mutable.Map.empty[ConnectionId, User]

  val live: Service = new Service {
    override def addConnection(
      connectionId: ConnectionId,
      user:         User
    ): UIO[Boolean] =
      UIO.succeed(connections.put(connectionId, user).nonEmpty)

    override def removeConnection(connectionId: ConnectionId): UIO[Boolean] =
      UIO.succeed(connections.remove(connectionId).nonEmpty)

    override def connectionMap: UIO[Map[ConnectionId, User]] = UIO.succeed(connections.toMap)

    override def clear: UIO[Boolean] = UIO.succeed {
      connections.clear()
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
    lastUpdated = LocalDateTime.now(),
    isAdmin = true
  )

  implicit val runtime: zio.Runtime[zio.ZEnv] = zio.Runtime.default

  type GameService = Has[GameService.Service]

  type GameLayer = SessionProvider
    with Repository with UserConnectionRepo with Postman with Logging with TokenHolder

  trait Service {
    protected val userQueue: Ref[List[EventQueue[UserEvent]]]
    protected val gameQueue: Ref[List[EventQueue[GameEvent]]]

    def broadcastGameEvent(gameEvent: GameEvent): ZIO[GameLayer, GameException, GameEvent]

    def joinRandomGame(): ZIO[GameLayer, GameException, Game]

    def newGame(satoshiPerPoint: Int): ZIO[GameLayer, GameException, Game]

    def newGameSameUsers(oldGameId: GameId): ZIO[GameLayer with ChatService, GameException, Game]

    def play(
      gameId:    GameId,
      playEvent: PlayEvent
    ): ZIO[GameLayer, GameException, Game]

    def getGameForUser: ZIO[GameLayer, GameException, Option[Game]]

    def getGame(gameId: GameId): ZIO[GameLayer, GameException, Option[Game]]

    def abandonGame(gameId: GameId): ZIO[GameLayer, GameException, Boolean]

    def getFriends: ZIO[GameLayer, GameException, Seq[User]]

    def getGameInvites: ZIO[GameLayer, GameException, Seq[Game]]

    def getLoggedInUsers: ZIO[GameLayer, GameException, Seq[User]]

    def inviteByEmail(
      name:   String,
      email:  String,
      gameId: GameId
    ): ZIO[GameLayer with ChatService, GameException, Boolean]

    def inviteToGame(
      userId: UserId,
      gameId: GameId
    ): ZIO[GameLayer with ChatService, GameException, Boolean]

    def friend(userId: UserId): ZIO[GameLayer with ChatService, GameException, Boolean]

    def unfriend(userId: UserId): ZIO[GameLayer with ChatService, GameException, Boolean]

    def acceptGameInvitation(gameId: GameId): ZIO[GameLayer, GameException, Game]

    def cancelUnacceptedInvitations(
      gameId: GameId
    ): ZIO[GameLayer with ChatService, GameException, Boolean]

    def declineGameInvitation(
      gameId: GameId
    ): ZIO[GameLayer with ChatService, GameException, Boolean]

    def gameStream(
      gameId:       GameId,
      connectionId: ConnectionId
    ): ZStream[GameLayer, GameException, GameEvent]

    def userStream(connectionId: ConnectionId): ZStream[GameLayer, GameException, UserEvent]
  }

  lazy val interpreter: GraphQLInterpreter[
    Console with Clock with GameService with GameLayer with ChatService,
    CalibanError
  ] =
    runtime.unsafeRun(GameApi.api.interpreter)

  def joinRandomGame(): ZIO[GameService with GameLayer, GameException, Game] =
    URIO.accessM(_.get.joinRandomGame())

  def abandonGame(gameId: GameId): ZIO[GameService with GameLayer, GameException, Boolean] =
    URIO.accessM(_.get.abandonGame(gameId))

  def newGame(satoshiPerPoint: Int): ZIO[GameService with GameLayer, GameException, Game] =
    URIO.accessM(_.get.newGame(satoshiPerPoint))

  def newGameSameUsers(
    gameId: GameId
  ): ZIO[GameService with GameLayer with ChatService, GameException, Game] =
    URIO.accessM(_.get.newGameSameUsers(gameId))

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

  def getFriends: ZIO[GameService with GameLayer, GameException, Seq[User]] =
    URIO.accessM(_.get.getFriends)

  def inviteByEmail(
    name:   String,
    email:  String,
    gameId: GameId
  ): ZIO[GameService with GameLayer with ChatService, GameException, Boolean] =
    URIO.accessM(_.get.inviteByEmail(name, email, gameId))

  def inviteToGame(
    userId: UserId,
    gameId: GameId
  ): ZIO[GameService with GameLayer with ChatService, GameException, Boolean] =
    URIO.accessM(_.get.inviteToGame(userId, gameId))

  def getGameInvites: ZIO[GameService with GameLayer, GameException, Seq[Game]] =
    URIO.accessM(_.get.getGameInvites)

  def getLoggedInUsers: ZIO[GameService with GameLayer, GameException, Seq[User]] =
    URIO.accessM(_.get.getLoggedInUsers)

  def acceptGameInvitation(gameId: GameId): ZIO[GameService with GameLayer, GameException, Game] =
    URIO.accessM(_.get.acceptGameInvitation(gameId))

  def declineGameInvitation(
    gameId: GameId
  ): ZIO[GameService with GameLayer with ChatService, GameException, Boolean] =
    URIO.accessM(_.get.declineGameInvitation(gameId))

  def cancelUnacceptedInvitations(
    gameId: GameId
  ): ZIO[GameService with GameLayer with ChatService, GameException, Boolean] =
    URIO.accessM(_.get.cancelUnacceptedInvitations(gameId))

  def friend(
    userId: UserId
  ): ZIO[GameService with GameLayer with ChatService, GameException, Boolean] =
    URIO.accessM(_.get.friend(userId))

  def unfriend(
    userId: UserId
  ): ZIO[GameService with GameLayer with ChatService, GameException, Boolean] =
    URIO.accessM(_.get.unfriend(userId))

  def gameStream(
    gameId:       GameId,
    connectionId: ConnectionId
  ): ZStream[GameService with GameLayer, GameException, GameEvent] =
    ZStream.accessStream(_.get.gameStream(gameId, connectionId))

  def userStream(
    connectionId: ConnectionId
  ): ZStream[GameService with GameLayer, GameException, UserEvent] =
    ZStream.accessStream(_.get.userStream(connectionId))

  case class EventQueue[EventType](
    user:         User,
    connectionId: ConnectionId,
    queue:        Queue[EventType]
  )

  private def broadcast[EventType](
    allQueuesRef: Ref[List[EventQueue[EventType]]],
    event:        EventType
  ): ZIO[Logging, Nothing, EventType] = {
    for {
      _         <- log.info(s"Broadcasting event $event")
      allQueues <- allQueuesRef.get
      sent <- UIO
        .foreachPar(allQueues) { queue =>
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
          repository <- ZIO.service[Repository.Service]
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
                  Logging
                ](godLayer)
            }
          }
          players <- ZIO.foreach(savedOpt) {
            case (game, event) =>
              repository.gameOperations.updatePlayers(game).map((_, event))
          }
          _ <- ZIO.foreachPar(players) {
            case (game, _)
                if game.jugadores.isEmpty &&
                  (game.gameStatus == GameStatus.esperandoJugadoresInvitados ||
                    game.gameStatus == GameStatus.esperandoJugadoresAzar) =>
              repository.gameOperations
                .delete(game.id.get, softDelete = false).provideSomeLayer[
                  Logging
                ](
                  godLayer
                )
            case (game, _) if game.jugadores.isEmpty =>
              repository.gameOperations
                .delete(game.id.get, softDelete = true).provideSomeLayer[
                  Logging
                ](godLayer)
            case (game, event) => ZIO.succeed(game)
          }
          _ <- ZIO.foreachPar(players) { game =>
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
                wallet.copy(amount = wallet.amount - (game.abandonedPenalty * game.satoshiPerPoint))
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
          repository <- ZIO.service[Repository.Service]
          gameOpt <- repository.gameOperations
            .gamesWaitingForPlayers().bimap(GameException.apply, _.headOption)
          joined <- joinGame(gameOpt)
        } yield joined).mapError(GameException.apply)

      override def newGameSameUsers(
        oldGameId: GameId
      ): ZIO[GameLayer with ChatService, GameException, Game] =
        (for {
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          repository <- ZIO.service[Repository.Service]
          oldGame <- repository.gameOperations
            .get(oldGameId).map(_.getOrElse(throw GameException("Previous game did not exist")))
          withFirstUser <- {
            val newGame = Game(
              id = None,
              gameStatus = GameStatus.esperandoJugadoresInvitados,
              satoshiPerPoint = oldGame.satoshiPerPoint
            )
            val (game2, _) = newGame.applyEvent(Option(user), JoinGame())
            repository.gameOperations.upsert(game2)
          }
          _ <- ZIO.foreach(oldGame.jugadores.filter(_.id != user.id).map(_.user)) { u =>
            inviteToGame(u.id.get, withFirstUser.id.get)
          }
          afterInvites <- repository.gameOperations
            .get(withFirstUser.id.get).map(
              _.getOrElse(throw GameException("Previous game did not exist"))
            )
        } yield afterInvites).mapError(GameException.apply)

      def newGame(satoshiPerPoint: Int): ZIO[GameLayer, GameException, Game] =
        (for {
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          repository <- ZIO.service[Repository.Service]
          upserted <- {
            val newGame = Game(
              id = None,
              gameStatus = GameStatus.esperandoJugadoresInvitados,
              satoshiPerPoint = satoshiPerPoint
            )
            val (game2, _) = newGame.applyEvent(Option(user), JoinGame())
            repository.gameOperations.upsert(game2)
          }
          _ <- repository.gameOperations.updatePlayers(upserted)
          _ <- ZIO.foreachPar(upserted.jugadores.find(_.user.id == user.id))(j =>
            repository.userOperations.upsert(j.user)
          )
          _ <- broadcast(userEventQueues, UserEvent(user, UserEventType.JoinedGame))
        } yield upserted).mapError(GameException.apply)

      override def getGame(gameId: GameId): ZIO[GameLayer, GameException, Option[Game]] =
        (for {
          repository <- ZIO.service[Repository.Service]
          game       <- repository.gameOperations.get(gameId)
        } yield game).mapError(GameException.apply)

      override def getGameForUser: ZIO[GameLayer, GameException, Option[Game]] =
        (for {
          repository <- ZIO.service[Repository.Service]
          game       <- repository.gameOperations.getGameForUser
        } yield game).mapError(GameException.apply)

      def getFriends: ZIO[GameLayer, GameException, Seq[User]] =
        (for {
          repository <- ZIO.service[Repository.Service]
          friends    <- repository.userOperations.friends
        } yield friends).mapError(GameException.apply)

      def getGameInvites: ZIO[GameLayer, GameException, Seq[Game]] =
        (for {
          repository  <- ZIO.service[Repository.Service]
          gameInvites <- repository.gameOperations.gameInvites
        } yield gameInvites).mapError(GameException.apply)

      def getLoggedInUsers: ZIO[GameLayer, GameException, Seq[User]] =
        for {
          userConnectionRepo <- ZIO.service[UserConnectionRepo.Service]
          loggedInUsers      <- userConnectionRepo.connectionMap.map(_.values.take(20).toSeq)
        } yield loggedInUsers

      def friend(friendId: UserId): ZIO[GameLayer with ChatService, GameException, Boolean] =
        (for {
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          repository <- ZIO.service[Repository.Service]
          friendOpt  <- repository.userOperations.get(friendId)
          friended   <- ZIO.foreach(friendOpt)(friend => repository.userOperations.friend(friend))
          _ <- ZIO.foreach(friendOpt)(person =>
            ChatService
              .sendMessage(s"${user.name} es tu amigo :)", ChannelId.directChannel, Option(person))
          )
        } yield friended.getOrElse(false)).mapError(GameException.apply)

      def unfriend(enemyId: UserId): ZIO[GameLayer with ChatService, GameException, Boolean] =
        (for {
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          repository <- ZIO.service[Repository.Service]
          enemyOpt   <- repository.userOperations.get(enemyId)
          unfriended <- ZIO.foreach(enemyOpt)(enemy => repository.userOperations.unfriend(enemy))
          _ <- ZIO.foreach(enemyOpt)(person =>
            ChatService.sendMessage(
              s"${user.name} ya no es tu amigo :(",
              ChannelId.directChannel,
              Option(person)
            )
          )
        } yield unfriended.getOrElse(false)).mapError(GameException.apply)

      def inviteByEmail(
        name:   String,
        email:  String,
        gameId: GameId
      ): ZIO[GameLayer with ChatService, GameException, Boolean] = {
        (for {
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          repository <- ZIO.service[Repository.Service]
          postman    <- ZIO.service[Postman.Service]
          gameOpt    <- repository.gameOperations.get(gameId)
          invitedOpt <- repository.userOperations.userByEmail(email)
          invited <- invitedOpt.fold(
            repository.userOperations
              .upsert(User(None, email, name, userStatus = UserStatus.Invited))
              .provideSomeLayer[Logging](godLayer)
          )(ZIO.succeed(_))
          afterInvitation <- ZIO.foreach(gameOpt) { game =>
            if (!game.jugadores.exists(_.user.id == user.id))
              throw GameException(s"User ${user.id} is not in this game, he can't invite anyone")
            val (withInvite, invitation) =
              game.applyEvent(Option(user), InviteToGame(invited = invited))
            repository.gameOperations.upsert(withInvite).map((_, invitation))
          }
          _ <- ChatService
            .sendMessage(s"${user.name} te invitó a jugar", ChannelId.directChannel, invitedOpt)
          _ <- ZIO.foreach(afterInvitation)(g => repository.gameOperations.updatePlayers(g._1))
          envelopeOpt <- invitedOpt.fold(
            postman.inviteToPlayByEmail(user, invited).map(Option(_))
          )(_ =>
            ZIO.foreach(afterInvitation) {
              case (game, _) =>
                postman.inviteToGameEmail(user, invited, game)
            }
          )
          _ <- ZIO.foreach(envelopeOpt)(envelope => postman.deliver(envelope))
          _ <- ZIO.foreach(afterInvitation) {
            case (_, event) =>
              broadcast(gameEventQueues, event)
          }
        } yield true).mapError(GameException.apply)
      }

      def inviteToGame(
        userId: UserId,
        gameId: GameId
      ): ZIO[GameLayer with ChatService, GameException, Boolean] = {
        (for {
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          repository <- ZIO.service[Repository.Service]
          postman    <- ZIO.service[Postman.Service]
          gameOpt    <- repository.gameOperations.get(gameId)
          invitedOpt <- repository.userOperations.get(userId)
          afterInvitation <- ZIO.foreach(gameOpt) { game =>
            if (invitedOpt.isEmpty)
              throw GameException(s"User $userId does not exist")
            if (!game.jugadores.exists(_.user.id == user.id))
              throw GameException(s"User ${user.id} is not in this game, he can't invite anyone")
            val (withInvite, invitation) =
              game.applyEvent(Option(user), InviteToGame(invited = invitedOpt.get))
            repository.gameOperations.upsert(withInvite).map((_, invitation))
          }
          _ <- ChatService
            .sendMessage(s"${user.name} te invitó a jugar", ChannelId.directChannel, invitedOpt)
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

      private def joinGame(gameOpt: Option[Game]): ZIO[GameLayer, RepositoryException, Game] = {
        for {
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          repository <- ZIO.service[Repository.Service]
          newOrRetrieved <- gameOpt.fold(
            //The game may have no players yet, so god needs to save it
            repository.gameOperations
              .upsert(Game(None, gameStatus = GameStatus.esperandoJugadoresAzar)).provideSomeLayer[
                Logging
              ](godLayer)
          )(game => ZIO.succeed(game))
          afterApply <- {
            val (joined, joinGame) = newOrRetrieved.applyEvent(Option(user), JoinGame())
            val (started, startGame: GameEvent) =
              if (joined.canTransitionTo(GameStatus.requiereSopa)) {
                joined
                  .copy(gameStatus = GameStatus.requiereSopa).applyEvent(
                    Option(user),
                    Sopa(firstSopa = true)
                  )
                //TODO change player status, and update players in LoggedIn Players and in database, invalidate db cache
              } else {
                joined.applyEvent(Option(user), NoOp())
              }
            repository.gameOperations.upsert(started).map((_, joinGame, startGame))
          }
          _ <- repository.gameOperations.updatePlayers(afterApply._1)
          _ <- ZIO.foreachPar(afterApply._1.jugadores.find(_.user.id == user.id))(j =>
            repository.userOperations.upsert(j.user)
          )
          _ <- broadcast(gameEventQueues, afterApply._2)
          _ <- broadcast(gameEventQueues, afterApply._3)
          _ <- broadcast(userEventQueues, UserEvent(user, UserEventType.JoinedGame))
        } yield afterApply._1
      }

      def acceptGameInvitation(gameId: GameId): ZIO[GameLayer, GameException, Game] =
        (for {
          repository <- ZIO.service[Repository.Service]
          gameOpt    <- repository.gameOperations.get(gameId)
          joined     <- joinGame(gameOpt)
        } yield joined).mapError(GameException.apply)

      def declineGameInvitation(
        gameId: GameId
      ): ZIO[GameLayer with ChatService, GameException, Boolean] =
        (for {
          repository <- ZIO.service[Repository.Service]
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
            repository.gameOperations
              .upsert(afterEvent).map((_, declinedEvent))
              .provideSomeLayer[Logging](godLayer)
          }
          _ <- ZIO.foreachPar(afterEvent.toSeq.flatMap(_._1.jugadores))(jugador =>
            ChatService
              .sendMessage(
                s"${user.name} rechazó la invitación",
                ChannelId.directChannel,
                Option(jugador.user)
              )
          )
          _ <- ZIO.foreachPar(afterEvent)(g => repository.gameOperations.updatePlayers(g._1))
          _ <- ZIO.foreachPar(afterEvent) {
            case (_, event) =>
              broadcast(gameEventQueues, event)
          }
        } yield true).mapError(GameException.apply)

      def cancelUnacceptedInvitations(
        gameId: GameId
      ): ZIO[GameLayer with ChatService, GameException, Boolean] = {
        (for {
          repository <- ZIO.service[Repository.Service]
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          gameOpt    <- repository.gameOperations.get(gameId)
          afterEvent <- ZIO.foreach(gameOpt) { game =>
            if (game.gameStatus.enJuego)
              throw GameException(
                s"User $user tried to decline an invitation for a game that had already started"
              )
            repository.gameOperations
              .upsert(game.copy(jugadores = game.jugadores.filter(!_.invited)))
              .provideSomeLayer[Logging](godLayer)
          }
          _ <- ZIO.foreachPar(gameOpt.toSeq.flatMap(_.jugadores.filter(_.id != user.id)))(jugador =>
            ChatService
              .sendMessage(
                s"${user.name} canceló las invitaciones de los jugadores que no habían aceptado.",
                ChannelId.directChannel,
                Option(jugador.user)
              )
          )
          _ <- ZIO.foreachPar(afterEvent)(repository.gameOperations.updatePlayers)
        } yield true).mapError(GameException.apply)
      }

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

        val res = playEvent match {
          case e: Da if e.hoyoTecnico.isDefined => applyHoyoTecnico(game, e.hoyoTecnico.get)
          case e: Pide if e.hoyoTecnico.isDefined => applyHoyoTecnico(game, e.hoyoTecnico.get)
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

            if (helechoGame.jugadores.exists(_.fichas.nonEmpty)) {
              (helechoGame, helechoBorlote)
            } else {
              //Ya se acabo el juego, a nadie le quedan fichas
              val a = helechoGame.applyEvent(Option(user), TerminaJuego())
              (a._1, helechoBorlote :+ a._2)
            }
        }
        res._2.foldLeft((res._1, res._2))((a, b) => (b.processStatusMessages(a._1), a._2))
      }

      //TODO comment this out once we've tested redo.
      def testRedoEvent(
        before: Game,
        after:  Game,
        event:  GameEvent,
        user:   User
      ): Unit = {
        if (!event.isInstanceOf[Sopa]) { //Sopa is special, it isn't redone
          before.jugadores.foreach { jugador =>
            val sanitizedBefore = GameApi.sanitizeGame(before, jugador.user)
            val sanitizedAfter = GameApi.sanitizeGame(after, jugador.user)
            val redone =
              event
                .redoEvent(Option(user), sanitizedBefore)
                .copy(currentEventIndex = before.nextIndex)
            if (sanitizedAfter != redone) {
              println(sanitizedAfter.asJson.noSpaces)
              println(redone.asJson.noSpaces)
              throw new GameException("Done and ReDone should be the same")
            }
          }
        }
      }

      override def play(
        gameId:    GameId,
        playEvent: PlayEvent
      ): ZIO[GameLayer, GameException, Game] = {
        (for {
          repository <- ZIO.service[Repository.Service]
          user       <- ZIO.access[SessionProvider](_.get.session.user)
          gameOpt    <- repository.gameOperations.get(gameId)
          played <- gameOpt.fold(throw GameException("Game not found")) { game =>
            if (!game.jugadores.exists(_.user.id == user.id)) {
              throw GameException("This user isn't playing in this game!!")
            }
            val (played, event) = game.applyEvent(Option(user), playEvent)

            val withStatus = event.processStatusMessages(played)

            //TODO comment this out, or move it somewhere, or something.
            testRedoEvent(game, withStatus, event, user)

            val (transitioned, transitionEvents) = checkPlayTransition(user, withStatus, event)

            repository.gameOperations.upsert(transitioned).map((_, event +: transitionEvents))
          }
          _ <- ZIO.when(played._1.gameStatus == GameStatus.partidoTerminado) {
            updateAccounting(played._1)
          }
          _ <- ZIO.foreach(played._2)(broadcast(gameEventQueues, _))
        } yield played._1).mapError(GameException.apply)
      }

      def updateAccounting(
        game: Game
      ): ZIO[Repository with Logging, RepositoryException, List[UserWallet]] = {
        ZIO
          .foreach(game.cuentasCalculadas) {
            case (jugador, _, satoshi) =>
              for {
                repository <- ZIO.service[Repository.Service]
                walletOpt  <- repository.userOperations.getWallet(jugador.id.get)
                updated <- ZIO.foreach(walletOpt)(wallet =>
                  repository.userOperations
                    .updateWallet(wallet.copy(amount = wallet.amount + satoshi))
                )
              } yield updated.toSeq
          }.provideSomeLayer[
            Repository with Logging
          ](godLayer).map(_.flatten)
      }

      override def gameStream(
        gameId:       GameId,
        connectionId: ConnectionId
      ): ZStream[GameLayer, Nothing, GameEvent] =
        ZStream.unwrap {
          for {
            user  <- ZIO.access[SessionProvider](_.get.session.user)
            queue <- Queue.sliding[GameEvent](requestedCapacity = 100)
            _     <- gameEventQueues.update(EventQueue(user, connectionId, queue) :: _)
            after <- gameEventQueues.get
            _     <- log.info(s"GameStream started, queues have ${after.length} entries")
          } yield ZStream
            .fromQueue(queue)
            .ensuring(
              queue.shutdown *>
                gameEventQueues.update(_.filterNot(_.connectionId == connectionId)) *>
                log.debug(s"Shut down game queue")
            )
            .tap(event => log.debug(event.toString))
            .filter(_.gameId == Option(gameId))
        }

      override def userStream(connectionId: ConnectionId): ZStream[GameLayer, Nothing, UserEvent] =
        ZStream.unwrap {
          for {
            user               <- ZIO.access[SessionProvider](_.get.session.user)
            allUserQueues      <- userEventQueues.get
            userConnectionRepo <- ZIO.service[UserConnectionRepo.Service]
            _                  <- userConnectionRepo.addConnection(connectionId, user)
            _ <- {
              //Only broadcast connections if the user is not yet in one of the queues, and don't send
              //the event to the same user that just logged in (no point, they know they logged in)
              if (!allUserQueues.exists(_.user.id == user.id)) {
                val userEvent = UserEvent(user, UserEventType.Connected)
                UIO.foreach(allUserQueues) { userQueue =>
                  userQueue.queue.offer(userEvent)
                }
              } else {
                ZIO.unit
              }
            }
            queue <- Queue.sliding[UserEvent](requestedCapacity = 100)
            _     <- userEventQueues.update(EventQueue(user, connectionId, queue) :: _)
            after <- userEventQueues.get
            _     <- log.info(s"UserStream started, queues have ${after.length} entries")
          } yield ZStream
            .fromQueue(queue).ensuring {
              queue.shutdown *>
                userEventQueues.update(_.filterNot(_.connectionId == connectionId)) *>
                log.debug(s"Shut down user queue") *>
                userConnectionRepo.removeConnection(connectionId) *>
                //Only broadcast disconnects if it's the last entry of the user in the queue of connections
                broadcast(userEventQueues, UserEvent(user, UserEventType.Disconnected))
                  .whenM(userConnectionRepo.connectionMap.map(!_.values.exists(_.id == user.id)))
            }.catchAllCause { c =>
              c.prettyPrint
              ZStream.halt(c)
            }
        }

    }
  }

}
