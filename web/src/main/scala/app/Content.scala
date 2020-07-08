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
import chuti._
import components.components.ChutiComponent
import components.{Confirm, Toast}
import game.GameClient.{Queries, Subscriptions}
import io.circe.generic.auto._
import io.circe.{Decoder, Json}
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.VdomNode
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{Callback, _}
import org.scalajs.dom.window
import pages.GamePage.{calibanCallThroughJsonOpt, makeWebSocketClient}
import router.AppRouter
import service.UserRESTClient
import typings.std.global.Audio

import scala.util.{Failure, Success}

/**
  * This is a helper class meant to load initial app state, scalajs-react normally
  * suggests (and rightfully so) that the router should be the main content of the app,
  * but having a middle piece that loads app state makes some sense, that way the router is in charge of routing and
  * presenting the app menu.
  */
object Content extends ChutiComponent {
  private val connectionId = UUID.randomUUID().toString

  case class State(chutiState: ChutiState)

  class Backend($ : BackendScope[_, State]) {
    private val gameEventDecoder = implicitly[Decoder[GameEvent]]

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
            ) >> refresh
          else
            gameEvent.reapplyMode match {
              case ReapplyMode.none        => Callback.empty
              case ReapplyMode.fullRefresh => refresh
              case ReapplyMode.reapply     => reapplyEvent(gameEvent)
            }
        }
      } yield updated

      Callback.log(gameEvent.toString) >>
        playSound(gameEvent.soundUrl) >>
        updateGame >> {
        gameEvent match {
          case e: TerminaJuego if (e.partidoTerminado) =>
            refresh >> $.modState(s =>
              s.copy(chutiState = s.chutiState.copy(currentDialog = GlobalDialog.cuentas))
            )
          case _ => Callback.empty
        }
      }
    }

    private def reapplyEvent(gameEvent: GameEvent): Callback = {
      $.modState(
        { s =>
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
                  if (reapplied.gameStatus.acabado || reapplied.jugadores.isEmpty) {
                    None
                  } else {
                    Option(reapplied)
                  }
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
          s.copy(chutiState = s.chutiState.copy(gameInProgress = moddedGame))
        }
      )
    }

    def modGameInProgress(fn: Game => Game): Callback = {
      $.modState(s =>
        s.copy(chutiState =
          s.chutiState.copy(gameInProgress = s.chutiState.gameInProgress.map(g => fn(g)))
        )
      )
    }

    def onUserChanged(userOpt: Option[User]): Callback =
      userOpt.fold(Callback.empty) { user =>
        UserRESTClient.remoteSystem
          .upsert(user)
          .completeWith {
            case Success(u) =>
              Toast.success("User successfully saved ") >>
                $.modState(s => s.copy(chutiState = s.chutiState.copy(user = Some(u))))
            case Failure(t) =>
              t.printStackTrace()
              Toast.error("Error saving user")
          }
      }

    def playSound(soundUrlOpt: Option[String]): Callback = {
      soundUrlOpt.fold(Callback.empty) { url =>
        Callback {
          val sound = new Audio(src = url)
          sound.play()
        }
      }
    }

    def render(s: State): VdomNode =
      VdomArray(
        <.button(^.onClick ==> { _ =>
          playSound(Some("/sounds/campanita.mp3"))
        })("Sound!"),
        ChutiState.ctx.provide(s.chutiState) {
          <.div(
            ^.key    := "contentDiv",
            ^.height := 100.pct,
            Confirm.render(),
            Toast.render(),
            AppRouter.router()
          )
        }
      )

    def onGameViewModeChanged(gameViewMode: GameViewMode): Callback =
      $.modState(s => s.copy(chutiState = s.chutiState.copy(gameViewMode = gameViewMode)))

    def showDialog(dlg: GlobalDialog): Callback =
      $.modState(s => s.copy(chutiState = s.chutiState.copy(currentDialog = dlg)))

    def init(): Callback =
      $.modState(s =>
        s.copy(
          chutiState = s.chutiState.copy(
            modGameInProgress = modGameInProgress,
            onRequestGameRefresh = refresh,
            onGameViewModeChanged = onGameViewModeChanged,
            onUserChanged = onUserChanged,
            showDialog = showDialog
          )
        )
      )

    def refresh: Callback =
      Callback.log("Refreshing Content Component") >>
        UserRESTClient.remoteSystem.whoami().completeWith {
          case Success(user) =>
            $.modState(s => s.copy(chutiState = s.chutiState.copy(user = user)))
          case Failure(e) =>
            e.printStackTrace()
            Toast.error("Error retrieving user")
        } >>
        UserRESTClient.remoteSystem.wallet().completeWith {
          case Success(wallet) =>
            $.modState(s => s.copy(chutiState = s.chutiState.copy(wallet = wallet)))
          case Failure(e) =>
            e.printStackTrace()
            Toast.error("Error retrieving user's wallet")
        } >>
        $.state.map(_.chutiState.gameStream.foreach(_.close())) >>
        calibanCallThroughJsonOpt[Queries, Game](
          Queries.getGameForUser,
          callback = {
            case Some(game) =>
              $.modState(s =>
                s.copy(chutiState = s.chutiState.copy(
                  gameInProgress = Option(game),
                  gameStream = Option(
                    makeWebSocketClient[Option[Json]](
                      uriOrSocket = Left(new URI("ws://localhost:8079/api/game/ws")),
                      query = Subscriptions.gameStream(game.id.get.value, connectionId),
                      onData = { (_, data) =>
                        data.flatten.fold(Callback.empty)(json =>
                          gameEventDecoder
                            .decodeJson(json)
                            .fold(failure => Callback.throwException(failure), g => onGameEvent(g))
                        )
                      },
                      operationId = "-"
                    )
                  )
                )
                )
              )
            case None =>
              $.modState(s =>
                s.copy(chutiState = s.chutiState.copy(
                  gameInProgress = None,
                  gameStream = None
                )
                )
              )
          }
        )
  }

  private val component = ScalaComponent
    .builder[Unit]("content")
    .initialState {
      val modeStr = window.sessionStorage.getItem("gamePageMode")
      val gameViewMode = {
        val ret =
          try {
            GameViewMode.withName(modeStr)
          } catch {
            case _: Throwable =>
              GameViewMode.lobby
          }
        if (ret == GameViewMode.none) {
          //Should not happen, but let's be sure it doesn't happen again
          window.sessionStorage.setItem("gamePageMode", GameViewMode.lobby.toString)
          GameViewMode.lobby
        } else {
          ret
        }
      }

      State(chutiState = ChutiState(gameViewMode = gameViewMode))
    }
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.init() >> $.backend.refresh)
    .build

  def apply(): Unmounted[Unit, State, Backend] = component()
}
