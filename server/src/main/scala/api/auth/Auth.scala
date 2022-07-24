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
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{DefaultFullHttpRequest, HttpRequest}
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import util.ResponseExt
import zhttp.http.*
import zhttp.http.Cookie.SameSite
import zio.config.*
import zio.config.magnolia.*
import zio.config.typesafe.*
import zio.*

import java.io.IOException
import java.net.InetAddress
import java.time.Instant

/** This is a collection of utilities for authenticating users. Authentication is parametrized on a given Session Type, and we assume you're using
  * circe to convert it to/from JSON. We divide the authentication into SessionTransport and SessionStorage, currently I've written these:
  *   - CookieSessionTransport: uses cookies to store the session TODO: Currently, we only support the session cookie, we do not yet support the
  *     refresh cookie.
  *   - TokenEncryptedSessionStorage: The session is not stored as such, instead, the session is stored in a JWT and then encrypted. It works decently
  *     and uses little memory, but in order to support session invalidation we do have to keep invalidated sessions in memory Possible enhancements
  *   - TODO: HeaderSessionTransport: uses headers (bearer token) to store the session
  *   - TODO: InMemorySessionStorage - Store the session in memory.
  *   - InDiskSessionStorage - Store the session in disk. I don't think we can write this generically, since each system would use a different
  *     database. TODO: provide an example
  *   - TODO It'd be nice to have an easy, slam dunk integration with OAuth2
  */
object Auth {

  case class SessionError(
    message: String,
    cause:   Option[Throwable] = None
  ) extends Exception(message, cause.orNull)

  opaque type SecretKey = String

  object SecretKey {

    def apply(key: String): SecretKey = key

  }

  extension (s: SecretKey) {

    def key: String = s

  }

  case class SessionConfig(
    secretKey:         SecretKey,
    sessionName:       String = "_sessiondata",
    sessionDomain:     Option[String] = None,
    sessionPath:       Option[Path] = None,
    sessionIsSecure:   Boolean = true,
    sessionIsHttpOnly: Boolean = true,
    sessionSameSite:   Option[SameSite] = Some(SameSite.Strict),
    sessionTTL:        Duration = 12.hours
  )

  //  given secretKeyConfigDescriptor: ConfigDescriptor[SecretKey] = zio.config.magnolia.Descriptor.from[SecretKey]
  //  given durationDescriptor: Descriptor[Duration] = zio.config.magnolia.Descriptor.from[Long].
  //    zio.config.magnolia.Descriptor.implicitLongDesc.transform[Duration](_.minutes, _.toMinutes)
  //  given pathConfigDescriptor: ConfigDescriptor[Path] = string("PATH")(Path.apply, a => Some(a.encode))  //zio.config.magnolia.Descriptor.from[java.nio.file.Path]
  given Descriptor[Path] = Descriptor.from[Path](ConfigDescriptor.string.transform(Path.decode _, _.toString))

  //  given Descriptor[Option[Path]] = string("OPATH").transform[Option[Path]](p => Some(Path.apply(p)), _.fold("")(_.encode))
  given sessionConfigDescriptor: ConfigDescriptor[SessionConfig] = descriptor[SessionConfig]

  lazy private val sessionConfig: IO[ReadError[String], SessionConfig] = read(
    sessionConfigDescriptor from TypesafeConfigSource.fromResourcePath.at(PropertyTreePath.$("sessionConfig"))
  )

  // None, it'd be nice if JwtClaim was parametrized on Content, but this works
  // scala3 extension
  implicit class JwtClaimExt(claim: JwtClaim) {

    def session[SessionType: Decoder]: ZIO[Any, SessionError, SessionType] =
      ZIO.fromEither(decode[SessionType](claim.content)).mapError(e => SessionError(e.getMessage.nn, Some(e)))

  }

  object RequestWithSession {

    def apply[SessionType](
      session: Option[SessionType],
      request: Request
    ): RequestWithSession[SessionType] = {
      new RequestWithSession(session, request)
    }

    def unapply[SessionType](requestWithSession: RequestWithSession[SessionType]): Option[(Option[SessionType], Request)] = {
      Some((requestWithSession.session, requestWithSession.request))
    }

  }

  // Request defines it's own copy method, which breaks the usage of copy in the RequestWithSession, so we can't use case classes
  class RequestWithSession[SessionType](
    val session: Option[SessionType],
    val request: Request
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

    override def unsafeContext: ChannelHandlerContext = throw new IOException("Request does not have a context")

  }

  private def jwtEncode[SessionType: Encoder](
    session: SessionType
  ): ZIO[Any, SessionError, String] =
    for {
      config <- sessionConfig.orElseFail(SessionError("failed to encode jwtClaim"))
      now    <- Clock.instant
    } yield {
      val json = session.asJson.noSpaces
      val claim = JwtClaim(json)
        .issuedAt(now.getEpochSecond)
        .expiresAt(now.getEpochSecond + 300)
      Jwt.encode(claim, config.secretKey.key, JwtAlgorithm.HS512)
    }

  private def jwtDecode(token: String): ZIO[Any, SessionError, JwtClaim] =
    for {
      config <- sessionConfig.orElseFail(SessionError("failed to decode jwtClaim"))
      tok    <- ZIO.fromTry(Jwt.decode(token, config.secretKey.key, Seq(JwtAlgorithm.HS512))).mapError(e => SessionError("", Option(e)))
    } yield tok

  protected def jwtExpire[SessionType: Encoder](
    mySession: SessionType
  ): ZIO[Any, SessionError, String] =
    for {
      config <- sessionConfig.orElseFail(SessionError("failed to expire jwtClaim"))
      now    <- Clock.instant
    } yield {
      val json = mySession.asJson.noSpaces
      val claim = JwtClaim(json)
        .issuedAt(now.getEpochSecond)
        .expiresAt(now.getEpochSecond)
      Jwt.encode(claim, config.secretKey.key, JwtAlgorithm.HS512)
    }

  object SessionTransport {

    def cookieSessionTransport[
      SessionType: Encoder: Decoder: Tag
    ]: ZLayer[SessionStorage[SessionType, String], Nothing, SessionTransport[SessionType]] =
      ZLayer.fromZIO(
        for {
          invalidSessions <- Ref.make(Map.empty[SessionType, Instant])
          sessionStorage  <- ZIO.service[SessionStorage[SessionType, String]]
          mgr             <- ZIO.succeed(CookieSessionTransport(sessionStorage, invalidSessions))
          _               <- mgr.cleanUp.repeat(Schedule.fixed(1.hour)).forkDaemon
        } yield mgr
      )

  }

  trait SessionTransport[SessionType] {

    def auth: Middleware[Any, Nothing, RequestWithSession[SessionType], Response, Request, Response]

    def getSession(request: Request): IO[SessionError, Option[SessionType]]

    def invalidateSession(
      session:  SessionType,
      response: Response
    ): ZIO[Any, SessionError, Response]

    def cleanUp: UIO[Unit]

    def isValid(session: SessionType): ZIO[Any, Nothing, Boolean]

    def refreshSession(
      session:  SessionType,
      response: Response
    ): IO[SessionError, Response]

  }

  trait SessionStorage[SessionType, PK] {

    def storeSession(session: SessionType): IO[SessionError, PK]

    def getSession(sessionId: PK): IO[SessionError, Option[SessionType]]

    def deleteSession(sessionId: PK): IO[SessionError, Boolean]

  }

  object SessionStorage {

    def tokenEncripted[SessionType: Encoder: Decoder: Tag]: ZLayer[Any, Nothing, SessionStorage[SessionType, String]] =
      ZLayer.succeed {
        new SessionStorage[SessionType, String] {

          override def storeSession(session: SessionType): IO[SessionError, String] = jwtEncode(session)

          override def getSession(sessionId: String): IO[SessionError, Option[SessionType]] =
            for {
              decoded <- jwtDecode(sessionId)
              session <- decoded.session
            } yield Option(session)

          override def deleteSession(sessionId: String): IO[SessionError, Boolean] = ZIO.succeed(true)

        }
      }

  }

  private case class CookieSessionTransport[SessionType: Encoder: Decoder: Tag](
    sessionStorage:  SessionStorage[SessionType, String],
    invalidSessions: Ref[Map[SessionType, Instant]]
  ) extends SessionTransport[SessionType] {

    override def invalidateSession(
      session:  SessionType,
      response: Response
    ): ZIO[Any, SessionError, Response] =
      (for {
        now    <- Clock.instant
        config <- sessionConfig
        _      <- invalidSessions.update(_ + (session -> now.plusSeconds(config.sessionTTL.toSeconds + 60).nn))
      } yield {
        val deleteCookie = Cookie(
          name = config.sessionName,
          content = "deleted",
          maxAge = Option(Duration.Zero.toSeconds.toInt),
          domain = config.sessionDomain,
          path = config.sessionPath,
          isSecure = config.sessionIsSecure,
          isHttpOnly = config.sessionIsHttpOnly,
          sameSite = config.sessionSameSite
        )
        response.addCookie(deleteCookie)
      }).mapError(e => SessionError("Could not invalidate session", Some(e)))

    override def refreshSession(
      session:  SessionType,
      response: Response
    ): IO[SessionError, Response] =
      for {
        config     <- sessionConfig.mapError(e => SessionError("Could not touch session", Some(e)))
        isValid    <- isValid(session)
        _          <- ZIO.fail(SessionError("Session is invalid")).when(!isValid)
        sessionStr <- sessionStorage.storeSession(session)
      } yield {
        response.addCookie(
          Cookie(
            name = config.sessionName,
            content = sessionStr,
            maxAge = Option(config.sessionTTL.toSeconds.toInt),
            domain = config.sessionDomain,
            path = config.sessionPath,
            isSecure = config.sessionIsSecure,
            isHttpOnly = config.sessionIsHttpOnly,
            sameSite = config.sessionSameSite
          )
        )
      }

    override def cleanUp: UIO[Unit] = invalidSessions.update(_.filter(_._2.isAfter(Instant.now)))

    override def isValid(session: SessionType): ZIO[Any, Nothing, Boolean] = invalidSessions.get.map(!_.exists(_._1 == session))

    override def getSession(request: Request): IO[SessionError, Option[SessionType]] =
      for {
        config <- sessionConfig.mapError(e => SessionError(e.getMessage.nn, Some(e)))
        str <- ZIO
          .fromOption(request.cookiesDecoded.find(_.name == config.sessionName).map(_.content)).orElseFail(SessionError("No session cookie found"))
        session <- sessionStorage.getSession(str)
      } yield session

    override def auth: Middleware[Any, Nothing, RequestWithSession[SessionType], Response, Request, Response] = {
      new Middleware[Any, Nothing, RequestWithSession[SessionType], Response, Request, Response] {
        override def apply[R1, E1](http: Http[R1, E1, RequestWithSession[SessionType], Response]): HttpApp[R1, E1] =
          Http
            .fromFunctionZIO[Request] { request =>
              (for {
                session <- getSession(request)
              } yield {
                session match {
                  case _ if request.path.startsWith(Path.decode("/loginForm")) => http.contramap[Request](req => RequestWithSession(None, req))
                  case _                                                       => http.contramap[Request](req => RequestWithSession(session, req))
                }
              }).catchAll(e =>
                ZIO.logError(e.getMessage.nn) *>
                  ZIO.succeed(Http.response(ResponseExt.seeOther("/loginForm")))
              )
            }.flatten
      }
    }

  }

}
