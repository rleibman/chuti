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
import com.dimafeng.testcontainers.MariaDBContainer
import dao.RepositoryError
import zio.*

trait ChutiContainer {

  def container: MariaDBContainer

}

object ChutiContainer {

  val containerLayer: Layer[RepositoryError, ChutiContainer] = ZLayer.fromZIO(for {
    _ <- ZIO.logDebug("Creating container")
    c <- ZIO.succeed {
      val c = MariaDBContainer()
      c.container.start()
      c
    }
  } yield new ChutiContainer {

    override def container: MariaDBContainer = c

  })

  private def getConfig(container: MariaDBContainer): DatabaseConfig = {
    DatabaseConfig(
      dataSource = DataSourceConfig(
        driver = "org.mariadb.jdbc.Driver",
        url = container.container.getJdbcUrl.nn,
        user = container.container.getUsername.nn,
        password = container.container.getPassword.nn
      )
    )
  }

  val configLayer: ZLayer[ConfigurationService & ChutiContainer, ConfigurationError, ConfigurationService] =
    ZLayer.fromZIO {
      for {
        container  <- ZIO.serviceWith[ChutiContainer](_.container)
        baseConfig <- ZIO.serviceWithZIO[ConfigurationService](_.appConfig)
      } yield ConfigurationService.withConfig(
        baseConfig.copy(chuti = baseConfig.chuti.copy(db = getConfig(container)))
      )
    }

}
