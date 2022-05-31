package api

import io.circe.{Decoder, Encoder}
import io.circe.generic.auto.*
import io.circe.parser.*
import io.circe.syntax.*
import io.netty.handler.codec.http.{DefaultFullHttpRequest, HttpRequest}
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import zhttp.http.*
import zhttp.service.Server
import zio.*
import zio.clock.*

import java.net.InetAddress

@deprecated("bad, don't use")
object AuthenticationServer extends App {

  import Auth.*

  case class MySession(username: String)

  private val secretKey = SecretKey("secretKey") //TODO obviously (or maybe not so obviously) you should get this from a config file.

  // Secured application that requires session token
  private val authedRoute: HttpApp[Clock, Nothing] = Http.collectZIO[Auth.RequestWithSession[MySession]] {
    case r@Method.GET -> !! / "user" / "greet" =>
      ZIO.succeed(Response.text(s"Welcome to the ZIO party! ${r.session.username}"))
    case r@Method.GET -> !! / "user" / "refreshToken" =>
      for {
        jwtStr <- jwtEncode(r.session, secretKey)
      } yield Response.text(jwtStr)
  } @@ Auth.auth[MySession](secretKey)

  // Unsecured application that does not require a token
  private val unauthedRoute: HttpApp[Clock, Nothing] = Http.collectZIO[Request] {
    case Method.GET -> !! / "login" / username / password =>
      if (password.reverse.hashCode == username.hashCode) { //Todo this would be a db lookup / auth
        val session = MySession(username)
        for {
          jwtStr <- jwtEncode(session, secretKey)
        } yield Response.text(jwtStr)
      }
      else ZIO.succeed(Response.text("Invalid username or password.").setStatus(Status.Unauthorized))
  }

  private val app: UHttpApp = (unauthedRoute ++ authedRoute).provideLayer(Clock.live)

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = Server.start(8090, app).exitCode

}
