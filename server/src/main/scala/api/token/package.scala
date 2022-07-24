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

package api.token

import chuti.{User, UserId}
import dao.{Repository, SessionContext}
import game.GameService
import zio.*
import zio.cache.Cache
import zio.logging.*

import java.math.BigInteger
import java.security.SecureRandom

enum TokenPurpose(override val toString: String) {

  case NewUser extends TokenPurpose(toString = "NewUser")
  case LostPassword extends TokenPurpose(toString = "LostPassword")

}

trait TokenHolder {

  def peek(
    token:   Token,
    purpose: TokenPurpose
  ): Task[Option[User]]

  def createToken(
    user:    User,
    purpose: TokenPurpose,
    ttl:     Option[Duration] = Option(5.hours)
  ): Task[Token]

  def validateToken(
    token:   Token,
    purpose: TokenPurpose
  ): Task[Option[User]]

}

object TokenHolder {

  def mockLayer: ULayer[TokenHolder] =
    ZLayer.succeed(new TokenHolder {

      override def peek(
        token:   Token,
        purpose: TokenPurpose
      ): Task[Option[User]] = ZIO.none

      override def createToken(
        user:    User,
        purpose: TokenPurpose,
        ttl:     Option[Duration]
      ): Task[Token] = ZIO.succeed(Token(""))

      override def validateToken(
        token:   Token,
        purpose: TokenPurpose
      ): Task[Option[User]] = ZIO.none

    })

  def liveLayer: URLayer[Repository, TokenHolder] =
    ZLayer.fromZIO(for {
      repo <- ZIO.service[Repository].map(_.tokenOperations)
      freq = new zio.DurationSyntax(1).hour
      _ <- (ZIO.logInfo("Cleaning up old tokens") *> repo.cleanup.provideLayer(GameService.godLayer))
        .repeat(Schedule.spaced(freq).jittered).forkDaemon
    } yield {
      new TokenHolder {

        override def peek(
          token:   Token,
          purpose: TokenPurpose
        ): Task[Option[User]] = repo.peek(token, purpose).provideLayer(GameService.godLayer)

        override def createToken(
          user:    User,
          purpose: TokenPurpose,
          ttl:     Option[Duration]
        ): Task[Token] = repo.createToken(user, purpose, ttl).provideLayer(GameService.godLayer)

        override def validateToken(
          token:   Token,
          purpose: TokenPurpose
        ): Task[Option[User]] = repo.validateToken(token, purpose).provideLayer(GameService.godLayer)
      }
    })

  def tempCache(cache: Cache[(String, TokenPurpose), Nothing, User]): TokenHolder =
    new TokenHolder {

      private val random = SecureRandom.getInstanceStrong

      override def createToken(
        user:    User,
        purpose: TokenPurpose,
        ttl:     Option[Duration] = Option(3.hours)
      ): Task[Token] = {
        val t = new BigInteger(12 * 5, random).toString(32)
        cache.get((t, purpose)).as(Token(t))
      }

      override def validateToken(
        token:   Token,
        purpose: TokenPurpose
      ): Task[Option[User]] = {
        for {
          contains <- cache.contains((token.tok, purpose))
          u <-
            if (contains) {
              cache.get((token.tok, purpose)).map(Some.apply)
            } else ZIO.none
          _ <- cache.invalidate(token.tok, purpose)
        } yield u
      }

      override def peek(
        token:   Token,
        purpose: TokenPurpose
      ): Task[Option[User]] = {
        for {
          contains <- cache.contains((token.tok, purpose))
          u <-
            if (contains) {
              cache.get((token.tok, purpose)).map(Some.apply)
            } else ZIO.none
        } yield u
      }

    }

}
