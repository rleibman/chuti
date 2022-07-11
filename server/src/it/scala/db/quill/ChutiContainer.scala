package db.quill

import api.config.Config
import com.dimafeng.testcontainers.MySQLContainer
import com.typesafe.config
import com.typesafe.config.ConfigFactory
import dao.RepositoryError
import io.getquill.context.ZioJdbc.DataSourceLayer
import zio.*
import zio.logging.Logging

import java.io
import java.sql.SQLException
import javax.sql.DataSource
import scala.io.Source

object ChutiContainer {

  type ChutiContainer = Has[ChutiContainer.Service]

  private case class Migrator(
    path: String
  ) {

    def migrate(): ZIO[Has[DataSource], RepositoryError, Unit] =
      (for {
        files <- ZIO {
          import scala.language.unsafeNulls
          new java.io.File(path).listFiles.sortBy(_.getName)
        }.orDie
        dataSource <- ZIO.service[DataSource]
        statements <- ZIO
          .foreach(files) {
            case file: io.File if file.getName.nn.endsWith("sql") =>
              ZIO(Source.fromFile(file))
                .bracket(f => ZIO.succeed(f.close())) { source =>
                  val str = source
                    .getLines()
                    .map(str => str.replaceAll("--.*", "").nn.trim.nn)
                    .mkString("\n")
                  ZIO(str.split(";\n").nn)
                }
            case _ => ZIO.fail(RepositoryError("File must be of either sql or JSON type."))
          }.map(
            _.flatten
              .map(_.nn.trim.nn)
              .filter(_.nonEmpty)
          ).catchSome(e => ZIO.fail(RepositoryError(e)))
        res <- {
          ZIO {
            val conn = dataSource.getConnection.nn
            val stmt = conn.createStatement().nn
            (conn, stmt)
          }.bracket { case (conn, stmt) =>
            ZIO.succeed {
              stmt.close().nn
              conn.close().nn
            }
          } { case (_, stmt) =>
            ZIO
              .foreach_(statements) { statement =>
                ZIO(stmt.executeUpdate(statement).nn)
              }.catchSome { case e: SQLException =>
                ZIO.fail(RepositoryError(e))
              }
          }
        }
      } yield res).mapError(RepositoryError.apply)

  }

  trait Service {

    def container: MySQLContainer

  }

  val containerLayer: ZLayer[Logging, RepositoryError, ChutiContainer] = (for {
    _ <- Logging.debug("Creating container")
    c <- ZIO {
      val c = MySQLContainer()
      c.container.start()
      c
    }
    config = getConfig(c).nn
    _ <- Logging.debug("Migrating container")
    _ <-
      (Migrator("server/src/main/sql").migrate()
        *> Migrator("server/src/it/sql").migrate())
        .provideLayer(DataSourceLayer.fromConfig(config.getConfig("chuti.db").nn))
        .mapError(RepositoryError.apply)
  } yield new Service {

    override def container: MySQLContainer = c

  }).mapError(RepositoryError.apply).toLayer

  private def getConfig(container: MySQLContainer): config.Config =
    ConfigFactory
      .parseString(s"""
      chuti.db.dataSourceClassName=com.mysql.cj.jdbc.MysqlDataSource
      chuti.db.dataSource.url="${container.container.getJdbcUrl}?logger=com.mysql.cj.log.Slf4JLogger&profileSQL=true&serverTimezone=UTC&useLegacyDatetimeCode=false"
      chuti.db.dataSource.user="${container.container.getUsername}"
      chuti.db.dataSource.password="${container.container.getPassword}"
      chuti.db.dataSource.cachePrepStmts=true
      chuti.db.dataSource.prepStmtCacheSize=250
      chuti.db.dataSource.prepStmtCacheSqlLimit=2048
      chuti.db.maximumPoolSize=10
    """).nn

  val configLayer: URLayer[ChutiContainer & Config, Config] = {
    (for {
      container  <- ZIO.service[ChutiContainer.Service].map(_.container)
      baseConfig <- ZIO.service[Config.Service].map(_.config)
    } yield {
      new api.config.Config.Service {
        override val config: com.typesafe.config.Config = getConfig(container).withFallback(baseConfig).nn
      }
    }).toLayer
  }

}
