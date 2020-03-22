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

object Numero extends Enumeration {

  protected case class Val(num: Int) extends super.Val

  type Numero = Value

  val Numero0: Val = Val(0)
  val Numero1: Val = Val(1)
  val Numero2: Val = Val(2)
  val Numero3: Val = Val(3)
  val Numero4: Val = Val(4)
  val Numero5: Val = Val(5)
  val Numero6: Val = Val(6)
}

import Numero._

import scala.util.Random

case class Ficha(arriba: Numero, abajo: Numero) {
  val esMula: Boolean = arriba == abajo
}

case class Usuario(nombre: String, cartera: Double)

case class Fila(fichas: Seq[Ficha])

case class Jugador(usuario: Usuario,
                   fichas: Seq[Ficha],
                   casas: Seq[Fila],
                   cantador: Boolean,
                   mano: Boolean)

sealed trait Triunfo

case object TriunfanMulas

case class TriunfoNumero(num: Numero)

object Estado extends Enumeration {
  type Estado = Value
  val comienzo, cantando, jugando, terminado = Value
}

import Estado._

case class EstadoDeJuego(
                          jugadores: Seq[Jugador],
                          casaEnMesa: Seq[Ficha] = Seq.empty,
                          triunfo: Option[Triunfo] = None,
                          estado: Estado = comienzo
                        ) {
  def transform(fn: () => EstadoDeJuego): EstadoDeJuego = {
    fn()
  }

  def canta(jugador: Jugador, cuantas: Int): EstadoDeJuego = ???

  def pide(jugador: Jugador, ficha: Ficha, triunfo: Triunfo, estrictaDerecha: Boolean): EstadoDeJuego = ???

  def pide(jugador: Jugador, ficha: Ficha, estrictaDerecha: Boolean): EstadoDeJuego = ???

  def da(jugador: Jugador, ficha: Ficha): EstadoDeJuego = ???

  //Acuerdate de los regalos
  def caida: EstadoDeJuego = ???

  def resultado(): Option[Seq[Cuenta]] = ???
}

case class Cuenta(usuario: Usuario, puntos: Int, esHoyo: Boolean)

/**
 * Un partido consta de uno o mas juegos, todos los partidos tienen los mismos usuarios
 *
 * @param cuentas
 */
case class PartidoArchivo(
                    cuentas: Seq[(Usuario, Double)]
                  )

/**
 * Cada mesa tiene una serie de usuarios, una serie de partidos y un juego a cada momento.
 * Tambien tiene un historial de juegos, guardado en disco.
 *
 * @param partidos
 * @param usuarios
 */
case class Mesa(
                 partidos: Seq[PartidoArchivo],
                 usuarios: Array[Usuario]
               ) {
  val juegoActual: EstadoDeJuego = sopa(None)

  (0 to 6).combinations(1)

  lazy val todaLaFicha: Seq[Ficha] = ((0 to 6).combinations(2).toSeq.map(seq => Ficha(Numero(seq(0)), Numero(seq(1)))) ++ (0 to 6).map(i =>Ficha(Numero(i), Numero(i))))

  val laMulota = Ficha(Numero6, Numero6)
  val campanita = Ficha(Numero0, Numero1)

  def sopa(turno: Option[Usuario]): EstadoDeJuego = EstadoDeJuego(
    Random.shuffle(todaLaFicha)
      .grouped(todaLaFicha.length / usuarios.length)
      .zip(usuarios)
      .map { case (fichas, usuario) =>
        Jugador(usuario = usuario,
          fichas = fichas,
          casas = Seq.empty,
          cantador = turno.fold(fichas.contains(laMulota))(_ == usuario),
          mano = turno.fold(fichas.contains(laMulota))(_ == usuario)
        )
      }.toSeq)

  def total(costoPorPunto: Double): Seq[(Usuario, Double)] = partidos.flatMap(_.cuentas).groupBy(_._1).map(g => (g._1, g._2.map(_._2).sum)).toSeq

  /**
   * Imprime todo el asunto
   *
   * @return
   */
  def godPrint = ???

  /**
   * Imprime nada mas el asunto que un jugador puede ver
   *
   * @return
   */
  def jugadorPrint(jugador: Jugador) = ???
}