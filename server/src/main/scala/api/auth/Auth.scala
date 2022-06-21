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

package api.auth

import io.circe.parser.*
import io.circe.syntax.*
import io.circe.{Decoder, Encoder}
import io.netty.handler.codec.http.{DefaultFullHttpRequest, HttpRequest}
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import util.ResponseExt
import zhttp.http.*
import zhttp.http.Cookie.SameSite
import zio.clock.Clock
import zio.config.*
import zio.config.magnolia.DeriveConfigDescriptor.descriptor
import zio.config.magnolia.Descriptor
import zio.config.typesafe.*
import zio.duration.*
import zio.{IO, Ref, Task, URIO, ZIO}

import java.net.InetAddress

object Auth {

  // scala3 opaque type
  case class SecretKey(key: String) extends AnyVal

  case class SessionConfig(
    secretKey:              SecretKey,
    sessionName:            String = "_sessiondata",
    sessionDomain:          Option[String] = None,
    sessionPath:            Option[Path] = None,
    sessionIsSecure:        Boolean = true,
    sessionIsHttpOnly:      Boolean = true,
    sessionSameSite:        Option[SameSite] = Some(SameSite.Strict),
    sessionTTL:             Duration = 12.hours,
    refreshTokenName:       String = "_refreshtoken",
    refreshTokenDomain:     Option[String] = None,
    refreshTokenPath:       Option[Path] = None,
    refreshTokenIsSecure:   Boolean = true,
    refreshTokenIsHttpOnly: Boolean = true,
    refreshTokenSameSite:   Option[SameSite] = Some(SameSite.Strict),
    refreshTokenTTL:        Duration = 12.hours
  )

  implicit private val secretKeyConfigDescriptor: Descriptor[SecretKey] =
    zio.config.magnolia.Descriptor.implicitStringDesc.transform[SecretKey](SecretKey.apply, _.key)
  implicit private val durationDescriptor: Descriptor[Duration] =
    zio.config.magnolia.Descriptor.implicitLongDesc.transform[Duration](_.minutes, _.toMinutes)
  implicit private val pathDescriptor = zio.config.magnolia.Descriptor.implicitStringDesc.transform[Path](s => Path(s), _.toString)
  implicit private val sessionConfigDescriptor = descriptor[SessionConfig]
  private val sessionConfig: IO[ReadError[String], SessionConfig] = read(
    sessionConfigDescriptor from TypesafeConfigSource.fromResourcePath.at(PropertyTreePath.$("sessionConfig"))
  )

  // None, it'd be nice if JwtClaim was parametrized on Content, but this works
  // scala3 extension
  implicit class JwtClaimExt(claim: JwtClaim) {

    def session[SessionType: Decoder]: Option[SessionType] = decode[SessionType](claim.content).toOption

  }

  def jwtExpire[SessionType: Encoder](
    mySession: SessionType,
    key:       SecretKey
  ): URIO[Clock, String] =
    for {
      clock <- ZIO.service[Clock.Service].flatMap(_.instant)
    } yield {
      val json = mySession.asJson.noSpaces
      val claim = JwtClaim(json)
        .issuedAt(clock.getEpochSecond)
        .expiresAt(clock.getEpochSecond)
      Jwt.encode(claim, key.key, JwtAlgorithm.HS512)
    }

  def jwtEncode[SessionType: Encoder](
    mySession: SessionType,
    key:       SecretKey
  ): URIO[Clock, String] =
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
  def jwtDecode(
    token: String,
    key:   SecretKey
  ): Option[JwtClaim] = {
    Jwt.decode(token, key.key, Seq(JwtAlgorithm.HS512)).toOption
  }

  case class RequestWithSession[SessionType](
    session: Option[SessionType],
    request: Request
  ) extends Request {

    override def data: HttpData = request.data

    override def headers: Headers = request.headers

    override def method: Method = request.method

    override def url: URL = request.url

    override def version: Version = request.version

    override def unsafeEncode: HttpRequest = {
      new DefaultFullHttpRequest(
        version.toJava,
        method.toJava,
        (url.kind match {
          case URL.Location.Relative => url
          case _                     => url.copy(kind = URL.Location.Relative)
        }).encode
      )
    }

//    override def unsafeContext: ChannelHandlerContext = throw new IOException("Request does not have a context")

    override def remoteAddress: Option[InetAddress] = request.remoteAddress

  }

  /** Special middleware to authenticate the user and convert the request using bearer authentication header
    */
  def authBearerToken[SessionType: Decoder](
    secretKey: SecretKey
  ): Middleware[Any, Nothing, RequestWithSession[SessionType], Response, Request, Response] =
    new Middleware[Any, Nothing, RequestWithSession[SessionType], Response, Request, Response] {

      override def apply[R1, E1](http: Http[R1, E1, RequestWithSession[SessionType], Response]): HttpApp[R1, E1] =
        Http.collectHttp[Request] { case request =>
          (for {
            str     <- request.bearerToken
            tok     <- jwtDecode(str, secretKey)
            session <- tok.session[SessionType]
          } yield session) match {
            case _ if request.path.startsWith(Path("/LoginForm")) => http.contramap[Request](req => RequestWithSession(None, req))
            case Some(session)                                    => http.contramap[Request](req => RequestWithSession(Some(session), req))
            case None                                             => Http.response(ResponseExt.seeOther("/loginForm"))
          }
        }

    }

  /** Special middleware to authenticate the user and convert the request using bearer authentication header
    */
  def authCookie[SessionType: Decoder](secretKey: SecretKey): Middleware[Any, Nothing, RequestWithSession[SessionType], Response, Request, Response] =
    new Middleware[Any, Nothing, RequestWithSession[SessionType], Response, Request, Response] {

      override def apply[R1, E1](http: Http[R1, E1, RequestWithSession[SessionType], Response]): HttpApp[R1, E1] =
        Http
          .fromFunctionZIO[Request] { request =>
            (for {
              config <- sessionConfig
            } yield {
              (for {
                str     <- request.cookiesDecoded.find(_.name == config.sessionName).map(_.content)
                tok     <- jwtDecode(str, secretKey)
                session <- tok.session[SessionType]
              } yield session) match {
                case _ if request.path.startsWith(Path("/loginForm")) => http.contramap[Request](req => RequestWithSession(None, req))
                case Some(session)                                    => http.contramap[Request](req => RequestWithSession(Some(session), req))
                case None                                             => Http.response(ResponseExt.seeOther("/loginForm"))
              }
            }).catchAll(_ => ZIO.succeed(Http.response(ResponseExt.seeOther("/loginForm"))))
          }.flatten

    }

  object InvalidSessionManager {

    def apply[T](): ZIO[Any, Nothing, InvalidSessionManager[T]] = {
      Ref.make[Set[T]](Set.empty[T]).map(InvalidSessionManager(_))
    }

  }

  case class InvalidSessionManager[T] private (sessions: Ref[Set[T]]) {

    def invalidate(session: T): IO[Nothing, Unit] = sessions.update(_ + session)

    def cleanUp = ???

    def isValid(session: T): ZIO[Any, Nothing, Boolean] = sessions.get.map(_.contains(session))

  }

  def setSession(
    response:   Response,
    sessionStr: String
  ): IO[ReadError[String], Response] = {
    for {
      config <- sessionConfig
    } yield {
      // Session cookie
      // TODO get domain, path, secure and httpOnly from config
      val sessionCookie = Cookie(
        name = config.sessionName,
        content = sessionStr,
        expires = None,
        maxAge = None,
        domain = config.sessionDomain,
        path = config.sessionPath,
        isSecure = config.sessionIsSecure,
        isHttpOnly = config.sessionIsHttpOnly,
        sameSite = config.sessionSameSite
      )

      // Refresh Cookie
      // Read to see if there's an existing cookie
      // rotate token
      //  Creates and stores a new token, removing the old one after a configured period of time, if it exists.
      val refreshCookie = Cookie(
        name = config.refreshTokenName,
        content = sessionStr,
        expires = None,
        maxAge = None,
        domain = config.refreshTokenDomain,
        path = config.refreshTokenPath,
        isSecure = config.refreshTokenIsSecure,
        isHttpOnly = config.sessionIsHttpOnly,
        sameSite = config.refreshTokenSameSite
      )

      val ret = response
        .addCookie(sessionCookie)
//        .addCookie(refreshCookie)
      ret
    }
  }

  def invalidateSession(response: Response): Response = ???

  def touchSession(response: Response): Response = ???

  trait RefreshTokenStorage[T] {

    def lookup(selector: String): Task[Option[T]]

    def store(data: T): Task[Unit]

    def remove(selector: String): Task[Unit]

    def schedule[S](after: Duration)(op: => Task[S]): Unit

  }

  case class MemoryRefreshTokenStorage[T]() extends RefreshTokenStorage[T] {

    override def lookup(selector: String): Task[Option[T]] = ???

    override def store(data: T): Task[Unit] = ???

    override def remove(selector: String): Task[Unit] = ???

    override def schedule[S](after: Duration)(op: => Task[S]): Unit = ???

  }

}
