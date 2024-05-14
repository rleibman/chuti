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

package mail

import api.config.ConfigurationService
import api.token.{TokenHolder, TokenPurpose}
import chuti.{Game, User}
import courier.{Envelope, Mailer, Multipart}
import zio.*

import javax.mail.internet.InternetAddress

trait Postman {

  def deliver(email: Envelope): Task[Unit]

  def webHostName: String

  // You may want to move these to a different service if you wanted to keep the mechanics of sending and the content separate
  def inviteToPlayByEmail(
    user:    User,
    invited: User
  ): RIO[TokenHolder, Envelope] =
    for {
      tokenHolder <- ZIO.service[TokenHolder]
      token       <- tokenHolder.createToken(invited, TokenPurpose.NewUser, Option(3.days))
    } yield {
      val linkUrl = s"http://$webHostName/loginForm?newUserAcceptFriend&token=$token"
      Envelope
        .from(new InternetAddress("admin@chuti.fun", "Chuti Administrator"))
        .replyTo(new InternetAddress(user.email, user.name))
        .to(new InternetAddress(invited.email, invited.name))
        .subject(s"${user.name.capitalize} te invitó a ser su amigo en chuti.fun")
        .content(Multipart().html(s"""<html><body>
             |<p>${user.name.capitalize}<p> Te invitó a ser su amigo y a jugar en chuti.fun</p>
             |<p>Si quieres aceptar, ve a <a href="$linkUrl">$linkUrl</a></p>
             |<p>Te esperamos pronto! </p>
             |</body></html>""".stripMargin))
    }

  def inviteToGameEmail(
    user:    User,
    invited: User,
    game:    Game
  ): RIO[TokenHolder, Envelope] =
    ZIO.succeed {
      val linkUrl =
        s"http://$webHostName/#lobby"
      Envelope
        .from(new InternetAddress("admin@chuti.fun", "Chuti Administrator"))
        .replyTo(new InternetAddress(user.email, user.name))
        .to(new InternetAddress(invited.email, invited.name))
        .subject(s"${user.name.capitalize} te invitó a jugar chuti en chuti.fun")
        .content(Multipart().html(s"""<html><body>
             |<p>${user.name.capitalize}<p> Te invitó a jugar chuti, hasta ahorita se han apuntado en este juego</p>
             |<p>${game.jugadores.map(_.user.name).mkString(",")}</p>
             |<p>Si quieres aceptar, ve a <a href="$linkUrl">$linkUrl</a></p>
             |<p>Te esperamos pronto! </p>
             |</body></html>""".stripMargin))
    }

  def lostPasswordEmail(user: User): RIO[TokenHolder, Envelope] =
    for {
      tokenHolder <- ZIO.service[TokenHolder]

      token <- tokenHolder.createToken(user, TokenPurpose.LostPassword)
    } yield {
      val linkUrl = s"http://$webHostName/loginForm?passwordReset=true&token=$token"
      Envelope
        .from(new InternetAddress("admin@chuti.fun", "Chuti Administrator"))
        .to(new InternetAddress(user.email))
        .subject("chuti.fun: perdiste tu contraseña")
        .content(Multipart().html(s"""<html><body>
             | <p>Que triste que perdiste tu contraseña</p>
             | <p>Creamos un enlace por medio del cual podrás elegir una nueva.</p>
             | <p>Por favor haz click aquí: <a href="$linkUrl">$linkUrl</a>.</p>
             | <p>Nota que este enlace estará activo por un tiempo limitado</p>
             |</body></html>""".stripMargin))
    }

  def registrationEmail(user: User): RIO[TokenHolder, Envelope] =
    for {
      tokenHolder <- ZIO.service[TokenHolder]

      token <- tokenHolder.createToken(user, TokenPurpose.NewUser)
    } yield {
      val linkUrl =
        s"http://$webHostName/confirmRegistration?token=$token"
      Envelope
        .from(new InternetAddress("admin@chuti.fun", "Chuti Administrator"))
        .to(new InternetAddress(user.email))
        .subject("Bienvenido a chuti.fun!")
        .content(Multipart().html(s"""<html><body>
             | <p>Gracias por registrarte!</p>
             | <p>Todo lo que tienes que hacer ahora es ir al siguiente enlace para confirmar tu registro.</p>
             | <p>Por haz click aquí: <a href="$linkUrl">$linkUrl</a>.</p>
             | <p>Nota que este enlace estará activo por un tiempo limitado, si te tardas mucho tendrás que intentar de nuevo</p>
             |</body></html>""".stripMargin))
    }

}

/** An instatiation of the Postman that user the courier mailer
  */
object CourierPostman {

  def live(config: ConfigurationService): Postman =
    new Postman {

      lazy val mailer: Mailer = {
        val localhost = config.config.getString(s"${config.configKey}.smtp.localhost").nn
        java.lang.System.setProperty("mail.smtp.localhost", localhost)
        java.lang.System.setProperty("mail.smtp.localaddress", localhost)
        val auth = config.config.getBoolean(s"${config.configKey}.smtp.auth").nn
        if (auth)
          Mailer(
            config.config.getString(s"${config.configKey}.smtp.host").nn,
            config.config.getInt(s"${config.configKey}.smtp.port").nn
          ).auth(auth)
            .as(
              config.config.getString(s"${config.configKey}.smtp.user").nn,
              config.config.getString(s"${config.configKey}.smtp.password").nn
            )
            .startTls(config.config.getBoolean(s"${config.configKey}.smtp.startTTLS").nn)()
        else
          Mailer(
            config.config.getString(s"${config.configKey}.smtp.host").nn,
            config.config.getInt(s"${config.configKey}.smtp.port").nn
          ).auth(auth)()
      }

      override def deliver(email: Envelope): Task[Unit] = ZIO.fromFuture(implicit ec => mailer(email))

      override def webHostName: String = config.config.getString(s"${config.configKey}.webhostname").nn

    }

}
