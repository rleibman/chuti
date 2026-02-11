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

package components

import chuti.*
import japgolly.scalajs.react.*
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^.*

object CelebrationOverlay {

  sealed trait CelebrationType
  object CelebrationType {

    case object RoundEnd extends CelebrationType
    case class Hoyo(jugador: String) extends CelebrationType
    case class SpecialEvent(borlote: Borlote) extends CelebrationType

  }

  case class Props(
    celebrationType: CelebrationType,
    winner:          Option[String],
    scores:          Map[String, Int],
    bidResult:       Option[(cantante: String, bid: String, madeIt: Boolean)] = None, // (cantante, bid, madeIt)
    statusString:    Option[String] = None, // For RoundEnd, display game.statusString
    onDismiss:       Callback
  )

  case class State(
    confettiPieces: List[ConfettiPiece] = Nil
  )

  case class ConfettiPiece(
    index: Int,
    color: String,
    left:  Double
  )

  class Backend($ : BackendScope[Props, State]) {

    private val confettiColors = List("#FF962C", "#59228C", "#CDAFEA", "#EAAFCB", "#AFEAB0", "#FFCB05")

    def generateConfetti(): Callback = {
      val pieces = (0 until 30).map { i =>
        ConfettiPiece(
          index = i,
          color = confettiColors(scala.util.Random.nextInt(confettiColors.length)),
          left = scala.util.Random.nextDouble() * 100
        )
      }.toList
      $.modState(_.copy(confettiPieces = pieces))
    }

    def render(
      p: Props,
      s: State
    ): VdomNode = {
      import CelebrationType.*

      <.div(
        ^.className := "celebration-overlay",
        ^.onClick --> p.onDismiss,
        // Celebration content
        <.div(
          ^.className := "celebration-content score-popup",
          p.celebrationType match {
            case RoundEnd =>
              <.div(
                ^.className := "celebration-winner",
                ^.dangerouslySetInnerHtml := p.statusString
                  .getOrElse("Ronda terminada")
                  .replace("\n", "<br>")
              )

            case Hoyo(jugador) =>
              VdomArray(
                <.div(
                  ^.fontSize     := "3em",
                  ^.marginBottom := 10.px,
                  "⚠️"
                ),
                <.div(
                  ^.className := "celebration-winner",
                  ^.color     := "#CC0000",
                  s"¡$jugador tiene un hoyo!"
                )
              )

            case SpecialEvent(borlote) =>
              val (emoji, text) = borlote match {
                case Borlote.Campanita =>
                  ("🔔", "¡Campanita!")
                case Borlote.SantaClaus =>
                  ("🎅", "¡Santa Claus!")
                case Borlote.ElNiñoDelCumpleaños =>
                  ("🎂", "¡El Niño del Cumpleaños!")
                case Borlote.Helecho =>
                  ("🌿", "¡Helecho!")
                case _ =>
                  ("✨", "Evento especial")
              }
              VdomArray(
                <.div(
                  ^.fontSize     := "4em",
                  ^.marginBottom := 10.px,
                  emoji
                ),
                <.div(
                  ^.className := "celebration-winner",
                  text
                )
              )
          }
        )
      )
    }

  }

  import scala.language.unsafeNulls
  given Reusability[CelebrationType] = Reusability.by(_.toString)
  given Reusability[(cantante: String, bid: String, madeIt: Boolean)] =
    Reusability.by(t => (t.cantante, t.bid, t.madeIt))
  given Reusability[Props] =
    Reusability.by(p => (p.celebrationType.toString, p.winner, p.scores.hashCode, p.bidResult, p.statusString))
  given Reusability[State] = Reusability.by(_.confettiPieces.size) // Ignore timerHandle changes

  private val component = ScalaComponent
    .builder[Props]("CelebrationOverlay")
    .initialState(State())
    .backend[Backend](Backend(_))
    .renderPS(_.backend.render(_, _))
    .componentDidMount($ => $.backend.generateConfetti())
    .configure(Reusability.shouldComponentUpdate)
    .build

  def apply(
    celebrationType: CelebrationType,
    winner:          Option[String] = None,
    scores:          Map[String, Int] = Map.empty,
    bidResult:       Option[((cantante: String, bid: String, madeIt: Boolean))] = None,
    statusString:    Option[String] = None,
    onDismiss:       Callback,
    autoDismiss:     Boolean = true // Kept for API compatibility, but auto-dismiss is now handled externally
  ): Unmounted[Props, State, Backend] =
    component(Props(celebrationType, winner, scores, bidResult, statusString, onDismiss))

}
