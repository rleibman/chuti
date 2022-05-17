package akka

import akka.actor.ActorSystem

trait HasActorSystem {

  implicit val actorSystem: ActorSystem

}
