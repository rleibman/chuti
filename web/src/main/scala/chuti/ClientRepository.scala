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

package chuti

import caliban.client.SelectionBuilder
import caliban.client.scalajs.ChatClient.*
import caliban.client.scalajs.GameClient.*
import caliban.client.scalajs.given
import chuti.*
import dao.GameOperations
import japgolly.scalajs.react.callback.AsyncCallback
import sttp.client4.UriContext
import zio.json.ast.Json

object ClientRepository  {

  val game: GameOperations[AsyncCallback] = new GameOperations[AsyncCallback]() {

    override def getHistoricalUserGames: AsyncCallback[Seq[Game]] = ???

    override def userInGame(id: GameId): AsyncCallback[Boolean] = ???

    override def updatePlayers(game: Game): AsyncCallback[Game] = ???

    override def gameInvites: AsyncCallback[Seq[Game]] = ???

    override def gamesWaitingForPlayers(): AsyncCallback[Seq[Game]] = ???

    override def getGameForUser: AsyncCallback[Option[Game]] = ???

    override def upsert(e: Game): AsyncCallback[Game] = ???

    override def get(pk: GameId): AsyncCallback[Option[Game]] = ???

    override def delete(pk: GameId, softDelete: Boolean): AsyncCallback[Boolean] = ???

    override def search(search: Option[EmptySearch]): AsyncCallback[Seq[Game]] = ???

    override def count(search: Option[EmptySearch]): AsyncCallback[Long] = ???
  }


}
