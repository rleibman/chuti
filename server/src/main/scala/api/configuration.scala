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

import api.auth.Auth.{SecretKey, SessionConfig}
import chuti.GameError
import com.typesafe.config.{Config as TypesafeConfig, ConfigFactory}
import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import zio.*
import zio.config.magnolia.DeriveConfig
import zio.config.typesafe.TypesafeConfigProvider
import zio.nio.file.Path

import java.io.File

case class ConfigurationError(
  override val message: String = "",
  override val cause:   Option[Throwable] = None
) extends GameError(message, cause)

case class DbConfig(
  driver:                String,
  url:                   String,
  user:                  String,
  password:              String,
  cachePrepStmts:        Boolean = true,
  prepStmtCacheSize:     Int = 250,
  prepStmtCacheSqlLimit: Int = 2048,
  maximumPoolSize:       Int = 20,
  minimumIdle:           Int = 1000,
  connectionTimeoutMins: Long = 5
) {

  // SUPER IMPORTANT!!! this has to be a val, not a def!
  lazy val dataSource: HikariDataSource = {
    val dsConfig = HikariConfig()
    dsConfig.setDriverClassName(driver)
    dsConfig.setJdbcUrl(url)
    dsConfig.setUsername(user)
    dsConfig.setPassword(password)
    dsConfig.setMaximumPoolSize(maximumPoolSize)
    dsConfig.setMinimumIdle(minimumIdle)
    dsConfig.setAutoCommit(true)
    dsConfig.setConnectionTimeout(connectionTimeoutMins * 60 * 1000)

    HikariDataSource(dsConfig)
  }

}

case class SmtpConfig(
  localhost:   String,
  host:        String,
  auth:        Boolean = false,
  port:        Int = 25,
  user:        String = "",
  password:    String = "",
  startTTLS:   Boolean = false,
  webHostname: String = "localhost"
)

case class HttpConfig(
  hostName:         String,
  port:             Int = 8079,
  staticContentDir: String = "/Volumes/Personal/projects/chuti/debugDist"
)

case class ChutiConfig(
  db:            DbConfig,
  smtpConfig:    SmtpConfig,
  httpConfig:    HttpConfig,
  sessionConfig: SessionConfig
)

object AppConfig {

  def read(typesafeConfig: TypesafeConfig): UIO[AppConfig] = {
    given DeriveConfig[zio.nio.file.Path] = DeriveConfig[String].map(string => zio.nio.file.Path(string))
    given DeriveConfig[SecretKey] = DeriveConfig[String].map(SecretKey.apply)

    TypesafeConfigProvider
      .fromTypesafeConfig(typesafeConfig)
      .load(DeriveConfig.derived[AppConfig].desc)
      .orDie
  }

}

case class AppConfig(
  chuti: ChutiConfig
)

trait ConfigurationService {

  def appConfig: IO[ConfigurationError, AppConfig]

}

// utility to read config
object ConfigurationService {

  def withConfig(typesafeConfig: TypesafeConfig): ConfigurationService =
    new ConfigurationService {

      lazy override val appConfig: IO[ConfigurationError, AppConfig] = {
        AppConfig.read(typesafeConfig)
      }

    }

  def withConfig(withMe: AppConfig): ConfigurationService =
    new ConfigurationService {

      lazy override val appConfig: IO[ConfigurationError, AppConfig] = ZIO.succeed(withMe)

    }

  val live: ULayer[ConfigurationService] = ZLayer.succeed(new ConfigurationService {

    lazy override val appConfig: IO[ConfigurationError, AppConfig] = {
      import scala.language.unsafeNulls
      val confFileName = java.lang.System.getProperty("application.conf", "./src/main/resources/application.conf")

      val confFile = new File(confFileName)
      AppConfig.read(
        ConfigFactory
          .parseFile(confFile)
          .withFallback(ConfigFactory.load())
          .resolve()
      )
    }

  })

  def typedConfig: ZIO[ConfigurationService, ConfigurationError, AppConfig] = ZIO.environmentWithZIO(_.get.appConfig)

}
