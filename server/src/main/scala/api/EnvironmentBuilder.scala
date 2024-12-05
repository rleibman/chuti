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

import api.auth.Auth
import api.auth.Auth.{SessionStorage, SessionTransport}
import api.routes.AuthRoutes
import api.token.{TokenHolder, TokenPurpose}
import chat.*
import chuti.{Game, GameError, User}
import dao.*
import dao.quill.QuillRepository
import game.*
import mail.*
import util.{ChutiContainer, MockPostman}
import zio.*
import zio.cache.{Cache, Lookup}
import zio.json.*

import java.util.Locale

type ChutiEnvironment = ConfigurationService & Repository & Postman & TokenHolder &
  SessionStorage[
    ChutiSession,
    String
  ] & SessionTransport[ChutiSession] & AuthRoutes.OpsService

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

  lazy private val postmanLayer: ZLayer[ConfigurationService, GameError, Postman] = ZLayer.fromZIO(for {
    configService <- ZIO.service[ConfigurationService]
    appConfig     <- configService.appConfig
  } yield CourierPostman.live(appConfig.chuti.smtpConfig))

  val repoLayer: ZLayer[ConfigurationService, ConfigurationError, Repository] = QuillRepository.uncached >>> Repository.cached

  val live: Layer[GameError, ChutiEnvironment] = ZLayer.make[ChutiEnvironment](
    ConfigurationService.live,
    repoLayer,
    postmanLayer,
    TokenHolder.liveLayer,
    Auth.SessionStorage.tokenEncripted[ChutiSession],
    Auth.SessionTransport.cookieSessionTransport[ChutiSession],
    ZLayer.fromZIO(ZIO.service[Repository].map(_.userOperations))
  )

  val withContainer: Layer[GameError, ChutiEnvironment] = {

    ZLayer.make[ChutiEnvironment](
      ChutiContainer.containerLayer,
      ConfigurationService.live >>> ChutiContainer.configLayer,
      repoLayer,
      postmanLayer,
      TokenHolder.liveLayer,
      Auth.SessionStorage.tokenEncripted[ChutiSession],
      Auth.SessionTransport.cookieSessionTransport[ChutiSession],
      ZLayer.fromZIO(ZIO.service[Repository].map(_.userOperations))
    )
  }

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

    ZLayer.make[ChutiEnvironment](
      ConfigurationService.live,
      repositoryLayer,
      ZLayer.succeed(new MockPostman),
      ZLayer.fromZIO(for {
        cache <- Cache.make[(String, TokenPurpose), Any, Nothing, User](100, 5.days, Lookup(_ => ZIO.succeed(chuti.god)))
      } yield TokenHolder.tempCache(cache)),
      Auth.SessionStorage.tokenEncripted[ChutiSession],
      Auth.SessionTransport.cookieSessionTransport[ChutiSession],
      ZLayer.fromZIO(ZIO.service[Repository].map(_.userOperations))
    )
  }

}
