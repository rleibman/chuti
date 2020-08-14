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

import java.util.Locale

import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._

object RulesPage extends ChutiPage {
  case class State()

  trait Messages {
    case class MessageBundle(
      locale: String,
      map:    Map[String, String]
    )
    def bundles: Map[String, MessageBundle]

    def getLocalizedString(
      key:     String,
      default: String = "",
      locale:  Locale = new Locale("es", "MX")
    ): String = {
      (for {
        //TODO, find the most specific match, first by language-country, then by language or use whatever is found.
        bundle <- bundles.get(locale.getLanguage)
        str    <- bundle.map.get(key)
      } yield str).getOrElse(default)
    }
  }

  object RulesPageMessages extends Messages {
    override def bundles: Map[String, RulesPageMessages.MessageBundle] =
      Map(
        "en" ->
          MessageBundle(
            "en",
            Map(
              "rules" ->
                """<h1>Rules</h1>
                  |<p>Chuti is played with four people, and a full set of double 6 dominoes, this means that there’s 28 tiles.</p>
                  |<p>A full match consists of a series of games, games continue until one of the 4 players reaches a score of 21
                  |    points.</p>
                  |<p>A game starts by shuffling the tiles, each player takes 7 tiles.</p>
                  |<p>The game has 2 stages</p>
                  |<h2>Bidding</h2>
                  |<ul>
                  |    <li>On the first game of a match only, the player with the double six does the initial bid, after the first game,
                  |        the
                  |        first bid moves counterclockwise.
                  |    </li>
                  |    <li>The initial bidder estimates how many wins (or lines) they can make during the game, they have to call out a
                  |        minimum of 4 lines (this is named house) but can call out 5, 6, or 7 if they call out 7, they can call out a
                  |        straight 7 (this is rare, and not considered manly) or chuti… if you call chuti and make all 7 lines you gain 21
                  |        points (and likely win the match!)
                  |    </li>
                  |    <li>Going around counterclockwise once, each player gets one chance to outbid the previous players by calling a
                  |        higher
                  |        number.
                  |    </li>
                  |    <li>After all 4 players have bid, the player who called the highest number starts the game, he must make as many
                  |        lines
                  |        as he called, if they don’t, then the will get negative points and a “hole”.
                  |    </li>
                  |</ul>
                  |<h2>Game</h2>
                  |<ul>
                  |    <li>The player who won the bid has two choices to make: what number will be the trump for the duration of this game,
                  |        and what tile they will use to call. The trump will be considered the highest number throughout the game, the
                  |        player
                  |        can also call “no trumps”
                  |    </li>
                  |    <li>The game now progresses by a series of hands, on each hand the player who won the last hand drops a tile (the
                  |        “ask”) signifying what number they’re asking for.
                  |    </li>
                  |    <li>Each of the other players needs to respond with a tile that matches the number that was asked, if they don’t
                  |        have
                  |        a matching tile, but they have a trump they must respond with that one, and if they don’t have that, they can
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
                  |                win
                  |                against
                  |                1:6 but only if it’s played first.
                  |            </li>
                  |            <li>Remember that the caller can call “no trumps” each tile then has it’s normal value, doubles win when
                  |                “asked”
                  |            </li>
                  |        </ul>
                  |    </li>
                  |    <li>If the caller makes the lines they originally bid (or more) then they win the game, those lines count as
                  |        positive
                  |        (one point per line) and other players who made lines also get a point per line.
                  |    </li>
                  |    <li>If the caller does not make all the lines they bid, then they get what’s called a “hole”, they get marked
                  |        negative
                  |        that number.
                  |    </li>
                  |    <li>If you have a tile that matches the “ask” but give something else it’s considered a “technical hole”, the game
                  |        ends and a hole is marked against you (no negative points against you unless you’re the caller
                  |    </li>
                  |    <li>If you don’t have a tile that matches the “ask” but you have a trump and don’t give it, that’s also a “technical
                  |        hole”
                  |    </li>
                  |    <li>Once it’s obvious that you’ll win the rest of the hands in the game, you must “fall” by dropping all your tiles
                  |        that would make up the lines, the remaining tiles are given as “gifts”, to the players who would win if you were
                  |        to
                  |        use those tiles as “asks”.
                  |    </li>
                  |    <li>Once it’s obvious that you’ll win the rest of the hands, if you don’t “fall”, that’s considered a technical hole
                  |        as well.
                  |    </li>
                  |    <li>The first line played stays open, the last line played has to stay open until a new line is played.
                  |    </li>
                  |    <li>Once the game is over, the turn to call passes to the right of the last person who’s turn it was to call. It is
                  |        the responsibility of the person who’s turn it was to call to shuffle the tiles (make the “soup”)
                  |    </li>
                  |</ul>
                  |<h2>Other small technical details and nuances</h2>
                  |<ul>
                  |    <li>When teaching someone to play chuti, it is traditional to say that chuti is like the card game of “hearts” or
                  |        “brisca”, but nobody really knows since we know nobody who’s ever played those games.
                  |    </li>
                  |    <li>Though in general, when you “ask” everybody can answer immediately, you may, if you wish, ask for “strict
                  |        right”,
                  |        players then have to respond one by one counterclockwise
                  |    </li>
                  |    <li>After running your first line, you may evaluate your position and you may forfeit the game, you still get the
                  |        hole
                  |        and respective negative points, but at least you’re not giving anybody else gifts.
                  |    </li>
                  |    <li>When the calling player makes their house (four), and every player gets a gift, the calling player is called
                  |        “Santa Claus”
                  |    </li>
                  |    <li>When a single player gets all the gifts, they’re called “the birthday boy/girl”
                  |    </li>
                  |    <li>The 0:1 tile is named “little bell”, customarily it gets tapped on the table when it’s played, there’s strategic
                  |        reasons for this custom.
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
                  |    <li>If you win the game after calling chuti (i.e. you do seven lines) every other player gives you one additional
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
                  |        and 3:5), and the rest are doubles… this is called “going for it all, with two”
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
              "rules" ->
                s"""
           |    <h1>Reglas (Por <a
           |            href="http://solucionesytrucosuniversales.blogspot.com/2013/08/chuti-mul-reglas-de-juego-y-estrategias.html"
           |            )>Homero Cerecedo</a>)</h1>
           |    <ul>
           |        <li> Primeramente se hace la sopa (se revuelven las fichas boca abajo).</li>
           |        <li> Cada jugador toma una ficha, el que tome la mayor será quien tenga la mano, siendo el responsable de cantar
           |            (así se denomina a la acción de anunciar cuántas manos va a ganar en la partida), por lo menos, cuatro
           |            manos, las cuales son las mínimas que deberá hacer en la partida.
           |        </li>
           |        <li> Como en todos los casos, el jugador que está a la izquierda del que sacó la ficha mayor es el responsable
           |            de hacer la sopa y quien se quedará con las últimas siete fichas, después de que los demás jugadores tomen
           |            las suyas.
           |        </li>
           |        <li> Los demás jugadores tienen la oportunidad, después de que el de la mano cantó, de cantar por lo menos una
           |            más que las que el otro anunció, siguiendo el orden inverso a las manecillas del reloj.
           |        </li>
           |        <li> Le corresponderá salir al jugador que haya cantado un número más alto, estando obligado a hacer por lo
           |            menos ese número. Si hace una menos, se le restarán tantos puntos como manos cantó y se le anotará un pozo.
           |            Si las hace o gana más manos, se le anotan el total de manos ganadas en la partida.
           |        </li>
           |        <li> La cara de la ficha que apunta hacia el frente de la persona que inicia la partida es la que indica los
           |            triunfos, salvo que el jugador que sale anuncie que la partida va a ser sin triunfos, en cuyo caso las
           |            fichas solamente pueden ser jugadas por todos con el número de orificios mayor hacia el frente del jugador
           |            que sale. En las mulas esto no tiene importancia.
           |        </li>
           |        <li> Este número de puntos es el que marca la mano, estando todos obligados a tirar una ficha con el mismo
           |            número en una de sus caras, en caso de no tenerla, están obligados a tirar un triunfo. Si tampoco tienen
           |            triunfo, pueden tirar cualquier otra ficha.
           |        </li>
           |        <li> El jugador que gana la mano, jala hacia su extremo derecho las cuatro fichas, las cuales estarán mostrando
           |            los puntos hacia arriba si es que hay triunfos en dicha primera mano, de no haberlos, las fichas se colocan
           |            boca abajo. En la mano que aparezca el primer triunfo, al jalar el grupo, debe quedar con los puntos hacia
           |            arriba.
           |        </li>
           |        <li> Todas las manos posteriores a la primera donde salieron triunfos, deberán ser jaladas boca abajo.</li>
           |        <li> El último grupo de fichas colocadas boca abajo, siempre puede ser consultado por el jugador que así lo
           |            desee, pero no los anteriores. En caso de que alguien levante un grupo de cuatro fichas boca abajo que no
           |            sean las últimas, se le anotará un pozo.
           |        </li>
           |        <li> El juego lo gana el participante que llegue a 21 puntos, momento en el cual se hacen las cuentas de los
           |            pozos y el pago por el partido, el cual es del doble del valor del pozo.
           |        </li>
           |        <li> La siguiente persona a la que le corresponde cantar es la inmediata a la derecha de la persona que tenía la
           |            mano originalmente, independientemente de quien haya salido por cantar más y así sucesivamente.
           |        </li>
           |    </ul>
           |    <h2>Estrategia para ganar</h2>
           |    <p>La decisión del jugador que canta, oscila entre anunciar la mayor cantidad que considere que puede lograr, con el
           |        riesgo que le representa fallar, y cantar una cantidad segura, con el riesgo de que otro cante más y el primero
           |        no logre los puntos que, parecía, podía obtener.</p>
           |    <p>Los jugadores que tienen la opción de cantar después de que el jugador que lleva la mano ya cantó, es evaluar la
           |        posibilidad y el riesgo de hacer más puntos y tomar su decisión al respecto. El jugador que sale, intentará,
           |        primeramente, cumplir como mínimo las que anunció para, posteriormente, intentar obtener más puntos.</p>
           |    <p>El objetivo de los demás jugadores, en tanto, es evitar que el jugador que sale, logre ganar las manos
           |        anunciadas, para lo cual llevan en mente cada una de las fichas jugadas y deducen el juego del participante que
           |        salió, para hacer sus mejores jugadas en base a las fichas que tienen.</p>
           |    <h1>Reglas (segun Alfredo)</h1>
           |    <ul>
           |        <li>Se le reparten 7 fichas a cada jugador, le pasa una (la que no le sirve) al jugador de la derecha, muchas
           |            veces
           |            es campanita.
           |        </li>
           |        <li>El primero que canta es el que tenga mula de seis. El siguiente en cantar es el de la derecha.</li>
           |        <li>El chuty vale 21 puntos, con el que puedes ganar con la primera mano del juego.</li>
           |        <li>Cada que haces tu humilde son cuatro para el que gana.</li>
           |        <li>Puedes cantar más.</li>
           |        <li>Puedes robar la mano cantando una más que el anterior que cantó.</li>
           |        <li>Si el que canta no hace su casa, se le marca un hoyo, y se le restan las que cantó.</li>
           |        <li>Puedes cantar siete y no cantar chuty. Con el cual tu hoyo solo es de -7 y no de -21.</li>
           |        <li>Puedes ponerle jeta al que salva, y decirle que es un pendejo.</li>
           |        <li>Cuando el primero de la mesa llega a 21, llega el momento de Laaaaas Cuentas.</li>
           |        <li>Cada hoyo paga 3 o uno para cada uno.</li>
           |        <li>Si ganas cada uno te da 1 (o sea, recibes 3)</li>
           |        <li>Por lo tanto si te vas con un hoyo se cancela, el pago.</li>
           |        <li>Si te vas con más de un hoyo, se siguen pagando los hoyos.</li>
           |        <li>Si alguien quedó en negativos por tener varios hoyos, producto de estar cante y cante, le da dos puntos al
           |            ganador.
           |        <li>Si alguien quedó en cero le da un puntos al ganador.</li>
           |        <li>Si se va con chuty, recibe 6 o dos de cada jugador.</li>
           |        <li>Recuerda</li>
           |        <li>Siempre puedes correr la primera.</li>
           |        <li>Pueden ser de caída.</li>
           |        <li>Puede haber hoyo técnico.</li>
           |        <li>Puedes pedir siete para seguir pagando.</li>
           |        <li>Puedes hacer chuty y no ganar por estar en negativos.</li>
           |        <li>Y nunca pero nunca se pueden jugar números pares (es un misterio el por qué).</li>
           |        <li>Ah y este juego es de hombres.</li>
           |        <li>Me reí un chingo escribiendo y acordadándome de todo esto, cualquier duda me dices.</li>
           |        <li>me faltó hablar de los regalos, Santa Claus, la casa de caída, las bases, el ¿alguien me salva?, con mi casa
           |            me
           |            voy, con tu hoyo me voy, el preguntar cinco veces ¿cuántas? Pero ya no aguanto la risa
           |        </li>
           |    </ul>
           |""".stripMargin
            )
          )
      )
  }

  class Backend($ : BackendScope[_, State]) {

    def render(S: State): VdomElement = {
      <.div(^.dangerouslySetInnerHtml := RulesPageMessages.getLocalizedString("rules"))

    }
  }

  private val component = ScalaComponent
    .builder[Unit]("RulesPage")
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply(): Unmounted[Unit, State, Backend] = component()

}
