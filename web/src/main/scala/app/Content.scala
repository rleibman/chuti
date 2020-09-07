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

package app

import java.net.URI
import java.util.UUID

import app.GameViewMode.GameViewMode
import app.GlobalDialog.GlobalDialog
import caliban.client.SelectionBuilder
import caliban.client.scalajs.ScalaJSClientAdapter
import chuti._
import components.components.ChutiComponent
import components.{Confirm, Toast}
import game.GameClient.{
  Queries,
  Subscriptions,
  User => CalibanUser,
  UserEvent => CalibanUserEvent,
  UserEventType => CalibanUserEventType
}
import io.circe.generic.auto._
import io.circe.{Decoder, Json}
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.TimerSupport
import japgolly.scalajs.react.vdom.VdomNode
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{Callback, _}
import org.scalajs.dom.raw.Event
import org.scalajs.dom.window
import router.AppRouter
import service.UserRESTClient
import typings.std.OnErrorEventHandlerNonNull
import typings.std.global.Audio
import util.Config

import scala.collection.mutable
import scala.scalajs.js
import scala.scalajs.js.{ThisFunction, |}

/**
  * This is a helper class meant to load initial app state, scalajs-react normally
  * suggests (and rightfully so) that the router should be the main content of the app,
  * but having a middle piece that loads app state makes some sense, that way the router is in charge of routing and
  * presenting the app menu.
  */
object Content extends ChutiComponent with ScalaJSClientAdapter with TimerSupport {
  private val connectionId = UUID.randomUUID().toString

  case class State(chutiState: ChutiState)

  class Backend($ : BackendScope[_, State]) {
    private val gameEventDecoder = implicitly[Decoder[GameEvent]]

    def flipFicha(ficha: Ficha): Callback =
      $.modState(s => {
        s.copy(chutiState = s.chutiState.copy(flippedFichas = {
          if (s.chutiState.isFlipped(ficha))
            s.chutiState.flippedFichas - ficha
          else
            s.chutiState.flippedFichas + ficha
        }))
      })

    def onGameEvent(gameEvent: GameEvent): Callback = {
      val updateGame: Callback = for {
        currentgameOpt <- $.state.map(_.chutiState.gameInProgress)
        updated <- {
          val doRefresh = currentgameOpt.fold(true)(game =>
            gameEvent.index match {
              case Some(index) if index == game.nextIndex + 1 =>
                //If it's a future event, which means we missed an event somewhere, we'll need to call for a full refresh
                true
              case _ => false
            }
          )

          if (doRefresh)
            Callback.info(
              "Had to force a refresh because the event index was too far into the future"
            ) >> refresh(initial = false)()
          else
            gameEvent.reapplyMode match {
              case ReapplyMode.none        => Callback.empty
              case ReapplyMode.fullRefresh => refresh(initial = false)()
              case ReapplyMode.reapply     => reapplyEvent(gameEvent)
            }
        }
      } yield updated

      Callback.log(gameEvent.toString) >>
        Callback.traverse(gameEvent.soundUrl)(playSound) >>
        updateGame >> {
        gameEvent match {
          case e: TerminaJuego if e.partidoTerminado =>
            refresh(initial = false)() >> $.modState(s =>
              s.copy(chutiState = s.chutiState.copy(currentDialog = GlobalDialog.cuentas))
            )
          case _ => Callback.empty
        }
      }
    }

    private def reapplyEvent(gameEvent: GameEvent): Callback =
      $.modState(s => {
        val moddedGame = s.chutiState.gameInProgress.flatMap {
          currentGame: Game =>
            gameEvent.index match {
              case None =>
                throw GameException(
                  "Esto es imposible (el evento no tiene indice)"
                )
              case Some(index) if index == currentGame.currentEventIndex =>
                //If the game event is the next one to be applied, apply it to the game
                val reapplied = currentGame.reapplyEvent(gameEvent)
                if (reapplied.gameStatus.acabado || reapplied.jugadores.isEmpty)
                  None
                else
                  Option(reapplied)
              case Some(index) if index > currentGame.currentEventIndex =>
                //If it's a future event, put it in the queue, and add a timer to wait: if we haven't gotten the filling event
                //In a few seconds, just get the full game.
                Option(currentGame)
              case Some(index) =>
                //If it's past, mark an error, how could we have a past event???
                throw GameException(
                  s"Esto es imposible eventIndex = $index, gameIndex = ${currentGame.currentEventIndex}"
                )
            }
        }
        s.copy(chutiState =
          s.chutiState.copy(
            gameInProgress = moddedGame,
            ultimoBorlote = gameEvent match {
              case event: BorloteEvent => Option(event.borlote)
              case _:     Pide         => None
              case _ => s.chutiState.ultimoBorlote
            }
          )
        )
      })

    def toggleSound =
      $.modState(s => s.copy(chutiState = s.chutiState.copy(muted = !s.chutiState.muted)))

    def modGameInProgress(
      fn:       Game => Game,
      callback: Callback
    ): Callback =
      Callback.log("modGameInProgress") >> (for {
        chutiState <- $.state.map(_.chutiState)
        _ <- {
          val newGameOpt = chutiState.gameInProgress.map(fn)
          val doRefresh = (for {
            newGame <- newGameOpt
            oldGame <- chutiState.gameInProgress
          } yield newGame.id != oldGame.id).getOrElse(true)
          if (doRefresh)
            Callback.log("Full refresh needed") >> refresh(initial = false)() >> callback
          else {
            $.modState(
              s => s.copy(chutiState = s.chutiState.copy(gameInProgress = newGameOpt)),
              callback
            )
          }
        }
      } yield ())

    def onSessionChanged(
      userOpt:     Option[User],
      languageTag: String
    ): Callback = {
      val async = for {
        user <-
          AsyncCallback.traverse(userOpt.toSeq)(user => UserRESTClient.remoteSystem.upsert(user))
        locale <- UserRESTClient.remoteSystem.setLocale(languageTag)
      } yield {
        Callback(window.sessionStorage.setItem("languageTag", languageTag)) >>
          $.modState(s =>
            s.copy(
              chutiState = s.chutiState.copy(user = user.headOption, languageTag = languageTag)
            )
          ) >> Toast.success("Usuario guardado!")
      }
      async.completeWith(_.get)
    }

    val audioQueue: mutable.Queue[String] = mutable.Queue()
    val audio = new Audio("")
    audio.onended = ThisFunction.fromFunction2 { (_: Audio, _: Event) =>
      if (audioQueue.size > 4) {
        //If for whatever reason there's a bunch of errors, clear the queue after we've reached 4
        println("play queue got bigger than 4, clearing queue")
        audioQueue.clear()
      } else
        audioQueue.dequeue()
      if (audioQueue.nonEmpty) {
        audio.src = audioQueue.head
        audio.play()
      }
    }

    val fn: OnErrorEventHandlerNonNull = {
      (
        /* event */ _:     org.scalajs.dom.raw.Event | java.lang.String,
        /* source */ _:    js.UndefOr[java.lang.String],
        /* lineno */ _:    js.UndefOr[scala.Double],
        /* colno */ _:     js.UndefOr[scala.Double],
        /* error */ error: js.UndefOr[js.Error]
      ) =>
        js.Any.fromUnit {
          println(s"Error playing ${audio.src} ${error.map(_.message)}")
          if (audioQueue.nonEmpty) {
            audioQueue.dequeue()
            audio.src = audioQueue.head
            audio.play()
            ()
          }
        }
    }

    audio.onerror = fn

    def playSound(url: String): Callback = {
      $.state.flatMap { s =>
        if (s.chutiState.muted)
          Callback.empty
        else {
          Callback {
            if (audioQueue.size > 4) {
              println("play queue got bigger than 4, clearing queue")
              audioQueue.clear()
            }
            audioQueue.enqueue(url)
            if (audioQueue.size == 1) {
              audio.src = url
              audio.play()
            }
          }
        }
      }
    }

    def render(s: State): VdomNode =
      ChutiState.ctx.provide(s.chutiState) {
        <.div(
          ^.key    := "contentDiv",
          ^.height := 100.pct,
//          <.button(
//            ^.onClick --> {
//              playSound("filedoesntexist.mp3") >> playSound("sounds/santaclaus.mp3") >> playSound(
//                "sounds/caete3.mp3"
//              )
//            },
//            "Play"
//          ),
          Confirm.render(),
          Toast.render(),
          AppRouter.router()
        )
      }

    def onGameViewModeChanged(gameViewMode: GameViewMode): Callback =
      Callback.log("============== onGameViewModeChanged") >> $.modState(s =>
        s.copy(chutiState = s.chutiState.copy(gameViewMode = gameViewMode))
      )

    def showDialog(dlg: GlobalDialog): Callback =
      $.modState(s => s.copy(chutiState = s.chutiState.copy(currentDialog = dlg)))

    def onUserStreamData(
      currentUser:   User,
      currentGameId: Option[GameId]
    )(
      data: Option[(User, CalibanUserEventType, Option[GameId])]
    ): Callback = {
      import CalibanUserEventType._
      Callback.log(data.toString) >> {
        data match {
          case None => Callback.empty
          case Some((user, Disconnected, _)) =>
            $.modState(s =>
              s.copy(chutiState =
                s.chutiState
                  .copy(loggedInUsers = s.chutiState.loggedInUsers.filter(_.id != user.id))
              )
            )
          case Some((user, Connected | Modified, _)) =>
            $.modState(s =>
              s.copy(chutiState =
                s.chutiState
                  .copy(loggedInUsers = s.chutiState.loggedInUsers.filter(_.id != user.id) :+ user)
              )
            )
          case Some((_, AbandonedGame, gameId)) =>
            //Don't refresh when it's the same user who's joined/abandoned, as there are other game or application events
            //That'll request a refresh.
            //Also, don't refresh if I'm not involved in the game at all, or I'm not in any games, since I really don't care
            if (gameId != currentGameId)
              Callback.empty
            else
              refresh(initial = false)()
          case Some((user, JoinedGame, gameId)) =>
            //Don't refresh when it's the same user who's joined/abandoned, as there are other game or application events
            //That'll request a refresh.
            //Also, don't refresh if I'm not involved in the game at all, or I'm not in any games, since I really don't care
            if (user.id == currentUser.id || gameId != currentGameId)
              Callback.empty
            else
              refresh(initial = false)()
        }
      }
    }

    lazy private val userSelectionBuilder: SelectionBuilder[CalibanUser, User] =
      (CalibanUser.id ~ CalibanUser.name).mapN(
        (
          id:   Option[Int],
          name: String
        ) => {
          User(
            id = id.map(UserId.apply),
            email = "",
            name = name
          )
        }
      )

    lazy private val userEventSelectionBuilder
      : SelectionBuilder[CalibanUserEvent, (User, CalibanUserEventType, Option[GameId])] = {
      val t: SelectionBuilder[
        CalibanUserEvent,
        ((((Option[Int], String), String), CalibanUserEventType), Option[Int])
      ] = CalibanUserEvent.user(CalibanUser.id) ~
        CalibanUserEvent.user(CalibanUser.name) ~
        CalibanUserEvent.user(CalibanUser.email) ~
        CalibanUserEvent.userEventType ~
        CalibanUserEvent.gameId

      t.map {
        case ((((idOpt, name), email), eventType), gameIdOpt) =>
          (
            User(id = idOpt.map(UserId), name = name, email = email),
            eventType,
            gameIdOpt.map(GameId)
          )
      }
    }

    def refresh(initial: Boolean)(): Callback = {
      val ajax = for {
        oldState <- ($.state).asAsyncCallback
        whoami <-
          if (initial) UserRESTClient.remoteSystem.whoami()
          else AsyncCallback.pure(oldState.chutiState.user)
        isFirstLogin      <- UserRESTClient.remoteSystem.isFirstLoginToday()
        wallet            <- UserRESTClient.remoteSystem.wallet()
        friends           <- asyncCalibanCall(Queries.getFriends(userSelectionBuilder))
        loggedInUsersOpt  <- asyncCalibanCall(Queries.getLoggedInUsers(userSelectionBuilder))
        gameInProgressOpt <- asyncCalibanCallThroughJsonOpt[Queries, Game](Queries.getGameForUser)
        needNewGameStream = (for {
            oldGame <- oldState.chutiState.gameInProgress
            newGame <- gameInProgressOpt
            _       <- oldState.chutiState.gameStream
          } yield oldGame.id != newGame.id).getOrElse(true)

        _ <-
          (if (needNewGameStream)
             oldState.chutiState.gameStream.fold(
               Callback.log("Don't need to close stream, it hasn't been opened yet")
             )(_.close())
           else Callback.log("Don't need new game stream, game hasn't changed")).asAsyncCallback

      } yield $.modState { s =>
        val copy = if (initial) {
          s.copy(
            chutiState = s.chutiState.copy(
              flipFicha = flipFicha,
              modGameInProgress = modGameInProgress,
              onRequestGameRefresh = refresh(initial = false) _,
              onGameViewModeChanged = onGameViewModeChanged,
              onSessionChanged = onSessionChanged,
              toggleSound = toggleSound,
              showDialog = showDialog,
              playSound = playSound,
              user = whoami,
              isFirstLogin = isFirstLogin,
              userStream = Option(
                makeWebSocketClient[Option[(User, CalibanUserEventType, Option[GameId])]](
                  uriOrSocket = Left(new URI(s"ws://${Config.chutiHost}/api/game/ws")),
                  query = Subscriptions
                    .userStream(connectionId)(
                      userEventSelectionBuilder
                    ),
                  onData = { (_, data) =>
                    whoami.fold(Callback.empty)(currentUser =>
                      onUserStreamData(currentUser, gameInProgressOpt.flatMap(_.id))(data.flatten)
                    )
                  },
                  operationId = "-",
                  connectionId = s"$connectionId-${whoami.flatMap(_.id).fold(0)(_.value)}"
                )
              )
            )
          )
        } else
          s

        copy.copy(chutiState =
          copy.chutiState.copy(
            wallet = wallet,
            friends = friends.toList.flatten,
            loggedInUsers = loggedInUsersOpt.toList.flatten.distinctBy(_.id),
            gameInProgress = gameInProgressOpt,
            gameStream =
              if (!needNewGameStream) copy.chutiState.gameStream
              else
                gameInProgressOpt.map(game =>
                  makeWebSocketClient[Option[Json]](
                    uriOrSocket = Left(new URI(s"ws://${Config.chutiHost}/api/game/ws")),
                    query = Subscriptions.gameStream(game.id.get.value, connectionId),
                    onData = { (_, data) =>
                      data.flatten.fold(Callback.empty)(json =>
                        gameEventDecoder
                          .decodeJson(json)
                          .fold(failure => Callback.throwException(failure), g => onGameEvent(g))
                      )
                    },
                    operationId = "-",
                    connectionId =
                      s"$connectionId-${gameInProgressOpt.flatMap(_.id).fold(0)(_.value)}"
                  )
                )
          )
        )
      }

      for {
        _ <- Callback.log(
          if (initial) "Initializing Content Component" else "Refreshing Content Component"
        )
        modedState <- ajax.completeWith(_.get)
      } yield modedState
    }

  }

  private val component = ScalaComponent
    .builder[Unit]("content")
    .initialState {
      val modeStr = window.sessionStorage.getItem("gamePageMode")
      val gameViewMode = {
        val ret =
          try GameViewMode.withName(modeStr)
          catch {
            case _: Throwable =>
              GameViewMode.lobby
          }
        if (ret == GameViewMode.none) {
          //Should not happen, but let's be sure it doesn't happen again
          window.sessionStorage.setItem("gamePageMode", GameViewMode.lobby.toString)
          GameViewMode.lobby
        } else
          ret
      }

      State(chutiState = ChutiState(gameViewMode = gameViewMode))
    }
    .renderBackend[Backend]
    .componentDidMount(_.backend.refresh(initial = true)())
    .componentWillUnmount($ =>
      Callback.log("Closing down gameStream and userStream") >>
        $.state.chutiState.gameStream.fold(Callback.empty)(_.close()) >>
        $.state.chutiState.userStream.fold(Callback.empty)(_.close())
    )
    .build

  def apply(): Unmounted[Unit, State, Backend] = component()
}
