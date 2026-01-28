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

package game

import chuti.*
import zio.json.ast.Json

trait GameEngine[F[_]] {

  def newGame(satoshiPerPoint:    Long):   F[Game]
  def newGameSameUsers(oldGameId: GameId): F[Game]
  def play(
    gameId:    GameId,
    playEvent: PlayEvent
  ): F[Game] //Note, this mutates the game

  def playSilently(
    gameId:    GameId,
    playEvent: PlayEvent
  ): F[Boolean] //In the cases we don't want to return the whole game object after a play

  def joinRandomGame(): F[Game]

  def abandonGame(gameId: GameId): F[Boolean]

  def startGame(gameId: GameId): F[Boolean]

  def getLoggedInUsers: F[Seq[User]]

  def inviteByEmail(
    name:   String,
    email:  String,
    gameId: GameId
  ): F[Boolean]

  def inviteToGame(
    userId: UserId,
    gameId: GameId
  ): F[Boolean]

  def acceptGameInvitation(gameId: GameId): F[Game]

  def cancelUnacceptedInvitations(gameId: GameId): F[Boolean]

  def declineGameInvitation(gameId: GameId): F[Boolean]

}
