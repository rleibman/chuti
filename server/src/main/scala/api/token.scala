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

package api

import java.math.BigInteger
import java.security.SecureRandom

import chuti.User
import scalacache.Cache
import scalacache.caffeine.CaffeineCache
import zio.{Has, Task}

import scala.concurrent.duration.{Duration, _}

package object token {

  sealed trait TokenPurpose
  object TokenPurpose {
    //TODO check which of these are actually being used
    case object NewUser extends TokenPurpose
    case object LostPassword extends TokenPurpose
    case object FriendToken extends TokenPurpose
    case object GameInvite extends TokenPurpose
  }

  type TokenHolder = Has[TokenHolder.Service]

  case class Token(tok: String) {
    override def toString: String = tok
  }

  object TokenHolder {
    trait Service {

      def peek(
        token:   Token,
        purpose: TokenPurpose
      ): Task[Option[User]]

      def createToken(
        user:    User,
        purpose: TokenPurpose,
        ttl:     Option[Duration] = Option(3.hours)
      ): Task[Token]
      def validateToken(
        token:   Token,
        purpose: TokenPurpose
      ): Task[Option[User]]
    }

    val live: Service = new Service {

      private val random = SecureRandom.getInstanceStrong
      import scalacache.ZioEffect.modes._
      implicit val userTokenCache: Cache[User] = CaffeineCache[User]

      override def createToken(
        user:    User,
        purpose: TokenPurpose,
        ttl:     Option[Duration] = Option(3.hours)
      ): Task[Token] = {
        val t = new BigInteger(12 * 5, random).toString(32)
        scalacache.put(t, purpose)(user, ttl).as(Token(t))
      }

      override def validateToken(
        token:   Token,
        purpose: TokenPurpose
      ): Task[Option[User]] = {
        for {
          u <- scalacache.get(token.tok, purpose)
          _ <- scalacache.remove(token.tok, purpose)
        } yield u
      }

      override def peek(
        token:   Token,
        purpose: TokenPurpose
      ): Task[Option[User]] = {
        scalacache.get(token.tok, purpose)
      }
    }
  }

}
