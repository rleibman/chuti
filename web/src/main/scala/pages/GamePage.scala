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
import typings.semanticUiReact.components.Button

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

    def onGameEvent(
      props:     Props,
      gameEvent: GameEvent
    ): Callback = {
      Callback.log(gameEvent.toString) >> {
        gameEvent.reapplyMode match {
          case ReapplyMode.none        => Callback.empty
          case ReapplyMode.fullRefresh => refresh(props)
          case ReapplyMode.reapply     => reapplyEvent(gameEvent)
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

    def pageMenuItems(
      game:          Option[Game],
      pageMenuItems: Seq[(String, Callback)]
    ): Seq[(String, Callback)] = {
      val items: Seq[(String, Callback)] =
        game.toSeq.flatMap(_ =>
          Seq(
            (
              "Entrar al juego",
              Callback.log("Got here!") >> $.modState { s =>
                window.alert("Is this thing on?")
                s.copy(mode = GamePage.Mode.game)
              }
            )
          )
        ) ++
          Seq(
            ("Lobby", $.modState(_.copy(mode = GamePage.Mode.lobby)))
          )
      val names = items.map(_._1).toSet

      items ++ pageMenuItems
        .filter(i => !names.contains(i._1)) //Remove all of the same ones
    }

    def refresh(p: Props): Callback = {
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
                          .fold(failure => Callback.throwException(failure), g => onGameEvent(p, g))
                      )
                    },
                    operationId = "-"
                  )
                )
              )
            ) >>
              Callback.when(!p.chutiState.pageMenuItems.exists(_._1 == "Entrar al juego"))(
                p.chutiState.onPageMenuItemsChanged
                  .fold(Callback.empty)(_(pageMenuItems(Option(game), p.chutiState.pageMenuItems)))
              ),
          callbackWhenNone = $.modState(_.copy(mode = Mode.lobby)) >> Callback.when(
            p.chutiState.pageMenuItems.exists(_._1 == "Entrar al juego")
          )(
            p.chutiState.onPageMenuItemsChanged
              .fold(Callback.empty)(_(pageMenuItems(None, p.chutiState.pageMenuItems)))
          )
        )
    }

    def init(p: Props): Callback = {
      Callback.log(s"Initializing GamePage with ${p.chutiState}") >>
        refresh(p)
    }

    def onModeChanged(
      opt:      Option[Mode],
      callback: Callback
    ): Callback = {
      $.modState(_.copy(mode = opt.getOrElse(Mode.none))) >> callback
    }

    def onGameInProgressChange(
      p: Props
    )(
      opt:      Option[Option[Game]],
      callback: Callback
    ): Callback = {
      $.modState(_.copy(gameInProgress = opt.flatten)) >> refresh(p) >> callback
    }

    def render(
      p: Props,
      s: State
    ): VdomNode = {
      println(s"ReRendering GamePage with state $s")

      ChutiState.ctx.consume { chutiState =>
        def renderDebugBar = s.gameInProgress.fold(EmptyVdom) { game =>
          <.div(
            ^.className := "debugBar",
            ^.border    := "10px solid orange",
            <.span(s"Game Status = ${game.gameStatus}, "),
            <.span(s"Game Index = ${game.currentEventIndex}, "),
            <.span(s"Quien canta = ${game.quienCanta.map(_.user.name)}, "),
            <.span(s"Mano = ${game.mano.map(_.user.name)}, "),
            <.span(s"Turno = ${game.turno.map(_.user.name)}, ")
          )
        }

        val mode = if (s.gameInProgress.isEmpty && s.mode == game) {
          //I don't want to be in the lobby if the game is loading
          none
        } else {
          if (s.mode != Mode.none) {
            window.sessionStorage.setItem("gamePageMode", s.mode.toString)
          }
          s.mode
        }

        mode match {
          case Mode.lobby =>
            VdomArray(
              Button(onClick = { (_, _) =>
                $.modState(_.copy(mode = Mode.game))
              })("Push me"),
              //            renderDebugBar,
              LobbyComponent(
                p.chutiState,
                StateSnapshot(s.gameInProgress)(onGameInProgressChange(p)),
                onRequestGameRefresh = refresh(p),
                StateSnapshot(mode)(onModeChanged)
              )
            )
          case Mode.game =>
            VdomArray(
              //            renderDebugBar,
              GameComponent(
                p.chutiState,
                StateSnapshot(s.gameInProgress)(onGameInProgressChange(p)),
                onRequestGameRefresh = refresh(p),
                StateSnapshot(mode)(onModeChanged)
              )
            )
          case _ =>
            <.div(^.hidden := true, "We should never, ever get here, you should not be seeing this")
        }
      }
    }
  }
  case class Props(chutiState: ChutiState)

  private val component = ScalaComponent
    .builder[Props]("GamePageInner")
    .initialState {
      val modeStr = window.sessionStorage.getItem("gamePageMode")
      val mode = {
        val ret =
          try {
            Mode.withName(modeStr)
          } catch {
            case _: Throwable =>
              Mode.lobby
          }
        if (ret == none) {
          //Should not happen, but let's be sure it doesn't happen again
          window.sessionStorage.setItem("gamePageMode", Mode.lobby.toString)
          Mode.lobby
        } else {
          ret
        }
      }
      State(mode = mode)
    }
    .renderBackend[Backend]
    .componentDidMount($ => $.backend.init($.props))
    .build

  private def inner(chutiState: ChutiState): Unmounted[Props, State, Backend] =
    component(Props(chutiState))
  private def innerComponent =
    ScalaComponent
      .builder[Unit]
      .renderStatic {
        ChutiState.ctx.consume { chutiState =>
          inner(chutiState)
        }
      }
      .build

  def apply(): Unmounted[Unit, Unit, Unit] = innerComponent()
}
