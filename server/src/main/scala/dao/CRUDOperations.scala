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
