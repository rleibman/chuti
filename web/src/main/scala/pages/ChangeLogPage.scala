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

import japgolly.scalajs.react.*
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^.*

object ChangeLogPage extends ChutiPage {

  case class State()

  val changeLog: String =
    s"""
       |<h1>Change log</h1>
       |<h2>Version 1.3.0</h2>
       |
       |<ul>
       |<li>Nuevos sonidos para caídas</li>
       |<li>Rotar para que triunfo siempre este arriba en el centro</li>
       |</ul>
       |
       |<h3>Bugs</h3>
       |<ul>
       |<li>Chat messages with carriage return don’t work.</li>
       |<li>Corner case of reaching 21</li>
       |<li>Chat doesn’t automatically scrolls</li>
       |<li>Chat msg doesn’t take the whole line.</li>
       |<li>Cuentas cuando alguien esta en cero</li>
       |<li>Se pueden escuchar mas de un sonido por jugada</li>
       |</ul>
       |
       |<h2>Version 1.2.0</h2>
       |
       |<h3>User management screen</h3>
       |<ul>
       |  <li>Accounts, add money, request money transfer</li>
       |  <li>Allow user to rearrange the dominos (or do it automatically?)</li>
       |</ul>
       |
       |<h2>Version 1.1.0</h2>
       |<ul>
       |  <li>Historia de juegos</li>
       |  <li>Colorear la ficha ganadora en las fichas de a lado.</li>
       |  <li>Si tratas de registrarte dos veces te pone inactive.</li>
       |  <li>Sonido cuando llega un chat</li>
       |  <li>Cuando seleccionas una ficha, automaticamente selecciona triunfo?</li>
       |  <li>Invites by email are not working</li>
       |  <li>Rehydrate the system after restart: Tokens</li>
       |  <li>(in client) load last 10 minutes of messages</li>
       |  <li>About screen</li>
       |</ul>
       |
       |<h2>Version 1.0.0</h2>
       |<h3>General</h3>
       |<ul>
       |    <li>DONE Paint all dominos</li>
       |    <li>DONE Finalize wireframe</li>
       |    <li>DONE Where does the "table" go?</li>
       |    <li>DONE Database</li>
       |    <li>DONE mysql with json column</li>
       |    <li>DONE Server</li>
       |    <li>DONE decide server technology.</li>
       |    <li>DONE Client</li>
       |    <li>DONE decide client technology</li>
       |    <li>DONE Scalajs, scalablytyped, websockets, caliban, circe</li>
       |    <li>DONE code pages</li>
       |    <li>DONE Set up table</li>
       |    <li>DONE Game page</li>
       |    <li>DONE Cuentas</li>
       |    <li>DONE Registration</li>
       |    <li>DONE Login</li>
       |    <li>DONE Lost password</li>
       |    <li>DONE Set up table</li>
       |    <li>DONE Code game engine</li>
       |</ul>
       |
       |<h3>Random</h3>
       |<ul>
       |    <li>DONE Consistent language</li>
       |</ul>
       |
       |<h3>Pregame</h3>
       |<ul>
       |    <li>DONE Create new game</li>
       |    <li>DONE Join random game (first user)</li>
       |    <li>DONE Join random game (second and third users)</li>
       |    <li>DONE Join random game (fourth user)</li>
       |    <li>DONE Abandon unstarted game</li>
       |    <li>DONE Abandon a started game</li>
       |    <li>DONE Invite one existing user to game</li>
       |    <li>DONE Invite non-existing user to game</li>
       |    <li>DONE Accept game invite</li>
       |    <li>DONE Reject game invite</li>
       |</ul>
       |
       |<h3>Lobby</h3>
       |<ul>
       |    <li>DONE Mark users who are invited or playing in our game already</li>
       |    <li>DONE On last accept the screen ends up in "No es el momento de esperandoJugadoresInvitados", como parte e
       |        RedoEvent. This happens because joining is not an event, so the system doesn't know we're ready to play
       |    </li>
       |    <li>DONE Cuentas personales</li>
       |</ul>
       |
       |<h3>Game</h3>
       |<ul>
       |    <li>DONE Main screen setup</li>
       |    <li>DONE Decide what can be done depending on where you are (look at bot)</li>
       |</ul>
       |
       |<h3>Bugs</h3>
       |<ul>
       |    <li>DONE Last person joining is causing huge issues.</li>
       |    <li>DONE onComponentUnmount... close down ws sessions</li>
       |    <li>DONE Aplicando #C, ya esta listo para caerse pero el juego no lo detecta correctamente.</li>
       |    <li>DONE Aplicando #C, me encuentro en una posicion en la que dos personas pueden pedir... porque?</li>
       |    <li>DONE 2 filas a la derecha o izquierda look weird.</li>
       |    <li>DONE Si haces hoyo técnico y cantaste se te cuentan todos tus puntos negativos</li>
       |    <li>DONE Hoyo tecnico cuando: el cantador no tiene la mano, tiene nada mas una ficha y esta a punto de perder. No
       |        hay boton de caete.
       |    </li>
       |</ul>
       |
       |<h3>To test</h3>
       |<ul>
       |    <li>Transfer of ownership when original user abandons game</li>
       |    <li>Add unique constraint to friends</li>
       |</ul>
       |
       |<h3>Other</h3>
       |<ul>
       |    <li>DONE Remove all "Repository with DatabaseProvider", Repository should stand on it's own</li>
       |</ul>
       |
       |<h3>Partido terminado:</h3>
       |<ul>
       |    <li>DONE Poner un boton en el centro: "Regresar al Lobby"</li>
       |    <li>DONE Quitar "Abandona juego"</li>
       |    <li>DONE Poner un boton en el lobby: "Empezar nuevo partido con los mismos jugadores"</li>
       |</ul>
       |
       |<h2>Version 2.x.x (Futuro)</h2>
       |<h3>General</h3>
       |<ul>
       |  <li>Agregarle la razón a las cuentas</li>
       |  <li>No llamarles satoshi, sino ibéritos.<li>
       |  <li>Translate to English</li>
       |  <li>Versioning and upgrade in Game object</li>
       |  <li>Chose domino back logo</li>
       |</ul>
       |
       |<h3>Server</h3>
       |<ul>
       |    <li>Rehydrate the system after restart (clients need to reconnect)</li>
       |    <li>Don't send email invitations? if the user is online and in lobby?</li>
       |</ul>
       |
       |<h3>Web client</h3>
       |<ul>
       |    <li>Add WebRTC video</li>
       |    <li>Clean up GameException errors</li>
       |    <li>Clean up presentation of really bad errors</li>
       |    <li>Animations of Borlotes
       |    <ul>
       |    <li>Sopa</li>
       |    <li>Poner ficha</li>
       |    <li>Hoyo</li>
       |    <li>Chuti</li>
       |    <li>El hoyo que camina</li>
       |    <li>Tecnico</li>
       |    </ul></li>
       |    <li>No ensenes las fichas sino hasta que la sopa este hecha (el audio acabe)</li>
       |</ul>
       |
       |<h3>Chat en juego</h3>
       |<ul>
       |    <li>Mensajes predispuestos</li>
       |    <li>Con tu hoyo me voy</li>
       |    <li>Con mi casa me voy</li>
       |    <li>Mandar todos los borlotes por chat.</li>
       |</ul>
       |
       |<h3>Admin screen</h3>
       |<ul>
       |    <li>Games playing, game index, event list</li>
       |    <li>Glimpse into queues</li>
       |</ul>
       |
       |<h1>Bugs</h1>
       |<ul>
       |    <li>Double pressing of buttons is messing things up!</li>
       |    <li>En Safari no suena</li>
       |</ul>
       |
       |<h3>To test</h3>
       |<ul>
       |    <li>Invite by email, unhappy paths</li>
       |    <li>Transfer of ownership when original user abandons game</li>
       |    <li>Add unique constraint to friends</li>
       |</ul>
       |""".stripMargin

  class Backend($ : BackendScope[Unit, State]) {

    def render(): VdomElement = {
      <.div(^.dangerouslySetInnerHtml := changeLog)
    }

  }

  private val component = ScalaComponent
    .builder[Unit]
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply(): Unmounted[Unit, State, Backend] = component()

}
