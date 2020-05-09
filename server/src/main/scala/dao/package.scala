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

import api.ChutiSession
import slick.basic.BasicBackend
import zio.logging.Logging
import zio.{Has, Layer, UIO, ZIO, ZLayer}
import zioslick.RepositoryException

package object dao {
  type Repository = Has[Repository.Service]

  type DatabaseProvider = Has[DatabaseProvider.Service]

  type SessionProvider = Has[SessionProvider.Session]

  type RepositoryIO[E] =
    ZIO[DatabaseProvider with SessionProvider with Logging, RepositoryException, E]
}
