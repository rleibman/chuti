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

import auth.*
import auth.oauth.OAuthUserInfo
import chuti.{*, given}
import courier.{Envelope, Multipart}
import dao.ZIORepository
import mail.Postman
import zio.*
import zio.json.*
import zio.json.ast.Json

import javax.mail.internet.InternetAddress

object ChutiAuthServer {

  val live: ZLayer[
    ConfigurationService & Postman & ZIORepository,
    ConfigurationError,
    AuthServer[User, Option[UserId], ConnectionId]
  ] = ZLayer.fromZIO {
    for {
      config  <- ZIO.serviceWithZIO[ConfigurationService](_.appConfig)
      postman <- ZIO.service[Postman]
      repo    <- ZIO.serviceWith[ZIORepository](_.userOperations)
    } yield new AuthServer[User, Option[UserId], ConnectionId] {
      override def getPK(user: User): Option[UserId] = user.id

      override def login(
        userName:     String,
        password:     String,
        connectionId: Option[ConnectionId]
      ): IO[AuthError, Option[User]] =
        repo.login(userName, password).provide(ChutiSession.godSession.toLayer).mapError(AuthError(_))

      override def logout(): ZIO[Session[User, ConnectionId], AuthError, Unit] =
        ZIO.serviceWithZIO[Session[User, ConnectionId]](u => ZIO.logDebug(s"User ${u.user} logged out"))

      override def changePassword(
        userPK:      Option[UserId],
        newPassword: String
      ): ZIO[Session[User, ConnectionId], AuthError, Unit] =
        (for {
          id <- ZIO.fromOption(userPK).orElseFail(AuthBadRequest("You cannot change the password without a user id"))
          userOpt <- repo.get(id)
          user    <- ZIO.fromOption(userOpt).orElseFail(AuthBadRequest(s"User with id ${id.value} not found"))
          _       <- repo.changePassword(user, newPassword).mapError(AuthError(_)).unit
        } yield ()).mapError(AuthError.apply)

      override def userByEmail(email: String): IO[AuthError, Option[User]] =
        repo.userByEmail(email).provide(ChutiSession.godSession.toLayer).mapError(AuthError(_))

      override def userByPK(pk: UserId): IO[AuthError, Option[User]] =
        repo.get(pk).provide(ChutiSession.godSession.toLayer).mapError(AuthError(_))

      override def createUser(
        name:     String,
        email:    String,
        password: String
      ): IO[AuthError, User] =
        (for {
          existingByEmail <- repo.userByEmail(email)

          // Only reject if either email OR name exists AND is already activated
          // If inactive user exists by email, delete it to allow re-registration
          _ <- ZIO.fail(EmailAlreadyExists(email)).when(existingByEmail.exists(_.active))

          // Delete any existing inactive users with this email or name
          _ <- ZIO.foreachDiscard(existingByEmail.filter(!_.active))(u => repo.delete(u.id.get, softDelete = false))

          now <- Clock.instant

          // Create brand new user
          user <- repo.upsert(
            User(id = None, email = email, name = name, created = now, lastUpdated = now, active = false)
          )
          _ <- changePassword(user.id, password)
        } yield user).provide(ChutiSession.godSession.toLayer).mapError(AuthError(_))

      override def sendEmail(
        subject: String,
        body:    String,
        user:    User
      ): IO[AuthError, Unit] = {
        val smtpConfig = config.chuti.smtp
        val baseEmail = Envelope
          .from(new InternetAddress(smtpConfig.fromEmail, smtpConfig.fromName))
          .to(new InternetAddress(user.email))
          .subject(subject)
          .content(Multipart().html(body))
        val email =
          if (smtpConfig.bccEmail.nonEmpty)
            baseEmail.bcc(new InternetAddress(smtpConfig.bccEmail))
          else
            baseEmail
        postman.deliver(email)
      }

      override def activateUser(userPK: UserId): IO[AuthError, Unit] =
        (for {
          _    <- ZIO.logInfo(s"Activating user with PK: ${userPK.value}")
          user <- repo.get(userPK)
          _ <- ZIO.logInfo(s"Found user: ${user.map(u => s"${u.email} (active=${u.active})").getOrElse("NOT FOUND")}")
          _ <- ZIO.fail(AuthBadRequest(s"user ${userPK.value} not found")).when(user.isEmpty)
          result <- user.fold(ZIO.unit)(u =>
            ZIO.logInfo(s"Updating user ${u.email} to active=true") *>
              repo.upsert(u.copy(active = true)).unit
          )
          _ <- ZIO.logInfo(s"User ${userPK.value} activated successfully")
        } yield ())
          .provide(ChutiSession.godSession.toLayer)
          .mapError(AuthError(_))
          .tapError(e => ZIO.logErrorCause(s"Error activating user ${userPK.value}", Cause.fail(e)))

      override def getEmailBodyHtml(
        user:    User,
        purpose: UserCodePurpose,
        url:     String
      ): String =
        purpose match {
          case UserCodePurpose.LostPassword =>
            s"""<html><body>
               | <p>So sorry you lost your password</p>
               | <p>Here's a link to generate a new one.</p>
               | <p>please Click here: <a href="https://${config.chuti.smtp.webHostname}/#$url">https://${config.chuti.smtp.webHostname}/#$url</a>.</p>
               | <p>Note that this link has a limited time</p>
               |</body></html>""".stripMargin
          case UserCodePurpose.NewUser =>
            s"""<html><body>
               | <p>Thanks for registering!</p>
               | <p>All you have to do now is activate your account.</p>
               | <p>Please click here: <a href="https://${config.chuti.smtp.webHostname}/#$url">https://${config.chuti.smtp.webHostname}/#$url</a>.</p>
               | <p>Note that this link is time sensitive, eventually it will expire</p>
               |</body></html>""".stripMargin
        }

      // OAuth Methods

      override def userByOAuthProvider(
        provider:   String,
        providerId: String
      ): IO[AuthError, Option[User]] = ???
//        repo
//          .userByOAuthProvider(provider, providerId).provide(ChutiSession.adminSession.toLayer).mapError(
//            AuthError(_)
//          )

      override def createOAuthUser(
        oauthInfo:    OAuthUserInfo,
        provider:     String,
        connectionId: Option[ConnectionId]
      ): IO[AuthError, User] = ???
//        (for {
//          now <- Clock.instant
//          newUser = User(
//            id = None,
//            email = oauthInfo.email,
//            name = oauthInfo.name,
//            created = now,
//            active = oauthInfo.emailVerified, // Auto-activate if email is verified by OAuth provider
//            oauth = Some(OAuthUserData(provider, oauthInfo.providerId, Some(oauthInfo.rawData.toJson)))
//          )
//          user <- repo.upsert(newUser)
//        } yield user).provide(ChutiSession.godSession.toLayer).mapError(AuthError(_))

      override def linkOAuthToUser(
        user:         User,
        provider:     String,
        providerId:   String,
        providerData: Json
      ): IO[AuthError, User] = ???
//        (for {
//          updatedUser <- repo.upsert(
//            user.copy(
//              oauth = Some(OAuthUserData(provider, providerId, Some(providerData.toJson)))
//            )
//          )
//        } yield updatedUser).provide(ChutiSession.adminSession.toLayer).mapError(AuthError(_))
    }
  }

}
