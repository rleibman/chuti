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
import zio.{Has, RIO, Task, ZIO}

import scala.concurrent.duration.*

object Postman {
  type Postman = Has[Service]

  trait Service {
    def deliver(email: Envelope): Task[Unit]
    def webHostName: String

    //You may want to move these to a different service if you wanted to keep the mechanics of sending and the content separate
    def inviteToPlayByEmail(
      user:    User,
      invited: User
    ): RIO[TokenHolder, Envelope] =
      for {
        tokenHolder <- ZIO.access[TokenHolder](_.get)
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
        tokenHolder <- ZIO.access[TokenHolder](_.get)
        token       <- tokenHolder.createToken(user, TokenPurpose.LostPassword)
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
        tokenHolder <- ZIO.access[TokenHolder](_.get)
        token       <- tokenHolder.createToken(user, TokenPurpose.NewUser)
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
}

/**
  * An instatiation of the Postman that user the courier mailer
  */
object CourierPostman {
  def live(config: Config.Service): Postman.Service =
    new Postman.Service {
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

      override def deliver(email: Envelope): Task[Unit] = Task.fromFuture(implicit ec => mailer(email))

      override def webHostName: String = config.config.getString(s"${config.configKey}.webhostname")

    }
}
