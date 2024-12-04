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

package pages

import java.util.Locale

import app.ChutiState
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^.*
import japgolly.scalajs.react.{BackendScope, ScalaComponent}
import _root_.util.LocalizedMessages

object RulesPage extends ChutiPage {

  case class State()

  object RulesPageMessages extends LocalizedMessages {

    override def bundles: Map[String, RulesPageMessages.MessageBundle] =
      Map(
        "en" ->
          MessageBundle(
            "en",
            Map(
              "RulesPage.rules" ->
                """<h1>Rules</h1>
                  |<p>Chuti is played with four people, and a full set of double 6 dominoes, this means that there’s 28 tiles.</p>
                  |<p>A full match consists of a series of games, games continue until one of the 4 players reaches a score of 21
                  |    points.</p>
                  |<p>A game starts by shuffling the tiles, each player takes 7 tiles.</p>
                  |<p>The game has 2 stages</p>
                  |<h2>Bidding</h2>
                  |<ul>
                  |    <li>On the first game of a match only, the player with the double six does the initial bid, after the first game,
                  |        the first bid moves counterclockwise.
                  |    </li>
                  |    <li>The initial bidder estimates how many wins (or lines) they can make during the game, they have to call out a
                  |        minimum of 4 lines (this is known as <b>"house"</b>) but can call out 5, 6, or 7 if they call out 7, they can call out a
                  |        straight 7 (this is rare, and not considered manly) or chuti… if you call chuti and make all 7 lines you gain 21
                  |        points (and likely win the match!)
                  |    </li>
                  |    <li>Going around counterclockwise once, each player gets one chance to outbid the previous players by calling a
                  |        higher number.
                  |    </li>
                  |    <li>After all 4 players have bid, the player who called the highest number starts the game, he must make as many
                  |        lines as he called, if they don’t, then the will get negative points and a <b>“hole”</b>.
                  |    </li>
                  |</ul>
                  |<h2>Game</h2>
                  |<ul>
                  |    <li>The player who won the bid has two choices to make: what number will be the <b>trump</b> for the duration of this game,
                  |        and what tile they will use to call. The trump will be considered the highest number throughout the game, the
                  |        player can also call <b>“no trumps”</b>
                  |    </li>
                  |    <li>The game now progresses by a series of hands, on each hand the player who won the last hand drops a tile (the
                  |        <b>“ask”</b>) signifying what number they’re asking for.
                  |    </li>
                  |    <li>Each of the other players needs to respond with a tile that matches the number that was asked, if they don’t
                  |        have a matching tile, but they have a trump they must respond with that one, and if they don’t have that, they can
                  |        respond with anything else.
                  |    </li>
                  |    <li>The order of winning is:
                  |        <ul>
                  |            <li>If the “ask” tile is the trump double, that one wins (otherwise doubles just take their value)
                  |            </li>
                  |            <li>trumps win over other numbers, within trumps, they win by value
                  |            </li>
                  |            <li>If the “ask” is not a trump, the number asked is always the largest of the two (e.g. assuming neither 4
                  |                nor 5
                  |                is a
                  |                trump, if you “ask” with the 5:4, you’re asking for fives.
                  |            </li>
                  |            <li>If the “ask” is a double, it’ll win against all other tiles (except for trumps), so yes, the 1:1 will
                  |                win against 1:6 but only if it’s played first.
                  |            </li>
                  |            <li>Remember that the caller can call “no trumps” each tile then has it’s normal value, doubles win when
                  |                “asked”
                  |            </li>
                  |        </ul>
                  |    </li>
                  |    <li>If the caller makes the lines they originally bid (or more) then they win the game, those lines count as
                  |        positive (one point per line) and other players who made lines also get a point per line.
                  |    </li>
                  |    <li>If the caller does not make all the lines they bid, then they get what’s called a <b>“hole”</b>, they get marked
                  |        negative points.
                  |    </li>
                  |    <li>If you have a tile that matches the “ask” but give something else it’s considered a <b>“technical hole”</b>, the game
                  |        ends and a hole is marked against you (no negative points against you unless you’re the caller
                  |    </li>
                  |    <li>If you don’t have a tile that matches the “ask” but you have a trump and don’t give it, that’s also a “technical
                  |        hole”
                  |    </li>
                  |    <li>Once it’s obvious that you’ll win the rest of the hands in the game, you must <b>“fall”</b> by dropping all your tiles
                  |        that would make up the lines, the remaining tiles are given as <b>“gifts”</b>, to the players who would win if you were
                  |        to use those tiles as “asks”.
                  |    </li>
                  |    <li>Once it’s obvious that you’ll win the rest of the hands, if you don’t “fall”, that’s considered a technical hole
                  |        as well.
                  |    </li>
                  |    <li>The first line played stays open, the last line played has to stay open until a new line is played.
                  |    </li>
                  |    <li>Once the game is over, the turn to call passes to the right of the last person who’s turn it was to call. It is
                  |        the responsibility of the person whose turn it was to call to shuffle the tiles (makes the <b>“soup”</b>)
                  |    </li>
                  |</ul>
                  |<h2>Other small technical details and nuances</h2>
                  |<ul>
                  |    <li>When teaching someone to play chuti, it is traditional to say that chuti is like the card game of “hearts” or
                  |        “brisca”, but nobody really knows since we know nobody who’s ever played those games.
                  |    </li>
                  |    <li>In general, when you “ask” everybody can answer immediately, you may, if you wish, ask for <b>“strict
                  |        right”</b>, players then have to respond one by one counterclockwise
                  |    </li>
                  |    <li>After running your first line, you may evaluate your position and you may <b>forfeit</b> the game, you still get the
                  |        hole and respective negative points, but at least you’re not giving anybody else gifts.
                  |    </li>
                  |    <li>When the calling player makes their house (four), and every player gets a gift, the calling player is called
                  |        <b>“Santa Claus”</b>
                  |    </li>
                  |    <li>When a single player gets all the gifts, they’re called <b>“the birthday boy/girl”</b>
                  |    </li>
                  |    <li>The 0:1 tile is named <b>“little bell”</b>, customarily it gets tapped on the table when it’s played, there’s strategic
                  |        reasons for this custom that you won't really understand until you've been playing for a while.
                  |    <li>If a player saves the “caller” by bidding more, it’s ok if the other two players call him an idiot.
                  |    </li>
                  |</ul>
                  |<h2>Scoring</h2>
                  |<ul>
                  |    The match is over once a player reaches 21, at that point the match is scored, each point counts a pre-agreed amount
                  |    of dollars (or pesos, or satoshi, or whatever you’re gambling for). Scoring is in the following manner
                  |    <li>The winner gets one point from every other player
                  |    </li>
                  |    <li>Every hole you have pays one point to every other player
                  |    </li>
                  |    <li>If your score is negative, you give an extra 2 points to the winner
                  |    </li>
                  |    <li>If your score is zero, you give an extra 1 point to the winner.
                  |    </li>
                  |    <li>If you won the game after calling chuti (i.e. you do seven lines) every other player gives you one additional
                  |        point.
                  |    </li>
                  |</ul>
                  |<h2>Strategy</h2>
                  |<ul>
                  |    <li>Bidding has some risk, you want to outbid others to win points, but you don’t want to run the risk of losing
                  |        the game, outbidding the caller can also be a bad thing if the original caller was in trouble.
                  |    </li>
                  |    <li>Doubles are very valuable, since they win the hand. For example, if you have 7 doubles, you can bid chuti and
                  |        play “no trumps”, winning instantly
                  |    </li>
                  |    <li>In a way, the match consists of a series of games in which 3 players are playing against the caller and trying
                  |        to make them lose.
                  |    </li>
                  |    <li>Though you’re playing to win the match, you’re also playing to make more points in the match by causing
                  |        “holes” in the other players, sometimes if everybody else is far behind you, you may want to not outbid to let
                  |        other players keep on getting holes.
                  |    </li>
                  |    <li>You’re trying to deduce what tiles other players have, based on what’s been played, so pay attention.
                  |    </li>
                  |    <li>It’s risky, but considered “manly” to call chuti if you have the two highest tiles in a number (e.g. the 3:6
                  |        and 3:5), and the rest are doubles… this is called <b>“going for it all, with two”</b>
                  |    <li>Some hands are obvious winners and let you make your bid easily (or even automatically), but others require
                  |        strategy and thinking… and then there’s hands you cannot possible do anything with, those you just give up.
                  |    </li>
                  |</ul>
                  |""".stripMargin
            )
          ),
        "es" ->
          MessageBundle(
            "es",
            Map(
              "RulesPage.rules" ->
                s"""
           |  <h1>Reglas</h1>
           |  <p>Chuti es un juego de cuatro jugadores, se juega con un un domino de doble 6, es decir, un domino de 28 fichas.</p>
           |  <p>Un partido completo consta de una serie de juegos, los juegos continúan hasta que uno de los cuatro jugadores llegue a 21 puntos</p>
           |  <p>Para empezar cada juego, se colocan todas las fichas boca abajo y se hace la sopa (se revuelven), cada jugador toma 7 fichas. </p>
           |  <p>Cada juego tiene dos fases:</p>
           |  <h2>Fase 1: Apuestas</h2>
           |  <p>Solo en el primer juego, el jugador con la mula de seis hace la apuesta inicial, después del primer juego, la apuesta inicial se va turnando en sentido contrario a las manecillas del reloj</p>
           |  <ul>
           |	<li>Quien obtuvo la mano será el primero en “cantar”, o sea anunciar cuántos manos pretende ganar. El número mínimo a cantar será de 4 manos (cantar “Casa”).</li>
           |  <li>Los demás jugadores tienen la oportunidad, después de que el de la mano cantó, de cantar por lo menos una más que las que el otro anunció, siguiendo el orden hacia la derecha del de la mano</li>
           |  </ul>
           |  <h2>Fase 2: Juego</h2>
           |  <ul>
           |    <li>Le corresponderá salir al jugador que haya cantado un número más alto, estando obligado a ganar por lo menos ese número de manos. Si no lo logra, se le restarán tantos puntos como manos cantó y se le anotará un “hoyo”. Si las hace o gana más manos, se le anotan el total de manos ganadas en la partida.</li>
           |    <li>El jugador que sale anuncia qué número triunfa. Usualmente saldrá con un triunfo, en cuyo caso deberá poner ese número de frente a los jugadores. El triunfo mayor será quien gane en cada mano, a excepción de que se salga con la “mula”, la cual “arrastra” siempre a los demás triunfos. Si la mano no la inicia con la mula y es otro jugador quien la tira, ésta tendrá su valor numérico relativo (del 0 al 6) en cuanto a jerarquía de triunfo.</li>
           |    <li>Quien inicia la partida puede anunciar que no habrá triunfos. En cuyo caso el valor de cada ficha será la que gane en cada mano, respetando siempre la regla de que la mula arrastra si se sale con ella.</li>
           |    <li>Cuando se sale con un triunfo, los demás jugadores están obligados a tirar otro triunfo, en caso de tenerlo. De no ser así, podrán tirar cualquier otra ficha.</li>
           |    <li>Quien gana la primera mano jala hacia sí las 4 fichas y las deja boca arriba. El resto de las manos deberán ser jaladas por quien las gane y quedar boca abajo.</li>
           |    <li>El último grupo de fichas colocadas boca abajo, siempre puede ser consultado por el jugador que así lo desee, pero no los anteriores. En caso de que alguien levante un grupo de cuatro fichas boca abajo que no sean las últimas, se le anotará un hoyo.</li>
           |    <li>En el momento en que el jugador que salió asegura la cantidad de manos que ganó deberá anunciarlo mostrando sus fichas para demostrarlo. Esto aplica incluso antes de correr la primera mano (“de caída”). Los demás jugadores deberán corroborar esto; si otro jugador detecta que aún no “estaba hecho” (asegurar ganar las manos), o en el caso contrario, si demuestra que ya estaba hecho y no lo dijo a tiempo, se le restarán los puntos que cantó y se le anotará un hoyo</li>
           |    <li>Cuando un jugador cumple las manos comprometidas y quedan fichas por jugar dará “regalos”, es decir las manos las ganará quien tenga la ficha más alta relativa a la ficha de regalo. </li>
           |    <li>Si un jugador no cumple las manos comprometidas se le anotará un hoyo y se le restarán el número de puntos igual al número que canto.  En este caso, se le anotarán puntos positivos a quien haya ganado otras manos, pero no habrá regalos.</li>
           |    <li>La siguiente persona a la que le corresponde cantar es quien está a la derecha de la persona que tenía la mano en la ronda anterior, independientemente de quien haya salido por cantar más, y así sucesivamente. </li>
           |    <li>El juego lo gana el participante que llegue a 21 puntos, momento en el cual se hacen las cuentas de los hoyos y el pago por el partido, el cual es del doble del valor del hoyo.</li>
           |  </ul>
           |  <h2>Algunos detalles técnicos y otras sutilezas</h2>
           |  <ul>
           |    <li>Cuando se le enseña a alguien a jugar chuti, es tradicional decir que es como el juego de corazones, o como brisca en cartas, pero en realidad nadie lo sabe porque nadie jamas a jugado esos juegos</li>
           |    <li>En general, cuando pides, los demas jugadores contestan con una ficha inmediatamente, pero puedes, si deseas, pedir “estricta derecha”, entonces los jugadores tienen que responder uno a uno en order opuesto a las manecillas de un reloj</li>
           |    <li>Después de correr tu primera fila, es tu derecho evaluar to posicion y renunciar la mano, se cuenta un hoyo, y se apuntan los puntos negativos, pero de esa manera se previene que los demás jugadores tengan puntos</li>
           |    <li>Cuando el jugador que canta hace su casa (es decir, cuatro filas) y le regala una ficha a cada uno de los otros 3, esto se llama “Santa Claus”</li>
           |    <li>Cuando el jugador que canta hace su casa (es decir, cuatro filas) y le regala tres fichas a un jugador, a este se le llama “El Niño del cumpleaños”</li>
           |    <li>La ficha 0:1 se llama “campanita”, es costumbre dar tres golpecillos con ella en la mesa cuando se juega (la razon de hacer esto es algo esoterica: con un poco de experiencia entenderás)</li>
           |    <li>Cuando un jugador “salva” al que le tocaba cantar, es perfectamente razonable que los otros dos le digan palabrotas.
           |  </ul>
           |  <h2>Puntuación</h2>
           |  <p>El juego termina cuando un jugador llega a 21 puntos, en ese momento el partido se hacen las cuentas, cada punto se multiplica por una cantidad pre-acordada de dolares (o pesos, dinares yugoslavos, satoshi, o lo que prendas, o lo que esten apostando)</p>
           |  <ul>
           |    <li>El jugador que gana el juego recibe un punto de cada uno de los otros jugadores</li>
           |    <li>Cada hoyo le paga un punto a los otros 3 jugadores</li>
           |    <li>Si estas en numeros negativos, le pagas dos puntos al ganador</li>
           |    <li>Si estas en cero, le pagas un punto al ganador</li>
           |    <li>Si ganas el juego con chuti (es decir, cantaste chuti e hiciste 7 filas) cada jugador te da un punto adicional</li>
           |  </ul>
           |  <h2>Estrategia para ganar</h2>
           |  <p>La decisión del jugador que canta, oscila entre anunciar la mayor cantidad que considere que puede lograr, con el
           |      riesgo que le representa fallar, y cantar una cantidad segura, con el riesgo de que otro cante más y el primero
           |      no logre los puntos que, parecía, podía obtener.</p>
           |  <p>Los jugadores que tienen la opción de cantar después de que el jugador que lleva la mano ya cantó, es evaluar la
           |      posibilidad y el riesgo de hacer más puntos y tomar su decisión al respecto. El jugador que sale, intentará,
           |      primeramente, cumplir como mínimo las que anunció para, posteriormente, intentar obtener más puntos.</p>
           |  <p>El objetivo de los demás jugadores, en tanto, es evitar que el jugador que sale, logre ganar las manos
           |      anunciadas, para lo cual llevan en mente cada una de las fichas jugadas y deducen el juego del participante que
           |      salió, para hacer sus mejores jugadas en base a las fichas que tienen.</p>
           |""".stripMargin
            )
          )
      )

  }

  class Backend($ : BackendScope[Unit, State]) {

    import RulesPageMessages.*

    def render(): VdomElement = {
      ChutiState.ctx.consume { chutiState =>
        given locale: Locale = chutiState.locale
        <.div(
          ^.margin                  := 10.px,
          ^.dangerouslySetInnerHtml := localized("RulesPage.rules")
        )
      }

    }

  }

  private val component = ScalaComponent
    .builder[Unit]("RulesPage")
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply(): Unmounted[Unit, State, Backend] = component()

}
