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

package service

import chuti.Search
import japgolly.scalajs.react.extra.Ajax
import japgolly.scalajs.react.extra.internal.AjaxException
import japgolly.scalajs.react.{AsyncCallback, Callback}
import org.scalajs.dom.XMLHttpRequest
import zio.json.*

trait RESTOperations {

  def processErrors[A](fn: XMLHttpRequest => Either[String, A])(xhr: XMLHttpRequest): A =
    try {
      if (xhr.status >= 400)
        throw AjaxException(xhr)
      else {
        fn(xhr) match {
          case Right(a) => a
          case Left(e) =>
            throw RuntimeException(e)
        }
      }
    } catch {
      case e: AjaxException =>
        e.printStackTrace()
        throw e
    }

  def RESTOperation[Request: JsonEncoder, Response: JsonDecoder](
    method:      String,
    fullUrl:     String,
    request:     Option[Request] = None,
    withTimeout: Option[(Double, XMLHttpRequest => Callback)] = None
  ): AsyncCallback[Response] = {
    val step1 = Ajax(method, fullUrl)
      .and(_.withCredentials = true)

    val step2 = request
      .fold(step1.send)(s => step1.setRequestContentTypeJson.send(s.toJson))

    withTimeout
      .fold(step2)(a => step2.withTimeout(a._1, a._2))
      .asAsyncCallback
      .map {
        processErrors(_.responseText.fromJson[Response])
      }
  }

}

trait RESTClient[E, PK, SEARCH <: Search] {

  def remoteSystem: RESTClient.Service[E, PK, SEARCH]

}

object RESTClient {

  abstract class Service[E: JsonDecoder: JsonEncoder, PK, SEARCH <: Search: JsonEncoder] extends RESTOperations {

    def get(id: PK): AsyncCallback[Option[E]]

    def delete(id: PK): AsyncCallback[Boolean]

    def upsert(obj: E): AsyncCallback[E]

    def search(search: Option[SEARCH] = None): AsyncCallback[Seq[E]]

    def count(searchObj: Option[SEARCH]): AsyncCallback[Int]

  }

}

abstract class LiveRESTClient[E: JsonDecoder: JsonEncoder, PK, SEARCH <: Search: JsonEncoder] extends RESTClient[E, PK, SEARCH] {

  val baseUrl: String

  override def remoteSystem: RESTClient.Service[E, PK, SEARCH] = new LiveClientService

  class LiveClientService extends RESTClient.Service[E, PK, SEARCH] {

    override def get(id: PK): AsyncCallback[Option[E]] = RESTOperation[String, Option[E]]("GET", s"$baseUrl/${id.toString}", None)

    override def delete(id: PK): AsyncCallback[Boolean] = RESTOperation[String, Boolean]("DELETE", s"$baseUrl/${id.toString}", None)

    override def upsert(obj: E): AsyncCallback[E] = RESTOperation[E, E]("POST", s"$baseUrl", Option(obj))

    override def search(searchObj: Option[SEARCH]): AsyncCallback[Seq[E]] = RESTOperation[SEARCH, Seq[E]]("POST", s"$baseUrl/search", searchObj)

    override def count(searchObj: Option[SEARCH]): AsyncCallback[Int] = RESTOperation[SEARCH, Int]("POST", s"$baseUrl/count", searchObj)

  }

}
