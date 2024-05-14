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

package game

import api.ChutiSession
import api.token.TokenHolder
import caliban.{CalibanError, GraphQLInterpreter}
import chat.*
import chuti.*
import chuti.Numero.{Numero0, Numero1}
import chuti.Triunfo.TriunfoNumero
import dao.{Repository, RepositoryError, SessionContext}
import game.GameService.{EventQueue, GameLayer}
import mail.Postman
import zio.{Clock, Console, *}
import zio.logging.*
import zio.stream.ZStream

trait GameService {

  protected val userQueue: Ref[List[EventQueue[UserEvent]]]
  protected val gameQueue: Ref[List[EventQueue[GameEvent]]]

  def broadcastGameEvent(gameEvent: GameEvent): ZIO[GameLayer, GameError, GameEvent]

  def joinRandomGame(): ZIO[GameLayer, GameError, Game]

  def newGame(satoshiPerPoint: Int): ZIO[GameLayer, GameError, Game]

  def newGameSameUsers(oldGameId: GameId): ZIO[GameLayer & ChatService, GameError, Game]

  def play(
    gameId:    GameId,
    playEvent: PlayEvent
  ): ZIO[GameLayer, GameError, Game]

  def getGameForUser: ZIO[GameLayer, GameError, Option[Game]]

  def getGame(gameId: GameId): ZIO[GameLayer, GameError, Option[Game]]

  def abandonGame(gameId: GameId): ZIO[GameLayer, GameError, Boolean]

  def startGame(gameId: GameId): ZIO[GameLayer, GameError, Boolean]

  def getFriends: ZIO[GameLayer, GameError, Seq[User]]

  def getGameInvites: ZIO[GameLayer, GameError, Seq[Game]]

  def getLoggedInUsers: ZIO[GameLayer, GameError, Seq[User]]

  def getHistoricalUserGames: ZIO[GameLayer, GameError, Seq[Game]]

  def inviteByEmail(
    name:   String,
    email:  String,
    gameId: GameId
  ): ZIO[GameLayer & ChatService, GameError, Boolean]

  def inviteToGame(
    userId: UserId,
    gameId: GameId
  ): ZIO[GameLayer & ChatService, GameError, Boolean]

  def friend(userId: UserId): ZIO[GameLayer & ChatService, GameError, Boolean]

  def unfriend(userId: UserId): ZIO[GameLayer & ChatService, GameError, Boolean]

  def acceptGameInvitation(gameId: GameId): ZIO[GameLayer, GameError, Game]

  def cancelUnacceptedInvitations(
    gameId: GameId
  ): ZIO[GameLayer & ChatService, GameError, Boolean]

  def declineGameInvitation(
    gameId: GameId
  ): ZIO[GameLayer & ChatService, GameError, Boolean]

  def gameStream(
    gameId:       GameId,
    connectionId: ConnectionId
  ): ZStream[GameLayer, GameError, GameEvent]

  def userStream(connectionId: ConnectionId): ZStream[GameLayer, GameError, UserEvent]

}

object GameService {

  lazy val godLayer: ULayer[SessionContext] =
    SessionContext.live(ChutiSession(chuti.god))

  lazy val godlessLayer: ULayer[SessionContext] =
    SessionContext.live(ChutiSession(chuti.godless))

  given runtime: Runtime[Any] = zio.Runtime.default

  type GameLayer = SessionContext & Repository & Postman & TokenHolder

  lazy val interpreter: IO[Throwable, GraphQLInterpreter[GameService & GameLayer & ChatService, CalibanError]] = GameApi.api.interpreter

  def joinRandomGame(): ZIO[GameService & GameLayer, GameError, Game] = ZIO.service[GameService].flatMap(_.joinRandomGame())

  def abandonGame(gameId: GameId): ZIO[GameService & GameLayer, GameError, Boolean] = ZIO.service[GameService].flatMap(_.abandonGame(gameId))

  def startGame(gameId: GameId): ZIO[GameService & GameLayer, GameError, Boolean] = ZIO.service[GameService].flatMap(_.startGame(gameId))

  def newGame(satoshiPerPoint: Int): ZIO[GameService & GameLayer, GameError, Game] = ZIO.service[GameService].flatMap(_.newGame(satoshiPerPoint))

  def newGameSameUsers(
    gameId: GameId
  ): ZIO[GameService & GameLayer & ChatService, GameError, Game] = ZIO.service[GameService].flatMap(_.newGameSameUsers(gameId))

  def play(
    gameId:    GameId,
    playEvent: PlayEvent
  ): ZIO[GameService & GameLayer, GameError, Boolean] =
    ZIO
      .service[GameService].flatMap(_.play(gameId, playEvent).as(true))

  def getGameForUser: ZIO[GameService & GameLayer, GameError, Option[Game]] = ZIO.service[GameService].flatMap(_.getGameForUser)

  def getGame(gameId: GameId): ZIO[GameService & GameLayer, GameError, Option[Game]] = ZIO.service[GameService].flatMap(_.getGame(gameId))

  def getFriends: ZIO[GameService & GameLayer, GameError, Seq[User]] = ZIO.service[GameService].flatMap(_.getFriends)

  def inviteByEmail(
    name:   String,
    email:  String,
    gameId: GameId
  ): ZIO[GameService & GameLayer & ChatService, GameError, Boolean] = ZIO.service[GameService].flatMap(_.inviteByEmail(name, email, gameId))

  def inviteToGame(
    userId: UserId,
    gameId: GameId
  ): ZIO[GameService & GameLayer & ChatService, GameError, Boolean] = ZIO.service[GameService].flatMap(_.inviteToGame(userId, gameId))

  def getGameInvites: ZIO[GameService & GameLayer, GameError, Seq[Game]] = ZIO.service[GameService].flatMap(_.getGameInvites)

  def getLoggedInUsers: ZIO[GameService & GameLayer, GameError, Seq[User]] = ZIO.service[GameService].flatMap(_.getLoggedInUsers)

  def getHistoricalUserGames: ZIO[GameService & GameLayer, GameError, Seq[Game]] = ZIO.service[GameService].flatMap(_.getHistoricalUserGames)

  def acceptGameInvitation(gameId: GameId): ZIO[GameService & GameLayer, GameError, Game] =
    ZIO.service[GameService].flatMap(_.acceptGameInvitation(gameId))

  def declineGameInvitation(
    gameId: GameId
  ): ZIO[GameService & GameLayer & ChatService, GameError, Boolean] = ZIO.service[GameService].flatMap(_.declineGameInvitation(gameId))

  def cancelUnacceptedInvitations(
    gameId: GameId
  ): ZIO[GameService & GameLayer & ChatService, GameError, Boolean] = ZIO.service[GameService].flatMap(_.cancelUnacceptedInvitations(gameId))

  def friend(
    userId: UserId
  ): ZIO[GameService & GameLayer & ChatService, GameError, Boolean] = ZIO.service[GameService].flatMap(_.friend(userId))

  def unfriend(
    userId: UserId
  ): ZIO[GameService & GameLayer & ChatService, GameError, Boolean] = ZIO.service[GameService].flatMap(_.unfriend(userId))

  def gameStream(
    gameId:       GameId,
    connectionId: ConnectionId
  ): ZStream[GameService & GameLayer, GameError, GameEvent] = ZStream.service[GameService].flatMap(_.gameStream(gameId, connectionId))

  def userStream(
    connectionId: ConnectionId
  ): ZStream[GameService & GameLayer, GameError, UserEvent] = ZStream.service[GameService].flatMap(_.userStream(connectionId))

  case class EventQueue[EventType](
    user:         User,
    connectionId: ConnectionId,
    queue:        Queue[EventType]
  )

  private def broadcast[EventType](
    allQueuesRef: Ref[List[EventQueue[EventType]]],
    event:        EventType
  ): ZIO[Any, Nothing, EventType] = {
    for {
      _         <- ZIO.logInfo(s"Broadcasting event $event")
      allQueues <- allQueuesRef.get
      sent <-
        ZIO
          .foreachPar(allQueues) { queue =>
            queue.queue
              .offer(event)
              .onInterrupt(allQueuesRef.update(_.filterNot(_ == queue)))
          }
          .as(event)
    } yield sent
  }

  def make(): ULayer[GameService] =
    ZLayer.fromZIO(for {
      _               <- ZIO.logInfo("Making GameService")
      userEventQueues <- Ref.make(List.empty[EventQueue[UserEvent]])
      gameEventQueues <- Ref.make(List.empty[EventQueue[GameEvent]])
    } yield new GameService {

      override val userQueue: Ref[List[EventQueue[UserEvent]]] = userEventQueues
      override val gameQueue: Ref[List[EventQueue[GameEvent]]] = gameEventQueues

      override def abandonGame(gameId: GameId): ZIO[GameLayer, GameError, Boolean] =
        (for {
          user <- ZIO.service[SessionContext].map(_.session.user)

          repository <- ZIO.service[Repository]
          gameOpt    <- repository.gameOperations.get(gameId)
          savedOpt <- ZIO.foreach(gameOpt) { game =>
            if (!game.jugadores.exists(_.id == user.id)) ZIO.fail(GameError("Ese usuario no esta jugando matarile-rile-ron"))
            else {
              val (gameAfterApply, appliedEvent) =
                game.applyEvent(Option(user), AbandonGame())
              // God needs to save this user, because the user has already left the game
              repository.gameOperations
                .upsert(gameAfterApply).map((_, appliedEvent)).provideLayer(godLayer)
            }
          }
          players <- ZIO.foreach(savedOpt) { case (game, event) =>
            repository.gameOperations.updatePlayers(game).map((_, event))
          }
          _ <- ZIO.foreachDiscard(players) {
            case (game, _)
                if game.jugadores.isEmpty &&
                  (game.gameStatus == GameStatus.esperandoJugadoresInvitados ||
                    game.gameStatus == GameStatus.esperandoJugadoresAzar) =>
              repository.gameOperations
                .delete(game.id.get).provideLayer(godLayer)
            case (game, _) if game.jugadores.isEmpty =>
              repository.gameOperations
                .delete(game.id.get, softDelete = true).provideLayer(godLayer)
            case (game, _) => ZIO.succeed(game)
          }
          //          _ <- ZIO.foreachPar(players) { game =>
          // TODO make sure that current losses in this game are also assigned to the user
          // TODO change player status, and update players in LoggedIn Players and in database, invalidate db cache
          //            repository.userOperations
          //              .upsert(
          //                user
          //                  .copy(
          //                    userStatus = UserStatus.Idle
          //                  )
          //              )
          //          }
          walletOpt <- repository.userOperations.getWallet
          _ <- ZIO.foreachDiscard(gameOpt.flatMap(g => walletOpt.map(w => (g, w)))) {
            case (game, wallet) if game.gameStatus.enJuego =>
              val lostPoints =
                game.cuentasCalculadas.find(_._1.id == user.id).map(_._2).getOrElse(0)

              repository.userOperations
                .updateWallet(
                  wallet.copy(amount = wallet.amount - ((lostPoints + game.abandonedPenalty) * game.satoshiPerPoint))
                ).provideLayer(godLayer)
            case _ => ZIO.succeed(true)
          }
          _ <- ZIO.foreachDiscard(savedOpt) { case (_, appliedEvent) =>
            broadcast(gameEventQueues, appliedEvent)
          }
          _ <- broadcast(
            userEventQueues,
            UserEvent(user, UserEventType.AbandonedGame, savedOpt.flatMap(_._1.id))
          )
        } yield savedOpt.nonEmpty).mapError(GameError.apply)

      override def broadcastGameEvent(
        gameEvent: GameEvent
      ): ZIO[GameLayer, GameError, GameEvent] = {
        broadcast(gameEventQueues, gameEvent)
      }

      private def botLayer: ULayer[SessionContext] = {
        SessionContext.live(ChutiSession(hal9000))
      }

      override def startGame(gameId: GameId): ZIO[GameLayer, GameError, Boolean] =
        (for {
          repository <- ZIO.service[Repository]
          gameOpt    <- repository.gameOperations.get(gameId)
          _ <- ZIO.foreachDiscard(gameOpt) { game =>
            {
              (game.jugadores.size until game.numPlayers)
                .foldLeft(
                  ZIO
                    .succeed(game).asInstanceOf[
                      ZIO[Repository & Postman & TokenHolder, Throwable, Game]
                    ]
                )((foldedGame, _) =>
                  foldedGame
                    .map(g =>
                      joinGame(Option(g))
                        .provideSomeLayer[Repository & Postman & TokenHolder](
                          botLayer
                        )
                    ).flatten
                )
            }
          }
        } yield true).mapError(GameError.apply)

      override def joinRandomGame(): ZIO[GameLayer, GameError, Game] =
        (for {
          repository <- ZIO.service[Repository]
          gameOpt <-
            repository.gameOperations
              .gamesWaitingForPlayers().mapBoth(GameError.apply, _.headOption)
          joined <- joinGame(gameOpt)
        } yield joined).mapError(GameError.apply)

      override def newGameSameUsers(
        oldGameId: GameId
      ): ZIO[GameLayer & ChatService, GameError, Game] =
        (for {
          user <- ZIO.service[SessionContext].map(_.session.user)

          repository <- ZIO.service[Repository]
          now        <- Clock.instant
          oldGame <-
            repository.gameOperations
              .get(oldGameId)
              .flatMap(ZIO.fromOption(_).orElseFail(GameError("No existe juego previo")))
          withFirstUser <- {
            val newGame = Game(
              id = None,
              gameStatus = GameStatus.esperandoJugadoresInvitados,
              satoshiPerPoint = oldGame.satoshiPerPoint,
              created = now
            )
            val (game2, _) = newGame.applyEvent(Option(user), JoinGame())
            repository.gameOperations.upsert(game2)
          }
          _ <- ZIO.foreachDiscard(oldGame.jugadores.filter(_.id != user.id).map(_.user)) { u =>
            inviteToGame(u.id.get, withFirstUser.id.get)
          }
          afterInvites <-
            repository.gameOperations
              .get(withFirstUser.id.get)
              .flatMap(ZIO.fromOption(_).orElseFail(GameError("No existe juego previo")))
        } yield afterInvites).mapError(GameError.apply)

      override def newGame(satoshiPerPoint: Int): ZIO[GameLayer, GameError, Game] =
        (for {
          user <- ZIO.service[SessionContext].map(_.session.user)

          repository <- ZIO.service[Repository]
          now        <- Clock.instant
          upserted <- {
            val newGame = Game(
              id = None,
              gameStatus = GameStatus.esperandoJugadoresInvitados,
              satoshiPerPoint = satoshiPerPoint,
              created = now
            )
            val (game2, _) = newGame.applyEvent(Option(user), JoinGame())
            repository.gameOperations.upsert(game2)
          }
          _ <- repository.gameOperations.updatePlayers(upserted)
          _ <- ZIO.foreachDiscard(upserted.jugadores.find(_.user.id == user.id).filter(!_.user.isBot)) { j =>
            repository.userOperations.upsert(j.user)
          }
          _ <- broadcast(userEventQueues, UserEvent(user, UserEventType.JoinedGame, upserted.id))
        } yield upserted).mapError(GameError.apply)

      override def getGame(gameId: GameId): ZIO[GameLayer, GameError, Option[Game]] =
        (for {
          repository <- ZIO.service[Repository]
          game       <- repository.gameOperations.get(gameId)
        } yield game).mapError(GameError.apply)

      override def getHistoricalUserGames: ZIO[GameLayer, GameError, Seq[Game]] =
        (for {
          repository <- ZIO.service[Repository]
          games      <- repository.gameOperations.getHistoricalUserGames
        } yield games).mapError(GameError.apply)

      override def getGameForUser: ZIO[GameLayer, GameError, Option[Game]] =
        (for {
          repository <- ZIO.service[Repository]
          game       <- repository.gameOperations.getGameForUser
        } yield game).mapError(GameError.apply)

      override def getFriends: ZIO[GameLayer, GameError, Seq[User]] =
        (for {
          repository <- ZIO.service[Repository]
          friends    <- repository.userOperations.friends
        } yield friends).mapError(GameError.apply)

      override def getGameInvites: ZIO[GameLayer, GameError, Seq[Game]] =
        (for {
          repository  <- ZIO.service[Repository]
          gameInvites <- repository.gameOperations.gameInvites
        } yield gameInvites).mapError(GameError.apply)

      override def getLoggedInUsers: ZIO[GameLayer, GameError, Seq[User]] = userEventQueues.get.map(_.map(_.user).distinctBy(_.id).take(20))

      override def friend(
        friendId: UserId
      ): ZIO[GameLayer & ChatService, GameError, Boolean] =
        (for {
          user <- ZIO.service[SessionContext].map(_.session.user)

          repository <- ZIO.service[Repository]
          friendOpt  <- repository.userOperations.get(friendId)
          friended   <- ZIO.foreach(friendOpt)(friend => repository.userOperations.friend(friend))
          _ <- ZIO.foreachDiscard(friendOpt) { person =>
            ChatService
              .sendMessage(
                s"${user.name} es tu amigo :)",
                ChannelId.directChannel,
                Option(person)
              )
          }
        } yield friended.getOrElse(false)).mapError(GameError.apply)

      override def unfriend(
        enemyId: UserId
      ): ZIO[GameLayer & ChatService, GameError, Boolean] =
        (for {
          user <- ZIO.service[SessionContext].map(_.session.user)

          repository <- ZIO.service[Repository]
          enemyOpt   <- repository.userOperations.get(enemyId)
          unfriended <- ZIO.foreach(enemyOpt)(enemy => repository.userOperations.unfriend(enemy))
          _ <- ZIO.foreachDiscard(enemyOpt) { person =>
            ChatService.sendMessage(
              s"${user.name} ya no es tu amigo :(",
              ChannelId.directChannel,
              Option(person)
            )
          }
        } yield unfriended.getOrElse(false)).mapError(GameError.apply)

      override def inviteByEmail(
        name:   String,
        email:  String,
        gameId: GameId
      ): ZIO[GameLayer & ChatService, GameError, Boolean] = {
        (for {
          user <- ZIO.service[SessionContext].map(_.session.user)

          repository <- ZIO.service[Repository]
          postman    <- ZIO.service[Postman]
          now        <- Clock.instant
          gameOpt    <- repository.gameOperations.get(gameId)
          invitedOpt <- repository.userOperations.userByEmail(email)
          invited <- invitedOpt.fold(
            repository.userOperations
              .upsert(User(None, email, name, created = now, lastUpdated = now))
              .provideLayer(godLayer)
          )(ZIO.succeed(_))
          afterInvitation <- ZIO.foreach(gameOpt) { game =>
            if (!game.jugadores.exists(_.user.id == user.id))
              ZIO.fail(
                GameError(
                  s"El usuario ${user.id} no esta en este juego, por lo cual no puede invitar a nadie"
                )
              )
            else {
              val (withInvite, invitation) =
                game.applyEvent(Option(user), InviteToGame(invited = invited))
              repository.gameOperations.upsert(withInvite).map((_, invitation))
            }
          }
          _ <-
            ChatService
              .sendMessage(s"${user.name} te invitó a jugar", ChannelId.directChannel, invitedOpt)
          _ <- ZIO.foreachDiscard(afterInvitation)(g => repository.gameOperations.updatePlayers(g._1))
          _ <-
            invitedOpt
              .fold(
                postman.inviteToPlayByEmail(user, invited).map(Option(_))
              )(_ =>
                ZIO.foreach(afterInvitation) { case (game, _) =>
                  postman.inviteToGameEmail(user, invited, game)
                }
              ).flatMap(envelopeOpt => ZIO.foreach(envelopeOpt)(envelope => postman.deliver(envelope))).forkDaemon
          _ <- ZIO.foreachDiscard(afterInvitation) { case (_, event) =>
            broadcast(gameEventQueues, event)
          }
        } yield true).mapError(GameError.apply)
      }

      override def inviteToGame(
        userId: UserId,
        gameId: GameId
      ): ZIO[GameLayer & ChatService, GameError, Boolean] = {
        (for {
          user <- ZIO.service[SessionContext].map(_.session.user)

          repository <- ZIO.service[Repository]
          postman    <- ZIO.service[Postman]
          gameOpt    <- repository.gameOperations.get(gameId)
          invitedOpt <- repository.userOperations.get(userId)
          afterInvitation <- ZIO.foreach(gameOpt) { game =>
            if (invitedOpt.isEmpty) ZIO.fail(GameError(s"El usuario $userId no existe"))
            else if (!game.jugadores.exists(_.user.id == user.id)) {
              ZIO.fail(
                GameError(
                  s"El usuario ${user.id} no esta en este juego, por lo que no puede invitar a nadie"
                )
              )
            } else {

              val (withInvite, invitation) =
                game.applyEvent(Option(user), InviteToGame(invited = invitedOpt.get))
              repository.gameOperations.upsert(withInvite).map((_, invitation))
            }
          }
          _ <-
            ChatService
              .sendMessage(s"${user.name} te invitó a jugar", ChannelId.directChannel, invitedOpt)
          _ <- ZIO.foreachDiscard(afterInvitation)(g => repository.gameOperations.updatePlayers(g._1))
          _ <-
            ZIO
              .foreach(invitedOpt.flatMap(u => afterInvitation.map(g => (u, g._1)))) { case (invited, game) =>
                postman.inviteToGameEmail(user, invited, game)
              }.flatMap(envelopeOpt => ZIO.foreach(envelopeOpt)(envelope => postman.deliver(envelope))).forkDaemon
          _ <- ZIO.foreachDiscard(afterInvitation) { case (_, event) =>
            broadcast(gameEventQueues, event)
          }
        } yield true).mapError(GameError.apply)
      }

      private def joinGame(gameOpt: Option[Game]): ZIO[GameLayer, RepositoryError, Game] = {
        for {
          user <- ZIO.service[SessionContext].map(_.session.user)

          repository <- ZIO.service[Repository]
          now        <- Clock.instant
          newOrRetrieved <- gameOpt.fold(
            // The game may have no players yet, so god needs to save it
            repository.gameOperations
              .upsert(
                Game(None, gameStatus = GameStatus.esperandoJugadoresAzar, created = now)
              ).provideLayer(godLayer)
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
                // TODO change player status, and update players in LoggedIn Players and in database, invalidate db cache
              } else
                joined.applyEvent(Option(user), NoOp())
            repository.gameOperations.upsert(started).map((_, joinGame, startGame))
          }
          _ <- repository.gameOperations.updatePlayers(afterApply._1)
          _ <- ZIO.foreachDiscard(afterApply._1.jugadores.find(_.user.id == user.id).filter(!_.user.isBot)) { j =>
            repository.userOperations.upsert(j.user)
          }
          _ <- broadcast(gameEventQueues, afterApply._2)
          _ <- broadcast(gameEventQueues, afterApply._3)
          _ <- broadcast(
            userEventQueues,
            UserEvent(user, UserEventType.JoinedGame, afterApply._1.id)
          )
        } yield afterApply._1
      }

      override def acceptGameInvitation(gameId: GameId): ZIO[GameLayer, GameError, Game] =
        (for {
          repository <- ZIO.service[Repository]
          gameOpt    <- repository.gameOperations.get(gameId)
          joined     <- joinGame(gameOpt)
        } yield joined).mapError(GameError.apply)

      override def declineGameInvitation(
        gameId: GameId
      ): ZIO[GameLayer & ChatService, GameError, Boolean] =
        (for {
          repository <- ZIO.service[Repository]
          user       <- ZIO.service[SessionContext].map(_.session.user)

          gameOpt <- repository.gameOperations.get(gameId)
          afterEvent <- ZIO.foreach(gameOpt) { game =>
            if (game.gameStatus.enJuego)
              ZIO.fail(
                GameError(
                  s"El usuario $user trato de declinar una invitacion a un juego que ya habia empezado"
                )
              )
            else if (!game.jugadores.exists(_.user.id == user.id)) ZIO.fail(GameError(s"El usuario ${user.id} ni siquera esta en este juego"))
            else {
              val (afterEvent, declinedEvent) = game.applyEvent(Option(user), DeclineInvite())
              repository.gameOperations
                .upsert(afterEvent).map((_, declinedEvent))
                .provideLayer(godLayer)
            }
          }
          _ <- ZIO.foreachParDiscard(afterEvent.toSeq.flatMap(_._1.jugadores)) { jugador =>
            ChatService
              .sendMessage(
                s"${user.name} rechazó la invitación",
                ChannelId.directChannel,
                Option(jugador.user)
              )
          }
          _ <- ZIO.foreachDiscard(afterEvent)(g => repository.gameOperations.updatePlayers(g._1))
          _ <- ZIO.foreachDiscard(afterEvent) { case (_, event) =>
            broadcast(gameEventQueues, event)
          }
        } yield true).mapError(GameError.apply)

      override def cancelUnacceptedInvitations(
        gameId: GameId
      ): ZIO[GameLayer & ChatService, GameError, Boolean] = {
        (for {
          repository <- ZIO.service[Repository]
          user       <- ZIO.service[SessionContext].map(_.session.user)

          gameOpt <- repository.gameOperations.get(gameId)
          afterEvent <- ZIO.foreach(gameOpt) { game =>
            if (game.gameStatus.enJuego)
              ZIO.fail(
                GameError(
                  s"User $user tried to decline an invitation for a game that had already started"
                )
              )
            else
              repository.gameOperations
                .upsert(game.copy(jugadores = game.jugadores.filter(!_.invited)))
                .provideLayer(godLayer)
          }
          _ <- ZIO.foreachParDiscard(gameOpt.toSeq.flatMap(_.jugadores.filter(_.id != user.id))) { jugador =>
            ChatService
              .sendMessage(
                s"${user.name} canceló las invitaciones de los jugadores que no habían aceptado.",
                ChannelId.directChannel,
                Option(jugador.user)
              )
          }
          _ <- ZIO.foreachDiscard(afterEvent)(repository.gameOperations.updatePlayers)
        } yield true).mapError(GameError.apply)
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
          case e: Da if e.hoyoTecnico.isDefined   => applyHoyoTecnico(game, e.hoyoTecnico.get)
          case e: Pide if e.hoyoTecnico.isDefined => applyHoyoTecnico(game, e.hoyoTecnico.get)
          case e: Da
              if e.ficha == Game.campanita && !game.triunfo
                .contains(TriunfoNumero(Numero1)) && !game.triunfo
                .contains(TriunfoNumero(Numero0)) =>
            val a = game.applyEvent(Option(user), BorloteEvent(Borlote.Campanita))
            (a._1, Seq(a._2))
          case _ =>
            val (regalosGame, regalosBorlote) =
              if (game.jugadores.filter(!_.cantante).count(_.filas.size == 1) == 3) {
                val a = game.applyEvent(Option(user), BorloteEvent(Borlote.SantaClaus))
                (a._1, Seq(a._2))
              } else if (game.jugadores.filter(!_.cantante).exists(_.filas.size == 3)) {
                val a = game.applyEvent(Option(user), BorloteEvent(Borlote.ElNiñoDelCumpleaños))
                (a._1, Seq(a._2))
              } else (game, Seq.empty)

            val (helechoGame, helechoBorlote) =
              if (
                game.jugadores.find(!_.cantante).fold(false)(_.filas.size == 4) && game.jugadores
                  .exists(_.filas.size < 4)
              ) {
                val a = game.applyEvent(Option(user), BorloteEvent(Borlote.Helecho))
                (a._1, Seq(a._2))
              } else (regalosGame, regalosBorlote)

            if (helechoGame.jugadores.exists(_.fichas.nonEmpty)) (helechoGame, helechoBorlote)
            else {
              // Ya se acabo el juego, a nadie le quedan fichas
              val a = helechoGame.applyEvent(Option(user), TerminaJuego())
              (a._1, helechoBorlote :+ a._2)
            }
        }
        res._2.foldLeft((res._1, res._2))((a, b) => (b.processStatusMessages(a._1), a._2))
      }

      //      // TODO comment this out once we've tested redo.
      //      protected def testRedoEvent(
      //                         before: Game,
      //                         after: Game,
      //                         event: GameEvent,
      //                         user: User
      //                       ): IO[GameError, Unit] = {
      //        ZIO.foreach(before.jugadores) { jugador =>
      //          val sanitizedBefore = GameApi.sanitizeGame(before, jugador.user)
      //          val sanitizedAfter = GameApi.sanitizeGame(after, jugador.user)
      //          val redone =
      //            event
      //              .redoEvent(Option(user), sanitizedBefore)
      //              .copy(currentEventIndex = before.nextIndex)
      //          {
      //            println(sanitizedAfter.toJson.noSpaces)
      //            println(redone.toJson.noSpaces)
      //            ZIO.fail(GameError("Done and ReDone should be the same"))
      //          }.unless(sanitizedAfter == redone)
      //        }.unless(event.isInstanceOf[Sopa])
      //      }.unit

      override def play(
        gameId:    GameId,
        playEvent: PlayEvent
      ): ZIO[GameLayer, GameError, Game] = {
        (for {
          repository <- ZIO.service[Repository]
          user       <- ZIO.service[SessionContext].map(_.session.user)

          gameOpt <- repository.gameOperations.get(gameId)
          played <- gameOpt.fold(ZIO.fail(GameError("No encontre ese juego"))) { game =>
            if (!game.jugadores.exists(_.user.id == user.id)) ZIO.fail(GameError("El usuario no esta jugando en este juego!!"))
            else {
              val (played, event) = game.applyEvent(Option(user), playEvent)

              val withStatus = event.processStatusMessages(played)

              // comment this out, or move it somewhere, or something.
              //            testRedoEvent(game, withStatus, event, user)

              val (transitioned, transitionEvents) = checkPlayTransition(user, withStatus, event)

              repository.gameOperations.upsert(transitioned).map((_, event +: transitionEvents))
            }
          }
          _ <- ZIO.when(played._1.gameStatus == GameStatus.partidoTerminado) {
            updateAccounting(played._1)
          }
          _ <- ZIO.foreachDiscard(played._2)(broadcast(gameEventQueues, _))
        } yield played._1).mapError(GameError.apply)
      }

      def updateAccounting(
        game: Game
      ): ZIO[Repository, RepositoryError, List[UserWallet]] = {
        ZIO
          .foreach(game.cuentasCalculadas) { case (jugador, _, satoshi) =>
            for {
              repository <- ZIO.service[Repository]
              walletOpt  <- repository.userOperations.getWallet(jugador.id.get)
              updated <- ZIO.foreach(walletOpt)(wallet =>
                repository.userOperations
                  .updateWallet(wallet.copy(amount = wallet.amount + satoshi)).provideLayer(godLayer)
              )
            } yield updated.toList
          }.provideSomeLayer[
            Repository
          ](godLayer).map(_.flatten.toList)
      }

      override def gameStream(
        gameId:       GameId,
        connectionId: ConnectionId
      ): ZStream[GameLayer, Nothing, GameEvent] =
        ZStream.unwrap {
          for {
            user  <- ZIO.service[SessionContext].map(_.session.user)
            queue <- Queue.sliding[GameEvent](requestedCapacity = 100)
            _     <- gameEventQueues.update(EventQueue(user, connectionId, queue) :: _)
            after <- gameEventQueues.get
            _     <- ZIO.logInfo(s"GameStream started, queues have ${after.length} entries")
          } yield ZStream
            .fromQueue(queue)
            .ensuring(
              queue.shutdown *>
                gameEventQueues.update(_.filterNot(_.connectionId == connectionId)) *>
                ZIO.logDebug(s"Shut down game queue")
            )
            .tap(event => ZIO.logDebug(event.toString))
            .filter(_.gameId == Option(gameId))
        }

      override def userStream(
        connectionId: ConnectionId
      ): ZStream[GameLayer, Nothing, UserEvent] =
        ZStream.unwrap {
          for {
            user          <- ZIO.service[SessionContext].map(_.session.user)
            allUserQueues <- userEventQueues.get
            _ <- {
              // Only broadcast connections if the user is not yet in one of the queues, and don't send
              // the event to the same user that just logged in (no point, they know they logged in)
              {
                val userEvent = UserEvent(user, UserEventType.Connected, None)
                ZIO.foreach(allUserQueues)(userQueue => userQueue.queue.offer(userEvent))
              }.unless(allUserQueues.exists(_.user.id == user.id))
            }
            queue <- Queue.sliding[UserEvent](requestedCapacity = 100)
            _     <- userEventQueues.update(EventQueue(user, connectionId, queue) :: _)
            after <- userEventQueues.get
            _     <- ZIO.logInfo(s"UserStream started, queues have ${after.length} entries")
          } yield ZStream
            .fromQueue(queue).ensuring {
              queue.shutdown *>
                userEventQueues.update(_.filterNot(_.connectionId == connectionId)) *>
                ZIO.logDebug(s"Shut down user queue") *>
                // Only broadcast disconnects if it's the last entry of the user in the queue of connections
                broadcast(userEventQueues, UserEvent(user, UserEventType.Disconnected, None))
                  .whenZIO(userEventQueues.get.map(!_.exists(_.user.id == user.id)))
            }.catchAllCause { c =>
              c.prettyPrint
              ZStream.failCause(c)
            }
        }

    })

}
