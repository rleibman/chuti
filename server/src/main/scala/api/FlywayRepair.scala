/*
 * Copyright (c) 2025 Roberto Leibman - All Rights Reserved
 *
 * This source code is protected under international copyright law.  All rights
 * reserved and protected by the copyright holders.
 * This file is confidential and only available to authorized individuals with the
 * permission of the copyright holders.  If you encounter this file and do not have
 * permission, please contact the copyright holders and delete this file.
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
