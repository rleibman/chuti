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

package chuti

import chuti.Estado.comienzo
import org.scalatest.flatspec.AnyFlatSpec

class EstadoDeJuegoSpec extends AnyFlatSpec {
  "Creating a new game" should "create a new game" in {

  }
//  private def mesa() = Mesa(
//    partidos = List.empty,
//    usuarios = List(
//      User(id = Some(UserId(1)), email = "juan1@example.com", name = "Juan1", wallet = 10.0),
//      User(id = Some(UserId(2)), email = "pedro@example.com", name = "Pedro", wallet = 10.0),
//      User(id = Some(UserId(3)), email = "jose1@example.com", name = "Jose1", wallet = 10.0),
//      User(id = Some(UserId(4)), email = "maria@example.com", name = "Maria", wallet = 10.0)
//    )
//  )
//
//  "Creating a new game" should "create a new game" in {
//    val miMesa = mesa()
//    assert(miMesa.juegoActual !== null)
//    assert(
//      miMesa.juegoActual.jugadores.find(_.cantador) === miMesa.juegoActual.jugadores
//          .find(_.fichas.exists(_ == Mesa.laMulota))
//    )
//    assert(miMesa.juegoActual.estado === Estado.comienzo)
//    println(miMesa.godPrint)
//  }
//  "Testing NoOp" should "modify the game very little" in {
//    val miMesa = mesa()
//    assert(miMesa.juegoActual.currentIndex == 0)
//    val processed = miMesa.juegoActual.event(NoOp())
//    assert(processed._1.currentIndex === 1)
//    assert(processed._2.index === Option(0))
//  }
//
}
