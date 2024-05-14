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

package dao

import chuti.Search

/** Collects the basic CRUD operations of a single object (or object graph) against a data source.
  * @tparam E
  *   the typeof the object to be stored.
  * @tparam PK
  *   the type of the primary key of the object.
  * @tparam SEARCH
  *   the type of the search object.
  */
trait CRUDOperations[E, PK, SEARCH <: Search] {

  def upsert(e: E):  RepositoryIO[E]
  def get(pk:   PK): RepositoryIO[Option[E]]
  def delete(
    pk:         PK,
    softDelete: Boolean = false
  ): RepositoryIO[Boolean]
  def search(search: Option[SEARCH] = None): RepositoryIO[Seq[E]]
  def count(search:  Option[SEARCH] = None): RepositoryIO[Long]

}
