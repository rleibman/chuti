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

import java.security.{InvalidKeyException, NoSuchAlgorithmException}

import api.config.Config
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.apache.commons.codec.binary.Hex
import zio.{Has, Task, URLayer, ZLayer}

package object coinbase {
  object Currency extends Enumeration {
    type Currency = Value
    val BTC: Currency = Value
  }

  import Currency._

  type Coinbase = Has[Service]

  trait Service {
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

  def akkaHttpLayer: URLayer[Config, Coinbase] = ZLayer.fromService(config => akkaHttp(config))

  def akkaHttp(config: Config.Service): Service =
    new Service {
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
      val hash = Hex.encodeHexString(sha256.doFinal(prehash.getBytes))
      hash
    } catch {
      case e @ (_: NoSuchAlgorithmException | _: InvalidKeyException) =>
        e.printStackTrace()
        throw e
    }
  }
}
