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
import zio.*

import scala.jdk.CollectionConverters.*

/** Standalone utility to repair Flyway schema history
  *
  * Run this when you get validation errors about migration description mismatches. This updates the
  * flyway_schema_history table to match the current migration files.
  *
  * Usage: sbt "project server" "runMain dmscreen.FlywayRepair"
  */
object FlywayRepair extends ZIOAppDefault {

  private def createFlyway(config: AppConfig): UIO[Flyway] =
    ZIO.succeed {
      val dataSource = config.dataSource
      val flywayConfig = config.chuti.flyway

      Flyway
        .configure()
        .dataSource(dataSource)
        .locations(flywayConfig.locations*)
        .cleanDisabled(flywayConfig.cleanDisabled)
        .baselineOnMigrate(flywayConfig.baselineOnMigrate)
        .baselineVersion(flywayConfig.baselineVersion)
        .baselineDescription(flywayConfig.baselineDescription)
        .load()
    }

  override def run: ZIO[Any, Any, Any] =
    (for {
      _      <- ZIO.logInfo("Starting Flyway repair...")
      config <- ZIO.serviceWithZIO[ConfigurationService](_.appConfig)
      flyway <- createFlyway(config)
      _      <- ZIO.logInfo("Running Flyway repair to fix schema history mismatches...")
      result <- ZIO.attempt(flyway.repair())
      _ <- ZIO.logInfo(
        s"Flyway repair completed successfully!\n" +
          s"  Removed failed migrations: ${result.migrationsRemoved.size()}\n" +
          s"  Deleted missing migrations: ${result.migrationsDeleted.size()}\n" +
          s"  Aligned applied migrations: ${result.migrationsAligned.size()}\n" +
          s"\nYou can now start the application normally."
      )
      _ <- ZIO.logInfo("\nRepair Details:")
      _ <- ZIO.foreach(result.repairActions.asScala) { action =>
        ZIO.logInfo(s"  - $action")
      }
    } yield ())
      .tapError(error =>
        ZIO.logError(s"Flyway repair failed: ${error.getMessage}") *>
          ZIO.logErrorCause("Repair error details:", Cause.fail(error))
      )
      .provide(ConfigurationService.live)

}
