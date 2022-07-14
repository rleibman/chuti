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

import better.files.File
import com.typesafe.config.*
import zio.IO
import zio.config.magnolia.*
import zio.config.typesafe.TypesafeConfigSource
import zio.config.*

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

  type Config = Config.Service

  object Config {

    private val chutiConfigDescriptor: ConfigDescriptor[ChutiConfig] = descriptor[ChutiConfig]
    val chutiConfig: IO[ReadError[String], ChutiConfig] = read(
      chutiConfigDescriptor from TypesafeConfigSource.fromResourcePath.at(PropertyTreePath.$("chuti"))
    )

    trait Service {

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

  }

  lazy val live: Config.Service = new Config.Service {}

}
