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

import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._

object RulesPage extends ChutiPage {
  case class State()

  val rules: String =
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
   |        </li>
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

  class Backend($ : BackendScope[_, State]) {

    def render(S: State): VdomElement = {
      <.div(^.dangerouslySetInnerHtml := rules)
    }
  }

  private val component = ScalaComponent
    .builder[Unit]("RulesPage")
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply(): Unmounted[Unit, State, Backend] = component()

}
