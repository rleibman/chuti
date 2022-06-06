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

import api.Auth.*
import api.Chuti.Environment
import api.token.{Token, TokenHolder, TokenPurpose}
import api.{Chuti, ChutiSession, token}
import cats.data.NonEmptyList
import chuti.*
import dao.{CRUDOperations, Repository, SessionProvider}
import io.circe.Encoder
import io.circe.generic.auto.*
import mail.Postman
import util.*
import zhttp.http.*
import zio.clock.Clock
import zio.logging.Logging
import zio.magic.*
import zio.{Has, ZIO}

import java.time.Instant
import java.util.Locale

object AuthRoutes extends CRUDRoutes[User, UserId, PagedStringSearch] {

  implicit val localeEncoder: Encoder[Locale] = Encoder.encodeString.contramap(l => l.toString)

  // TODO get from config
  val availableLocales: NonEmptyList[Locale] = NonEmptyList.of("es_MX", "es", "en-US", "en", "eo").map(Locale.forLanguageTag)

  override type OpsService = Has[CRUDOperations[User, UserId, PagedStringSearch]]

  override val url: String = "auth"

  override def getPK(obj: User): UserId = obj.id.get

  override def authOther: Http[Environment & OpsService, Throwable, RequestWithSession[ChutiSession], Response] =
    Http.collectZIO[RequestWithSession[ChutiSession]] { req =>
      (req match {
        case Method.GET -> !! / "api" / "auth" / "isFirstLoginToday" =>
          for {
            userOps       <- ZIO.service[Repository.Service].map(_.userOperations)
            firstLoginOpt <- userOps.firstLogin
          } yield ResponseExt.json(firstLoginOpt.fold(true)(_.isAfter(Instant.now.minus(java.time.Duration.ofDays(1)))))
        case Method.GET -> !! / "api" / "auth" / "locale" =>
          ZIO(ResponseExt.json(req.session.locale.toLanguageTag))
        case Method.PUT -> !! / "api" / "auth" / "locale" =>
          for {
            locale    <- req.bodyAsString
            secretKey <- Chuti.secretKey
            jwtString <- jwtEncode(req.session.copy(locale = Locale.forLanguageTag(locale)), secretKey)
          } yield ResponseExt.json(jwtString)
        case Method.GET -> !! / "api" / "auth" / "whoami" =>
          ZIO(ResponseExt.json(req.session.user))
        case Method.GET -> !! / "api" / "auth" / "userWallet" =>
          for {
            userOps <- ZIO.service[Repository.Service].map(_.userOperations)
            wallet  <- userOps.getWallet
          } yield ResponseExt.json(wallet)
        case Method.POST -> !! / "api" / "auth" / "changePassword" =>
          for {
            userOps <- ZIO.service[Repository.Service].map(_.userOperations)
            body    <- req.bodyAsString
            ret     <- userOps.changePassword(req.session.user, body)
          } yield ResponseExt.json(ret)
        case Method.GET -> !! / "api" / "auth" / "refreshToken" =>
          for {
            secretKey <- Chuti.secretKey
            jwtString <- jwtEncode(req.session, secretKey)
          } yield ResponseExt.json(jwtString)
        case Method.GET -> !! / "api" / "auth" / "doLogout" =>
          for {
            secretKey <- Chuti.secretKey
            clock     <- ZIO.service[Clock.Service].flatMap(_.instant)
            jwtString <- jwtExpire(req.session, secretKey)
          } yield ResponseExt.json(jwtString)
      }).injectSome[Environment & OpsService](SessionProvider.layer(req.session))
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
          )).injectSome[Environment & OpsService](SessionProvider.layer(ChutiSession.adminSession))
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
            .injectSome[Environment & OpsService](SessionProvider.layer(ChutiSession.adminSession))
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
            .injectSome[Environment & OpsService](SessionProvider.layer(ChutiSession.adminSession))
        }
      case Method.GET -> !! / "getInvitedUserByToken" =>
        Http.collectZIO[Request] { req =>
          (for {
            token <- ZIO
              .fromOption(req.queryParams.get("token").toSeq.flatten.headOption)
              .orElseFail(HttpError.BadRequest("Must pass token"))
            tokenHolder <- ZIO.service[TokenHolder.Service]
            user        <- tokenHolder.peek(Token(token), TokenPurpose.NewUser)
          } yield ResponseExt.json(user))
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
          }).injectSome[Environment & OpsService](SessionProvider.layer(ChutiSession.adminSession))
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
            .injectSome[Environment & OpsService](SessionProvider.layer(ChutiSession.adminSession))
        }
      case Method.POST -> !! / "doLogin" =>
        Http.collectZIO[Request] { req =>
          for {
            formData <- req.formData
            email <-
              ZIO.fromOption(formData.get("email")).orElseFail(HttpError.BadRequest("Missing email"))
            password <- ZIO.fromOption(formData.get("password")).orElseFail(HttpError.BadRequest("Missing Password"))

            userOps   <- ZIO.service[Repository.Service].map(_.userOperations)
            login     <- userOps.login(email, password)
            _         <- login.fold(Logging.debug(s"Bad login for $email"))(_ => Logging.debug(s"Good Login for $email"))
            secretKey <- Chuti.secretKey
            jwtString <- ZIO.foreach(login) { user =>
              jwtEncode(ChutiSession(user, req.preferredLocale(availableLocales, formData.get("forcedLocale"))), secretKey)
            }
          } yield {
            jwtString.fold(
              Response(Status.SeeOther, Headers.location("/loginForm?bad=true"))
            )(tok =>
              Response(
                Status.SeeOther,
                Headers.location("/").addHeader(HeaderNames.contentType, HeaderValues.textPlain),
                HttpData.fromCharSequence(tok)
              )
            )
          }
        }
    }

}
