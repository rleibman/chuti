package routes

import api.Chuti.Environment
import zhttp.http.{Http, Request, Response}
import zio.ZIO

object CRUDRoutes {

  val route: ZIO[Environment, Throwable, Http[Environment, Throwable, Request, Response]] = ???

}
