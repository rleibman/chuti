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

//class MyEventLoop {
//  val eventsById = eventsFromLastFrame.groupBy(_.toEntityId).withDefault(Seq.empty)
//  // Map[Int, Seq[Event]]
//
//  val eventsToRoute = world.entities.flatMap { entity =>
//    val replyEvents = eventsById(entity.id).flatMap { ev =>
//      ev.applyTo(entity) // take damage from others etc.
//    }
//    val (externalEvents, stateTransitions) = updateEntity(
//      entity.asInstanceOf[ReadonlyEntity],
//      worldFromLastFrame
//    )
//    for (t <- stateTransitions) {
//      t.applyTo(entity) // change entity position etc.
//    }
//    replyEvents ++ externalEvents
//  }
//}
//
//abstract class ClientConnection {
//  // send
//  def sendPlayerEvents(event:Seq[Event], stateTransitions:Seq[Event])
//
//  // receive
//  def receive : (Seq[Event], Seq[StateTransition[Living]])
//}
//
//object MultiplayerServer { // server is arg
//  def run(world:World, timesteps:Int, clients:Map[Int, ClientConnection]) {
//    for(i <- 1 to timesteps) {
//
//    }
//  }
//}
//
//abstract class ServerConnection {
//  // send
//  def playerUpdate(events:Seq[Event], stateTransitions:Seq[StateTransition[Living]])
//
//  // receive
//  def receivePlayer : (Seq[ToEntityEvent], Seq[StateTransition[Living]])
//  def receiveNpcStateTransitions : Map[Int, Seq[StateTransition[Living]]]
//}
//
//object MultiplayerClient { // server is arg
//  def run(world:World, timesteps:Int, server:ServerConnection) {
//    for(i <- 1 to timesteps) {
//
//    }
//  }
//}
