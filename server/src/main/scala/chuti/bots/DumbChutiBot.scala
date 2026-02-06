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

package chuti.bots

import chuti.*
import chuti.Triunfo.*
import zio.*

case object DumbChutiBot extends ChutiBot {

  private def calculaCasa(
    jugador: Jugador,
    game:    Game
  ): (Int, Triunfo) = {
    // Si el jugador le toca cantar, no le queda de otra, tiene que arriesgarse, asi es que usa otra heuristica... el numero de fichas
    val (deCaidaCount, deCaidaTriunfo) = calculaDeCaida(jugador, game)

    val numTriunfos =
      jugador.fichas
        .flatMap(f => if (f.esMula) Seq(f.arriba) else Seq(f.arriba, f.abajo))
        .groupBy(identity)
        .map { case (n, l) =>
          (n, l.size)
        }
        .maxBy(_._2)

    if (numTriunfos._2 > deCaidaCount)
      // Tenemos algunos triunfos, pero no suficientes para ser de caida, asi es que cantamos uno menos de lo que tenemos
      (numTriunfos._2 - 1, TriunfoNumero(numTriunfos._1))
    else
      (deCaidaCount, deCaidaTriunfo)
  }

  private def calculaDeCaida(
    jugador: Jugador,
    game:    Game
  ): (Int, Triunfo) = {
    // Este jugador es muy conservador, no se fija en el numero de fichas de un numero que tiene, solo en cuantas son de caida
    // En el futuro podemos inventar jugadores que se fijen en ambas partes y que sean mas o menos conservadores
    // Esta bien que las mulas cuenten por dos.
    val numerosQueTengo: Seq[Numero] = jugador.fichas
      .flatMap(f => Seq(f.arriba, f.abajo))
      .distinct
    val fichasDeOtros = Game.todaLaFicha.diff(jugador.fichas)
    // Calcula todas las posibilidades de caida
    val posiblesGanancias = numerosQueTengo
      .map(num =>
        (
          num,
          game
            .copy(triunfo = Option(TriunfoNumero(num))).cuantasDeCaida(
              jugador.fichas,
              fichasDeOtros
            ).size
        )
      )

    val conTriunfosCount =
      posiblesGanancias.filter(_._2 == posiblesGanancias.maxBy(_._2)._2).maxBy(_._1.value)
    val sinTriunfosCount =
      game.copy(triunfo = Option(SinTriunfos)).cuantasDeCaida(jugador.fichas, fichasDeOtros).size

    if (conTriunfosCount._2 > sinTriunfosCount)
      (conTriunfosCount._2, TriunfoNumero(conTriunfosCount._1))
    else
      (sinTriunfosCount, SinTriunfos)
  }

  private def calculaCanto(
    jugador: Jugador,
    game:    Game
  ): (Int, Triunfo) = {
    if (jugador.turno) calculaCasa(jugador, game)
    else calculaDeCaida(jugador, game)
  }

  private def pideInicial(
    jugador: Jugador,
    game:    Game
  ): IO[GameError, PlayEvent] =
    ZIO.succeed {
      val (_, triunfo) = calculaCanto(jugador, game)
      val hypotheticalGame = game.copy(triunfo = Option(triunfo))
      if (hypotheticalGame.puedesCaerte(jugador))
        Caete(triunfo = Option(triunfo))
      else {
        Pide(
          ficha = hypotheticalGame.highestValueByTriunfo(jugador.fichas).get,
          triunfo = Option(triunfo),
          estrictaDerecha = false
        )
      }
    }

  private def pide(
    jugador: Jugador,
    game:    Game
  ): IO[GameError, PlayEvent] = {
    game.triunfo match {
      case None => ZIO.fail(GameError("Nuncamente!"))
      case Some(SinTriunfos) =>
        ZIO.succeed(
          Pide(
            jugador.fichas.maxByOption(ficha => if (ficha.esMula) 100 else ficha.arriba.value).get,
            triunfo = None,
            estrictaDerecha = false
          )
        )
      case Some(TriunfoNumero(triunfo)) =>
        ZIO.succeed(
          Pide(
            jugador.fichas
              .maxByOption(ficha =>
                (if (ficha.es(triunfo)) 200 else if (ficha.esMula) 100 else 0) + ficha.arriba.value
              ).get,
            triunfo = None,
            estrictaDerecha = false
          )
        )
    }
  }

  private def da(
    jugador: Jugador,
    game:    Game
  ): IO[GameError, PlayEvent] = {
    if (game.enJuego.isEmpty) ZIO.fail(GameError("Nuncamente"))
    else {
      val pide = game.enJuego.head
      game.triunfo match {
        case None => ZIO.fail(GameError("Nuncamente!"))
        case Some(SinTriunfos) =>
          val pideNum = pide._2.arriba
          ZIO.succeed(
            Da(
              jugador.fichas
                .filter(_.es(pideNum))
                .sortBy(_.other(pideNum).value)
                .headOption
                .getOrElse(jugador.fichas.minBy(f => if (f.esMula) 100 else f.value))
            )
          )
        case Some(TriunfoNumero(triunfo)) =>
          val pideNum =
            if (pide._2.es(triunfo))
              triunfo
            else
              pide._2.arriba
          ZIO.succeed(
            Da(
              jugador.fichas
                .filter(_.es(pideNum))
                .sortBy(_.other(pideNum).value)
                .headOption
                .getOrElse(
                  jugador.fichas.minBy(f =>
                    if (f.es(triunfo))
                      triunfo.value - 100 - f.other(triunfo).value
                    else if (f.esMula) 100
                    else f.value
                  )
                )
            )
          )
      }
    }
  }

  def caite(): IO[GameError, PlayEvent] = ZIO.succeed(Caete())

  def canta(
    jugador: Jugador,
    game:    Game
  ): IO[GameError, PlayEvent] =
    ZIO
      .succeed {
        import CuantasCantas.*
        val (cuantas, _) = calculaCanto(jugador, game)

        val cuantasCantas =
          if (cuantas <= 4 && jugador.turno)
            Casa
          else if (cuantas <= 4)
            Buenas
          else CuantasCantas.byNum(cuantas)

        val result =
          if (jugador.turno) Canta(cuantasCantas)
          else {
            val prev = game.prevPlayer(jugador).cuantasCantas.getOrElse(Casa)
            if (cuantasCantas.prioridad > prev.prioridad)
              Canta(cuantasCantas)
            else
              Canta(Buenas)
          }

        result
      }.tap(result => ZIO.log(s"Bot ${jugador.user.name} bidding: ${result.cuantasCantas}"))

  override def decideTurn(
    user: User,
    game: Game
  ): IO[GameError, PlayEvent] = {
    val jugador = game.jugador(user.id)
    game.gameStatus match {
      case GameStatus.jugando =>
        if (game.triunfo.isEmpty && jugador.cantante && jugador.filas.isEmpty && game.enJuego.isEmpty)
          pideInicial(jugador, game)
        else if (jugador.mano && game.puedesCaerte(jugador))
          caite()
        else if (jugador.mano && game.enJuego.isEmpty)
          pide(jugador, game)
        else if (
          game.enJuego.isEmpty && game.jugadores.exists(
            _.cuantasCantas == Option(CuantasCantas.CantoTodas)
          )
        )
          ZIO.succeed(NoOpPlay()) // Skipping this,
        else
          da(jugador, game)
      case GameStatus.cantando =>
        canta(jugador, game)
      case GameStatus.requiereSopa =>
        // Bot needs to shuffle the deck
        // firstSopa is true if this is the very first shuffle (no events yet)
        ZIO.succeed(Sopa(firstSopa = game.currentEventIndex == 0))
      case GameStatus.partidoTerminado =>
        // Game is over, nothing to do
        ZIO.succeed(NoOpPlay())
      case other =>
        ZIO.logInfo(s"I'm too dumb to know what to do when $other").as(NoOpPlay())
    }
  }

}
