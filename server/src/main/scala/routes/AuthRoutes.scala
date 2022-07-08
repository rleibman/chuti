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

package routes

import api.Chuti.Environment
import api.auth.Auth.*
import api.token.{Token, TokenHolder, TokenPurpose}
import api.{ChutiSession, token}
import cats.data.NonEmptyList
import chuti.*
import dao.{CRUDOperations, Repository, SessionContext}
import io.circe.Encoder
import io.circe.generic.auto.*
import mail.Postman
import util.*
import zhttp.http.*
import zio.logging.Logging
import zio.{Has, ZIO}

import java.time.Instant
import java.util.Locale

object AuthRoutes extends CRUDRoutes[User, UserId, PagedStringSearch] {

  given localeEncoder: Encoder[Locale] = Encoder.encodeString.contramap(_.toString)

  // TODO get from config
  val availableLocales: NonEmptyList[Locale] = NonEmptyList.of("es_MX", "es", "en-US", "en", "eo").map(Locale.forLanguageTag)

  override type OpsService = Has[CRUDOperations[User, UserId, PagedStringSearch]]

  override val url: String = "auth"

  override def getPK(obj: User): UserId = obj.id.get

  override def authOther: Http[Environment & OpsService, Throwable, RequestWithSession[ChutiSession], Response] =
    Http.collectHttp[RequestWithSession[ChutiSession]] {
      case req @ Method.GET -> !! / "api" / "auth" / "isFirstLoginToday" if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          (for {
            userOps       <- ZIO.service[Repository.Service].map(_.userOperations)
            firstLoginOpt <- userOps.firstLogin
          } yield ResponseExt.json(firstLoginOpt.fold(true)(_.isAfter(Instant.now.minus(java.time.Duration.ofDays(1))))))
            .provideSomeLayer[Environment & OpsService](SessionContext.live(req.session.get))
        )
      case req @ Method.GET -> !! / "api" / "auth" / "locale" if req.session.nonEmpty =>
        Http.collect(_ => ResponseExt.json(req.session.get.locale.toLanguageTag))
      case req @ Method.PUT -> !! / "api" / "auth" / "locale" if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          for {
            locale           <- req.bodyAsString
            sessionTransport <- ZIO.service[SessionTransport.Service[ChutiSession]]
            response         <- sessionTransport.refreshSession(req.session.get.copy(locale = Locale.forLanguageTag(locale)), ResponseExt.json(true))
          } yield response
        )
      case req @ Method.GET -> !! / "api" / "auth" / "whoami" if req.session.nonEmpty =>
        Http.collect { r =>
          println(r)
          ResponseExt.json(req.session.get.user)
        }
      case req @ Method.GET -> !! / "api" / "auth" / "userWallet" if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          (for {
            userOps <- ZIO.service[Repository.Service].map(_.userOperations)
            wallet  <- userOps.getWallet
          } yield ResponseExt.json(wallet)).provideSomeLayer[Environment & OpsService](SessionContext.live(req.session.get))
        )
      case req @ Method.POST -> !! / "api" / "auth" / "changePassword" if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          (for {
            userOps <- ZIO.service[Repository.Service].map(_.userOperations)
            body    <- req.bodyAsString
            ret     <- userOps.changePassword(req.session.get.user, body)
          } yield ResponseExt.json(ret)).provideSomeLayer[Environment & OpsService](SessionContext.live(req.session.get))
        )
      case req @ Method.GET -> !! / "api" / "auth" / "refreshToken" if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          for {
            sessionTransport <- ZIO.service[SessionTransport.Service[ChutiSession]]
            response         <- sessionTransport.refreshSession(req.session.get, ResponseExt.json(true))
          } yield response
        )
      case req @ Method.GET -> !! / "api" / "auth" / "doLogout" if req.session.nonEmpty =>
        Http.collectZIO(_ =>
          for {
            sessionTransport <- ZIO.service[SessionTransport.Service[ChutiSession]]
            response         <- sessionTransport.invalidateSession(req.session.get, Response(Status.SeeOther, Headers.location("/loginForm")))
          } yield response
        )
    }

  override def unauthRoute: Http[Environment & OpsService, Throwable, Request, Response] =
    Http.collectHttp[Request] {
      case Method.GET -> !! / "serverVersion" =>
        Http.succeed(ResponseExt.json(chuti.BuildInfo.version))
      case Method.POST -> !! / "passwordReset" =>
        Http.collectZIO[Request] { req =>
          (for {
            formData <- req.formData
            token = formData.get("token")
            password = formData.get("password")
            _ <- ZIO(Response.fromHttpError(HttpError.BadRequest("You need to pass a token and password"))).when(token.isEmpty || password.isEmpty)
            userOps     <- ZIO.service[Repository.Service].map(_.userOperations)
            tokenHolder <- ZIO.service[TokenHolder.Service]
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
          )).provideSomeLayer[Environment & OpsService](SessionContext.live(ChutiSession.adminSession))
        }
      case Method.POST -> !! / "passwordRecoveryRequest" =>
        Http.collectZIO[Request] { req =>
          (for {
            email   <- req.bodyAsString
            userOps <- ZIO.service[Repository.Service].map(_.userOperations)
            postman <- ZIO.service[Postman.Service]
            userOpt <- userOps.userByEmail(email)
            _ <-
              ZIO
                .foreach(userOpt)(postman.lostPasswordEmail).flatMap(envelope => ZIO.foreach(envelope)(postman.deliver)).forkDaemon
          } yield Response.ok)
            .provideSomeLayer[Environment & OpsService](SessionContext.live(ChutiSession.adminSession))
        }
      case Method.POST -> !! / "updateInvitedUser" =>
        Http.collectZIO[Request] { req =>
          (for {
            request <- req.bodyAs[UpdateInvitedUserRequest]
            userOps <- ZIO.service[Repository.Service].map(_.userOperations)
            validate <- ZIO.succeed(
              if (request.user.email.trim.isEmpty)
                Option("User Email cannot be empty")
              else if (request.user.name.trim.isEmpty)
                Option("User Name cannot be empty")
              else if (request.password.trim.isEmpty || request.password.trim.length < 3)
                Option("Password is invalid")
              else
                None
            )
            tokenHolder <- ZIO.service[token.TokenHolder.Service]
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
            .provideSomeLayer[Environment & OpsService](SessionContext.live(ChutiSession.adminSession))
        }
      case Method.GET -> !! / "getInvitedUserByToken" =>
        Http.collectZIO[Request] { req =>
          for {
            token <- ZIO
              .fromOption(req.queryParams.get("token").toSeq.flatten.headOption)
              .orElseFail(HttpError.BadRequest("Must pass token"))
            tokenHolder <- ZIO.service[TokenHolder.Service]
            user        <- tokenHolder.peek(Token(token), TokenPurpose.NewUser)
          } yield ResponseExt.json(user)
        }
      case Method.PUT -> !! / "userCreation" =>
        Http.collectZIO[Request] { req =>
          (for {
            postman             <- ZIO.service[Postman.Service]
            userOps             <- ZIO.service[Repository.Service].map(_.userOperations)
            userCreationRequest <- req.bodyAs[UserCreationRequest]
            validate <- ZIO.succeed( // TODO Use cats validate instead of option
              if (userCreationRequest.user.email.trim.isEmpty)
                Option("User Email cannot be empty")
              else if (userCreationRequest.user.name.trim.isEmpty)
                Option("User Name cannot be empty")
              else if (userCreationRequest.password.trim.isEmpty || userCreationRequest.password.trim.length < 3)
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
            _ <- ZIO.foreach_(saved)(userOps.changePassword(_, userCreationRequest.password))
            _ <- Logging.info("About to send")
            _ <- ZIO
              .foreach(saved)(postman.registrationEmail)
              .flatMap(envelope => ZIO.foreach(envelope)(postman.deliver))
              .forkDaemon
            _ <- Logging.info("Maybe sent")
          } yield {
            if (exists)
              ResponseExt.json(UserCreationResponse(Option("A user with that email already exists")))
            else
              ResponseExt.json(UserCreationResponse(validate))
          }).provideSomeLayer[Environment & OpsService](SessionContext.live(ChutiSession.adminSession))
        }
      case Method.GET -> !! / "confirmRegistration" =>
        Http.collectZIO[Request] { req =>
          (for {
            formData    <- req.formData
            token       <- ZIO.fromOption(formData.get("token")).orElseFail(HttpError.BadRequest("Token not found"))
            userOps     <- ZIO.service[Repository.Service].map(_.userOperations)
            tokenHolder <- ZIO.service[TokenHolder.Service]
            user        <- tokenHolder.validateToken(Token(token), TokenPurpose.NewUser)
            activate <-
              ZIO.foreach(user)(user => userOps.upsert(user.copy(active = true)))
          } yield activate.fold(ResponseExt.seeOther("/loginForm?registrationFailed"))(_ => ResponseExt.seeOther("/loginForm?registrationSucceeded")))
            .provideSomeLayer[Environment & OpsService](SessionContext.live(ChutiSession.adminSession))
        }
      case Method.POST -> !! / "doLogin" =>
        Http.collectZIO[Request] { req =>
          for {
            formData <- req.formData
            email <-
              ZIO.fromOption(formData.get("email")).orElseFail(HttpError.BadRequest("Missing email"))
            password <- ZIO.fromOption(formData.get("password")).orElseFail(HttpError.BadRequest("Missing Password"))

            userOps          <- ZIO.service[Repository.Service].map(_.userOperations)
            login            <- userOps.login(email, password)
            _                <- login.fold(Logging.debug(s"Bad login for $email"))(_ => Logging.debug(s"Good Login for $email"))
            sessionTransport <- ZIO.service[SessionTransport.Service[ChutiSession]]
            res <- login.fold(ZIO(Response(Status.SeeOther, Headers.location("/loginForm?bad=true")))) { user =>
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

}
