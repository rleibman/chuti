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

package mail

import api.config.Config
import api.token.{TokenHolder, TokenPurpose}
import chuti.{Game, User}
import courier.{Envelope, Mailer, Multipart}
import javax.mail.internet.InternetAddress
import mail.Postman.Postman
import zio.{Has, RIO, ZIO}

object Postman {
  type Postman = Has[Service]

  trait Service {
    def deliver(email: Envelope): ZIO[Postman, Throwable, Unit]
    def webHostName: String

    //You may want to move these to a different service if you wanted to keep the mechanics of sending and the content separate
    def inviteNewFriendEmail(
      user:    User,
      invited: User
    ): RIO[TokenHolder, Envelope] =
      for {
        tokenHolder <- ZIO.access[TokenHolder](_.get)
        token       <- tokenHolder.createToken(invited, TokenPurpose.NewUser)
      } yield {
        val linkUrl = s"http://${webHostName}/acceptFriendInvite?token=$token"
        Envelope
          .from(new InternetAddress("admin@chuti.fun", "Chuti Administrator"))
          .replyTo(new InternetAddress(user.email, user.name))
          .to(new InternetAddress(invited.email, invited.name))
          .subject(s"${user.name.capitalize} te invito a ser su amigo en ${webHostName}")
          .content(Multipart().html(s"""<html><body>
                                     |<p>${user.name.capitalize}<p> Te invito a ser su amigo en chuti.fun</p>
                                     |<p>Si quieres aceptar, ve a <a href="$linkUrl">$linkUrl</a></p>
                                     |<p>Te esperamos pronto! </p>
                                     |</body></html>""".stripMargin))
      }

    def inviteExistingUserFriendEmail(
      user:    User,
      invited: User
    ): RIO[TokenHolder, Envelope] =
      for {
        tokenHolder <- ZIO.access[TokenHolder](_.get)
        token       <- tokenHolder.createToken(invited, TokenPurpose.FriendToken)
      } yield {
        val linkUrl = s"http://${webHostName}/newUserAcceptFriend?token=$token"
        Envelope
          .from(new InternetAddress("admin@chuti.fun", "Chuti Administrator"))
          .replyTo(new InternetAddress(user.email, user.name))
          .to(new InternetAddress(invited.email, invited.name))
          .subject(s"${user.name.capitalize} te invito a ser su amigo en chuti.fun")
          .content(Multipart().html(s"""<html><body>
               |<p>${user.name.capitalize}<p> Te invito a ser su amigo en chuti.fun</p>
               |<p>Si quieres aceptar, ve a <a href="$linkUrl">$linkUrl</a></p>
               |<p>Te esperamos pronto! </p>
               |</body></html>""".stripMargin))
      }

    def inviteToGameEmail(
      user:    User,
      invited: User,
      game:    Game
    ): RIO[TokenHolder, Envelope] =
      for {
        tokenHolder <- ZIO.access[TokenHolder](_.get)
        token       <- tokenHolder.createToken(invited, TokenPurpose.GameInvite)
      } yield {
        val linkUrl =
          s"http://${webHostName}/acceptGameInvite?gameId=${game.id.getOrElse(0)}&token=$token"
        Envelope
          .from(new InternetAddress("admin@chuti.fun", "Chuti Administrator"))
          .replyTo(new InternetAddress(user.email, user.name))
          .to(new InternetAddress(invited.email, invited.name))
          .subject(s"${user.name.capitalize} te invito a jugar chuti")
          .content(Multipart().html(s"""<html><body>
                                     |<p>${user.name.capitalize}<p> Te invito a jugar chuti, hasta ahorita se han apuntado en este juego</p>
                                     |<p>${game.jugadores.map(_.user.name).mkString(",")}</p>
                                     |<p>Si quieres aceptar, ve a <a href="$linkUrl">$linkUrl</a></p>
                                     |<p>Te esperamos pronto! </p>
                                     |</body></html>""".stripMargin))
      }

    def lostPasswordEmail(user: User): RIO[TokenHolder, Envelope] =
      for {
        tokenHolder <- ZIO.access[TokenHolder](_.get)
        token       <- tokenHolder.createToken(user, TokenPurpose.LostPassword)
      } yield {
        val linkUrl = s"http://${webHostName}/loginForm?passwordReset=true&token=$token"
        Envelope
          .from(new InternetAddress("admin@chuti.fun", "Chuti Administrator"))
          .to(new InternetAddress(user.email))
          .content(Multipart().html(s"""<html><body>
                                 | <p>We are sorry you've lost your password</p>
                                 | <p>We have temporarily created a link for you that will allow you to reset it.</p>
                                 | <p>Please go here to reset it: <a href="$linkUrl">$linkUrl</a>.</p>
                                 | <p>Note that this token will expire after a while, please change your password as soon as you can</p>
                                 |</body></html>""".stripMargin))
      }

    def registrationEmail(user: User): RIO[TokenHolder, Envelope] =
      for {
        tokenHolder <- ZIO.access[TokenHolder](_.get)
        token       <- tokenHolder.createToken(user, TokenPurpose.NewUser)
      } yield {
        val linkUrl =
          s"http://${webHostName}/confirmRegistration?token=$token"
        Envelope
          .from(new InternetAddress("admin@chuti.fun", "Chuti Administrator"))
          .to(new InternetAddress(user.email))
          .subject("Welcome to chuti!")
          .content(Multipart().html(s"""<html><body>
                                       | <p>Thank you for registering!</p>
                                       | <p>All you need to do now is go here to confirm your registration.</p>
                                       | <p>Please go here: <a href="$linkUrl">$linkUrl</a>.</p>
                                       | <p>Note that this token will expire after a while, if you take too long you'll have to register again</p>
                                       |</body></html>""".stripMargin))
      }
  }
}

/**
  * An instatiation of the Postman that user the courier mailer
  */
object CourierPostman {
  def live(config: Config.Service): Postman.Service = new Postman.Service {
    lazy val mailer: Mailer = {
      val localhost = config.config.getString(s"${config.configKey}.smtp.localhost")
      System.setProperty("mail.smtp.localhost", localhost)
      System.setProperty("mail.smtp.localaddress", localhost)
      val auth = config.config.getBoolean(s"${config.configKey}.smtp.auth")
      if (auth)
        Mailer(
          config.config.getString(s"${config.configKey}.smtp.host"),
          config.config.getInt(s"${config.configKey}.smtp.port")
        ).auth(auth)
          .as(
            config.config.getString(s"${config.configKey}.smtp.user"),
            config.config.getString(s"${config.configKey}.smtp.password")
          )
          .startTls(config.config.getBoolean(s"${config.configKey}.smtp.startTTLS"))()
      else
        Mailer(
          config.config.getString(s"${config.configKey}.smtp.host"),
          config.config.getInt(s"${config.configKey}.smtp.port")
        ).auth(auth)()
    }

    override def deliver(email: Envelope): ZIO[Postman, Throwable, Unit] =
      ZIO.fromFuture(implicit ec => mailer(email))

    override def webHostName: String = config.config.getString(s"${config.configKey}.webhostname")

  }
}
