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
import dao.{Repository, SessionProvider}
import game.GameService
import scalacache.Cache
import scalacache.caffeine.CaffeineCache
import zio.*
import zio.clock.Clock
import zio.logging.{Logger, Logging}
import zio.random.Random

import scala.concurrent.duration.*

package object token {

  sealed trait TokenPurpose
  object TokenPurpose {

    case object NewUser extends TokenPurpose {

      override def toString: String = "NewUser"

    }
    case object LostPassword extends TokenPurpose {

      override def toString: String = "LostPassword"

    }

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
//          SessionProvider & Logging
          val layer: ZLayer[Any, Nothing, SessionProvider & Logging & Clock & Random] =
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

    val tempCache: Service = new Service {

      private val random = SecureRandom.getInstanceStrong
      import scalacache.ZioEffect.modes.*
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
