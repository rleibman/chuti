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

import api.{ChutiEnvironment, ConfigurationError, ConfigurationService}
import chuti.GameError
import com.typesafe.config.{Config as TypesafeConfig, ConfigFactory}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{DefaultFullHttpRequest, HttpRequest}
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import util.ResponseExt
import zio.http.*
import zio.http.Cookie.SameSite
import zio.config.*
import zio.config.magnolia.*
import zio.config.typesafe.*
import zio.*
import zio.json.*

import java.io.{File, IOException}
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
    override val message: String,
    override val cause:   Option[Throwable] = None
  ) extends GameError(message, cause)

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
//  given Descriptor[Path] = Descriptor.from[Path](ConfigDescriptor.string.transform(Path.decode _, _.toString))

  //  given Descriptor[Option[Path]] = string("OPATH").transform[Option[Path]](p => Some(Path.apply(p)), _.fold("")(_.encode))
//  given sessionConfigDescriptor: ConfigDescriptor[SessionConfig] = descriptor[SessionConfig]

//  lazy private val sessionConfig = read(
//    sessionConfigDescriptor from TypesafeConfigSource.fromResourcePath.at(PropertyTreePath.$("sessionConfig"))
//  )

  private val sessionConfig: URIO[Any, SessionConfig] = {
    import scala.language.unsafeNulls
    val confFileName = java.lang.System.getProperty("application.conf", "./src/main/resources/application.conf")
    given DeriveConfig[zio.nio.file.Path] = DeriveConfig[String].map(string => zio.nio.file.Path(string))

    val typesafeConfig = ConfigFactory
      .parseFile(new File(confFileName))
      .withFallback(ConfigFactory.load())
      .resolve()

    TypesafeConfigProvider
      .fromTypesafeConfig(typesafeConfig)
      .load(DeriveConfig.derived[SessionConfig].desc)
      .orDie
  }

  val sessionConfigLayer: ZLayer[Any, Nothing, SessionConfig] = ZLayer.fromZIO(sessionConfig)

  // None, it'd be nice if JwtClaim was parametrized on Content, but this works
  extension (claim: JwtClaim) {

    def session[SessionType: JsonDecoder]: ZIO[Any, SessionError, SessionType] =
      ZIO.fromEither(claim.content.fromJson[SessionType]).mapError(SessionError(_))

  }

  private def jwtEncode[SessionType: JsonEncoder](
    session: SessionType
  ): ZIO[ConfigurationService, SessionError, String] =
    for {
      configService <- ZIO.service[ConfigurationService]
      config        <- configService.appConfig.mapBoth(e => SessionError("", Option(e)), _.chuti.sessionConfig)
      now           <- Clock.instant
    } yield {
      val json = session.toJson
      val claim = JwtClaim(json)
        .issuedAt(now.getEpochSecond)
        .expiresAt(now.getEpochSecond + 300)
      Jwt.encode(claim, config.secretKey.key, JwtAlgorithm.HS512)
    }

  private def jwtDecode(token: String): ZIO[ConfigurationService, SessionError, JwtClaim] =
    for {
      configService <- ZIO.service[ConfigurationService]
      config        <- configService.appConfig.mapBoth(e => SessionError("", Option(e)), _.chuti.sessionConfig)
      tok           <- ZIO.fromTry(Jwt.decode(token, config.secretKey.key, Seq(JwtAlgorithm.HS512))).mapError(e => SessionError("", Option(e)))
    } yield tok

  protected def jwtExpire[SessionType: JsonEncoder](
    mySession: SessionType
  ): ZIO[ConfigurationService, SessionError, String] =
    for {
      configService <- ZIO.service[ConfigurationService]
      config        <- configService.appConfig.mapBoth(e => SessionError("", Option(e)), _.chuti.sessionConfig)
      now           <- Clock.instant
    } yield {
      val json = mySession.toJson
      val claim = JwtClaim(json)
        .issuedAt(now.getEpochSecond)
        .expiresAt(now.getEpochSecond)
      Jwt.encode(claim, config.secretKey.key, JwtAlgorithm.HS512)
    }

  object SessionTransport {

    def cookieSessionTransport[
      SessionType: JsonEncoder: JsonDecoder: Tag
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

    def bearerAuthWithContext: HandlerAspect[ChutiEnvironment, SessionType]

    def getSession(request: Request): ZIO[ConfigurationService, SessionError, Option[SessionType]]

    def invalidateSession(
      session:  SessionType,
      response: Response
    ): ZIO[ConfigurationService, SessionError, Response]

    def cleanUp: UIO[Unit]

    def isValid(session: SessionType): ZIO[ConfigurationService, Nothing, Boolean]

    def refreshSession(
      session:  SessionType,
      response: Response
    ): ZIO[ConfigurationService, SessionError, Response]

  }

  trait SessionStorage[SessionType, PK] {

    def storeSession(session: SessionType): ZIO[ConfigurationService, SessionError, PK]

    def getSession(sessionId: PK): ZIO[ConfigurationService, SessionError, Option[SessionType]]

    def deleteSession(sessionId: PK): ZIO[ConfigurationService, SessionError, Boolean]

  }

  object SessionStorage {

    def tokenEncripted[SessionType: JsonEncoder: JsonDecoder: Tag]: ZLayer[Any, Nothing, SessionStorage[SessionType, String]] =
      ZLayer.succeed {
        new SessionStorage[SessionType, String] {

          override def storeSession(session: SessionType): ZIO[ConfigurationService, SessionError, String] = jwtEncode(session)

          override def getSession(sessionId: String): ZIO[ConfigurationService, SessionError, Option[SessionType]] =
            for {
              decoded <- jwtDecode(sessionId)
              session <- decoded.session
            } yield Option(session)

          override def deleteSession(sessionId: String): ZIO[ConfigurationService, SessionError, Boolean] = ZIO.succeed(true)

        }
      }

  }

  private case class CookieSessionTransport[SessionType: JsonEncoder: JsonDecoder: Tag](
    sessionStorage:  SessionStorage[SessionType, String],
    invalidSessions: Ref[Map[SessionType, Instant]]
  ) extends SessionTransport[SessionType] {

    override def invalidateSession(
      session:  SessionType,
      response: Response
    ): ZIO[ConfigurationService, SessionError, Response] =
      (for {
        now           <- Clock.instant
        configService <- ZIO.service[ConfigurationService]
        config        <- configService.appConfig.mapBoth(e => SessionError("", Option(e)), _.chuti.sessionConfig)
        _             <- invalidSessions.update(_ + (session -> now.plusSeconds(config.sessionTTL.toSeconds + 60).nn))
      } yield {
        val deleteCookie = Cookie.Response(
          name = config.sessionName,
          content = "deleted",
          maxAge = Option(Duration.Zero),
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
    ): ZIO[ConfigurationService, SessionError, Response] =
      for {
        configService <- ZIO.service[ConfigurationService]
        config        <- configService.appConfig.mapBoth(e => SessionError("", Option(e)), _.chuti.sessionConfig)
        isValid       <- isValid(session)
        _             <- ZIO.fail(SessionError("Session is invalid")).when(!isValid)
        sessionStr    <- sessionStorage.storeSession(session)
      } yield {
        response.addCookie(
          Cookie.Response(
            name = config.sessionName,
            content = sessionStr,
            maxAge = Option(config.sessionTTL),
            domain = config.sessionDomain,
            path = config.sessionPath,
            isSecure = config.sessionIsSecure,
            isHttpOnly = config.sessionIsHttpOnly,
            sameSite = config.sessionSameSite
          )
        )
      }

    override def cleanUp: UIO[Unit] = invalidSessions.update(_.filter(_._2.isAfter(Instant.now)))

    override def isValid(session: SessionType): ZIO[ConfigurationService, Nothing, Boolean] = invalidSessions.get.map(!_.exists(_._1 == session))

    override def getSession(request: Request): ZIO[ConfigurationService, SessionError, Option[SessionType]] =
      for {
        configService <- ZIO.service[ConfigurationService]
        config        <- configService.appConfig.mapBoth(e => SessionError("", Option(e)), _.chuti.sessionConfig)
        str <- ZIO
          .fromOption(request.cookies.find(_.name == config.sessionName).map(_.content)).orElseFail(SessionError("No session cookie found"))
        session <- sessionStorage.getSession(str)
      } yield session

    override val bearerAuthWithContext: HandlerAspect[ConfigurationService, SessionType] =
      HandlerAspect.interceptIncomingHandler(Handler.fromFunctionZIO[Request] { request =>
        request.header(Header.Authorization) match {
          // We got a bearer token, let's decode it
          case Some(Header.Authorization.Bearer(token)) =>
            for {
              claim <- jwtDecode(token.value.asString).orElseFail(Response.badRequest("Invalid or expired token!"))
              u     <- claim.session.mapError(e => Response.internalServerError(e.getMessage))
            } yield (request, u)
          // No bearer token, let's see if we have a session cookie
          case _ =>
            for {
              session           <- getSession(request).mapError(e => Response.internalServerError(e.getMessage))
              loginFormRedirect <- ResponseExt.seeOther("/loginForm").mapError(e => Response.internalServerError(e.getMessage))
              r <-
                session match {
                  case _ if request.path.startsWith(Path.decode("/loginForm")) =>
                    ZIO.fail(Response.unauthorized.addHeaders(Headers(Header.WWWAuthenticate.Bearer(realm = "Access"))))
                  case Some(s) => ZIO.succeed((request, s))
                  case None    => ZIO.fail(loginFormRedirect)
                }
            } yield r
        }
      })

  }

}
