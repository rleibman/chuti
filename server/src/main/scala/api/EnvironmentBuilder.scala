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

import ai.LLMService
import api.token.{TokenHolder, TokenPurpose}
import auth.*
import auth.oauth.{OAuthService, OAuthStateStore}
import chat.ChatService
import chuti.*
import chuti.bots.AIBot
import dao.*
import dao.quill.QuillRepository
import game.GameService
import mail.*
import util.{ChutiContainer, MockPostman}
import zio.*
import zio.cache.{Cache, Lookup}
import zio.json.*

import java.util.Locale

type ChutiEnvironment = AuthConfig & ZIORepository & ConfigurationService & Postman &
  AuthServer[User, UserId, ConnectionId] & TokenHolder & OAuthService & OAuthStateStore & GameService & ChatService &
  FlywayMigration
//  RateLimiter &
//  RateLimitConfig &
//net.leibman.analytics.AnalyticsZIORepository &
//  net.leibman.analytics.AnalyticsCleanupService

given JsonCodec[Locale] =
  JsonCodec(
    JsonEncoder.string.contramap(_.toString),
    JsonDecoder.string.mapOrFail(s =>
      Locale.forLanguageTag(s) match {
        case l: Locale => Right(l)
        case null => Left(s"invalid locale $s")
      }
    )
  )

object EnvironmentBuilder {

  /** Converts chuti's OAuth configuration to zio-auth's OAuthService
    *
    * chuti.oauth is a Map[String, OAuthProviderConfig] where keys are provider names ("google", "github", etc.)
    */
  private val oauthServiceLayer: ZLayer[ConfigurationService, ConfigurationError, OAuthService] =
    ZLayer.fromZIO {
      for {
        config <- ZIO.serviceWithZIO[ConfigurationService](_.appConfig)
      } yield {
        // Extract provider configs from map
        val googleConfig = config.chuti.oauth.get("google")
        val githubConfig = config.chuti.oauth.get("github")
        val discordConfig = config.chuti.oauth.get("discord")

        // Create OAuthService with configured providers
        OAuthService.live(
          googleConfig,
          githubConfig,
          discordConfig
        )
      }
    }.flatten

  lazy private val postmanLayer: ZLayer[ConfigurationService, GameError, Postman] = ZLayer.fromZIO(for {
    configService <- ZIO.service[ConfigurationService]
    appConfig     <- configService.appConfig
  } yield CourierPostman.live(appConfig.chuti.smtp))

  val repoLayer: ZLayer[ConfigurationService, ConfigurationError, ZIORepository] =
    QuillRepository.uncached >>> ZIORepository.cached

  // AI layers - optional, only created if config is present
  private val llmServiceLayer: ZLayer[ConfigurationService, ConfigurationError, ai.LLMService] = ZLayer.fromZIO {
    ZIO.serviceWithZIO[ConfigurationService](_.appConfig).map { appConfig =>
      appConfig.chuti.ai match {
        case Some(aiConfig) => ai.LLMService.live(aiConfig.ollama)
        case None           =>
          // Provide a no-op LLMService when AI config is not present
          new ai.LLMService {
            def generate(prompt: String): IO[ai.LLMError, String] =
              ZIO.fail(ai.LLMError("AI configuration not provided"))
          }
      }
    }
  }

  private val aiBotLayer: ZLayer[ai.LLMService & ConfigurationService, ConfigurationError, Option[AIBot]] = ZLayer
    .fromZIO {
      for {
        config     <- ZIO.serviceWithZIO[ConfigurationService](_.appConfig).map(_.chuti.ai)
        llmService <- ZIO.service[ai.LLMService]
      } yield config.map(c => AIBot(config = c.ollama, llmService = llmService))
    }

  val live: ULayer[ChutiEnvironment] = ZLayer
    .make[ChutiEnvironment](
      ChutiAuthServer.live,
      ConfigurationService.live,
      repoLayer,
      postmanLayer,
      TokenHolder.liveLayer,
      oauthServiceLayer,
      OAuthStateStore.live(),
      ZLayer.fromZIO(ZIO.serviceWithZIO[ConfigurationService](_.appConfig).map(_.chuti.session)),
      llmServiceLayer,
      aiBotLayer,
      GameService.make(),
      ChatService.make(),
      FlywayMigration.live
    ).orDie

  val withContainer: ULayer[ChutiEnvironment] = ZLayer
    .make[ChutiEnvironment](
      ChutiAuthServer.live,
      ChutiContainer.containerLayer,
      ConfigurationService.live >>> ChutiContainer.configLayer,
      ZLayer.fromZIO(ZIO.serviceWithZIO[ConfigurationService](_.appConfig).map(_.chuti.session)),
      repoLayer,
      postmanLayer,
      TokenHolder.liveLayer,
      oauthServiceLayer,
      OAuthStateStore.live(),
      llmServiceLayer,
      aiBotLayer,
      GameService.make(),
      ChatService.make(),
      FlywayMigration.live
    ).orDie

  final def testLayer(gameFiles: String*): ULayer[ChutiEnvironment] = {
    import better.files.File
    import chuti.given

    def readGame(filename: String): Task[Game] = {
      val file = File(filename)
      ZIO.fromEither(file.contentAsString.fromJson[Game]).mapError(e => GameError(e))
    }

    val repositoryLayer = ZLayer.fromZIO {
      ZIO
        .foreachPar(gameFiles)(filename => readGame(filename))
        .map(games => InMemoryRepository.fromGames(games))
        .orDie
    }.flatten

    ZLayer
      .make[ChutiEnvironment](
        ConfigurationService.live,
        repositoryLayer,
        ZLayer.succeed(MockPostman),
        ZLayer.fromZIO(for {
          cache <- Cache
            .make[(String, TokenPurpose), Any, Nothing, User](100, 5.days, Lookup(_ => ZIO.succeed(chuti.god)))
        } yield TokenHolder.tempCache(cache)),
        OAuthStateStore.live(),
        llmServiceLayer,
        aiBotLayer,
        GameService.make(),
        ChatService.make(),
        ChutiAuthServer.live,
        oauthServiceLayer,
        ZLayer.fromZIO(ZIO.serviceWithZIO[ConfigurationService](_.appConfig).map(_.chuti.session)),
        FlywayMigration.live
      ).orDie
  }

}
