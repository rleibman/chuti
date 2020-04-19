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

import chat.ChatComponent
import chuti.{ChannelId, ChatMessage, GameId, GameState, User}
import japgolly.scalajs.react._
import japgolly.scalajs.react.component.Scala.Unmounted
import japgolly.scalajs.react.vdom.html_<^._
import typings.semanticUiReact.components.{Button, Container, Header, Loader}
import typings.semanticUiReact.genericMod.SemanticSIZES

object LobbyPage extends ChutiPage {
  case class State(
    friends:        Seq[User] = Seq.empty,
    privateMessage: Option[ChatMessage] = None,
    gameInProgress: Option[GameState] = None,
    invites:        Seq[GameState] = Seq.empty
  )

  class Backend($ : BackendScope[_, State]) {

    def render(s: State): VdomElement = {
      chutiContext.consume { chutiState =>
        chutiState.user.fold(<.div(Loader(active = true, size = SemanticSIZES.massive)("Cargando"))) {
          user =>
            <.div(
              ChatComponent(
                user,
                ChannelId.lobbyChannel,
                onPrivateMessage = Option(msg => $.modState(_.copy(privateMessage = Option(msg))))
              ),
              Container()(s.privateMessage.fold("")(_.msg)),
              Button()("Juega Con Quien sea"),
              Button()("Empezar Juego Nuevo"),
              TagMod(
                Container()(
                  Header()("Invitaciones"),
                  s.invites.toVdomArray { game =>
                    <.div(
                      ^.key := game.id.fold("")(_.toString),
                      game.jugadores.map(_.user.name).mkString(","),
                      Button()("Aceptar")
                    )
                  }
                )
              ).when(s.invites.nonEmpty),
              TagMod(
                Container()(
                  Header()("Amigos"),
                  s.friends.toVdomArray { friend =>
                    <.div(
                      ^.key := friend.id.fold("")(_.toString),
                      user.name
                    )
                  }
                )
              ).when(s.friends.nonEmpty),
              TagMod(
                Container()(
                  Header()("Juego En Progreso"),
                  Button()("Reanudar Juego"),
                  Button()("Abandonar Juego") //Que ojete!
                )
              ).when(s.gameInProgress.nonEmpty)
            )
        }
      }
    }
  }

  val component = ScalaComponent
    .builder[Unit]("LobbyPage")
    .initialState(State())
    .renderBackend[Backend]
    .build

  def apply(): Unmounted[Unit, State, Backend] = component()

}
