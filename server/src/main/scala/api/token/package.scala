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

import chuti.{User, UserId}
import dao.{Repository, SessionContext}
import game.GameService
import zio.*
import zio.duration.*
import zio.cache.Cache
import zio.clock.Clock
import zio.logging.{Logger, Logging}
import zio.random.Random

import java.math.BigInteger
import java.security.SecureRandom

package object token {

  enum TokenPurpose(override val toString: String) {

    case NewUser extends TokenPurpose(toString = "NewUser")
    case LostPassword extends TokenPurpose(toString = "LostPassword")

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
        ttl:     Option[Duration] = Option(5.hours)
      ): Task[Token]
      def validateToken(
        token:   Token,
        purpose: TokenPurpose
      ): Task[Option[User]]

    }

    def mockLayer: ULayer[TokenHolder] =
      ZLayer.succeed(new Service {

        override def peek(
          token:   Token,
          purpose: TokenPurpose
        ): Task[Option[User]] = Task.none

        override def createToken(
          user:    User,
          purpose: TokenPurpose,
          ttl:     Option[Duration]
        ): Task[Token] = Task.succeed(Token(""))

        override def validateToken(
          token:   Token,
          purpose: TokenPurpose
        ): Task[Option[User]] = Task.none

      })

    def liveLayer: ZLayer[Repository & Logging, Nothing, TokenHolder] =
      ZLayer.fromServices[Repository.Service, Logger[String], TokenHolder.Service]((repo, log) => {
        new Service {
          //          SessionContext & Logging
          val layer: ZLayer[Any, Nothing, SessionContext & Logging & Clock & Random] =
            GameService.godLayer ++ ZLayer.succeed(log) ++ Clock.live ++ Random.live

          zio.Runtime.default.unsafeRun {
            val freq = new zio.DurationSyntax(1).hour
            (log.info("Cleaning up old tokens") *> repo.tokenOperations.cleanup.provideLayer(layer))
              .repeat(Schedule.spaced(freq).jittered).forkDaemon
          }

          override def peek(
            token:   Token,
            purpose: TokenPurpose
          ): Task[Option[User]] = repo.tokenOperations.peek(token, purpose).provideLayer(layer)

          override def createToken(
            user:    User,
            purpose: TokenPurpose,
            ttl:     Option[Duration]
          ): Task[Token] = repo.tokenOperations.createToken(user, purpose, ttl).provideLayer(layer)

          override def validateToken(
            token:   Token,
            purpose: TokenPurpose
          ): Task[Option[User]] = repo.tokenOperations.validateToken(token, purpose).provideLayer(layer)
        }
      })

    def tempCache(cache: Cache[(String, TokenPurpose), Nothing, User]): Service =
      new Service {

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

}
