/*
 * Copyright 2020 Roberto Leibman
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package zioslick

case class RepositoryException(
  msg:   String = "",
  cause: Option[Throwable] = None
) extends Exception(msg, cause.orNull)
