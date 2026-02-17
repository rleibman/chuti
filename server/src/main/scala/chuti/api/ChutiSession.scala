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

package chuti.api

import auth.{AuthenticatedSession, Session, UnauthenticatedSession}
import chuti.{ConnectionId, User}
import zio.json.*
import zio.{ULayer, ZLayer}

import java.util.Locale

type ChutiSession = Session[User, ConnectionId]

object ChutiSession {

  val empty: ChutiSession = UnauthenticatedSession[User, ConnectionId]()

  val godSession:                ChutiSession = AuthenticatedSession(Some(chuti.god), Some(ConnectionId.random))
  val godlessSession:            ChutiSession = AuthenticatedSession(Some(chuti.godless), Some(ConnectionId.random))
  def botSession(botUser: User): ChutiSession = AuthenticatedSession(Some(botUser), Some(ConnectionId.random))

  def apply(user: User): ChutiSession = AuthenticatedSession(Some(user), Some(ConnectionId.random))

}

extension (session: ChutiSession) {

  def toLayer: ULayer[ChutiSession] = ZLayer.succeed(session)

}
