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

import zio.{IO, ZIO}
import zioslick.RepositoryException

trait Search[A]

case class EmptySearch[A]() extends Search[A]

/**
 * Collects the basic CRUD operations of a single object (or object graph) against a data source.
 * @tparam E
 * @tparam PK
 * @tparam SEARCH
 */
trait CRUDOperations[E, PK, SEARCH <: Search[E]] {
  def upsert(e: E): RepositoryIO[E]
  def get(pk: PK): RepositoryIO[Option[E]]
  def delete(pk: PK, softDelete: Boolean = false): RepositoryIO[Boolean]
  def search(search: Option[SEARCH] = None): RepositoryIO[Seq[E]]
  def count(search: Option[SEARCH] = None): RepositoryIO[Long]
}
