/*
 * Copyright 2020 Roberto Leibman
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package api

import dao.{LiveRepository}
import mail.CourierPostman

/**
  * This Creates a live environment, with actual running stuff (real email, real database, etc)
  */
trait LiveEnvironment extends LiveRepository with CourierPostman with Config {}
