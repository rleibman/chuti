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

package api

import io.circe.parser.decode
import io.circe.syntax.*
import io.circe.{Decoder, Encoder}
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{DefaultFullHttpRequest, HttpRequest}
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import util.ResponseExt
import zhttp.http.*
import zio.clock.Clock
import zio.{URIO, ZIO}

import java.io.IOException

object Auth {

  case class SecretKey(key: String) extends AnyVal

  // None, it'd be nice if JwtClaim was parametrized on Content, but this works
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
    session: SessionType,
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
    override def unsafeContext: ChannelHandlerContext = throw new IOException("Request does not have a context")

  }

  // Special middleware to authenticate the user and convert the request
  def auth[SessionType: Decoder](secretKey: SecretKey): Middleware[Any, Nothing, RequestWithSession[SessionType], Response, Request, Response] =
    new Middleware[Any, Nothing, RequestWithSession[SessionType], Response, Request, Response] {

      override def apply[R1, E1](http: Http[R1, E1, RequestWithSession[SessionType], Response]): HttpApp[R1, E1] =
        Http.collectHttp[Request] { case request =>
          (for {
            str     <- request.bearerToken
            tok     <- jwtDecode(str, secretKey)
            session <- tok.session[SessionType]
          } yield session) match {
            case Some(session) => http.contramap[Request](req => RequestWithSession(session, req))
            case None          => Http.response(ResponseExt.seeOther("/loginForm"))
          }
        }

    }

}
