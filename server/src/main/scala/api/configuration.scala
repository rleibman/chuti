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

import auth.oauth.OAuthProviderConfig
import auth.{AuthConfig, SecretKey}
import chuti.GameError
import com.typesafe.config.{ConfigFactory, Config as TypesafeConfig}
import com.zaxxer.hikari.*
import zio.*
import zio.config.magnolia.DeriveConfig
import zio.config.typesafe.*

import java.io.File

case class ConfigurationError(
  override val msg:   String = "",
  override val cause: Option[Throwable] = None
) extends GameError(msg, cause)

case class DataSourceConfig(
  driver:                String,
  url:                   String,
  user:                  String,
  password:              String,
  maximumPoolSize:       Int = 20,
  minimumIdle:           Int = 1000,
  connectionTimeoutMins: Long = 5
) {

  def createDataSource: HikariDataSource = {
    val config = new HikariConfig()
    config.setDriverClassName(driver)
    config.setJdbcUrl(url)
    config.setUsername(user)
    config.setPassword(password)
    config.setMaximumPoolSize(maximumPoolSize)
    config.setMinimumIdle(minimumIdle)
    config.setConnectionTimeout(connectionTimeoutMins * 60 * 1000)
    new HikariDataSource(config)
  }

}

case class DatabaseConfig(
  dataSource: DataSourceConfig
) {}

case class SmtpConfig(
  host:        String,
  auth:        Boolean = false,
  port:        Int = 25,
  user:        String = "",
  password:    String = "",
  startTTLS:   Boolean = false,
  webHostname: String = "www.chuti.fun",
  fromEmail:   String = "administrator@chuti.fun",
  fromName:    String = "Chuti Administrator",
  bccEmail:    String = "roberto@leibman.net"
)

case class HttpConfig(
  hostName:         String,
  port:             Int,
  staticContentDir: String
)

case class RateLimitConfig(
  enabled:               Boolean = true,
  maxRequests:           Int = 100,
  windowDurationSeconds: Long = 60
) {

  def windowDuration: scala.concurrent.duration.Duration =
    scala.concurrent.duration.Duration(windowDurationSeconds, scala.concurrent.duration.SECONDS)

}

case class FlywayConfig(
  locations:           List[String] = List("classpath:sql"),
  enabled:             Boolean = true,
  cleanDisabled:       Boolean = true,
  validateOnMigrate:   Boolean = true,
  mixed:               Boolean = false,
  target:              String = "",
  baselineOnMigrate:   Boolean = true,
  baselineVersion:     String = "016",
  baselineDescription: String = "Existing database baseline (V001-V016 already applied)"
)

case class AnalyticsConfig(
  retentionDays:        Int = 7,
  cleanupIntervalHours: Int = 24
)

case class ChutiConfig(
  db:        DatabaseConfig,
  smtp:      SmtpConfig,
  http:      HttpConfig,
  session:   AuthConfig,
  rateLimit: RateLimitConfig,
  flyway:    FlywayConfig,
  oauth:     Map[String, OAuthProviderConfig],
  analytics: AnalyticsConfig
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
) {

  lazy val dataSource: HikariDataSource = {
    chuti.db.dataSource.createDataSource
  }

}

trait ConfigurationService {

  def appConfig: IO[ConfigurationError, AppConfig]

}

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
