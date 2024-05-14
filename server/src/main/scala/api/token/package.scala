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
