package db.quill

import api.config.Config
import com.dimafeng.testcontainers.MySQLContainer
import com.typesafe.config
import com.typesafe.config.ConfigFactory
import dao.RepositoryError
import io.getquill.context.ZioJdbc.DataSourceLayer
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

  val containerLayer: ZLayer[Any, RepositoryError, ChutiContainer] = ZLayer.fromZIO((for {
    _ <- ZIO.logDebug("Creating container")
    c <- ZIO.succeed {
      val c = MySQLContainer()
      c.container.start()
      c
    }
    config = getConfig(c).nn
    _ <- ZIO.logDebug("Migrating container")
    _ <-
      (Migrator("server/src/main/sql").migrate()
        *> Migrator("server/src/it/sql").migrate())
        .provideLayer(DataSourceLayer.fromConfig(config.getConfig("chuti.db").nn))
        .mapError(RepositoryError.apply)
  } yield new ChutiContainer {

    override def container: MySQLContainer = c

  }).mapError(RepositoryError.apply))

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

  val configLayer: URLayer[ChutiContainer & Config, Config] = ZLayer.fromZIO {
    for {
      container  <- ZIO.service[ChutiContainer].map(_.container)
      baseConfig <- ZIO.service[Config.Service].map(_.config)
    } yield {
      new api.config.Config.Service {
        override val config: com.typesafe.config.Config = getConfig(container).withFallback(baseConfig).nn
      }
    }
  }

}
