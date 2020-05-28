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

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.{
  AuthorizationFailedRejection,
  Directives,
  Route,
  ValidationRejection
}
import api._
import api.token.{Token, TokenHolder, TokenPurpose}
import better.files.File
import chuti.{BuildInfo => _, _}
import com.softwaremill.session.CsrfDirectives.setNewCsrfToken
import com.softwaremill.session.CsrfOptions.checkHeader
import dao.{DatabaseProvider, Repository, SessionProvider}
import game.GameService.GameLayer
import game.{GameService, UserConnectionRepo}
import io.circe.generic.auto._
import mail.CourierPostman
import mail.Postman.Postman
import slick.basic.BasicBackend
import zio._
import zio.logging.log
import zio.logging.slf4j.Slf4jLogger

object AuthRoute {
  private def postman: ULayer[Postman] = ZLayer.succeed(CourierPostman.live(config.live))
}

trait AuthRoute
    extends CRUDRoute[User, UserId, PagedStringSearch] with SessionUtils with HasActorSystem {
  this: Repository.Service with DatabaseProvider.Service =>

  lazy val allowed = Set(
    "/login.html",
    "/css/chuti.css",
    "/css/app-sui-theme.css",
    "/chuti-login-opt-bundle.js",
    "/chuti-login-opt-bundle.js.map",
    "/css/app.css",
    "/images/favicon.png",
    "/favicon.ico",
    "/images/favicon.svg",
    "/webfonts/fa-solid-900.woff2",
    "/webfonts/fa-solid-900.woff",
    "/webfonts/fa-solid-900.ttf"
  )

  def repositoryLayer:       ULayer[Repository] = ZLayer.succeed(this)
  def databaseProviderLayer: ULayer[DatabaseProvider] = ZLayer.succeed(this)
  def postmanLayer:          ULayer[Postman] = AuthRoute.postman

  def gameLayer(session: ChutiSession): Layer[Nothing, GameLayer] =
    zio.console.Console.live ++
      SessionProvider.layer(session) ++
      databaseProviderLayer ++
      repositoryLayer ++
      postmanLayer ++
      Slf4jLogger.make((_, b) => b) ++
      ZLayer.succeed(TokenHolder.live) ++
      ZLayer.succeed(UserConnectionRepo.live)

  lazy private val adminSession = ChutiSession(GameService.god)

  override def crudRoute: CRUDRoute.Service[User, UserId, PagedStringSearch] =
    new CRUDRoute.Service[User, UserId, PagedStringSearch]() with ZIODirectives with Directives {

      private val staticContentDir: String =
        config.live.config.getString(s"${config.live.configKey}.staticContentDir")

      override val url: String = "auth"

      override val ops: Repository.UserOperations = userOperations

      override def getPK(obj: User): UserId = obj.id.get

      override val databaseProvider: DatabaseProvider.Service = new DatabaseProvider.Service {
        override def db: UIO[BasicBackend#DatabaseDef] = AuthRoute.this.db
      }

      override def unauthRoute: Route =
        extractLog { akkaLog =>
          path("serverVersion") {
            complete(chuti.BuildInfo.version)
          } ~ path("loginForm") {
            get {
              akkaLog.debug(File(s"$staticContentDir/login.html").path.toAbsolutePath.toString)
              getFromFile(s"$staticContentDir/login.html")
            }
          } ~
            path("passwordReset") {
              post {
                formFields(Symbol("token").as[String].?, Symbol("password").as[String].?) {
                  (token, password) =>
                    if (token.isEmpty || password.isEmpty) {
                      reject(ValidationRejection("You need to pass a token and password"))
                    } else {
                      val z = for {
                        tokenHolder <- ZIO.access[TokenHolder](_.get)
                        userOpt <- tokenHolder
                          .validateToken(Token(token.get), TokenPurpose.LostPassword)
                        passwordChanged <- ZIO.foreach(userOpt)(user =>
                          ops.changePassword(user, password.get)
                        )
                      } yield passwordChanged.fold(
                        redirect("/loginForm?passwordChangeFailed", StatusCodes.SeeOther)
                      )(changed =>
                        redirect("/loginForm?passwordChangeSucceeded", StatusCodes.SeeOther)
                      )

                      z.provideLayer(fullLayer(adminSession)).tapError(e =>
                          Task.succeed(akkaLog.error(e, "Resetting Password"))
                        )
                    }
                }
              }
            } ~
            path("passwordRecoveryRequest") {
              post {
                entity(as[String]) { email =>
                  complete {
                    (for {
                      postman <- ZIO.access[Postman](_.get)
                      userOpt <- ops.userByEmail(email)
                      envelopeOpt <- ZIO.foreach(userOpt) { user =>
                        postman.lostPasswordEmail(user)
                      }
                      emailed <- ZIO.foreach(envelopeOpt) { envelope =>
                        postman.deliver(envelope)
                      }

                    } yield emailed.nonEmpty)
                      .provideLayer(gameLayer(adminSession)).tapError(e =>
                        Task.succeed(akkaLog.error(e, "In Password Recover Request"))
                      )
                  }
                }
              }
            } ~
            path("userCreation") {
              put {
                entity(as[UserCreationRequest]) { request =>
                  (for {
                    postman <- ZIO.access[Postman](_.get)
                    validate <- ZIO.succeed(
                      if (request.user.email.trim.isEmpty)
                        Option("User Email cannot be empty")
                      else if (request.user.name.trim.isEmpty)
                        Option("User Name cannot be empty")
                      else if (request.password.trim.isEmpty || request.password.trim.length < 3)
                        Option("Password is invalid")
                      else if (request.user.id.nonEmpty)
                        Option("You can't register an existing user")
                      else
                        None
                    )
                    exists <- ops.userByEmail(request.user.email).map(_.nonEmpty)
                    saved <- if (validate.nonEmpty || exists) ZIO.none
                    else ops.upsert(request.user.copy(active = false)).map(Option(_))
                    _        <- ZIO.foreach(saved)(ops.changePassword(_, request.password))
                    envelope <- ZIO.foreach(saved)(postman.registrationEmail)
                    _        <- ZIO.foreach(envelope)(postman.deliver)
                  } yield {
                    if (exists)
                      complete(
                        UserCreationResponse(Option("A user with that email already exists"))
                      )
                    else
                      complete(UserCreationResponse(validate))
                  }).provideLayer(gameLayer(adminSession)).tapError(e =>
                      Task.succeed(akkaLog.error(e, "Creating user"))
                    )
                }
              }
            } ~
            path("confirmRegistration") {
              get {
                parameters(Symbol("token").as[String]) { token =>
                  (for {
                    tokenHolder <- ZIO.access[TokenHolder](_.get)
                    user        <- tokenHolder.validateToken(Token(token), TokenPurpose.NewUser)
                    activate <- ZIO.foreach(user)(user =>
                      userOperations.upsert(user.copy(active = true))
                    )
                  } yield activate.fold(
                    redirect("/loginForm?registrationFailed", StatusCodes.SeeOther)
                  )(_ => redirect("/loginForm?registrationSucceeded", StatusCodes.SeeOther)))
                    .provideLayer(fullLayer(adminSession)).tapError(e =>
                      Task.succeed(akkaLog.error(e, "Confirming registration"))
                    )
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
                    login <- ops.login(email, password)
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
                  }).provideLayer(fullLayer(adminSession)).tapError(e =>
                      Task.succeed(akkaLog.error(e, "In Login"))
                    )
                }
              }
            } ~
            pathPrefix("unauth") {
              (get | put | post) {

                extractUnmatchedPath { path =>
                  if (allowed(path.toString())) {
                    log.debug(s"GET $path")
                    encodeResponse {
                      getFromDirectory(staticContentDir)
                    }
                  } else {
                    log.info(s"Trying to get $path, not in $allowed")
                    reject(AuthorizationFailedRejection)
                  }
                }
              }
            }
        }

      override def other(session: ChutiSession): Route =
        //randomTokenCsrfProtection(checkHeader) //TODO this is necessary, but it wasn't working, so we're leaving it for now.
        // This should be protected and accessible only when logged in
        path("whoami") {
          get {
            complete(Option(session.user))
          }
        } ~
          path("doLogout") {
            extractLog { log =>
              post {
                myInvalidateSession { ctx =>
                  log.info(s"Logging out $session")
                  ctx.redirect("/loginForm", StatusCodes.SeeOther)
                }
              } ~
                get {
                  myInvalidateSession { ctx =>
                    log.info(s"Logging out $session")
                    ctx.redirect("/loginForm", StatusCodes.SeeOther)
                  }
                }
            }
          }
    }

}
