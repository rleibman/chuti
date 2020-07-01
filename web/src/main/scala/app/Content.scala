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
import japgolly.scalajs.react.vdom.html_<^._
import japgolly.scalajs.react.{Callback, _}
import org.scalajs.dom.window
import pages.GamePage.{calibanCallThroughJsonOpt, makeWebSocketClient}
import router.AppRouter
import service.UserRESTClient

import scala.util.{Failure, Success}

/**
  * This is a helper class meant to load initial app state, scalajs-react normally
  * suggests (and rightfully so) that the router should be the main content of the app,
  * but having a middle piece that loads app state makes some sense, that way the router is in charge of routing and
  * presenting the app menu.
  */
object Content extends ChutiComponent {
  private val connectionId = UUID.randomUUID().toString

  class Backend($ : BackendScope[_, ChutiState]) {
    private val gameEventDecoder = implicitly[Decoder[GameEvent]]

    def processGameEventQueue: Callback = Callback.empty //TODO write this

    def onGameEvent(gameEvent: GameEvent): Callback = {
      Callback.log(gameEvent.toString) >> {
        gameEvent.reapplyMode match {
          case ReapplyMode.none        => Callback.empty
          case ReapplyMode.fullRefresh => refresh()
          case ReapplyMode.reapply     => reapplyEvent(gameEvent)
        }
      } >> {
        gameEvent match {
          case e: TerminaJuego if (e.partidoTerminado) =>
            $.modState(_.copy(currentDialog = GlobalDialog.cuentas))
          case _ => Callback.empty
        }
      }

    }

    private def reapplyEvent(gameEvent: GameEvent): Callback = {
      $.modState(
        { s =>
          val moddedGame = s.gameInProgress.flatMap {
            currentGame: Game =>
              gameEvent.index match {
                case None =>
                  throw GameException(
                    "This clearly could not be happening (event has no index)"
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
                    s"This clearly could not be happening eventIndex = $index, gameIndex = ${currentGame.currentEventIndex}"
                  )
              }
          }
          val moddedQueue = s.gameInProgress.toSeq.flatMap { currentGame: Game =>
            gameEvent.index match {
              case Some(index) if index == currentGame.nextIndex + 1 =>
                //If it's a future event, put it in the queue, and add a timer to wait: if we haven't gotten the filling event
                //In a few seconds, just get the full game.
                s.gameEventQueue :+ gameEvent
              case _ => s.gameEventQueue
            }
          }

          s.copy(gameInProgress = moddedGame, gameEventQueue = moddedQueue)
        },
        processGameEventQueue
      )
    }

    def onUserChanged(userOpt: Option[User]): Callback =
      userOpt.fold(Callback.empty) { user =>
        UserRESTClient.remoteSystem
          .upsert(user)
          .completeWith {
            case Success(u) =>
              Toast.success("User successfully saved ") >>
                $.modState(s => s.copy(user = Some(u)))
            case Failure(t) =>
              t.printStackTrace()
              Callback.empty //TODO do something else here
          }
      }

    def render(s: ChutiState): VdomElement =
      ChutiState.ctx.provide(s) {
        <.div(
          ^.height := 100.pct,
          Confirm.render(),
          Toast.render(),
          AppRouter.router()
        )
      }

    def onGameViewModeChanged(gameViewMode: GameViewMode): Callback =
      $.modState(_.copy(gameViewMode = gameViewMode))

    def onGameInProgressChanged(gameOpt: Option[Game]): Callback =
      $.modState(_.copy(gameInProgress = gameOpt))

    def showDialog(dlg: GlobalDialog): Callback =
      $.modState(_.copy(currentDialog = dlg))

    def init(): Callback =
      $.modState(s =>
        s.copy(
          onGameInProgressChanged = onGameInProgressChanged,
          onRequestGameRefresh = refresh,
          onGameViewModeChanged = onGameViewModeChanged,
          onUserChanged = onUserChanged,
          showDialog = showDialog
        )
      )

    def refresh(): Callback =
      Callback.log("Refreshing Content Component") >>
        UserRESTClient.remoteSystem.whoami().completeWith {
          case Success(user) =>
            $.modState { s =>
              s.copy(
                user = user
              )
            }
          case Failure(e) =>
            e.printStackTrace()
            Callback.empty
          //TODO do something more with this here
        } >>
        $.state.map { s: ChutiState =>
          s.gameStream.foreach(_.close())
        } >>
        calibanCallThroughJsonOpt[Queries, Game](
          Queries.getGameForUser,
          //TODO now that I think of it, this is stupid, there should be a single callback that will get Option[Game]
          callbackWhenSome = game =>
            $.modState(
              _.copy(
                gameInProgress = Option(game),
                gameStream = Option(
                  makeWebSocketClient[Json](
                    uriOrSocket = Left(new URI("ws://localhost:8079/api/game/ws")),
                    query = Subscriptions.gameStream(game.id.get.value, connectionId),
                    onData = { (_, data) =>
                      data.fold(Callback.empty)(json =>
                        gameEventDecoder
                          .decodeJson(json)
                          .fold(failure => Callback.throwException(failure), g => onGameEvent(g))
                      )
                    },
                    operationId = "-"
                  )
                )
              )
            ),
          callbackWhenNone = $.modState(
            _.copy(
              gameInProgress = None,
              gameStream = None
            )
          )
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

      ChutiState(gameViewMode = gameViewMode)
    }
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.init() >> $.backend.refresh())
    .build

  def apply(): Unmounted[Unit, ChutiState, Backend] = component()
}
