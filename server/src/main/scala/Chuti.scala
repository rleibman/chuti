/*
 * Copyright 2020 Roberto Leibman
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import api.{Api, LiveEnvironment}
import core.{BootedCore, CoreActors}
import web.Web

import scala.concurrent.ExecutionContext

/**
  * This is the actual application you run that contains everything it needs, the core, the actors, the api, the web, the environment.
 **/
object Chuti
    extends App //To run it
    with BootedCore //For stop and start
    with CoreActors with Api // The api
    with Web // As a web service
    with LiveEnvironment {}
