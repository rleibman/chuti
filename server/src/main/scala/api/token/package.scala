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

import chuti.{GameError, User, UserId}
import dao.ZIORepository
import game.GameService
import zio.*
import zio.cache.Cache
import zio.logging.*

import java.math.BigInteger
import java.security.SecureRandom

package object token {

  enum TokenPurpose(override val toString: String) {

    case NewUser extends TokenPurpose(toString = "NewUser")
    case LostPassword extends TokenPurpose(toString = "LostPassword")

  }

  trait TokenHolder {

    def peek(
      token:   Token,
      purpose: TokenPurpose
    ): IO[GameError, Option[User]]

    def createToken(
      user:    User,
      purpose: TokenPurpose,
      ttl:     Option[Duration] = Option(5.hours)
    ): IO[GameError, Token]

    def validateToken(
      token:   Token,
      purpose: TokenPurpose
    ): IO[GameError, Option[User]]

  }

  object TokenHolder {

    def mockLayer: ULayer[TokenHolder] =
      ZLayer.succeed(new TokenHolder {

        override def peek(
          token:   Token,
          purpose: TokenPurpose
        ): IO[GameError, Option[User]] = ZIO.none

        override def createToken(
          user:    User,
          purpose: TokenPurpose,
          ttl:     Option[Duration]
        ): IO[GameError, Token] = ZIO.succeed(Token(""))

        override def validateToken(
          token:   Token,
          purpose: TokenPurpose
        ): IO[GameError, Option[User]] = ZIO.none

      })

    def liveLayer: URLayer[ZIORepository, TokenHolder] =
      ZLayer.fromZIO(for {
        repo <- ZIO.service[ZIORepository]
        freq = new zio.DurationSyntax(1).hour
        _ <- (ZIO.logInfo("Cleaning up old tokens") *> repo.tokenOperations.cleanup.provide(GameService.godLayer))
          .repeat(Schedule.spaced(freq).jittered).forkDaemon
      } yield {
        new TokenHolder {

          override def peek(
            token:   Token,
            purpose: TokenPurpose
          ): IO[GameError, Option[User]] = repo.tokenOperations.peek(token, purpose).provide(GameService.godLayer)

          override def createToken(
            user:    User,
            purpose: TokenPurpose,
            ttl:     Option[Duration]
          ): IO[GameError, Token] = repo.tokenOperations.createToken(user, purpose, ttl.map(_.asScala)).provide(GameService.godLayer)

          override def validateToken(
            token:   Token,
            purpose: TokenPurpose
          ): IO[GameError, Option[User]] =
            repo.tokenOperations.validateToken(token, purpose).provide(GameService.godLayer)
        }
      })

    def tempCache(cache: Cache[(String, TokenPurpose), Nothing, User]): TokenHolder =
      new TokenHolder {

        private val random = SecureRandom.getInstanceStrong

        override def createToken(
          user:    User,
          purpose: TokenPurpose,
          ttl:     Option[Duration] = Option(3.hours)
        ): IO[GameError, Token] = {
          val t = new BigInteger(12 * 5, random).toString(32)
          cache.get((t, purpose)).as(Token(t))
        }

        override def validateToken(
          token:   Token,
          purpose: TokenPurpose
        ): IO[GameError, Option[User]] = {
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
        ): IO[GameError, Option[User]] = {
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

}
