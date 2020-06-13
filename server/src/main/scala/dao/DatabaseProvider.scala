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

package dao

import com.typesafe.config.Config
import slick.basic.BasicBackend
import slick.jdbc.JdbcBackend
import zio.{UIO, ZIO, ZLayer, _}

object DatabaseProvider {
  trait Service {
    def db: UIO[BasicBackend#DatabaseDef]
  }

  val live: ZLayer[Has[Config] with Has[JdbcBackend], Throwable, DatabaseProvider] =
    ZLayer.fromServicesManaged[Config, JdbcBackend, Any, Throwable, Service] {
      (cfg: Config, backend: JdbcBackend) =>
        ZManaged
          .make(ZIO.effect(backend.Database.forConfig("", cfg)))(db => ZIO.effectTotal(db.close()))
          .map(d =>
            new DatabaseProvider.Service {
              val db: UIO[BasicBackend#DatabaseDef] = ZIO.effectTotal(d)
            }
          )
    }
}
