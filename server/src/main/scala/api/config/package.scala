/*
 * Copyright (c) 2024 Roberto Leibman
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package api

import better.files.File
import com.typesafe.config.*
import zio.{Config, IO}
import zio.config.magnolia.*
import zio.config.typesafe.TypesafeConfigProvider
import zio.config.*
import zio.schema.codec.DecodeError.ReadError

/** A trait to keep app configuration
  */
//TODO use zio config instead
package object config {

  case class DataSourceConfig(
    url:                   String,
    user:                  String,
    password:              String,
    cachePrepStmts:        Boolean = true,
    prepStmtCacheSize:     Int = 250,
    prepStmtCacheSqlLimit: Int = 2048
  )

  case class DbConfig(
    dataSourceName:   String = "com.mysql.cj.jdbc.MysqlDataSource",
    maximumPoolSize:  Int = 10,
    dataSourceConfig: DataSourceConfig
  )

  case class SmtpConfig(
    localhost: String,
    host:      String,
    auth:      Boolean = false,
    port:      Int = 25
  )

  case class HttpConfig(
    hostName:         String,
    port:             Int = 8079,
    staticContentDir: String = "/Volumes/Personal/projects/chuti/debugDist"
  )

  case class ChutiConfig(
    db:         DbConfig,
    smtpConfig: SmtpConfig,
    httpConfig: HttpConfig
  )

  trait ConfigurationService {

    val configKey = "chuti"
    lazy val config: com.typesafe.config.Config = {
      val confFileName =
        System.getProperty("application.conf", "./src/main/resources/application.conf").nn
      val confFile = File(confFileName)
      val config = ConfigFactory
        .parseFile(confFile.toJava).nn
        .withFallback(ConfigFactory.load()).nn
      config
    }

  }

  object ConfigurationService {

    val chutiConfig: IO[Config.Error, ChutiConfig] = {
      read(deriveConfig[ChutiConfig] from TypesafeConfigProvider.fromResourcePath().nested("chuti"))
    }

  }

  lazy val live: ConfigurationService = new ConfigurationService {}

}
