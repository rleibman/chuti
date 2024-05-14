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

import coinbase.Currency
import org.apache.commons.codec.binary.Hex
import zio.{Task, ULayer, ZLayer}

import java.security.{InvalidKeyException, NoSuchAlgorithmException}
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

package object coinbase {

  trait Coinbase {

    def transactionRequest(
      to:          String,
      amount:      BigDecimal,
      currency:    Currency,
      description: Option[String] = None
    ): Task[Unit]

    def transactionSend(
      to:                            String,
      amount:                        BigDecimal,
      currency:                      Currency,
      description:                   Option[String] = None,
      skipNotifications:             Boolean = true,
      fee:                           Option[BigDecimal] = None,
      idem:                          Option[String] = None,
      financial_institution_website: Option[String] = None
    ): Task[Unit]

    def walletCreateAddress(name: String): Task[String]

  }

  enum Currency {

    case BTC

  }

  val live: ULayer[Coinbase] = ZLayer.succeed(liveCoinbase())

  private def liveCoinbase(): Coinbase =
    new Coinbase {

      override def transactionRequest(
        to:          String,
        amount:      BigDecimal,
        currency:    Currency,
        description: Option[String]
      ): Task[Unit] = ???

      override def transactionSend(
        to:                            String,
        amount:                        BigDecimal,
        currency:                      Currency,
        description:                   Option[String],
        skipNotifications:             Boolean,
        fee:                           Option[BigDecimal],
        idem:                          Option[String],
        financial_institution_website: Option[String]
      ): Task[Unit] = ???

      override def walletCreateAddress(name: String): Task[String] = ???

    }

  def getHMACHeader(
    secretKey:   String,
    timestamp:   String,
    method:      String,
    requestPath: String,
    body:        String
  ): String = {
    val prehash =
      if (method == "POST" || method == "PUT") timestamp + method.toUpperCase + requestPath
      else timestamp + method.toUpperCase + requestPath + body
    val keyspec = new SecretKeySpec(secretKey.getBytes, "HmacSHA256")
    try {
      val sha256 = Mac.getInstance("HmacSHA256").asInstanceOf[Mac]
      sha256.init(keyspec)
      val hash = Hex.encodeHexString(sha256.doFinal(prehash.getBytes)).nn
      hash
    } catch {
      case e @ (_: NoSuchAlgorithmException | _: InvalidKeyException) =>
        e.printStackTrace()
        throw e
    }
  }

}
