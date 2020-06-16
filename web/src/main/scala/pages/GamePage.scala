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

package pages

import java.net.URI
import java.util.UUID

import app.ChutiState
import caliban.client.scalajs.ScalaJSClientAdapter
import chat.ChatComponent
import chuti._
import game.GameClient.{Queries, Subscriptions}
import io.circe.generic.auto._
import io.circe.{Decoder, Json}
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.extra.StateSnapshot
import japgolly.scalajs.react.vdom.html_<^._
import org.scalajs.dom.window

object GamePage extends ChutiPage with ScalaJSClientAdapter {
  private val connectionId = UUID.randomUUID().toString

  object Mode extends Enumeration {
    type Mode = Value
    val lobby, game, none = Value
  }
  import Mode._

  case class State(
    mode:           Mode,
    gameInProgress: Option[Game] = None,
    gameStream:     Option[WebSocketHandler] = None,
    //TODO events have to get applied to the local copy of the game in order, if we receive
    //TODO an event out of order we need to wait until we have it's match, and if a timeout passes
    //TODO without receiving it, we need to ask the server for the full state of the game again
//    gameEventQueue: SortedSet[GameEvent] =
//      SortedSet.empty(Ordering.by[GameEvent, Option[Int]](_.index))
    gameEventQueue: Seq[GameEvent] = Seq.empty
  )

  class Backend($ : BackendScope[Props, State]) {
//    private val gameDecoder = implicitly[Decoder[Game]]
    private val gameEventDecoder = implicitly[Decoder[GameEvent]]

    def processGameEventQueue: Callback = Callback.empty //TODO write this

    def onGameEvent(gameEvent: GameEvent): Callback = {
      Callback.log(gameEvent.toString) >>
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
                    println("reapplying event")
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
    def refresh(): Callback = {
      //We may have to close an existing stream
      $.state.map { s: State =>
        s.gameStream.foreach(_.close())
      } >>
        calibanCallThroughJsonOpt[Queries, Game](
          Queries.getGameForUser,
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
          callbackWhenNone = $.modState(_.copy(mode = Mode.none))
        )
    }

    def init(): Callback = {
      Callback.log(s"Initializing") >>
        refresh()
    }

    def onModeChanged(
      opt:      Option[Mode],
      callback: Callback
    ): Callback = {
      $.modState(_.copy(mode = opt.getOrElse(Mode.none))) >> callback
    }

    def onGameInProgressChange(
      opt:      Option[Option[Game]],
      callback: Callback
    ): Callback = {
      $.modState(_.copy(gameInProgress = opt.flatten)) >> refresh() >> callback
    }

    def render(
      p: Props,
      s: State
    ): VdomNode = {
      val mode = if (s.gameInProgress.isEmpty && s.mode == game) {
        none
      } else {
        window.sessionStorage.setItem("gamePageMode", s.mode.toString)
        s.mode
      }

      mode match {
        case Mode.lobby =>
          <.div(
            LobbyComponent(
              p.chutiState,
              StateSnapshot(s.gameInProgress)(onGameInProgressChange),
              onRequestGameRefresh = refresh(),
              StateSnapshot(mode)(onModeChanged)
            )
          )
        case Mode.game =>
          <.div(
            GameComponent(
              p.chutiState,
              StateSnapshot(s.gameInProgress)(onGameInProgressChange),
              onRequestGameRefresh = refresh(),
              StateSnapshot(mode)(onModeChanged)
            )
          )
        case _ =>
          <.div("We should never, ever get here, you should not be seeing this")
      }

    }
  }
  case class Props(chutiState: ChutiState)

  private val component = ScalaComponent
    .builder[Props]("GamePage")
    .initialState {
      val modeStr = window.sessionStorage.getItem("gamePageMode")
      val mode =
        try {
          Mode.withName(modeStr)
        } catch {
          case _: Throwable =>
            Mode.lobby
        }
      State(mode = if (mode == none) Mode.lobby else mode)
    }
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.init())
    .build

  def apply(chutiState: ChutiState): Unmounted[Props, State, Backend] = component(Props(chutiState))
}
