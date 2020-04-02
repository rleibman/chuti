/*
 * Copyright 2020 Roberto Leibman
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import api.ChutiSession
import slick.basic.BasicBackend
import zio.{Has, UIO, ZIO}
import zioslick.RepositoryException

package object dao {
  type DatabaseProvider = Has[DatabaseProvider.Service]

  object DatabaseProvider {

    trait Service {
      def db: UIO[BasicBackend#DatabaseDef]
    }
  }

  type SessionProvider = Has[SessionProvider.Session]

  object SessionProvider {
    trait Session {
      def session: ChutiSession
    }
    def live(a: ChutiSession): Session = {
      new Session {
        val session: ChutiSession = a
      }
    }
  }

  type RepositoryIO[E] = ZIO[DatabaseProvider with SessionProvider, RepositoryException, E]
}
