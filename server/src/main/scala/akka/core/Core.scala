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

package akka.core

import akka.actor.ActorSystem

/** @author
  *   rleibman
  */
trait Core {

  implicit def actorSystem: ActorSystem

}

// $COVERAGE-OFF$ This is actual code that we can't test, so we shouldn't report on it
/** This trait implements ``Core`` by starting the required ``ActorSystem`` and registering the termination handler to stop the system when the JVM
  * exits.
  */
trait BootedCore extends Core {

  /** Construct the ActorSystem we will use in our application
    */
  implicit val actorSystem: ActorSystem = ActorSystem("chuti")

  /** Ensure that the constructed ActorSystem is shut down when the JVM shuts down
    */
  sys.addShutdownHook {
    actorSystem.terminate()
    ()
  }

}
// $COVERAGE-ON$

/** This trait contains the actors that make up our application; it can be mixed in with ``BootedCore`` for running code or ``TestKit`` for unit and
  * integration tests.
  */
trait CoreActors { this: Core => }
