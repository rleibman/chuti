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

import api.token.TokenHolder
import api.{*, given}
import chat.*
import chuti.Numero.{Numero0, Numero1}
import chuti.Triunfo.TriunfoNumero
import chuti.bots.{ChutiBot, DumbChutiBot}
import chuti.{*, given}
import dao.{RepositoryError, ZIORepository}
import mail.Postman
import zio.*
import zio.json.*
import zio.stream.ZStream

type GameEnvironment = ZIORepository & ChatService & TokenHolder & Postman
type GameTask[A] = ZIO[GameEnvironment & ChutiSession, GameError, A]

trait GameService extends GameEngine[GameTask] {

  def broadcastGameEvent(gameEvent: GameEvent): GameTask[GameEvent]

  def gameStream(
    gameId:       GameId,
    connectionId: ConnectionId
  ): ZStream[ChutiSession & ZIORepository, GameError, GameEvent]

  def userStream(connectionId: ConnectionId): ZStream[ChutiSession & ZIORepository, GameError, UserEvent]

}

object GameService {

  lazy val godLayer: ULayer[ChutiSession] = ChutiSession.godSession.toLayer

  lazy val godlessLayer: ULayer[ChutiSession] = ChutiSession.godlessSession.toLayer

  case class EventQueue[EventType](
    user:         User,
    connectionId: ConnectionId,
    queue:        Queue[EventType]
  )

  private def broadcast[EventType](
    allQueuesRef: Ref[List[EventQueue[EventType]]],
    event:        EventType
  ): ZIO[Any, Nothing, EventType] = {
    event match {
      case _: NoOp => ZIO.succeed(event) //Don't broadcast no-ops
      case _ =>

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
  }

  def make(): ULayer[GameService] =
    ZLayer.fromZIO(for {
      userEventQueues <- Ref.make(List.empty[EventQueue[UserEvent]])
      gameEventQueues <- Ref.make(List.empty[EventQueue[GameEvent]])
    } yield new GameService {

      val userQueue: Ref[List[EventQueue[UserEvent]]] = userEventQueues
      val gameQueue: Ref[List[EventQueue[GameEvent]]] = gameEventQueues

      override def abandonGame(gameId: GameId): ZIO[ChutiSession & ZIORepository, GameError, Boolean] =
        (for {
          user <- ZIO
            .serviceWith[ChutiSession](_.user).someOrFail(RepositoryError("User is required for this operation"))
          repository <- ZIO.service[ZIORepository]
          game <- repository.gameOperations
            .get(gameId).flatMap(g => ZIO.fromOption(g).orElseFail(RepositoryError("Could not find game", None)))
          _ <- ZIO
            .fail(GameError("Ese usuario no esta jugando matarile-rile-ron")).when(
              !game.jugadores.exists(_.id == user.id)
            )
          (saved, event) <- {
            val (gameAfterApply, appliedEvent) =
              game.applyEvent(user, AbandonGame())
            // God needs to save this user, because the user has already left the game
            repository.gameOperations
              .upsert(gameAfterApply).map((_, appliedEvent)).provideLayer(godLayer)
          }
          withoutPlayers <-
            repository.gameOperations
              .updatePlayers(game)
              .provideLayer(godLayer) // Need to do this as god, because we've already taken the player out of the game and saved it
          _ <- withoutPlayers match {
            case game
                if game.jugadores.isEmpty &&
                  (game.gameStatus == GameStatus.esperandoJugadoresInvitados ||
                    game.gameStatus == GameStatus.esperandoJugadoresAzar) =>
              // The game is not even anything, so just delete it completely
              repository.gameOperations.delete(game.id, softDelete = false).provideLayer(godLayer)
            case game if game.jugadores.isEmpty =>
              // The game had already started, so we soft delete it
              repository.gameOperations.delete(game.id, softDelete = true).provideLayer(godLayer)
            case game => ZIO.succeed(true)
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
          _ <- ZIO.foreachDiscard(walletOpt.map(w => (withoutPlayers, w))) {
            case (game, wallet) if game.gameStatus.enJuego =>
              val lostPoints =
                game.cuentasCalculadas.find(_.jugador.id == user.id).map(_.puntos).getOrElse(0)

              repository.userOperations
                .updateWallet(
                  wallet.copy(amount = wallet.amount - ((lostPoints + game.abandonedPenalty) * game.satoshiPerPoint))
                ).provideLayer(godLayer)
            case _ => ZIO.succeed(true)
          }
          _ <- broadcast(gameEventQueues, event)
          _ <- broadcast(
            userEventQueues,
            UserEvent(user, UserEventType.AbandonedGame, Some(withoutPlayers.id))
          )
        } yield true).mapError(GameError.apply)

      override def broadcastGameEvent(
        gameEvent: GameEvent
      ): ZIO[ChutiSession, GameError, GameEvent] = {
        broadcast(gameEventQueues, gameEvent)
      }

      private def botLayer: ULayer[ChutiSession] = {
        ChutiSession.botSession(hal9000).toLayer
      }

      override def startGame(gameId: GameId): ZIO[GameEnvironment & ChutiSession, GameError, Boolean] =
        (for {
          repository <- ZIO.service[ZIORepository]
          game <- ZIO.serviceWithZIO[ZIORepository](
            _.gameOperations
              .get(gameId).flatMap(g => ZIO.fromOption(g).orElseFail(RepositoryError("Could not find game", None)))
          )
          gameStarted <-
            ZIO.foldLeft(game.jugadores.size until game.numPlayers)(game) {
              (
                foldedGame,
                _
              ) =>
                // If there's not enough human players, we need to add some bots
                joinGame(Option(foldedGame), JugadorType.dumbBot)
                  .provideSomeLayer[ZIORepository & Postman & TokenHolder](
                    botLayer
                  )
            }
          _ <- ZIO.log(s"Game started: $gameStarted")
          _ <- doAutoPlay(gameStarted)
        } yield true).mapError(GameError.apply)

      private val botsByJugadorType: Map[JugadorType, ChutiBot] = Map[JugadorType, ChutiBot](
        JugadorType.dumbBot -> DumbChutiBot
      )

      private val botDelay = Schedule.fixed(10.seconds).jittered.addDelay(_ => 2.seconds)

      // TODO, we need to call this after human players play, don't blindly add it to `play` though, otherwise we get infinite loops
      private def doAutoPlay(game: Game): ZIO[GameEnvironment, GameError, Game] = {
        ZIO.foldLeft(game.jugadores)(game) {
          (
            current,
            jugador
          ) =>
            val botOpt = botsByJugadorType.get(jugador.jugadorType)
            ZIO.log(s"bot $botOpt selected for player ${jugador.user.name}, jugadorType = ${jugador.jugadorType}") *>
            ZIO
              .foreach(botOpt)(bot =>
                ZIO.log(s"Bot for player ${jugador.user.name} is playing") *>
                bot
                  .takeTurn(current.id).provideSome[GameEnvironment](
                    ChutiSession.botSession(jugador.user).toLayer,
                    ZLayer.succeed(this: GameService)
                  )
              )
              .map(_.getOrElse(current))
        }
      }

      override def joinRandomGame(): ZIO[ChutiSession & ZIORepository, GameError, Game] =
        (for {
          gameOpt <- ZIO.serviceWithZIO[ZIORepository](
            _.gameOperations.gamesWaitingForPlayers().mapBoth(GameError.apply, _.headOption)
          )
          joined <- joinGame(gameOpt, JugadorType.human)
        } yield joined).mapError(GameError.apply)

      override def newGameSameUsers(
        oldGameId: GameId
      ): ZIO[TokenHolder & ChutiSession & ChatService & ZIORepository & ChutiSession & Postman, GameError, Game] =
        (for {
          user <- ZIO
            .serviceWith[ChutiSession](_.user).someOrFail(RepositoryError("User is required for this operation"))
          repository <- ZIO.service[ZIORepository]
          now        <- Clock.instant
          oldGame <-
            repository.gameOperations
              .get(oldGameId).map(_.getOrElse(throw GameError("No existe juego previo")))
          withFirstUser <- {
            val newGame = Game(
              id = GameId.empty,
              gameStatus = GameStatus.esperandoJugadoresInvitados,
              satoshiPerPoint = oldGame.satoshiPerPoint,
              created = now
            )
            val (game2, _) = newGame.applyEvent(user, JoinGame(user, JugadorType.human))
            repository.gameOperations.upsert(game2)
          }
          _ <- ZIO.foreachDiscard(oldGame.jugadores.filter(_.id != user.id).map(_.user)) { u =>
            inviteToGame(u.id, withFirstUser.id)
          }
          afterInvites <-
            repository.gameOperations
              .get(withFirstUser.id).map(
                _.getOrElse(throw GameError("No existe juego previo"))
              )
        } yield afterInvites).mapError(GameError.apply)

      override def newGame(satoshiPerPoint: Long): ZIO[ChutiSession & ZIORepository, GameError, Game] =
        (for {
          user <- ZIO
            .serviceWith[ChutiSession](_.user).someOrFail(RepositoryError("User is required for this operation"))
          repository <- ZIO.service[ZIORepository]
          now        <- Clock.instant
          upserted <- {
            val newGame = Game(
              id = GameId.empty,
              gameStatus = GameStatus.esperandoJugadoresInvitados,
              satoshiPerPoint = satoshiPerPoint,
              created = now
            )
            val (game2, _) = newGame.applyEvent(user, JoinGame(user, JugadorType.human))
            repository.gameOperations.upsert(game2)
          }
          _ <- repository.gameOperations.updatePlayers(upserted)
          _ <- ZIO.foreachDiscard(upserted.jugadores.find(_.user.id == user.id).filter(!_.user.isBot)) { j =>
            repository.userOperations.upsert(j.user)
          }
          _ <- broadcast(userEventQueues, UserEvent(user, UserEventType.JoinedGame, Some(upserted.id)))
        } yield upserted).mapError(GameError.apply)

      override def getLoggedInUsers: ZIO[ChutiSession, GameError, Seq[User]] =
        userEventQueues.get.map(_.map(_.user).distinctBy(_.id).take(20))

      override def inviteByEmail(
        name:   String,
        email:  String,
        gameId: GameId
      ): ZIO[TokenHolder & ChutiSession & ChatService & ZIORepository & ChutiSession & Postman, GameError, Boolean] = {
        (for {
          user <- ZIO
            .serviceWith[ChutiSession](_.user).someOrFail(RepositoryError("User is required for this operation"))
          repository <- ZIO.service[ZIORepository]
          postman    <- ZIO.service[Postman]
          now        <- Clock.instant
          gameOpt    <- repository.gameOperations.get(gameId)
          invitedOpt <- repository.userOperations.userByEmail(email)
          invited <- invitedOpt.fold(
            repository.userOperations
              .upsert(User(UserId.empty, email, name, created = now, lastUpdated = now))
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
                game.applyEvent(user, InviteToGame(invited = invited))
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
      ): ZIO[TokenHolder & ChutiSession & ChatService & ZIORepository & ChutiSession & Postman, GameError, Boolean] = {
        (for {
          user <- ZIO
            .serviceWith[ChutiSession](_.user).someOrFail(RepositoryError("User is required for this operation"))
          repository <- ZIO.service[ZIORepository]
          postman    <- ZIO.service[Postman]
          gameOpt    <- repository.gameOperations.get(gameId)
          invitedOpt <- repository.userOperations.get(userId)
          afterInvitation <- ZIO.foreach(gameOpt) { game =>
            if (invitedOpt.isEmpty)
              ZIO.fail(GameError(s"El usuario $userId no existe"))
            else if (!game.jugadores.exists(_.user.id == user.id))
              ZIO.fail(
                GameError(
                  s"El usuario ${user.id} no esta en este juego, por lo que no puede invitar a nadie"
                )
              )
            else {
              val (withInvite, invitation) =
                game.applyEvent(user, InviteToGame(invited = invitedOpt.get))
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

      private def joinGame(
        gameOpt:     Option[Game],
        jugadorType: JugadorType
      ): ZIO[ChutiSession & ZIORepository, GameError, Game] = {
        for {
          user <- ZIO
            .serviceWith[ChutiSession](_.user).someOrFail(RepositoryError("User is required for this operation"))
          repository <- ZIO.service[ZIORepository]
          now        <- Clock.instant
          newOrRetrieved <- gameOpt.fold(
            // The game may have no players yet, so god needs to save it
            repository.gameOperations
              .upsert(
                Game(GameId.empty, gameStatus = GameStatus.esperandoJugadoresAzar, created = now)
              ).provideLayer(godLayer)
          )(game => ZIO.succeed(game))
          afterApply <- {
            val (joined, joinGame) = newOrRetrieved.applyEvent(user, JoinGame(user, jugadorType))
            val (started, startGame: GameEvent) =
              if (joined.canTransitionTo(GameStatus.cantando)) {
                joined
                  .copy(gameStatus = GameStatus.requiereSopa)
                  .applyEvent(user, Sopa(firstSopa = true))
                // TODO change player status, and update players in LoggedIn Players and in database, invalidate db cache
              } else
                joined.applyEvent(user, NoOp())
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
            UserEvent(user, UserEventType.JoinedGame, afterApply._1.id.toOption)
          )
        } yield afterApply._1
      }

      override def acceptGameInvitation(gameId: GameId): ZIO[ChutiSession & ZIORepository, GameError, Game] =
        (for {
          gameOpt <- ZIO.serviceWithZIO[ZIORepository](_.gameOperations.get(gameId))
          joined  <- joinGame(gameOpt, JugadorType.human)
        } yield joined).mapError(GameError.apply)

      override def declineGameInvitation(
        gameId: GameId
      ): ZIO[ChutiSession & ZIORepository & ChatService, GameError, Boolean] =
        (for {
          repository <- ZIO.service[ZIORepository]
          userOpt    <- ZIO.serviceWith[ChutiSession](_.user)
          user       <- ZIO.fromOption(userOpt).orElseFail(GameError("Usuario no autenticado"))

          gameOpt <- repository.gameOperations.get(gameId)
          afterEvent <- ZIO.foreach(gameOpt) { game =>
            if (game.gameStatus.enJuego)
              throw GameError(
                s"El usuario $user trato de declinar una invitacion a un juego que ya habia empezado"
              )
            if (!game.jugadores.exists(_.user.id == user.id))
              throw GameError(s"El usuario ${user.id} ni siquera esta en este juego")
            val (afterEvent, declinedEvent) = game.applyEvent(user, DeclineInvite())
            repository.gameOperations
              .upsert(afterEvent).map((_, declinedEvent))
              .provideLayer(godLayer)
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
      ): ZIO[ChutiSession & ZIORepository & ChatService, GameError, Boolean] = {
        (for {
          repository <- ZIO.service[ZIORepository]
          userOpt    <- ZIO.serviceWith[ChutiSession](_.user)
          user       <- ZIO.fromOption(userOpt).orElseFail(GameError("Usuario no autenticado"))

          gameOpt <- repository.gameOperations.get(gameId)
          afterEvent <- ZIO.foreach(gameOpt) { game =>
            if (game.gameStatus.enJuego)
              throw GameError(
                s"User $user tried to decline an invitation for a game that had already started"
              )
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
          val a = game.applyEvent(user, HoyoTecnico(razon))
          val b = a._1.applyEvent(user, BorloteEvent(Borlote.HoyoTecnico))
          (b._1, Seq(a._2, b._2))
        }

        val res = playEvent match {
          case e: Da if e.hoyoTecnico.isDefined   => applyHoyoTecnico(game, e.hoyoTecnico.get)
          case e: Pide if e.hoyoTecnico.isDefined => applyHoyoTecnico(game, e.hoyoTecnico.get)
          case e: Da
              if e.ficha == Game.campanita && !game.triunfo
                .contains(TriunfoNumero(Numero1)) && !game.triunfo
                .contains(TriunfoNumero(Numero0)) =>
            val a = game.applyEvent(user, BorloteEvent(Borlote.Campanita))
            (a._1, Seq(a._2))
          case _ =>
            val (regalosGame, regalosBorlote) =
              if (game.jugadores.filter(!_.cantante).count(_.filas.size == 1) == 3) {
                val a = game.applyEvent(user, BorloteEvent(Borlote.SantaClaus))
                (a._1, Seq(a._2))
              } else if (game.jugadores.filter(!_.cantante).exists(_.filas.size == 3)) {
                val a = game.applyEvent(user, BorloteEvent(Borlote.ElNiñoDelCumpleaños))
                (a._1, Seq(a._2))
              } else (game, Seq.empty)

            val (helechoGame, helechoBorlote) =
              if (
                game.jugadores.find(!_.cantante).fold(false)(_.filas.size == 4) && game.jugadores
                  .exists(_.filas.size < 4)
              ) {
                val a = game.applyEvent(user, BorloteEvent(Borlote.Helecho))
                (a._1, Seq(a._2))
              } else (regalosGame, regalosBorlote)

            if (helechoGame.jugadores.exists(_.fichas.nonEmpty)) (helechoGame, helechoBorlote)
            else {
              // Ya se acabo el juego, a nadie le quedan fichas
              val a = helechoGame.applyEvent(user, TerminaJuego())
              (a._1, helechoBorlote :+ a._2)
            }
        }
        res._2.foldLeft((res._1, res._2))(
          (
            a,
            b
          ) => (b.processStatusMessages(a._1), a._2)
        )
      }

      // TODO comment this out once we've tested redo.
      def testRedoEvent(
        before: Game,
        after:  Game,
        event:  GameEvent,
        user:   User
      ): Unit = {
        if (!event.isInstanceOf[Sopa]) { // Sopa is special, it isn't redone
          before.jugadores.foreach { jugador =>
            val sanitizedBefore = GameApi.sanitizeGame(before, jugador.user)
            val sanitizedAfter = GameApi.sanitizeGame(after, jugador.user)
            val redone =
              event
                .redoEvent(user, sanitizedBefore)
                .copy(currentEventIndex = before.nextIndex)
            if (sanitizedAfter != redone) {
              println(sanitizedAfter.toJson)
              println(redone.toJson)
              throw GameError("Done and ReDone should be the same")
            }
          }
        }
      }

      override def playSilently(
        gameId:    GameId,
        playEvent: PlayEvent
      ): GameTask[Boolean] = play(gameId, playEvent).as(true)

      override def play(
        gameId:    GameId,
        playEvent: PlayEvent
      ): ZIO[ZIORepository & ChutiSession, GameError, Game] = {

        (for {
          repository <- ZIO.service[ZIORepository]
          userOpt    <- ZIO.serviceWith[ChutiSession](_.user)
          user       <- ZIO.fromOption(userOpt).orElseFail(GameError("Usuario no autenticado"))

          gameOpt <- repository.gameOperations.get(gameId)
          played <-
            gameOpt.fold(throw GameError("No encontre ese juego")) { game =>
              if (!game.jugadores.exists(_.user.id == user.id))
                throw GameError("El usuario no esta jugando en este juego!!")
              playEvent match {
                case _: NoOpPlay =>
                  // NoOp plays just return the current game state without changes
                  ZIO.succeed((game, Seq.empty[GameEvent]))
                case _ =>
                  val (played, event) = game.applyEvent(user, playEvent)
                  val withStatus = event.processStatusMessages(played)
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
      ): ZIO[ZIORepository, RepositoryError, List[UserWallet]] = {
        ZIO
          .foreach(game.cuentasCalculadas) { case (jugador, _, satoshi) =>
            for {
              repository <- ZIO.service[ZIORepository]
              walletOpt  <- repository.userOperations.getWallet(jugador.id)
              updated <- ZIO.foreach(walletOpt)(wallet =>
                repository.userOperations
                  .updateWallet(wallet.copy(amount = wallet.amount + satoshi)).provideLayer(godLayer)
              )
            } yield updated.toList
          }.provideSomeLayer[
            ZIORepository
          ](godLayer).map(_.flatten.toList)
      }

      override def gameStream(
        gameId:       GameId,
        connectionId: ConnectionId
      ): ZStream[ChutiSession, Nothing, GameEvent] =
        ZStream.unwrap {
          for {
            user <- ZIO
              .serviceWith[ChutiSession](_.user).someOrFail(
                RepositoryError("User is required for this operation")
              ).orDie
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
            .filter(_.gameId == gameId)
        }

      override def userStream(
        connectionId: ConnectionId
      ): ZStream[ChutiSession, Nothing, UserEvent] =
        ZStream.unwrap {
          for {
            userOpt       <- ZIO.serviceWith[ChutiSession](_.user)
            user          <- ZIO.fromOption(userOpt).orElseFail(GameError("Authenticated User required")).orDie
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

  def friend(
    friendId: UserId
  ): ZIO[ChutiSession & ZIORepository & ChatService, GameError, Boolean] =
    (for {
      user <- ZIO
        .serviceWith[ChutiSession](_.user).someOrFail(RepositoryError("User is required for this operation"))
      repository <- ZIO.service[ZIORepository]
      friendOpt  <- repository.userOperations.get(friendId)
      friended   <- ZIO.foreach(friendOpt)(friend => repository.userOperations.friend(friendId))
      _ <- ZIO.foreachDiscard(friendOpt) { person =>
        ChatService
          .sendMessage(
            s"${user.name} es tu amigo :)",
            ChannelId.directChannel,
            Option(person)
          )
      }
    } yield friended.getOrElse(false)).mapError(GameError.apply)

  def unfriend(
    enemyId: UserId
  ): ZIO[ChutiSession & ZIORepository & ChatService, GameError, Boolean] =
    (for {
      user <- ZIO
        .serviceWith[ChutiSession](_.user).someOrFail(RepositoryError("User is required for this operation"))
      repository <- ZIO.service[ZIORepository]
      enemyOpt   <- repository.userOperations.get(enemyId)
      unfriended <- ZIO.foreach(enemyOpt)(enemy => repository.userOperations.unfriend(enemyId))
      _ <- ZIO.foreachDiscard(enemyOpt) { person =>
        ChatService.sendMessage(
          s"${user.name} ya no es tu amigo :(",
          ChannelId.directChannel,
          Option(person)
        )
      }
    } yield unfriended.getOrElse(false)).mapError(GameError.apply)

}
