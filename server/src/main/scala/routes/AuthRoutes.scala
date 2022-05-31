package routes

import api.Chuti.Environment
import chuti.{PagedStringSearch, User, UserId}
import dao.SessionProvider
import zhttp.http.*
import zio.ZIO
import zio.logging.Logging

object AuthRoutes extends CRUDRoutes[User, UserId, PagedStringSearch] {
  override val url: String = "auth"

  override def getPK(obj: User): UserId = obj.id.get

  override def other: Http[Environment & SessionProvider, Throwable, Request, Response] = Http.route[Request] {
    case Method.GET -> !! / "api" / "auth" / "serverVersion" => Http.empty
    case Method.GET -> !! / "api" / "auth" / "loginForm" => Http.empty
    case Method.GET -> !! / "api" / "auth" / "passwordReset" => Http.empty
    case Method.GET -> !! / "api" / "auth" / "passwordRecoveryRequest" => Http.empty
    case Method.GET -> !! / "api" / "auth" / "updateInvitedUser" => Http.empty
    case Method.GET -> !! / "api" / "auth" / "getInvitedUserByToken" => Http.empty
    case Method.GET -> !! / "api" / "auth" / "userCreation" => Http.empty
    case Method.GET -> !! / "api" / "auth" / "confirmRegistration" => Http.empty
    case Method.GET -> !! / "api" / "auth" / "doLogin" => Http.empty
    case Method.GET -> !! / "api" / "auth" / "unauth" => Http.empty
  }


  override def unauthRoute: Http[Environment, Throwable, Request, Response] =
    Http.route[Request] {
      case Method.GET -> !! / "api" / "auth" / "isFirstLoginToday" => Http.empty
      case Method.GET -> !! / "api" / "auth" / "locale" => Http.empty
      case Method.GET -> !! / "api" / "auth" / "whoami" => Http.empty
      case Method.GET -> !! / "api" / "auth" / "userWallet" => Http.empty
      case Method.GET -> !! / "api" / "auth" / "changePassword" => Http.empty
      case Method.GET -> !! / "api" / "auth" / "doLogout" => Http.empty
    }
}
