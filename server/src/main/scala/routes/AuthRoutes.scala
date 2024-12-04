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

import api.auth.Auth.*
import api.token.{Token, TokenHolder, TokenPurpose}
import api.{ChutiEnvironment, ChutiSession, token}
import cats.data.NonEmptyList
import chuti.*
import chuti.UserId.*
import dao.{CRUDOperations, Repository}
import io.circe.Encoder
import io.circe.generic.auto.*
import mail.Postman
import util.*
import zio.http.{handler, *}
import zio.logging.*
import zio.{Clock, ZIO}

import java.time.Instant
import java.util.Locale
import scala.language.unsafeNulls

object AuthRoutes extends CRUDRoutes[User, UserId, PagedStringSearch] {

  given localeEncoder: Encoder[Locale] = Encoder.encodeString.contramap(_.toString)

  // TODO get from config
  val availableLocales: NonEmptyList[Locale] = NonEmptyList.of("es_MX", "es", "en-US", "en", "eo").map(t => Locale.forLanguageTag(t).nn)

  override type OpsService = CRUDOperations[User, UserId, PagedStringSearch]

  override val url: String = "auth"

  override def getPK(obj: User): UserId = obj.id.get

  override def authOther: Routes[ChutiEnvironment & ChutiSession, Throwable] =
    Routes(
      Method.GET / "api" / "auth" / "isFirstLoginToday" -> handler { (_: Request) =>
        for {
          userOps       <- ZIO.serviceWith[Repository](_.userOperations)
          now           <- Clock.instant
          firstLoginOpt <- userOps.firstLogin
        } yield ResponseExt.json(firstLoginOpt.fold(true)(_.isAfter(now.minus(java.time.Duration.ofDays(1)).nn)))
      },
      Method.GET / "api" / "auth" / "locale" -> handler { (_: Request) =>
        for {
          session <- ZIO.service[ChutiSession]
        } yield ResponseExt.json(session.locale.toLanguageTag.nn)
      },
      Method.PUT / "api" / "auth" / "locale" -> handler { (req: Request) =>
        for {
          locale           <- req.body.asString
          session          <- ZIO.service[ChutiSession]
          sessionTransport <- ZIO.service[SessionTransport[ChutiSession]]
          response         <- sessionTransport.refreshSession(session.copy(locale = Locale.forLanguageTag(locale).nn), ResponseExt.json(true))
        } yield response
      },
      Method.GET / "api" / "auth" / "whoami" -> handler { (_: Request) =>
        ZIO.serviceWith[ChutiSession](session => ResponseExt.json(session.user))
      },
      Method.GET / "api" / "auth" / "userWallet" -> handler { (_: Request) =>
        for {
          userOps <- ZIO.service[Repository].map(_.userOperations)
          wallet  <- userOps.getWallet
        } yield ResponseExt.json(wallet)
      },
      Method.POST / "api" / "auth" / "changePassword" -> handler { (req: Request) =>
        for {
          userOps <- ZIO.service[Repository].map(_.userOperations)
          session <- ZIO.service[ChutiSession]
          newPass <- req.body.asString
          ret     <- userOps.changePassword(session.user, newPass)
        } yield ResponseExt.json(ret)
      },
      Method.GET / "api" / "auth" / "refreshToken" -> handler { (_: Request) =>
        for {
          session          <- ZIO.service[ChutiSession]
          sessionTransport <- ZIO.service[SessionTransport[ChutiSession]]
          response         <- sessionTransport.refreshSession(session, ResponseExt.json(true))
        } yield response
      },
      Method.GET / "api" / "auth" / "doLogout" -> handler { (_: Request) =>
        for {
          session          <- ZIO.service[ChutiSession]
          sessionTransport <- ZIO.service[SessionTransport[ChutiSession]]
          loginFormURL     <- ZIO.fromEither(URL.decode("/loginForm")).mapError(GameException.apply)
          response         <- sessionTransport.invalidateSession(session, Response(Status.SeeOther, Headers(Header.Location(loginFormURL))))
        } yield response
      }
    )

  override def unauthRoute: Routes[ChutiEnvironment, Throwable] =
    Routes(
      Method.GET / "serverVersion" -> handler { (_: Request) =>
        ResponseExt.json(chuti.BuildInfo.version)
      },
      Method.POST / "passwordReset" -> handler { (req: Request) =>
        (for {
          formData <- req.formData
          token = formData.get("token")
          password = formData.get("password")
          _           <- ZIO.fail(SessionError("You need to pass a token and password")).when(token.isEmpty || password.isEmpty)
          userOps     <- ZIO.service[Repository].map(_.userOperations)
          tokenHolder <- ZIO.service[TokenHolder]
          userOpt <-
            tokenHolder
              .validateToken(Token(token.get), TokenPurpose.LostPassword)
          passwordChangeFailed    <- ResponseExt.seeOther("/loginForm?passwordChangeFailed")
          passwordChangeSucceeded <- ResponseExt.seeOther("/loginForm?passwordChangeSucceeded")
          passwordChanged         <- ZIO.foreach(userOpt)(user => userOps.changePassword(user, password.get))
        } yield passwordChanged.fold(
          passwordChangeFailed
        )(isChanged =>
          if (isChanged)
            passwordChangeSucceeded
          else
            passwordChangeFailed
        )).provideSomeLayer[ChutiEnvironment](ChutiSession.adminSession.toLayer)
      },
      Method.POST / "passwordRecoveryRequest" -> handler { (req: Request) =>
        (for {
          emailJson <- req.body.asString
          email     <- ZIO.fromEither(io.circe.parser.decode[String](emailJson)).mapError(GameException.apply)
          userOps   <- ZIO.service[Repository].map(_.userOperations)
          postman   <- ZIO.service[Postman]
          userOpt   <- userOps.userByEmail(email)
          _         <- ZIO.foreach(userOpt)(postman.lostPasswordEmail).flatMap(envelope => ZIO.foreach(envelope)(postman.deliver)).forkDaemon
        } yield Response.ok)
          .provideSomeLayer[ChutiEnvironment & OpsService](ChutiSession.adminSession.toLayer)
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
          .provideSomeLayer[ChutiEnvironment & OpsService](ChutiSession.adminSession.toLayer)
      },
      Method.GET / "getInvitedUserByToken" -> handler { (req: Request) =>
        for {
          token <- ZIO
            .fromOption(req.queryParam("token"))
            .orElseFail(GameException("Must pass token"))
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
        }).provideSomeLayer[ChutiEnvironment & OpsService](ChutiSession.adminSession.toLayer)
      },
      Method.GET / "confirmRegistration" -> handler { (req: Request) =>
        (for {
          formData                    <- req.formData
          token                       <- ZIO.fromOption(formData.get("token")).orElseFail(GameException("Token not found"))
          userOps                     <- ZIO.service[Repository].map(_.userOperations)
          tokenHolder                 <- ZIO.service[TokenHolder]
          user                        <- tokenHolder.validateToken(Token(token), TokenPurpose.NewUser)
          registrationFailedResponse  <- ResponseExt.seeOther("/loginForm?registrationFailed")
          registrationSucceedResponse <- ResponseExt.seeOther("/loginForm?registrationSucceeded")
          activate <-
            ZIO.foreach(user)(user => userOps.upsert(user.copy(active = true)))
        } yield activate.fold(registrationFailedResponse)(_ => registrationSucceedResponse))
          .provideSomeLayer[ChutiEnvironment & OpsService](ChutiSession.adminSession.toLayer)
      },
      Method.POST / "doLogin" -> handler { (req: Request) =>
        for {
          formData <- req.formData
          email    <- ZIO.fromOption(formData.get("email")).orElseFail(GameException("Missing email"))
          password <- ZIO.fromOption(formData.get("password")).orElseFail(GameException("Missing Password"))

          userOps          <- ZIO.service[Repository].map(_.userOperations)
          login            <- userOps.login(email, password)
          _                <- login.fold(ZIO.logDebug(s"Bad login for $email"))(_ => ZIO.logDebug(s"Good Login for $email"))
          sessionTransport <- ZIO.service[SessionTransport[ChutiSession]]
          logingFormUrl    <- ZIO.fromEither(URL.decode("/loginForm?bad=true")).mapError(GameException.apply)
          rootUrl          <- ZIO.fromEither(URL.decode("/")).mapError(GameException.apply)
          res <- login.fold(ZIO.succeed(Response(Status.SeeOther, Headers(Header.Location(logingFormUrl))))) { user =>
            sessionTransport.refreshSession(
              ChutiSession(user, req.preferredLocale(availableLocales, formData.get("forcedLocale"))),
              Response(
                Status.SeeOther,
                Headers(Header.Location(rootUrl), Header.ContentType(MediaType.text.plain))
              )
            )
          }
        } yield res
      }
    )

}
