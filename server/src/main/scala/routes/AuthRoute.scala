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

import java.time.LocalDateTime

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server._
import api._
import api.token.{Token, TokenHolder, TokenPurpose}
import better.files.File
import chuti.{BuildInfo => _, _}
import com.softwaremill.session.CsrfDirectives.setNewCsrfToken
import com.softwaremill.session.CsrfOptions.checkHeader
import dao.Repository.UserOperations
import dao.{CRUDOperations, SessionProvider}
import game.GameService
import io.circe.generic.auto._
import mail.Postman.Postman
import mail.{CourierPostman, Postman}
import zio.Cause.Fail
import zio._
import zio.logging.{Logging, log}
import zioslick.RepositoryException

object AuthRoute {
  private def postman: ULayer[Postman] = ZLayer.succeed(CourierPostman.live(config.live))
}

trait AuthRoute
    extends CRUDRoute[User, UserId, PagedStringSearch] with SessionUtils with HasActorSystem {

  lazy val allowed = Set(
    "/login.html",
    "/css/chuti.css",
    "/css/app-sui-theme.css",
    "/chuti-login-opt-bundle.js",
    "/chuti-login-opt-bundle.js.map",
    "/css/app.css",
    "/images/favicon.ico",
    "/images/logo.png",
    "/favicon.ico",
    "/webfonts/fa-solid-900.woff2",
    "/webfonts/fa-solid-900.woff",
    "/webfonts/fa-solid-900.ttf"
  )

  lazy private val adminSession = ChutiSession(GameService.god)

  override def crudRoute: CRUDRoute.Service[User, UserId, PagedStringSearch] =
    new CRUDRoute.Service[User, UserId, PagedStringSearch]() with ZIODirectives with Directives {

      override def deleteOperation(
        objOpt: Option[User]
      ): ZIO[SessionProvider with Logging with OpsService, Throwable, Boolean] = {
        for {
          user <- ZIO.access[SessionProvider](_.get.session.user)
          sup  <- super.deleteOperation(objOpt)
          _    <- SessionUtils.removeFromCache(user.id).ignore
        } yield sup
      }

      override def upsertOperation(
        obj: User
      ): ZIO[SessionProvider with Logging with OpsService, RepositoryException, User] = {
        for {
          user <- ZIO.access[SessionProvider](_.get.session.user)
          sup  <- super.upsertOperation(obj)
          _    <- SessionUtils.removeFromCache(user.id).ignore
        } yield sup
      }

      private val staticContentDir: String =
        config.live.config.getString(s"${config.live.configKey}.staticContentDir")

      override val url: String = "auth"

      override def getPK(obj: User): UserId = obj.id.get

      override def unauthRoute: ZIO[
        Postman with Logging with TokenHolder with OpsService,
        Nothing,
        Route
      ] =
        ZIO
          .environment[
            Postman with Logging with TokenHolder
          ].flatMap { r =>
            for {
              userOps <-
                ZIO
                  .service[CRUDOperations[User, UserId, PagedStringSearch]].map(a =>
                    a.asInstanceOf[UserOperations]
                  )
            } yield {
              extractLog { akkaLog =>
                path("serverVersion") {
                  complete(chuti.BuildInfo.version)
                } ~ path("loginForm") {
                  get {
                    akkaLog
                      .debug(File(s"$staticContentDir/login.html").path.toAbsolutePath.toString)
                    getFromFile(s"$staticContentDir/login.html")
                  }
                } ~
                  path("passwordReset") {
                    post {
                      formFields(Symbol("token").as[String].?, Symbol("password").as[String].?) {
                        (token, password) =>
                          if (token.isEmpty || password.isEmpty)
                            reject(ValidationRejection("You need to pass a token and password"))
                          else {
                            val z: ZIO[
                              SessionProvider with Logging with TokenHolder,
                              Throwable,
                              StandardRoute
                            ] = for {
                              tokenHolder <- ZIO.access[TokenHolder](_.get)
                              userOpt <-
                                tokenHolder
                                  .validateToken(Token(token.get), TokenPurpose.LostPassword)
                              passwordChanged <- ZIO.foreach(userOpt)(user =>
                                userOps.changePassword(user, password.get)
                              )
                            } yield passwordChanged.fold(
                              redirect("/loginForm?passwordChangeFailed", StatusCodes.SeeOther)
                            )(
                              if (_)
                                redirect("/loginForm?passwordChangeSucceeded", StatusCodes.SeeOther)
                              else
                                redirect("/loginForm?passwordChangeFailed", StatusCodes.SeeOther)
                            )

                            z.tapError(e =>
                              log.error("Resetting Password", Fail(e))
                            ).provideSomeLayer[
                                Logging with TokenHolder
                              ](SessionProvider.layer(adminSession)).provide(r)
                          }
                      }
                    }
                  } ~
                  path("passwordRecoveryRequest") {
                    post {
                      entity(as[String]) { email =>
                        complete {
                          (for {
                            postman <- ZIO.service[Postman.Service]
                            userOpt <- userOps.userByEmail(email)
                            _ <-
                              ZIO
                                .foreach(userOpt)(postman.lostPasswordEmail).flatMap(envelope =>
                                  ZIO.foreach(envelope)(postman.deliver)
                                ).forkDaemon
                          } yield ())
                            .tapError(e =>
                              log.error("In Password Recover Request", Fail(e))
                            ).provideSomeLayer[
                              Logging with TokenHolder with Postman
                            ](SessionProvider.layer(adminSession)).provide(r)
                        }
                      }
                    }
                  } ~
                  path("updateInvitedUser") {
                    post {
                      entity(as[UpdateInvitedUserRequest]) { request =>
                        (for {
                          validate <- ZIO.succeed(
                            if (request.user.email.trim.isEmpty)
                              Option("User Email cannot be empty")
                            else if (request.user.name.trim.isEmpty)
                              Option("User Name cannot be empty")
                            else if (
                              request.password.trim.isEmpty || request.password.trim.length < 3
                            )
                              Option("Password is invalid")
                            else
                              None
                          )
                          tokenHolder <- ZIO.service[token.TokenHolder.Service]
                          userOpt <- validate.fold(
                            tokenHolder.validateToken(Token(request.token), TokenPurpose.NewUser)
                          )(_ => ZIO.none)
                          savedUser <- ZIO.foreach(userOpt)(u =>
                            userOps.upsert(u.copy(name = request.user.name, active = true))
                          )
                          passwordChanged <- ZIO.foreach(savedUser)(user =>
                            userOps.changePassword(user, request.password)
                          )
                        } yield validate.fold(
                          passwordChanged.fold(
                            complete(UpdateInvitedUserResponse(None: Option[String]))
                          )(p =>
                            complete(
                              UpdateInvitedUserResponse(Option.when(!p)("Error setting password"))
                            )
                          )
                        )(error => complete(UpdateInvitedUserResponse(Option(error)))))
                          .tapError(e =>
                            log.error("Updating invited user", Fail(e))
                          ).provideSomeLayer[
                            Logging with TokenHolder with Postman
                          ](SessionProvider.layer(adminSession)).provide(r)
                      }
                    }
                  } ~
                  path("getInvitedUserByToken") {
                    parameters(Symbol("token").as[String]) { token =>
                      (for {
                        tokenHolder <- ZIO.access[TokenHolder](_.get)
                        user        <- tokenHolder.peek(Token(token), TokenPurpose.NewUser)
                      } yield complete(user)).provide(r)
                    }
                  } ~
                  path("userCreation") {
                    put {
                      entity(as[UserCreationRequest]) { request =>
                        (for {
                          postman <- ZIO.service[Postman.Service]
                          validate <- ZIO.succeed(
                            if (request.user.email.trim.isEmpty)
                              Option("User Email cannot be empty")
                            else if (request.user.name.trim.isEmpty)
                              Option("User Name cannot be empty")
                            else if (
                              request.password.trim.isEmpty || request.password.trim.length < 3
                            )
                              Option("Password is invalid")
                            else if (request.user.id.nonEmpty)
                              Option("You can't register an existing user")
                            else
                              None
                          )
                          exists <- userOps.userByEmail(request.user.email).map(_.nonEmpty)
                          saved <-
                            if (validate.nonEmpty || exists) ZIO.none
                            else userOps.upsert(request.user.copy(active = false)).map(Option(_))
                          _ <- ZIO.foreach(saved)(userOps.changePassword(_, request.password))
                          _ <- (log.info("About to send") *> ZIO
                              .foreach(saved)(postman.registrationEmail).flatMap(envelope =>
                                ZIO.foreach(envelope)(postman.deliver)
                              ) *> log.info("Maybe sent")).forkDaemon
                        } yield {
                          if (exists)
                            complete(
                              UserCreationResponse(Option("A user with that email already exists"))
                            )
                          else
                            complete(UserCreationResponse(validate))
                        }).tapError(e => log.error("Creating user", Fail(e))).provideSomeLayer[
                            Logging with TokenHolder with Postman
                          ](SessionProvider.layer(adminSession)).provide(r)
                      }
                    }
                  } ~
                  path("confirmRegistration") {
                    get {
                      parameters(Symbol("token").as[String]) { token =>
                        (for {
                          tokenHolder <- ZIO.access[TokenHolder](_.get)
                          user        <- tokenHolder.validateToken(Token(token), TokenPurpose.NewUser)
                          activate <-
                            ZIO.foreach(user)(user => userOps.upsert(user.copy(active = true)))
                        } yield activate.fold(
                          redirect("/loginForm?registrationFailed", StatusCodes.SeeOther)
                        )(_ => redirect("/loginForm?registrationSucceeded", StatusCodes.SeeOther)))
                          .tapError(e =>
                            log.error("Confirming registration", Fail(e))
                          ).provideSomeLayer[
                            Logging with TokenHolder with Postman
                          ](SessionProvider.layer(adminSession)).provide(r)
                      }
                    }
                  } ~
                  path("doLogin") {
                    post {
                      formFields(
                        (
                          Symbol("email").as[String],
                          Symbol("password").as[String]
                        )
                      ) { (email, password) =>
                        (for {
                          login <- userOps.login(email, password)
                          _ <- login.fold(log.debug(s"Bad login for $email"))(_ =>
                            log.debug(s"Good Login for $email")
                          )
                        } yield {
                          login match {
                            case Some(user) =>
                              mySetSession(ChutiSession(user)) {
                                setNewCsrfToken(checkHeader) { ctx =>
                                  ctx.redirect("/", StatusCodes.SeeOther)
                                }
                              }
                            case None =>
                              redirect("/loginForm?bad=true", StatusCodes.SeeOther)
                          }
                        }).tapError(e => log.error("In Login", Fail(e))).provideSomeLayer[
                            Logging with TokenHolder with Postman
                          ](SessionProvider.layer(adminSession)).provide(r)
                      }
                    }
                  } ~
                  pathPrefix("unauth") {
                    (get | put | post) {

                      extractUnmatchedPath { path =>
                        if (allowed(path.toString())) {
                          akkaLog.debug(s"GET $path")
                          encodeResponse {
                            getFromDirectory(staticContentDir)
                          }
                        } else {
                          akkaLog.info(s"Trying to get $path, not in $allowed")
                          reject(AuthorizationFailedRejection)
                        }
                      }
                    }
                  }
              }
            }
          }

      override def other: RIO[SessionProvider with Logging with OpsService, Route] =
        for {
          session <- ZIO.service[SessionProvider.Session].map(_.session)
          runtime <- ZIO.environment[SessionProvider with Logging with OpsService]
        } yield {
          // These should be protected and accessible only when logged in
          path("isFirstLoginToday") {
            get {
              complete {
                (for {
                  userOps       <- ZIO.access[OpsService](_.get).map(a => a.asInstanceOf[UserOperations])
                  firstLoginOpt <- userOps.firstLogin
                } yield firstLoginOpt.fold(true)(_.isAfter(LocalDateTime.now.minusDays(1))))
                  .provide(runtime)
              }
            }
          } ~
            path("whoami") {
              get {
                complete(Option(session.user))
              }
            } ~
            path("userWallet") {
              get {
                complete((for {
                  userOps <- ZIO.access[OpsService](_.get).map(a => a.asInstanceOf[UserOperations])
                  wallet  <- userOps.getWallet
                } yield wallet).provide(runtime))
              }
            } ~
            path("changePassword") {
              post {
                entity(as[String]) { newPassword =>
                  complete((for {
                    user <- ZIO.access[SessionProvider](_.get.session.user)
                    userOps <-
                      ZIO.access[OpsService](_.get).map(a => a.asInstanceOf[UserOperations])
                    changedPassword <- userOps.changePassword(user, newPassword)
                  } yield changedPassword).provide(runtime))
                }
              }
            } ~
            path("doLogout") {
              extractLog { akkaLog =>
                post {
                  myInvalidateSession { ctx =>
                    akkaLog.info(s"Logging out $session")
                    ctx.redirect("/loginForm", StatusCodes.SeeOther)
                  }
                } ~
                  get {
                    myInvalidateSession { ctx =>
                      akkaLog.info(s"Logging out $session")
                      ctx.redirect("/loginForm", StatusCodes.SeeOther)
                    }
                  }
              }
            }
        }
    }

}
