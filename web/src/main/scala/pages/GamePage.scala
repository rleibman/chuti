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
import japgolly.scalajs.react.extra.StateSnapshot.SetFn
import japgolly.scalajs.react.vdom.html_<^._

object GamePage extends ChutiPage with ScalaJSClientAdapter {
  private val connectionId = UUID.randomUUID().toString

  case class State(
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
            val moddedGame = s.gameInProgress.map {
              currentGame: Game =>
                gameEvent.index match {
                  case None =>
                    throw GameException(
                      "This clearly could not be happening (event has no index)"
                    )
                  case Some(index) if index == currentGame.currentEventIndex =>
                    //If the game event is the next one to be applied, apply it to the game
                    currentGame.reapplyEvent(gameEvent)
                  case Some(index) if index > currentGame.currentEventIndex =>
                    //If it's a future event, put it in the queue, and add a timer to wait: if we haven't gotten the filling event
                    //In a few seconds, just get the full game.
                    currentGame
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
          game =>
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
            )
        )
    }

    def init(): Callback = {
      Callback.log(s"Initializing") >>
        refresh()
    }

    def onGameInProgressChange(opt: Option[Option[Game]], callback: Callback): Callback = {
      refresh() >> callback
    }

    def render(
      p: Props,
      s: State
    ): VdomElement = {
      LobbyPage(p.chutiState, StateSnapshot(s.gameInProgress)(onGameInProgressChange))
    }
  }
  case class Props(chutiState: ChutiState)

  val component = ScalaComponent
    .builder[Props]("GamePage")
    .initialState(State())
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.init())
    .build

  def apply(chutiState: ChutiState): Unmounted[Props, State, Backend] = component(Props(chutiState))

}
