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

  object SessionProvider  {
    trait Session {
      def session: UIO[ChutiSession]
    }
    def live(a: ChutiSession): Session = { new Session {
      val session: UIO[ChutiSession] = UIO(a)
    }}
  }

  type RepositoryIO[E] = ZIO[DatabaseProvider with SessionProvider, RepositoryException, E]
}
