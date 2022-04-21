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

import api.config
import slick.basic.BasicBackend
import slick.jdbc.JdbcBackend.*
import zio.{UIO, ULayer, ZIO, ZLayer}

object MySQLDatabaseProvider {
  lazy private val privateDB = {
    println(">>>>>>>>>>>>>>>>>>>>>>>>>> This should only ever be seen once")
    Database.forConfig(config.live.configKey)
  }
  lazy val liveLayer: ULayer[DatabaseProvider] = ZLayer.succeed(new DatabaseProvider.Service {
    override def db: UIO[BasicBackend#DatabaseDef] = ZIO.succeed(privateDB)
  })

//  // this doesn't work as expected, what we should do instead is bubble up the zmanaged and use that in the
//  //  same way that we do the memoized chat and game layers
//  @deprecated
//  val liveManagedLayer: URLayer[Config with Logging, DatabaseProvider] =
//    ZLayer
//      .fromServicesManaged[Config.Service, Logger[String], Any, Nothing, DatabaseProvider.Service] {
//        (cfg, log) =>
//          ZManaged
//            .make(
//              log.info(">>>>>>>>>>>>>>>>>>>>>>>>>> This should only ever be seen once") *>
//                ZIO.effect { Database.forConfig(cfg.configKey) }
//            )(db => log.info("Closing the database") *> ZIO.effectTotal(db.close()))
//            .map(d =>
//              new DatabaseProvider.Service {
//                val db: UIO[BasicBackend#DatabaseDef] = ZIO.effectTotal(d)
//              }
//            ).tapError(e => ZManaged.fromEffect(log.error("Error opening database!", Fail(e))))
//            .orDie // No use going on if you can't connect to the database, is there?
//      }

}
