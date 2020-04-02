/*
 * Copyright 2020 Roberto Leibman
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package api

import akka.actor.ActorSystem

trait HasActorSystem {
  implicit val actorSystem: ActorSystem
}
