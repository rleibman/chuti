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

package chuti.api

import ai.{AIConfig, OllamaConfig}
import auth.oauth.OAuthProviderConfig
import auth.{AuthConfig, SecretKey}
import chuti.GameError
import com.typesafe.config.{Config as TypesafeConfig, ConfigFactory}
import com.zaxxer.hikari.*
import zio.*
import zio.config.magnolia.DeriveConfig
import zio.config.typesafe.*

import java.io.File

case class ConfigurationError(
  override val msg:   String = "",
  override val cause: Option[Throwable] = None,
) extends GameError(msg, cause)

case class DataSourceConfig(
  driver:   String,
  url:      String,
  user:     String,
  password: String,
  /** Keep `minimumIdle` WELL below `maximumPoolSize`. HikariCP does not reject a `minimumIdle` above the maximum — it
    * quietly clamps it *to* the maximum, and a pool never shrinks below its minimum, so it would then hold every
    * connection open for ever. (That is exactly what happened in meal-o-rama and dmscreen, which both had 1000 here.)
    */
  maximumPoolSize: Int = 10,
  minimumIdle:     Int = 2,
  /** How long a request waits for a free connection before failing. Was five minutes, which is not a timeout — it is a
    * hang. If the pool is exhausted for thirty seconds, something is wrong and the caller should hear about it.
    */
  connectionTimeoutSeconds: Long = 30,
  /** How long a connection may sit idle before the pool closes it. */
  idleTimeoutMinutes: Long = 5,
  /** Retire connections before the database does: MariaDB drops idle connections at `wait_timeout`, and a connection
    * the pool still believes in but the server has already closed fails the next query that borrows it.
    */
  maxLifetimeMinutes: Long = 30,
  /** Logs a stack trace for any connection held longer than this, which is how you find a real leak. Off (0) by
    * default: it is a diagnostic, not something to run permanently.
    */
  leakDetectionThresholdSeconds: Long = 0,
)

case class DatabaseConfig(
  dataSource: DataSourceConfig,
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
  bccEmail:    String = "roberto@leibman.net",
)

case class HttpConfig(
  hostName:         String,
  port:             Int,
  staticContentDir: String,
)

case class RateLimitConfig(
  enabled:               Boolean = true,
  maxRequests:           Int = 100,
  windowDurationSeconds: Long = 60,
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
  baselineDescription: String = "Existing database baseline (V001-V016 already applied)",
)

case class AnalyticsConfig(
  retentionDays:        Int = 7,
  cleanupIntervalHours: Int = 24,
)

case class ChutiConfig(
  db:        DatabaseConfig,
  smtp:      SmtpConfig,
  http:      HttpConfig,
  session:   AuthConfig,
  rateLimit: RateLimitConfig,
  flyway:    FlywayConfig,
  oauth:     Map[String, OAuthProviderConfig],
  analytics: AnalyticsConfig,
  ai:        Option[AIConfig] = None,
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
  chuti: ChutiConfig,
) {

  /** The shared connection pool.
    *
    * This being a `lazy val` on an `AppConfig` that is captured once at layer construction is load-bearing: both
    * `QuillRepository` and `FlywayMigration` force it, and each would otherwise stand up its own pool. See
    * `ConfigurationService.live`.
    */
  lazy val dataSource: HikariDataSource = {
    val ds = chuti.db.dataSource

    val config = HikariConfig()
    config.setPoolName("chuti") // so the pool names itself in the logs, and in a `SHOW PROCESSLIST` hunt
    config.setDriverClassName(ds.driver)
    config.setJdbcUrl(ds.url)
    config.setUsername(ds.user)
    config.setPassword(ds.password)
    config.setMaximumPoolSize(ds.maximumPoolSize)
    config.setMinimumIdle(ds.minimumIdle)
    config.setConnectionTimeout(ds.connectionTimeoutSeconds * 1000)
    config.setIdleTimeout(ds.idleTimeoutMinutes * 60 * 1000)
    config.setMaxLifetime(ds.maxLifetimeMinutes * 60 * 1000)
    if (ds.leakDetectionThresholdSeconds > 0)
      config.setLeakDetectionThreshold(ds.leakDetectionThresholdSeconds * 1000)

    HikariDataSource(config)
  }

}

trait ConfigurationService {

  def appConfig: IO[ConfigurationError, AppConfig]

}

object ConfigurationService {

  // There used to be a `withConfig(typesafeConfig: TypesafeConfig)` here too. It had the same defect as `live` below,
  // and no callers, so it is gone rather than left lying around as a way to reintroduce the leak. Read the config once
  // and pass the AppConfig to `withConfig`.

  /** The only way to build one by hand. Every caller shares the single `AppConfig` — and so the single pool. */
  def withConfig(withMe: AppConfig): ConfigurationService =
    new ConfigurationService {

      override val appConfig: IO[ConfigurationError, AppConfig] = ZIO.succeed(withMe)

    }

  /** Reads the configuration ONCE, at layer construction, and hands every caller the same `AppConfig` instance.
    *
    * This used to be `ZLayer.succeed(new ConfigurationService { lazy val appConfig = AppConfig.read(...) })`. A
    * `lazy val` of type `IO[_, AppConfig]` memoizes the *effect*, not its result — so every time that effect was run it
    * re-read the config file and produced a NEW `AppConfig`.
    *
    * That matters because `AppConfig.dataSource` is a `lazy val` holding a HikariCP pool: a new `AppConfig` means a new
    * pool. Both `QuillRepository` and `FlywayMigration` force it, so the server stood up two pools and closed neither.
    */
  val live: ULayer[ConfigurationService] = ZLayer.fromZIO {
    import scala.language.unsafeNulls
    val confFileName = java.lang.System.getProperty("application.conf", "./src/main/resources/application.conf")
    val confFile = File(confFileName)

    AppConfig
      .read(
        ConfigFactory
          .parseFile(confFile)
          .withFallback(ConfigFactory.load())
          .resolve(),
      )
      .map(withConfig)
  }

  def typedConfig: ZIO[ConfigurationService, ConfigurationError, AppConfig] = ZIO.environmentWithZIO(_.get.appConfig)

}
