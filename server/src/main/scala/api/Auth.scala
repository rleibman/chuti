package api

import io.circe.{Decoder, Encoder}
import io.circe.parser.decode
import io.circe.syntax._
import io.netty.handler.codec.http.{DefaultFullHttpRequest, HttpRequest}
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import zhttp.http.{Headers, Http, HttpApp, HttpData, Method, Middleware, Request, Response, Status, URL, Version}
import zio.{URIO, ZIO}
import zio.clock.Clock

import java.net.InetAddress

object Auth {
  case class SecretKey(key: String) extends AnyVal

  // None, it'd be nice if JwtClaim was parametrized on Content, but this works
  implicit class JwtClaimExt(claim: JwtClaim) {

    def session[SessionType: Decoder]: Option[SessionType] = decode[SessionType](claim.content).toOption

  }

  def jwtEncode[SessionType: Encoder](mySession: SessionType, key: SecretKey): URIO[Clock, String] =
    for {
      clock <- ZIO.service[Clock.Service].flatMap(_.instant)
    } yield {
      val json = mySession.asJson.noSpaces
      val claim = JwtClaim(json)
        .issuedAt(clock.getEpochSecond)
        .expiresAt(clock.getEpochSecond + 300)
      Jwt.encode(claim, key.key, JwtAlgorithm.HS512)
    }

  // Helper to decode the JWT token
  def jwtDecode(token: String, key: SecretKey): Option[JwtClaim] = {
    Jwt.decode(token, key.key, Seq(JwtAlgorithm.HS512)).toOption
  }


  case class RequestWithSession[SessionType](session: SessionType, request: Request) extends Request {
    override def data: HttpData = request.data

    override def headers: Headers = request.headers

    override def method: Method = request.method

    override def remoteAddress: Option[InetAddress] = request.remoteAddress

    override def url: URL = request.url

    override def version: Version = request.version

    override def unsafeEncode: HttpRequest = {
      new DefaultFullHttpRequest(version.toJava, method.toJava, (url.kind match {
        case URL.Location.Relative => url
        case _ => url.copy(kind = URL.Location.Relative)
      }).encode
      )
    }
  }

  // Special middleware to authenticate the user and convert the request
  def auth[SessionType: Decoder](secretKey: SecretKey): Middleware[Any, Nothing, RequestWithSession[SessionType], Response, Request, Response] =
    new Middleware[Any, Nothing, RequestWithSession[SessionType], Response, Request, Response] {
      override def apply[R1, E1](http: Http[R1, E1, RequestWithSession[SessionType], Response]): HttpApp[R1, E1] =
        Http.collectHttp[Request] { case request =>
          (for {
            str <- request.bearerToken
            tok <- jwtDecode(str, secretKey)
            session <- tok.session[SessionType]
          } yield session) match {
            case Some(session) => http.contramap[Request](req => RequestWithSession(session, req))
            case _ => Http.status(Status.Unauthorized)
          }
        }

    }
}
