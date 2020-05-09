package chuti

import chuti.Triunfo._
import org.mockito.scalatest.MockitoSugar
import org.scalatest.flatspec.AnyFlatSpec

class ManoGanadoraSpec extends AnyFlatSpec with MockitoSugar with GameAbstractSpec {
  val tests = Seq(
    //(fichas, pidiendo, triunfo) debe ganar (x)
    ("6:6,5:5,4:4,3:3", "6:6", SinTriunfos)              -> "6:6",
    ("6:6,5:5,4:4,1:0", "1:0", SinTriunfos)              -> "1:0",
    ("5:5,2:2,4:4,1:0", "2:2", SinTriunfos)              -> "2:2",
    ("5:5,1:6,4:4,1:0", "1:0", SinTriunfos)              -> "1:6",
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

  tests.map { s =>
    s"cuando juegan ${s._1._1}, triunfando ${s._1._3}, pidiendo ${s._1._2}" should s" ganar ${s._2}" in {
      val fichas = s._1._1.split(",").map(Ficha.fromString)
      val ganadora = Game.calculaFichaGanadora(fichas, Ficha.fromString(s._1._2), s._1._3)
      val esperada = Ficha.fromString(s._2)
      assert(ganadora === esperada)
    }

  }

}
