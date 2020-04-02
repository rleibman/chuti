/*
 * Copyright 2020 Roberto Leibman
 *
 * SPDX-License-Identifier: Apache-2.0
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
  def upsert(e: E):  RepositoryIO[E]
  def get(pk:   PK): RepositoryIO[Option[E]]
  def delete(
    pk:         PK,
    softDelete: Boolean = false
  ): RepositoryIO[Boolean]
  def search(search: Option[SEARCH] = None): RepositoryIO[Seq[E]]
  def count(search:  Option[SEARCH] = None): RepositoryIO[Long]
}
