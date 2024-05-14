/*
 * Copyright (c) 2024 Roberto Leibman
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package api.routes

import api.Chuti.ChutiEnvironment
import api.auth.Auth.*
import api.token.{Token, TokenHolder, TokenPurpose}
import api.{ChutiSession, token}
import cats.data.NonEmptyList
import chuti.*
import dao.{CRUDOperations, Repository, RepositoryError, RepositoryIO, SessionContext}
import mail.Postman
import util.*
import zio.http.*
import zio.{Clock, ZIO}
import zio.logging.*
import zio.json.*

import java.time.Instant
import java.util.Locale
import scala.language.unsafeNulls
import UserId.*

object AuthRoutes { // extends CRUDRoutes[User, UserId, PagedStringSearch] {

  given localeEncoder: JsonEncoder[Locale] = JsonEncoder.string.contramap(_.toString)

  // TODO get from config
  lazy val availableLocales: NonEmptyList[Locale] = NonEmptyList.of("es_MX", "es", "en-US", "en", "eo").map(t => Locale.forLanguageTag(t).nn)
  val defaultSoftDelete:     Boolean = false

  type OpsService = CRUDOperations[User, UserId, PagedStringSearch]

  val url: String = "auth"

  def getPK(obj: User): UserId = obj.id.get

  def authOther: Routes[ChutiEnvironment & OpsService, Throwable] =
    Routes(
      Method.GET / "api" / "auth" / "isFirstLoginToday" -> handler { (req: RequestWithSession[ChutiSession]) =>
        (for {
          userOps       <- ZIO.service[Repository].map(_.userOperations)
          now           <- Clock.instant
          firstLoginOpt <- userOps.firstLogin
        } yield ResponseExt.json(firstLoginOpt.fold(true)(_.isAfter(now.minus(java.time.Duration.ofDays(1)).nn))))
          .provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
      },
      Method.GET / "api" / "auth" / "locale" -> handler { (req: RequestWithSession[ChutiSession]) =>
        Response.json(req.session.get.locale.toLanguageTag.nn)
      },
      Method.PUT / "api" / "auth" / "locale" -> handler { (req: RequestWithSession[ChutiSession]) =>
        for {
          locale           <- req.body.asString
          sessionTransport <- ZIO.service[SessionTransport[ChutiSession]]
          response         <- sessionTransport.refreshSession(req.session.get.copy(locale = Locale.forLanguageTag(locale).nn), ResponseExt.json(true))
        } yield response
      },
      Method.GET / "api" / "auth" / "whoami" -> handler { (req: RequestWithSession[ChutiSession]) =>
        ResponseExt.json(req.session.get.user)
      },
      Method.GET / "api" / "auth" / "userWallet" -> handler { (req: RequestWithSession[ChutiSession]) =>
        (for {
          userOps <- ZIO.service[Repository].map(_.userOperations)
          wallet  <- userOps.getWallet
        } yield ResponseExt.json(wallet)).provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
      },
      Method.POST / "api" / "auth" / "changePassword" -> handler { (req: RequestWithSession[ChutiSession]) =>
        (for {
          userOps <- ZIO.service[Repository].map(_.userOperations)
          body    <- req.body.asString
          ret     <- userOps.changePassword(req.session.get.user, body)
        } yield ResponseExt.json(ret)).provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
      },
      Method.GET / "api" / "auth" / "refreshToken" -> handler { (req: RequestWithSession[ChutiSession]) =>
        for {
          sessionTransport <- ZIO.service[SessionTransport[ChutiSession]]
          response         <- sessionTransport.refreshSession(req.session.get, ResponseExt.json(true))
        } yield response
      },
      Method.GET / "api" / "auth" / "doLogout" -> handler { (req: Request) =>
        for {
          sessionTransport <- ZIO.service[SessionTransport[ChutiSession]]
          response         <- sessionTransport.invalidateSession(req.session.get, Response(Status.SeeOther, Headers.location("/loginForm")))
        } yield response
      }
    )

  def unauthRoute =
    Routes(
      Method.GET / "serverVersion" -> handler {
        Response.json(chuti.BuildInfo.version)
      },
      Method.POST / "passwordReset" -> handler { (req: Request) =>
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
      },
      Method.POST / "passwordRecoveryRequest" -> handler { (req: Request) =>
        (for {
          email   <- req.body.asString
          userOps <- ZIO.service[Repository].map(_.userOperations)
          postman <- ZIO.service[Postman]
          userOpt <- userOps.userByEmail(email)
          _ <-
            ZIO
              .foreach(userOpt)(postman.lostPasswordEmail).flatMap(envelope => ZIO.foreach(envelope)(postman.deliver)).forkDaemon
        } yield Response.ok)
          .provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(ChutiSession.adminSession))
      },
      Method.POST / "updateInvitedUser" -> handler { (req: Request) =>
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
      },
      Method.GET / "getInvitedUserByToken" -> handler { (req: Request) =>
        for {
          token <- ZIO
            .fromOption(req.queryParams.get("token").toSeq.flatten.headOption)
            .orElseFail(HttpError.BadRequest("Must pass token"))
          tokenHolder <- ZIO.service[TokenHolder]
          user        <- tokenHolder.peek(Token(token), TokenPurpose.NewUser)
        } yield ResponseExt.json(user)
      },
      Method.PUT / "userCreation" -> handler { (req: Request) =>
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
      },
      Method.GET / "confirmRegistration" -> handler { (req: Request) =>
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
      },
      Method.POST / "doLogin" -> handler { (req: Request) =>
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
    )

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

  lazy private val authCRUD: Routes[ChutiEnvironment & OpsService, Throwable] =
    Routes(
      Method.POST / "api" / `url` -> handler { (req: Request) =>
        (for {
          obj <- req.bodyAs[User]
          _   <- ZIO.logInfo(s"Upserting $url with $obj")
          ret <- upsertOperation(obj)
        } yield Response.json(ret.toJson))
          .provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
      },
      Method.PUT / "api" / `url` -> handler { (req: Request) =>
        (for {
          obj <- req.bodyAs[User]
          _   <- ZIO.logInfo(s"Upserting $url with $obj")
          ret <- upsertOperation(obj)
        } yield Response.json(ret.toJson))
          .provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
      },
      Method.POST / "api" / `url` / "search" -> handler { (req: Request) =>
        (for {
          search <- req.bodyAs[PagedStringSearch]
          res    <- searchOperation(Some(search))
        } yield Response.json(res.toJson)).provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
      },
      Method.POST / s"api" / `url` / "count" -> handler { (req: Request) =>
        (for {
          search <- req.bodyAs[PagedStringSearch]
          res    <- countOperation(Some(search))
        } yield Response.json(res.toJson)).provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
      },
      Method.GET / "api" / `url` / pk -> handler { (req: Request) =>
        (for {
          pk  <- ZIO.fromEither(pk.fromJson[UserId]).mapError(e => HttpError.BadRequest(e))
          res <- getOperation(pk)
        } yield Response.json(res.toJson)).provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
      },
      Method.DELETE / "api" / `url` / pk -> handler { (req: Request) =>
        (for {
          pk     <- ZIO.fromEither(pk.fromJson[UserId]).mapError(e => HttpError.BadRequest(e))
          getted <- getOperation(pk)
          res    <- deleteOperation(getted)
          _      <- ZIO.logInfo(s"Deleted ${pk.toString}")
        } yield Response.json(res.toJson)).provideSomeLayer[ChutiEnvironment & OpsService](SessionContext.live(req.session.get))
      }
    )

  lazy val authRoute: Routes[ChutiEnvironment & OpsService, Throwable] =
    authOther ++ authCRUD

}
