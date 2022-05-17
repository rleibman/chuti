package akka

import akka.core.{BootedCore, CoreActors}
import akka.web.Web

/** This is the actual application you run that contains everything it needs, the core, the actors, the api, the web, the environment.
  */
object Chuti
    extends App // To run it
    with BootedCore // For stop and start
    with CoreActors with Api // The api
    with Web // As a web service
    {

  start

}
