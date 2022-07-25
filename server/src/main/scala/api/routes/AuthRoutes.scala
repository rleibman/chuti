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

package api.routes

import api.Chuti.ChutiEnvironment
import api.auth.Auth.*
import api.token.{Token, TokenHolder, TokenPurpose}
import api.{ChutiSession, token}
import cats.data.NonEmptyList
import chuti.*
import dao.{CRUDOperations, Repository, RepositoryError, RepositoryIO, SessionContext}
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto.*
import mail.Postman
import util.*
import zhttp.http.*
import zio.{Clock, ZIO}
import zio.logging.*

import java.time.Instant
import java.util.Locale
import scala.language.unsafeNulls
import UserId.*
import io.circe.parser.parse

object AuthRoutes { // extends CRUDRoutes[User, UserId, PagedStringSearch] {

  given localeEncoder: Encoder[Locale] = Encoder.encodeString.contramap(_.toString)

  // TODO get from config
  lazy val availableLocales: NonEmptyList[Locale] = NonEmptyList.of("es_MX", "es", "en-US", "en", "eo").map(t => Locale.forLanguageTag(t).nn)
  val defaultSoftDelete:     Boolean = false

  type OpsService = CRUDOperations[User, UserId, PagedStringSearch]

  val url: String = "auth"

  def getPK(obj: User): UserId = obj.id.get

  def authOther: Http[ChutiEnvironment & OpsService, Throwable, RequestWithSession[ChutiSession], Response] =
    Http.collectHttp[RequestWithSession[ChutiSession]] {
      case req @ Method.GET -> !! / "api" / "auth" / "isFirstLoginToday" if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          (for {
            userOps       <- ZIO.service[Repository].map(_.userOperations)
            now           <- Clock.instant
            firstLoginOpt <- userOps.firstLogin
          } yield ResponseExt.json(firstLoginOpt.fold(true)(_.isAfter(now.minus(java.time.Duration.ofDays(1)).nn))))
            .provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
        )
      case req @ Method.GET -> !! / "api" / "auth" / "locale" if req.session.nonEmpty =>
        Http.collect(_ => ResponseExt.json(req.session.get.locale.toLanguageTag.nn))
      case req @ Method.PUT -> !! / "api" / "auth" / "locale" if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          for {
            locale           <- req.bodyAsString
            sessionTransport <- ZIO.service[SessionTransport[ChutiSession]]
            response <- sessionTransport.refreshSession(req.session.get.copy(locale = Locale.forLanguageTag(locale).nn), ResponseExt.json(true))
          } yield response
        )
      case req @ Method.GET -> !! / "api" / "auth" / "whoami" if req.session.nonEmpty =>
        Http.collect { r =>
          ResponseExt.json(req.session.get.user)
        }
      case req @ Method.GET -> !! / "api" / "auth" / "userWallet" if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          (for {
            userOps <- ZIO.service[Repository].map(_.userOperations)
            wallet  <- userOps.getWallet
          } yield ResponseExt.json(wallet)).provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
        )
      case req @ Method.POST -> !! / "api" / "auth" / "changePassword" if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          (for {
            userOps <- ZIO.service[Repository].map(_.userOperations)
            body    <- req.bodyAsString
            ret     <- userOps.changePassword(req.session.get.user, body)
          } yield ResponseExt.json(ret)).provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
        )
      case req @ Method.GET -> !! / "api" / "auth" / "refreshToken" if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          for {
            sessionTransport <- ZIO.service[SessionTransport[ChutiSession]]
            response         <- sessionTransport.refreshSession(req.session.get, ResponseExt.json(true))
          } yield response
        )
      case req @ Method.GET -> !! / "api" / "auth" / "doLogout" if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          for {
            sessionTransport <- ZIO.service[SessionTransport[ChutiSession]]
            response         <- sessionTransport.invalidateSession(req.session.get, Response(Status.SeeOther, Headers.location("/loginForm")))
          } yield response
        )
    }

  def unauthRoute: Http[ChutiEnvironment & OpsService, Throwable, Request, Response] =
    Http.collectHttp[Request] {
      case Method.GET -> !! / "serverVersion" =>
        Http.succeed(ResponseExt.json(chuti.BuildInfo.version))
      case Method.POST -> !! / "passwordReset" =>
        Http.collectZIO[Request] { req =>
          (for {
            formData <- req.formData
            token = formData.get("token")
            password = formData.get("password")
            _ <- ZIO
              .succeed(Response.fromHttpError(HttpError.BadRequest("You need to pass a token and password"))).when(token.isEmpty || password.isEmpty)
            userOps     <- ZIO.service[Repository].map(_.userOperations)
            tokenHolder <- ZIO.service[TokenHolder]
            userOpt <-
              tokenHolder
                .validateToken(Token(token.get), TokenPurpose.LostPassword)
            passwordChanged <- ZIO.foreach(userOpt)(user => userOps.changePassword(user, password.get))
          } yield passwordChanged.fold(
            ResponseExt.seeOther("/loginForm?passwordChangeFailed")
          )(isChanged =>
            if (isChanged)
              ResponseExt.seeOther("/loginForm?passwordChangeSucceeded")
            else
              ResponseExt.seeOther("/loginForm?passwordChangeFailed")
          )).provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(ChutiSession.adminSession))
        }
      case Method.POST -> !! / "passwordRecoveryRequest" =>
        Http.collectZIO[Request] { req =>
          (for {
            email   <- req.bodyAsString
            userOps <- ZIO.service[Repository].map(_.userOperations)
            postman <- ZIO.service[Postman]
            userOpt <- userOps.userByEmail(email)
            _ <-
              ZIO
                .foreach(userOpt)(postman.lostPasswordEmail).flatMap(envelope => ZIO.foreach(envelope)(postman.deliver)).forkDaemon
          } yield Response.ok)
            .provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(ChutiSession.adminSession))
        }
      case Method.POST -> !! / "updateInvitedUser" =>
        Http.collectZIO[Request] { req =>
          (for {
            request <- req.bodyAs[UpdateInvitedUserRequest]
            userOps <- ZIO.service[Repository].map(_.userOperations)
            validate <- ZIO.succeed(
              if (request.user.email.trim.nn.isEmpty)
                Option("User Email cannot be empty")
              else if (request.user.name.trim.nn.isEmpty)
                Option("User Name cannot be empty")
              else if (request.password.trim.nn.isEmpty || request.password.trim.nn.length < 3)
                Option("Password is invalid")
              else
                None
            )
            tokenHolder <- ZIO.service[token.TokenHolder]
            userOpt <- validate.fold(
              tokenHolder.validateToken(Token(request.token), TokenPurpose.NewUser)
            )(_ => ZIO.none)
            savedUser       <- ZIO.foreach(userOpt)(u => userOps.upsert(u.copy(name = request.user.name, active = true)))
            passwordChanged <- ZIO.foreach(savedUser)(user => userOps.changePassword(user, request.password))
          } yield {
            validate.fold(
              passwordChanged.fold(
                ResponseExt.json(UpdateInvitedUserResponse(None: Option[String]))
              )(p => ResponseExt.json(UpdateInvitedUserResponse(Option.when(!p)("Error setting password"))))
            )(error => ResponseExt.json(UpdateInvitedUserResponse(Option(error))))
          })
            .provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(ChutiSession.adminSession))
        }
      case Method.GET -> !! / "getInvitedUserByToken" =>
        Http.collectZIO[Request] { req =>
          for {
            token <- ZIO
              .fromOption(req.queryParams.get("token").toSeq.flatten.headOption)
              .orElseFail(HttpError.BadRequest("Must pass token"))
            tokenHolder <- ZIO.service[TokenHolder]
            user        <- tokenHolder.peek(Token(token), TokenPurpose.NewUser)
          } yield ResponseExt.json(user)
        }
      case Method.PUT -> !! / "userCreation" =>
        Http.collectZIO[Request] { req =>
          (for {
            postman             <- ZIO.service[Postman]
            userOps             <- ZIO.service[Repository].map(_.userOperations)
            userCreationRequest <- req.bodyAs[UserCreationRequest]
            validate <- ZIO.succeed( // TODO Use cats validate instead of option
              if (userCreationRequest.user.email.trim.nn.isEmpty)
                Option("User Email cannot be empty")
              else if (userCreationRequest.user.name.trim.nn.isEmpty)
                Option("User Name cannot be empty")
              else if (userCreationRequest.password.trim.nn.isEmpty || userCreationRequest.password.trim.nn.length < 3)
                Option("Password is invalid")
              else if (userCreationRequest.user.id.nonEmpty)
                Option("You can't register an existing user")
              else
                None
            )
            exists <- userOps.userByEmail(userCreationRequest.user.email).map(_.nonEmpty)
            saved <-
              if (validate.nonEmpty || exists)
                ZIO.none
              else
                userOps.upsert(userCreationRequest.user.copy(active = false)).map(Option(_))
            _ <- ZIO.foreachDiscard(saved)(userOps.changePassword(_, userCreationRequest.password))
            _ <- ZIO.logInfo("About to send")
            _ <- ZIO
              .foreach(saved)(postman.registrationEmail)
              .flatMap(envelope => ZIO.foreach(envelope)(postman.deliver))
              .forkDaemon
            _ <- ZIO.logInfo("Maybe sent")
          } yield {
            if (exists)
              ResponseExt.json(UserCreationResponse(Option("A user with that email already exists")))
            else
              ResponseExt.json(UserCreationResponse(validate))
          }).provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(ChutiSession.adminSession))
        }
      case Method.GET -> !! / "confirmRegistration" =>
        Http.collectZIO[Request] { req =>
          (for {
            formData    <- req.formData
            token       <- ZIO.fromOption(formData.get("token")).orElseFail(HttpError.BadRequest("Token not found"))
            userOps     <- ZIO.service[Repository].map(_.userOperations)
            tokenHolder <- ZIO.service[TokenHolder]
            user        <- tokenHolder.validateToken(Token(token), TokenPurpose.NewUser)
            activate <-
              ZIO.foreach(user)(user => userOps.upsert(user.copy(active = true)))
          } yield activate.fold(ResponseExt.seeOther("/loginForm?registrationFailed"))(_ => ResponseExt.seeOther("/loginForm?registrationSucceeded")))
            .provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(ChutiSession.adminSession))
        }
      case Method.POST -> !! / "doLogin" =>
        Http.collectZIO[Request] { req =>
          for {
            formData <- req.formData
            email <-
              ZIO.fromOption(formData.get("email")).orElseFail(HttpError.BadRequest("Missing email"))
            password <- ZIO.fromOption(formData.get("password")).orElseFail(HttpError.BadRequest("Missing Password"))

            userOps          <- ZIO.service[Repository].map(_.userOperations)
            login            <- userOps.login(email, password)
            _                <- login.fold(ZIO.logDebug(s"Bad login for $email"))(_ => ZIO.logDebug(s"Good Login for $email"))
            sessionTransport <- ZIO.service[SessionTransport[ChutiSession]]
            res <- login.fold(ZIO.succeed(Response(Status.SeeOther, Headers.location("/loginForm?bad=true")))) { user =>
              sessionTransport.refreshSession(
                ChutiSession(user, req.preferredLocale(availableLocales, formData.get("forcedLocale"))),
                Response(
                  Status.SeeOther,
                  Headers.location("/").addHeader(HeaderNames.contentType, HeaderValues.textPlain)
                )
              )
            }
          } yield res
        }
    }

  def getOperation(id: UserId): ZIO[
    SessionContext & OpsService,
    RepositoryError,
    Option[User]
  ] =
    for {
      ops <- ZIO.service[CRUDOperations[User, UserId, PagedStringSearch]]
      ret <- ops.get(id)
    } yield ret

  def deleteOperation(
    objOpt: Option[User]
  ): ZIO[SessionContext & OpsService, Throwable, Boolean] =
    for {
      ops <- ZIO.service[CRUDOperations[User, UserId, PagedStringSearch]]
      ret <- objOpt.fold(ZIO.succeed(false): RepositoryIO[Boolean])(obj => ops.delete(getPK(obj), defaultSoftDelete))
    } yield ret

  def upsertOperation(obj: User): ZIO[
    SessionContext & OpsService,
    RepositoryError,
    User
  ] = {
    for {
      ops <- ZIO.service[CRUDOperations[User, UserId, PagedStringSearch]]
      ret <- ops.upsert(obj)
    } yield ret
  }

  def countOperation(search: Option[PagedStringSearch]): ZIO[
    SessionContext & OpsService,
    RepositoryError,
    Long
  ] =
    for {
      ops <- ZIO.service[CRUDOperations[User, UserId, PagedStringSearch]]
      ret <- ops.count(search)
    } yield ret

  def searchOperation(search: Option[PagedStringSearch]): ZIO[
    SessionContext & OpsService,
    RepositoryError,
    Seq[User]
  ] =
    for {
      ops <- ZIO.service[CRUDOperations[User, UserId, PagedStringSearch]]
      ret <- ops.search(search)
    } yield ret

  lazy private val authCRUD: Http[ChutiEnvironment & OpsService, Throwable, RequestWithSession[ChutiSession], Response] =
    Http.collectHttp[RequestWithSession[ChutiSession]] {
      case req @ (Method.POST | Method.PUT) -> !! / "api" / `url` if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          (for {
            obj <- req.bodyAs[User]
            _   <- ZIO.logInfo(s"Upserting $url with $obj")
            ret <- upsertOperation(obj)
          } yield Response.json(ret.asJson.noSpaces))
            .provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
        )
      case req @ (Method.POST) -> !! / "api" / `url` / "search" if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          (for {
            search <- req.bodyAs[PagedStringSearch]
            res    <- searchOperation(Some(search))
          } yield Response.json(res.asJson.noSpaces)).provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
        )
      case req @ Method.POST -> !! / s"api" / `url` / "count" if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          (for {
            search <- req.bodyAs[PagedStringSearch]
            res    <- countOperation(Some(search))
          } yield Response.json(res.asJson.noSpaces)).provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
        )
      case req @ Method.GET -> !! / "api" / `url` / pk if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          (for {
            pk  <- ZIO.fromEither(parse(pk).flatMap(_.as[UserId])).mapError(e => HttpError.BadRequest(e.getMessage.nn))
            res <- getOperation(pk)
          } yield Response.json(res.asJson.noSpaces)).provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
        )
      case req @ Method.DELETE -> !! / "api" / `url` / pk if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          (for {
            pk     <- ZIO.fromEither(parse(pk).flatMap(_.as[UserId])).mapError(e => HttpError.BadRequest(e.getMessage.nn))
            getted <- getOperation(pk)
            res    <- deleteOperation(getted)
            _      <- ZIO.logInfo(s"Deleted ${pk.toString}")
          } yield Response.json(res.asJson.noSpaces)).provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
        )
    }

  lazy val authRoute: Http[ChutiEnvironment & OpsService, Throwable, RequestWithSession[ChutiSession], Response] =
    authOther ++ authCRUD

}
