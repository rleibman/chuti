package api

import java.math.BigInteger
import java.security.SecureRandom

import chuti.User
import scalacache.Cache
import scalacache.caffeine.CaffeineCache
import zio.{Has, Task}

import scala.concurrent.duration.{Duration, _}

package object token {

  sealed trait TokenPurpose
  object TokenPurpose {
    case object NewUser extends TokenPurpose
    case object LostPassword extends TokenPurpose
    case object FriendToken extends TokenPurpose
    case object GameInvite extends TokenPurpose
  }

  type TokenHolder = Has[TokenHolder.Service]

  case class Token(tok: String)

  object TokenHolder {
    trait Service {
      def createToken(
        user:    User,
        purpose: TokenPurpose,
        ttl:     Option[Duration] = Option(3.hours)
      ): Task[Token]
      def validateToken(
        token:   Token,
        purpose: TokenPurpose
      ): Task[Option[User]]
    }

    val live: Service = new Service {

      private val random = SecureRandom.getInstanceStrong
      import scalacache.ZioEffect.modes._
      implicit val userTokenCache: Cache[User] = CaffeineCache[User]

      override def createToken(
        user:    User,
        purpose: TokenPurpose,
        ttl:     Option[Duration] = Option(3.hours)
      ): Task[Token] = {
        val t = new BigInteger(12 * 5, random).toString(32)
        scalacache.put(t, purpose)(user, ttl).as(Token(t))
      }

      override def validateToken(
        token:   Token,
        purpose: TokenPurpose
      ): Task[Option[User]] = {
        for {
          u <- scalacache.get(token.tok, purpose)
          _ <- scalacache.remove(token.tok, purpose)
        } yield u
      }
    }
  }

}
