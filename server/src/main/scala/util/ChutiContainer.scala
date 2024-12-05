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

package util

import api.*
import com.dimafeng.testcontainers.MySQLContainer
import com.typesafe.config
import com.typesafe.config.ConfigFactory
import dao.RepositoryError
import io.getquill.context.ZioJdbc.DataSourceLayer
import io.getquill.jdbczio.Quill
import zio.*
import zio.logging.*

import java.io
import java.sql.SQLException
import javax.sql.DataSource
import scala.io.{BufferedSource, Source}

trait ChutiContainer {

  def container: MySQLContainer

}

object ChutiContainer {

  private case class Migrator(
    path: String
  ) {

    def migrate(): ZIO[DataSource, RepositoryError, Unit] = {
      for {
        files <- ZIO.succeed {
          import scala.language.unsafeNulls
          new java.io.File(path).listFiles.sortBy(_.getName)
        }
        dataSource <- ZIO.service[DataSource]
        statements <- ZIO
          .foreach(files) {
            case file: io.File if file.getName.nn.endsWith("sql") =>
              ZIO.acquireReleaseWith(ZIO.attempt(Source.fromFile(file)))(f => ZIO.attempt(f.close()).orDie) { source =>
                val str = source
                  .getLines()
                  .map(str => str.replaceAll("--.*", "").nn.trim.nn)
                  .mkString("\n")
                ZIO.attempt(str.split(";\n").nn)
              }
            case _ => ZIO.fail(RepositoryError("File must be of either sql or JSON type."))
          }.mapBoth(
            RepositoryError.apply,
            _.flatten
              .map(_.nn.trim.nn)
              .filter(_.nonEmpty)
          ).catchSome(e => ZIO.fail(RepositoryError(e)))
        res <- {
          ZIO.acquireReleaseWith {
            ZIO.attempt {
              val conn = dataSource.getConnection.nn
              val stmt = conn.createStatement().nn
              (conn, stmt)
            }
          } { case (conn, stmt) =>
            ZIO.succeed {
              stmt.close().nn
              conn.close().nn
            }
          } { case (_, stmt) =>
            ZIO
              .foreach(statements) { statement =>
                ZIO.attempt(stmt.executeUpdate(statement).nn)
              }.catchSome { case e: SQLException =>
                ZIO.fail(RepositoryError(e))
              }
          }
        }.unit.mapError(RepositoryError.apply)
      } yield res
    }

  }

  val containerLayer: Layer[RepositoryError, ChutiContainer] = ZLayer.fromZIO((for {
    _ <- ZIO.logDebug("Creating container")
    c <- ZIO.succeed {
      val c = MySQLContainer()
      c.container.start()
      c
    }
    _ <- ZIO.logDebug("Migrating container")
    _ <-
      (Migrator("server/src/main/sql").migrate()
        *> Migrator("server/src/it/sql").migrate())
        .provideLayer(Quill.DataSource.fromDataSource(getConfig(c).dataSource))
        .mapError(RepositoryError.apply)
  } yield new ChutiContainer {

    override def container: MySQLContainer = c

  }).mapError(RepositoryError.apply))

  private def getConfig(container: MySQLContainer) = {
    DbConfig(
      driver = "com.mysql.cj.jdbc.Driver",
      url = container.container.getJdbcUrl.nn,
      user = container.container.getUsername.nn,
      password = container.container.getPassword.nn
    )
  }

  val configLayer: ZLayer[ConfigurationService & ChutiContainer, ConfigurationError, ConfigurationService] = ZLayer.fromZIO {
    for {
      container  <- ZIO.service[ChutiContainer].map(_.container)
      baseConfig <- ZIO.serviceWithZIO[ConfigurationService](_.appConfig)
    } yield ConfigurationService.withConfig(baseConfig.copy(chuti = baseConfig.chuti.copy(db = getConfig(container))))
  }

}
