package db.quill

import api.config.Config
import com.dimafeng.testcontainers.MySQLContainer
import com.typesafe.config.ConfigFactory
import dao.RepositoryError
import io.getquill.context.ZioJdbc.DataSourceLayer
import zio.{Has, IO, Ref, UIO, ULayer, URLayer, ZIO}

import java.io
import java.sql.SQLException
import javax.sql.DataSource
import scala.io.Source

object ChutiContainer {

  type ChutiContainer = Has[ChutiContainer.Service]

  private case class Migrator(
    path: String
  ) {

    def migrate(): ZIO[Has[DataSource], SQLException, Unit] =
      for {
        files <- ZIO {
          new java.io.File(path).listFiles.sortBy(_.getName)
        }.orDie
        dataSource <- ZIO.service[DataSource]
        res <- {
          val statements = files
            .flatMap {
              case file: io.File if file.getName.endsWith("sql") =>
                val source = Source.fromFile(file)
                val str = source
                  .getLines().map(str => str.replaceAll("--.*", "").trim).mkString("\n")
                val statements = str.split(";\n")
                source.close
                statements
              case _ => throw new IllegalArgumentException("File must be of either sql or JSON type.")
            }
            .map(_.trim)
            .filter(_.nonEmpty)
          val conn = dataSource.getConnection
          val stmt = conn.createStatement()
          try {
            statements.foreach { statement =>
              stmt.executeUpdate(statement)
            }
            ZIO.succeed(())
          } catch {
            case e: SQLException => ZIO.fail(e)
          } finally {
            stmt.close()
            conn.close()
          }
        }
      } yield res

  }

  trait Service {

    def containerRef: Ref[Option[MySQLContainer]]
    private val useContainerFromRef: IO[RepositoryError, MySQLContainer] =
      for {
        configOption <- containerRef.get
        config       <- ZIO.fromOption(configOption).orElseFail(RepositoryError("The config isn't loaded"))
      } yield config

    private val startContainer: IO[RepositoryError, MySQLContainer] =
      for {
        c <- UIO {
          val c = MySQLContainer()
          c.container.start()
          c
        }
        config = getConfig(c)
        _ <-
          (Migrator("server/src/main/sql").migrate()
            *> Migrator("server/src/it/sql").migrate())
            .provideLayer(DataSourceLayer.fromConfig(config.getConfig("chuti.db")))
            .mapError(RepositoryError.apply)
        _ <- containerRef.update(_ => Some(c))
      } yield c

    val container: IO[RepositoryError, MySQLContainer] = useContainerFromRef.orElse(startContainer)

  }

  val containerLayer: ULayer[ChutiContainer] = (for {
    ref <-
      Ref.make[Option[MySQLContainer]](None)
  } yield new Service {

    override def containerRef: Ref[Option[MySQLContainer]] = ref

  }).toLayer

  private def getConfig(container: MySQLContainer) =
    ConfigFactory.parseString(s"""
      chuti.db.dataSourceClassName=com.mysql.cj.jdbc.MysqlDataSource
      chuti.db.dataSource.url="${container.container.getJdbcUrl}?logger=com.mysql.cj.log.Slf4JLogger&profileSQL=true"
      chuti.db.dataSource.user="${container.container.getUsername}"
      chuti.db.dataSource.password="${container.container.getPassword}"
      chuti.db.dataSource.cachePrepStmts=true
      chuti.db.dataSource.prepStmtCacheSize=250
      chuti.db.dataSource.prepStmtCacheSqlLimit=2048
      chuti.db.maximumPoolSize=10
    """)

  val configLayer: URLayer[ChutiContainer & Config, Config] = {
    (for {
      containerService <- ZIO.service[ChutiContainer.Service]
      baseConfig       <- ZIO.service[Config.Service].map(_.config)
      container        <- containerService.container
    } yield {
      new api.config.Config.Service {
        override val config: com.typesafe.config.Config = getConfig(container).withFallback(baseConfig)
      }
    }).orDie.toLayer
  }

}
