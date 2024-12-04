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
import api.token.TokenHolder
import chat.*
import chuti.GameException
import dao.*
import dao.quill.QuillRepository
import game.*
import io.circe.{Codec, Decoder, DecodingFailure, Encoder}
import mail.*
import zio.*
import zio.logging.*
import zio.logging.backend.SLF4J
import io.circe.generic.auto.*
import routes.AuthRoutes

import java.util.Locale

type ChutiEnvironment = ConfigurationService & Repository & Postman & TokenHolder &
  SessionStorage[
    ChutiSession,
    String
  ] & SessionTransport[ChutiSession] & AuthRoutes.OpsService

given Codec[Locale] =
  Codec.from(
    Decoder.decodeString.map(s =>
      Locale.forLanguageTag(s) match {
        case l: Locale => l
        case null => throw DecodingFailure(s"invalid locale $s", List.empty)
      }
    ),
    Encoder.encodeString.contramap(_.toString)
  )

object EnvironmentBuilder {

  lazy private val postmanLayer: ZLayer[ConfigurationService, GameException, Postman] = ZLayer.fromZIO(for {
    configService <- ZIO.service[ConfigurationService]
    appConfig     <- configService.appConfig
  } yield CourierPostman.live(appConfig.chuti.smtpConfig))

  val repoLayer: ZLayer[ConfigurationService, ConfigurationError, Repository] = QuillRepository.uncached >>> Repository.cached

  val live: ZLayer[Any, GameException, ChutiEnvironment] = ZLayer.make[ChutiEnvironment](
    ConfigurationService.live,
    repoLayer,
    postmanLayer,
    TokenHolder.liveLayer,
    Auth.SessionStorage.tokenEncripted[ChutiSession],
    Auth.SessionTransport.cookieSessionTransport[ChutiSession],
    ZLayer.fromZIO(ZIO.service[Repository].map(_.userOperations))
  )

}
