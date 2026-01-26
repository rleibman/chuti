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

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult
import zio.*

import javax.sql.DataSource

/** Flyway migration service for database schema management
  */
trait FlywayMigration {

  /** Run migrations and return the result
    */
  def migrate: Task[MigrateResult]

  /** Validate migrations without running them
    */
  def validate: Task[Unit]

  /** Get information about the current migration status
    */
  def info: Task[String]

}

object FlywayMigration {

  /** Live implementation of FlywayMigration service
    */
  val live: ZLayer[ConfigurationService, Nothing, FlywayMigration] =
    ZLayer.fromZIO {
      for {
        config    <- ZIO.serviceWithZIO[ConfigurationService](_.appConfig).orDie
        flyway    <- createFlyway(config)
        migration <- ZIO.succeed(FlywayMigrationLive(flyway, config.chuti.flyway))
      } yield migration
    }

  private def createFlyway(config: AppConfig): UIO[Flyway] =
    ZIO.succeed {
      val dataSource = config.dataSource
      val flywayConfig = config.chuti.flyway

      val baseConfig = Flyway
        .configure()
        .dataSource(dataSource)
        .locations(flywayConfig.locations*)
        .cleanDisabled(flywayConfig.cleanDisabled)
        .validateOnMigrate(flywayConfig.validateOnMigrate)
        .mixed(flywayConfig.mixed)
        .baselineOnMigrate(flywayConfig.baselineOnMigrate)
        .baselineVersion(flywayConfig.baselineVersion)
        .baselineDescription(flywayConfig.baselineDescription)

      // Only set target if it's not empty
      val finalConfig = if (flywayConfig.target.nonEmpty) {
        baseConfig.target(flywayConfig.target)
      } else {
        baseConfig
      }

      finalConfig.load()
    }

  private case class FlywayMigrationLive(
    flyway: Flyway,
    config: FlywayConfig
  ) extends FlywayMigration {

    override def migrate: Task[MigrateResult] =
      ZIO.attempt {
        if (config.enabled) {
          flyway.migrate()
        } else {
          // Return empty result if migrations are disabled
          null
        }
      }

    override def validate: Task[Unit] = ZIO.attempt(flyway.validate()).unit

    override def info: Task[String] =
      ZIO.attempt {
        val infoResult = flyway.info()
        val migrations = infoResult.all()

        val builder = new StringBuilder()
        builder.append(s"Flyway Info:\n")
        builder.append(s"  Schema Version: ${infoResult.current()}\n")
        builder.append(s"  Migrations:\n")

        migrations.foreach { migration =>
          builder.append(
            s"    - ${migration.getVersion}: ${migration.getDescription} (${migration.getState})\n"
          )
        }

        builder.toString()
      }

  }

  /** Run migrations on application startup
    *
    * @return
    *   ZIO that logs migration results
    */
  def runMigrations: ZIO[FlywayMigration, Nothing, Unit] =
    ZIO.serviceWithZIO[FlywayMigration] { migration =>
      (for {
        _      <- ZIO.logInfo("Starting Flyway database migrations...")
        result <- migration.migrate
        _ <-
          if (result != null) {
            ZIO.logInfo(
              s"Flyway migrations completed successfully. " +
                s"Migrations executed: ${result.migrationsExecuted}, " +
                s"Target schema version: ${result.targetSchemaVersion}"
            )
          } else {
            ZIO.logInfo("Flyway migrations are disabled")
          }
        info <- migration.info
        _    <- ZIO.logInfo(info)
      } yield ())
        .catchAll { error =>
          ZIO.logError(s"Flyway migration failed: ${error.getMessage}") *>
            ZIO.logErrorCause(s"Migration error details:", Cause.fail(error))
        }
    }

}
