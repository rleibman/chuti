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

import chuti.Numero.Numero0
import chuti.Triunfo._
import org.mockito.scalatest.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec

class ManoGanadoraSpec extends AnyFlatSpec with MockitoSugar with GameAbstractSpec {
  val manosTests = Seq(
    //(fichas, pidiendo, triunfo) debe ganar (x)
    ("3:2,3:3,1:1,3:4","3:2", TriunfoNumero(Numero0))    -> "3:4",
    ("6:6,5:5,4:4,3:3", "6:6", SinTriunfos)              -> "6:6",
    ("6:6,5:5,4:4,1:0", "1:0", SinTriunfos)              -> "1:0",
    ("5:5,2:2,4:4,1:0", "2:2", SinTriunfos)              -> "2:2",
    ("5:5,1:6,4:4,1:0", "1:0", SinTriunfos)              -> "1:6",
    ("6:4,6:5,4:4,0:0", "6:4", TriunfoNumero(Numero(3))) -> "6:5",
    ("6:6,5:5,4:4,3:3", "6:6", TriunfoNumero(Numero(6))) -> "6:6",
    ("6:6,5:5,4:4,3:3", "6:6", TriunfoNumero(Numero(3))) -> "3:3",
    ("6:6,5:5,4:4,1:0", "6:6", TriunfoNumero(Numero(1))) -> "1:0",
    ("6:6,5:5,4:4,1:0", "1:0", TriunfoNumero(Numero(3))) -> "1:0",
    ("6:6,5:5,4:4,3:0", "6:6", TriunfoNumero(Numero(3))) -> "3:0",
    ("3:3,3:4,3:5,1:2", "3:3", TriunfoNumero(Numero(3))) -> "3:3",
    ("3:3,3:6,3:5,1:2", "3:6", TriunfoNumero(Numero(3))) -> "3:6",
    ("3:3,3:6,3:5,1:2", "3:5", TriunfoNumero(Numero(3))) -> "3:6",
    ("3:3,3:6,3:5,6:6", "6:6", TriunfoNumero(Numero(3))) -> "3:6"
  )

  val cuantasDeCaidaTests: Seq[((String, Triunfo), Int)] = Seq(
    ("1:0,6:1,6:6,4:1,6:5,3:1,6:4", TriunfoNumero(Numero(6))) -> 4,
    ("5:5,4:3,6:5,1:1,6:4,3:1,6:1", TriunfoNumero(Numero(6))) -> 0,
    ("5:5,4:3,6:6,1:1,6:4,3:1,6:1", TriunfoNumero(Numero(6))) -> 1,
    ("5:5,4:3,6:6,1:1,6:5,3:1,6:1", TriunfoNumero(Numero(6))) -> 2,
    ("5:5,4:3,6:6,1:1,6:5,3:1,6:4", TriunfoNumero(Numero(6))) -> 3,
    ("5:5,6:1,6:6,1:1,6:5,3:1,6:4", TriunfoNumero(Numero(6))) -> 6,
    ("5:5,6:1,6:6,1:1,6:5,3:3,6:4", TriunfoNumero(Numero(6))) -> 7,
    ("6:6,6:5,6:4,6:3,6:2,5:5,5:3", TriunfoNumero(Numero(6))) -> 6,
    ("6:6,6:5,6:4,6:3,4:4,5:5,5:3", TriunfoNumero(Numero(6))) -> 6, //Juego interesante, cantar chuti
    ("6:6,3:3,5:5,4:4,4:3,4:2,4:1", TriunfoNumero(Numero(6))) -> 1,
    ("6:6,3:3,5:5,4:4,4:3,4:2,4:1", SinTriunfos)              -> 4, //Juego interesante, tratar cantando chuti sin triunfos
    ("6:6,6:5,5:5,4:4,4:3,4:2,4:1", SinTriunfos)              -> 4,
    ("1:0,2:1,3:2,4:3,5:4,6:5,1:3", SinTriunfos)              -> 0,
    ("6:6,6:5,6:4,6:3,6:2,5:5,5:4", SinTriunfos)              -> 7,
    ("6:6,6:5,6:4,6:3,6:2,5:5,5:3", SinTriunfos)              -> 6,
    ("6:6,6:5,6:4,6:3,4:4,5:5,5:3", SinTriunfos)              -> 6 //Juego interesante, cantar chuti
  )

  manosTests.map { s =>
    s"cuando juegan ${s._1._1}, triunfando ${s._1._3}, pidiendo ${s._1._2}" should s" ganar ${s._2}" in {
      val fichas = s._1._1.split(",").toSeq.map(Ficha.fromString)
      val game = Game(None, triunfo = Option(s._1._3))
      val ganadora = game.fichaGanadora(Ficha.fromString(s._1._2), fichas)
      val esperada = Ficha.fromString(s._2)
      assert(ganadora === esperada)
    }
  }

  cuantasDeCaidaTests.map { s =>
    s"calculando cuantas de caida con ${s._1._1} de mano y triunfando ${s._1._2}" should s"hacer ${s._2} filas " in {
      val fichas: Seq[Ficha] = s._1._1.split(",").toSeq.map(Ficha.fromString)
      val game = Game(None, triunfo = Option(s._1._2))
      val remainder = Game.todaLaFicha.diff(fichas)
      val cuantasDeCaida = game.cuantasDeCaida(fichas, remainder)
      val esperada = s._2
      println(cuantasDeCaida.length === esperada)
      assert(cuantasDeCaida.length === esperada)
    }
  }

}
