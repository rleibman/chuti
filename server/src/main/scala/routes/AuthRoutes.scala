package routes

import api.Chuti.Environment
import zhttp.http.*
import zio.ZIO

object AuthRoutes {

  val route: ZIO[Environment, Throwable, Http[Environment, Throwable, Request, Response]] = ???

}
