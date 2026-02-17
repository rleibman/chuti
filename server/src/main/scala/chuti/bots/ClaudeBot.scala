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
import chuti.CuantasCantas.*
import chuti.Triunfo.*
import zio.*

case class ClaudeBot() extends ChutiBot {

  // ── Personality system ──────────────────────────────────────────────

  enum BotPersonality(
    val bidAggression: Int,
    val riskTolerance: Double
  ) {

    case VeryConservative extends BotPersonality(-1, 0.0)
    case Conservative extends BotPersonality(0, 0.2)
    case Moderate extends BotPersonality(0, 0.5)
    case Aggressive extends BotPersonality(1, 0.7)
    case VeryAggressive extends BotPersonality(2, 0.9)

  }

  private def derivePersonality(
    userId: UserId,
    gameId: GameId
  ): BotPersonality = {
    val idx = math.abs((userId.value ^ gameId.value) % 5).toInt
    BotPersonality.values(idx)
  }

  // ── Game state analysis ─────────────────────────────────────────────

  private case class GameStateAnalysis(
    allPlayedTiles:       Set[Ficha],
    unknownTiles:         Seq[Ficha],
    myHand:               List[Ficha],
    numberPlayedCount:    Map[Numero, Int],
    exhaustedNumbers:     Set[Numero],
    trumpsPlayed:         Seq[Ficha],
    trumpsRemaining:      Seq[Ficha],
    myTrumps:             Seq[Ficha],
    iHaveHighestTrump:    Boolean,
    playerVoids:          Map[UserId, Set[Numero]],
    iAmCantante:          Boolean,
    cantanteId:           Option[UserId],
    cantanteBid:          Int,
    cantanteTricksWon:    Int,
    cantanteTricksNeeded: Int,
    myTricksWon:          Int,
    tricksPlayed:         Int,
    myScore:              Int,
    scorePressure:        Double,
    personality:          BotPersonality
  )

  private def analyze(
    jugador: Jugador,
    game:    Game
  ): GameStateAnalysis = {
    val personality = derivePersonality(jugador.id, game.id)

    val allPlayedTiles: Set[Ficha] =
      game.jugadores.flatMap(_.filas.flatMap(_.fichas)).toSet ++ game.enJuego.map(_._2).toSet

    val unknownTiles = Game.todaLaFicha.diff((allPlayedTiles ++ jugador.fichas.toSet).toSeq)

    val numberPlayedCount: Map[Numero, Int] = {
      val allNums = allPlayedTiles.toSeq.flatMap(f => Seq(f.arriba, f.abajo))
      Numero.values.map(n => n -> allNums.count(_ == n)).toMap
    }

    // Each number appears in 8 tiles (7 combinations + 1 mula, but actually each number N appears
    // in tiles N:0, N:1, ..., N:6 plus N:N is already included = 7 tiles total)
    // Actually: number N appears in tiles (N,0), (N,1), ..., (N,6) = 7 tiles
    val exhaustedNumbers = Numero.values.filter(n => numberPlayedCount.getOrElse(n, 0) >= 7).toSet

    val triunfo = game.triunfo

    val (trumpsPlayed, trumpsRemaining, myTrumps, iHaveHighestTrump) = triunfo match {
      case Some(TriunfoNumero(num)) =>
        val allTrumps = Game.todaLaFicha.filter(_.es(num))
        val played = allPlayedTiles.filter(_.es(num)).toSeq
        val myT = jugador.fichas.filter(_.es(num))
        val remaining = allTrumps.diff(played).diff(myT)
        val highestUnplayed = allTrumps.diff(played.toList)
        val iHaveHighest = highestUnplayed.nonEmpty && myT.nonEmpty && {
          val best = highestUnplayed.maxBy(f => if (f.esMula) 100 else f.other(num).value)
          myT.contains(best)
        }
        (played, remaining, myT, iHaveHighest)
      case _ => (Seq.empty, Seq.empty, Seq.empty[Ficha], false)
    }

    // Void inference from current trick
    val playerVoids: Map[UserId, Set[Numero]] = {
      if (game.enJuego.size >= 2) {
        val ledTile = game.enJuego.head._2
        val ledNum = triunfo match {
          case Some(TriunfoNumero(t)) if ledTile.es(t) => t
          case _                                       => ledTile.arriba
        }
        game.enJuego.tail.foldLeft(Map.empty[UserId, Set[Numero]]) { case (acc, (uid, tile)) =>
          if (!tile.es(ledNum)) {
            acc.updated(uid, acc.getOrElse(uid, Set.empty) + ledNum)
          } else acc
        }
      } else Map.empty
    }

    val cantanteOpt = game.quienCanta
    val iAmCantante = jugador.cantante
    val cantanteBid = cantanteOpt.flatMap(_.cuantasCantas).map(_.numFilas).getOrElse(4)
    val cantanteTricksWon = cantanteOpt.map(_.filas.size).getOrElse(0)
    val cantanteTricksNeeded = math.max(0, cantanteBid - cantanteTricksWon)
    val myTricksWon = jugador.filas.size
    val tricksPlayed = game.jugadores.map(_.filas.size).sum

    val myScore = jugador.cuenta.map(_.puntos).sum
    val maxOtherScore = game.jugadores.filter(_.id != jugador.id).map(_.cuenta.map(_.puntos).sum).maxOption.getOrElse(0)
    val scorePressure =
      if (maxOtherScore == 0 && myScore == 0) 0.0
      else (myScore - maxOtherScore).toDouble / math.max(21.0, math.max(myScore, maxOtherScore).toDouble)

    GameStateAnalysis(
      allPlayedTiles = allPlayedTiles,
      unknownTiles = unknownTiles,
      myHand = jugador.fichas,
      numberPlayedCount = numberPlayedCount,
      exhaustedNumbers = exhaustedNumbers,
      trumpsPlayed = trumpsPlayed,
      trumpsRemaining = trumpsRemaining,
      myTrumps = myTrumps,
      iHaveHighestTrump = iHaveHighestTrump,
      playerVoids = playerVoids,
      iAmCantante = iAmCantante,
      cantanteId = cantanteOpt.map(_.id),
      cantanteBid = cantanteBid,
      cantanteTricksWon = cantanteTricksWon,
      cantanteTricksNeeded = cantanteTricksNeeded,
      myTricksWon = myTricksWon,
      tricksPlayed = tricksPlayed,
      myScore = myScore,
      scorePressure = scorePressure,
      personality = personality
    )
  }

  // ── Helpers ─────────────────────────────────────────────────────────

  private def formatHand(jugador: Jugador): String = s"[${jugador.fichas.map(_.toString).mkString(", ")}]"

  private def probTileGetsBeaten(
    tile:     Ficha,
    game:     Game,
    analysis: GameStateAnalysis
  ): Double = {
    val beaters = analysis.unknownTiles.filter(other => game.score(tile, other) >= 1000)
    if (beaters.isEmpty) 0.0
    else {
      val unknownCount = analysis.unknownTiles.size.toDouble
      if (unknownCount == 0) 0.0
      else {
        // Probability at least one of 3 opponents holds a beater
        val pNoneHasBeater = beaters.foldLeft(1.0) {
          (
            acc,
            _
          ) =>
            acc * (1.0 - math.min(1.0, 3.0 / unknownCount))
        }
        math.min(1.0, 1.0 - pNoneHasBeater)
      }
    }
  }

  private def lowestValueTile(
    fichas:  List[Ficha],
    triunfo: Option[Triunfo]
  ): Ficha = {
    triunfo match {
      case Some(TriunfoNumero(num)) =>
        fichas.minBy(f =>
          if (f.es(num) && f.esMula) 300
          else if (f.es(num)) 200 + f.other(num).value
          else if (f.esMula) 100
          else f.value
        )
      case _ =>
        fichas.minBy(f => if (f.esMula) 100 else f.value)
    }
  }

  // ── Bidding ─────────────────────────────────────────────────────────

  private def evaluateTrumps(
    jugador:     Jugador,
    game:        Game,
    personality: BotPersonality = BotPersonality.Moderate
  ): Seq[(Triunfo, Int, Double)] = {
    val fichasDeOtros = Game.todaLaFicha.diff(jugador.fichas)
    val numerosQueTengo = jugador.fichas.flatMap(f => Seq(f.arriba, f.abajo)).distinct

    val withTrumps = numerosQueTengo.map { num =>
      val triunfo = TriunfoNumero(num)
      val hypothetical = game.copy(triunfo = Option(triunfo))
      val guaranteed = hypothetical.cuantasDeCaida(jugador.fichas, fichasDeOtros).size

      val myTrumps = jugador.fichas.filter(_.es(num))
      val nonGuaranteedTrumps = math.max(0, myTrumps.size - guaranteed)
      val nonTrumpMulas = jugador.fichas.count(f => f.esMula && !f.es(num))
      val hasTrumpMula = jugador.fichas.exists(f => f.esMula && f.es(num))

      // Non-trump mulas are almost-guaranteed in a trump game: only lose if opponent is
      // void in that number AND has trump. Treat them as ~0.85 of a guaranteed trick.
      val mulaBonus = nonTrumpMulas.toDouble * 0.85

      // Non-guaranteed trumps are strong. Check which higher trumps exist.
      val allOpponentTrumps = fichasDeOtros.filter(_.es(num))
      val opponentHasTrumpMula = allOpponentTrumps.exists(_.esMula)
      val trumpBonus = myTrumps.filterNot(f => f.esMula).foldLeft(0.0) {
        (
          acc,
          t
        ) =>
          val myValue = t.other(num).value
          val higherNonMula = allOpponentTrumps.count(o => !o.esMula && o.other(num).value > myValue)
          // The opponent's trump mula beats ALL non-mula trumps
          val higherOpponentTrumps = higherNonMula + (if (opponentHasTrumpMula) 1 else 0)
          // Fewer higher opponent trumps → more likely to win
          val winProb = if (higherOpponentTrumps == 0) 0.9 else if (higherOpponentTrumps == 1) 0.5 else 0.25
          acc + winProb
      }
      // Don't double-count trumps already in guaranteed
      val adjustedTrumpBonus = math.max(0.0, trumpBonus - guaranteed.toDouble + (if (hasTrumpMula) 1.0 else 0.0))

      // Bonus for trump mula (unbeatable trump)
      val trumpMulaBonus = if (hasTrumpMula) 0.2 else 0.0

      // Trumps can win off-suit tricks (versatility): non-guaranteed trumps that can
      // be used to trump other suits when you're void. This gives trump games an
      // edge over SinTriunfos where non-mula tiles are dead weight.
      val trumpVersatility = nonGuaranteedTrumps.toDouble * 0.2

      val handStrength = guaranteed.toDouble + adjustedTrumpBonus + mulaBonus + trumpMulaBonus + trumpVersatility
      // Return mulaBonus separately: it's near-certain value, not truly speculative
      (triunfo: Triunfo, guaranteed, handStrength, mulaBonus)
    }

    val sinTriunfos = {
      val hypothetical = game.copy(triunfo = Option(SinTriunfos))
      val guaranteed = hypothetical.cuantasDeCaida(jugador.fichas, fichasDeOtros).size
      // In SinTriunfos, mulas are guaranteed (already counted) and non-mula tiles
      // are very vulnerable (any higher tile of the same number beats them).
      // Slight bonus for high non-mula tiles, but they're unreliable.
      val highNonMulas = jugador.fichas.count(f => !f.esMula && f.arriba.value >= Numero.Numero5.value)
      val handStrength = guaranteed.toDouble + highNonMulas.toDouble * 0.1
      // No mulaBonus for SinTriunfos — mulas are already in guaranteed
      (SinTriunfos: Triunfo, guaranteed, handStrength, 0.0)
    }

    // Sort by personality-adjusted selection score: conservative players discount
    // truly speculative value, but treat mula bonus as reliable (near-guaranteed).
    // This prevents conservatives from over-preferring SinTriunfos when trump games
    // have strong mula support.
    val all = withTrumps :+ sinTriunfos
    all
      .sortBy { case (_, guaranteed, handStrength, reliableMulaBonus) =>
        val reliable = guaranteed.toDouble + reliableMulaBonus
        val speculative = handStrength - reliable
        val riskPenalty = (1.0 - personality.riskTolerance) * speculative * 0.5
        val selectionScore = handStrength - riskPenalty
        (-selectionScore, -guaranteed.toDouble)
      }.map { case (triunfo, guaranteed, handStrength, _) => (triunfo, guaranteed, handStrength) }
  }

  def canta(
    jugador: Jugador,
    game:    Game
  ): IO[GameError, PlayEvent] =
    ZIO.succeed {
      val analysis = analyze(jugador, game)
      val evaluations = evaluateTrumps(jugador, game, analysis.personality)
      val best = evaluations.head
      val (bestTrump, guaranteedTricks, handStrength) = best

      val baseBid = math.floor(handStrength).toInt
      val personalityAdj = analysis.personality.bidAggression
      val scoreAdj = if (analysis.scorePressure < -0.3) 1 else if (analysis.scorePressure > 0.5) -1 else 0
      // Clamp: never bid more than guaranteedTricks + maxRisk
      // VeryAggressive can risk 2 extra tricks, others can risk at most 1
      val maxRisk = if (analysis.personality == BotPersonality.VeryAggressive) 2 else 1
      val rawBid = baseBid + personalityAdj + scoreAdj
      val clampedBid = math.min(rawBid, guaranteedTricks + maxRisk)
      val adjustedBid = math.max(4, math.min(7, clampedBid))

      if (jugador.turno) {
        // Must bid — minimum is Casa (4). When turno, there's no opponent to outbid,
        // so only bid above Casa if guaranteed tricks justify it. Bidding higher than
        // needed just raises our own bar for no benefit.
        // Aggressive players can bid above Casa based on handStrength (expected value),
        // not just guaranteedTricks (worst-case). A hand with 4 trumps + 2 mulas might
        // have only 1 guaranteed but handStrength ~5, making Cinco reasonable.
        val turnoBid =
          if (guaranteedTricks >= 7) 7
          else if (guaranteedTricks >= 6) 6
          else if (guaranteedTricks >= 5) 5
          else if (analysis.personality == BotPersonality.VeryAggressive && handStrength >= 5.5) 6
          else if (analysis.personality == BotPersonality.VeryAggressive && handStrength >= 4.5) 5
          else if (analysis.personality == BotPersonality.Aggressive && handStrength >= 5.0) 5
          else 4
        val cuantasCantas = CuantasCantas.byNum(turnoBid)

        Canta(
          cuantasCantas,
          reasoning = Option(
            s"${cuantasCantas.toString} (${cuantasCantas.numFilas}): $guaranteedTricks guaranteed with trump $bestTrump, " +
              s"strength ${f"$handStrength%.1f"}, ${analysis.personality} personality ${formatHand(jugador)}"
          )
        )
      } else {
        // Compare against the cantante's bid (the highest bid so far), not just the previous player
        val cantanteActual = game.jugadores.find(_.cantante)
        val currentMaxBid = cantanteActual.flatMap(_.cuantasCantas).getOrElse(Casa)
        val savingBid = currentMaxBid.numFilas + 1 // Minimum bid needed to save

        // To save: guaranteed tricks must justify the saving bid, AND
        // hand strength must realistically support making it.
        // Normal: guaranteed >= savingBid (sure we can make it)
        // VeryAggressive: guaranteed >= savingBid - 1 AND handStrength >= savingBid (risky but plausible)
        val canOutbid = savingBid <= 7 && (
          (guaranteedTricks >= savingBid) ||
            (analysis.personality == BotPersonality.VeryAggressive &&
              guaranteedTricks >= savingBid - 1 &&
              handStrength >= savingBid.toDouble)
        )

        if (canOutbid) {
          // Bid at least what we can guarantee — no reason to underbid with 6+ guaranteed.
          // Chuti (7) is an instant 21-point win, and bidding 6 scores more than 5, etc.
          val actualBid = math.max(savingBid, math.min(7, guaranteedTricks))
          val cuantasCantas = CuantasCantas.byNum(actualBid)
          Canta(
            cuantasCantas,
            reasoning = Option(
              s"${cuantasCantas.toString}: $guaranteedTricks guaranteed with trump $bestTrump, " +
                s"strength ${f"$handStrength%.1f"}, saving over ${currentMaxBid.toString}. " +
                s"${analysis.personality} personality ${formatHand(jugador)}"
            )
          )
        } else {
          Canta(
            Buenas,
            reasoning = Option(
              s"Buenas: $guaranteedTricks guaranteed, strength ${f"$handStrength%.1f"}, " +
                s"can't outbid ${currentMaxBid.toString} ${formatHand(jugador)}"
            )
          )
        }
      }
    }

  // ── Leading (pideInicial) ───────────────────────────────────────────

  private def pideInicial(
    jugador: Jugador,
    game:    Game
  ): IO[GameError, PlayEvent] =
    ZIO.succeed {
      val personality = derivePersonality(jugador.id, game.id)
      val evaluations = evaluateTrumps(jugador, game, personality)
      val (bestTrump, guaranteed, _) = evaluations.head

      val hypothetical = game.copy(triunfo = Option(bestTrump))
      if (hypothetical.puedesCaerte(jugador)) {
        Caete(
          triunfo = Option(bestTrump),
          reasoning = Option(
            s"Falling on initial: guaranteed all tricks with trump $bestTrump ${formatHand(jugador)}"
          )
        )
      } else {
        // Use strategic lead selection instead of blindly leading highest trump.
        // The initial pide declares trump AND leads — don't waste strong trumps
        // that aren't guaranteed winners.
        val analysis = analyze(jugador, hypothetical)
        val ficha = chooseLeadAsCantante(jugador, hypothetical, analysis)
        val reason = buildLeadReasoning(ficha, jugador, hypothetical, analysis)
        Pide(
          ficha = ficha,
          triunfo = Option(bestTrump),
          estrictaDerecha = false,
          reasoning = Option(s"Initial: trump $bestTrump. $reason")
        )
      }
    }

  // ── Leading (pide) ─────────────────────────────────────────────────

  private def pide(
    jugador: Jugador,
    game:    Game
  ): IO[GameError, PlayEvent] =
    ZIO.succeed {
      val analysis = analyze(jugador, game)

      if (game.puedesCaerte(jugador)) {
        Caete(
          reasoning = Option(
            s"Falling: ${jugador.filas.size} tricks won + guaranteed remaining >= bid. ${formatHand(jugador)}"
          )
        )
      } else if (analysis.iAmCantante && shouldSurrender(jugador, game, analysis)) {
        MeRindo(
          reasoning = Option(
            s"Surrendering: ${analysis.myTricksWon} tricks won, need ${analysis.cantanteTricksNeeded} more, " +
              s"only ${analysis.myTrumps.size} trumps left. ${analysis.personality} personality. ${formatHand(jugador)}"
          )
        )
      } else {
        val ficha = chooseLead(jugador, game, analysis)
        val reason = buildLeadReasoning(ficha, jugador, game, analysis)
        Pide(
          ficha = ficha,
          triunfo = None,
          estrictaDerecha = false,
          reasoning = Option(reason)
        )
      }
    }

  private def shouldSurrender(
    jugador:  Jugador,
    game:     Game,
    analysis: GameStateAnalysis
  ): Boolean = {
    // RULE: Can ONLY surrender before the second trick is played (i.e., at most 1 total
    // trick completed across ALL players, not just this player's won tricks).
    val totalTricksPlayed = game.jugadores.map(_.filas.size).sum
    if (totalTricksPlayed > 1) { false }
    else if (analysis.personality == BotPersonality.VeryAggressive) {
      false
    } else {

      val fichasDeOtros = Game.todaLaFicha.diff(jugador.fichas ++ analysis.allPlayedTiles.toSeq)
      val guaranteedNow = game.cuantasDeCaida(jugador.fichas, fichasDeOtros).size

      // Estimate expected tricks beyond guaranteed (cuantasDeCaida is worst-case).
      // Count non-trump mulas AND non-guaranteed trumps as likely/possible wins.
      val likelyWins = game.triunfo match {
        case Some(TriunfoNumero(num)) =>
          val nonTrumpMulas = jugador.fichas.count(f => f.esMula && !f.es(num)).toDouble * 0.7
          val opponentTrumps = fichasDeOtros.filter(_.es(num))
          val nonGuaranteedTrumps = jugador.fichas.filter(f => f.es(num) && !f.esMula).foldLeft(0.0) {
            (
              acc,
              t
            ) =>
              val myValue = t.other(num).value
              val higherCount = opponentTrumps.count(o => o.esMula || (!o.esMula && o.other(num).value > myValue))
              // Rough win probability based on how many opponent trumps beat this one
              val winProb = if (higherCount == 0) 0.85 else if (higherCount == 1) 0.4 else 0.2
              acc + winProb
          }
          nonTrumpMulas + nonGuaranteedTrumps
        case _ => 0.0
      }
      val expectedTricks = analysis.myTricksWon.toDouble + guaranteedNow.toDouble + likelyWins
      // Only surrender when CLEARLY hopeless — expected tricks at least 1.5 short of bid.
      // Borderline hands are worth playing out; you might get lucky.
      val hopeless = expectedTricks < (analysis.cantanteBid.toDouble - 0.5)

      if (!hopeless) { false }
      else if (analysis.personality == BotPersonality.Aggressive && guaranteedNow > 1) { false }
      else {

        // Even when hopeless, don't always surrender — makes the game more interesting.
        // Conservative players surrender more often, aggressive less so.
        val surrenderChance = analysis.personality match {
          case BotPersonality.VeryConservative => 0.50
          case BotPersonality.Conservative     => 0.40
          case BotPersonality.Moderate         => 0.30
          case BotPersonality.Aggressive       => 0.20
          case BotPersonality.VeryAggressive   => 0.0 // already returned false above
        }
        // Deterministic pseudo-random based on game state to avoid needing ZIO Random
        val pseudoRandom = ((jugador.id.value * 31 + game.id.value * 17 + jugador.fichas.size * 7) % 100).abs / 100.0
        pseudoRandom < surrenderChance
      }
    }
  }

  private def chooseLead(
    jugador:  Jugador,
    game:     Game,
    analysis: GameStateAnalysis
  ): Ficha = {
    if (analysis.iAmCantante) {
      chooseLeadAsCantante(jugador, game, analysis)
    } else {
      chooseLeadAsDefender(jugador, game, analysis)
    }
  }

  // A non-trump mula N:N is "trump-vulnerable" if the tile N:trumpNum exists in
  // opponents' hands. That tile both matches N (following suit) AND is a trump,
  // so it would beat the mula. Only safe to lead if we hold N:trumpNum ourselves.
  private def isMulaTrumpSafe(
    mula:     Ficha,
    trumpNum: Numero,
    jugador:  Jugador
  ): Boolean = {
    val dangerTile = Ficha(mula.arriba, trumpNum)
    // Safe if we hold the danger tile ourselves, or it's already been played
    jugador.fichas.contains(dangerTile)
    // Note: could also check allPlayedTiles, but that requires analysis param
  }

  private def chooseLeadAsCantante(
    jugador:  Jugador,
    game:     Game,
    analysis: GameStateAnalysis
  ): Ficha = {
    game.triunfo match {
      case Some(TriunfoNumero(num)) =>
        val nonTrumps = jugador.fichas.filterNot(_.es(num))
        val nonTrumpMulas = nonTrumps.filter(_.esMula)
        // Split mulas into safe (we hold the N:trump tile) and vulnerable
        val safeMulas = nonTrumpMulas.filter(m =>
          isMulaTrumpSafe(m, num, jugador) ||
            analysis.allPlayedTiles.contains(Ficha(m.arriba, num))
        )
        val vulnerableMulas = nonTrumpMulas.filterNot(safeMulas.contains)

        // Identify "strong" trumps: only beaten by 0-1 opponent trumps.
        // These are near-top trumps worth leading early to clear the field.
        val allOpponentTrumps = analysis.trumpsRemaining
        val strongTrumps = analysis.myTrumps.filterNot(_.esMula).filter { t =>
          val myValue = t.other(num).value
          val higherNonMula = allOpponentTrumps.count(o => !o.esMula && o.other(num).value > myValue)
          val opponentHasMula = allOpponentTrumps.exists(_.esMula)
          val totalHigher = higherNonMula + (if (opponentHasMula) 1 else 0)
          totalHigher <= 1
        }

        // 1. If I have the highest trump, lead it (guaranteed win)
        if (analysis.iHaveHighestTrump && analysis.myTrumps.nonEmpty) {
          analysis.myTrumps.maxBy(f => if (f.esMula) 100 else f.other(num).value)
        }
        // 2. If all opponent trumps exhausted, trumps no longer matter — lead best tile
        else if (analysis.trumpsRemaining.isEmpty) {
          if (nonTrumps.nonEmpty) {
            nonTrumps.sortBy(f => (if (f.esMula) -100.0 else 0.0, probTileGetsBeaten(f, game, analysis))).head
          } else {
            jugador.fichas.maxBy(f => if (f.esMula) 100 else f.other(num).value)
          }
        }
        // 3. Have strong near-top trumps — lead them first to clear opponent trumps.
        //    This makes subsequent mula leads safer. Lead highest strong trump.
        else if (strongTrumps.nonEmpty) {
          strongTrumps.maxBy(_.other(num).value)
        }
        // 4. Have SAFE non-trump mulas — lead them (opponent trumps somewhat depleted)
        else if (safeMulas.nonEmpty) {
          safeMulas.maxBy(_.value)
        }
        // 5. Have multiple trumps AND the highest — spend one to pull out opponents' trumps
        else if (analysis.myTrumps.size >= 2 && analysis.iHaveHighestTrump) {
          val sorted = analysis.myTrumps.sortBy(f => if (f.esMula) 100 else f.other(num).value)
          sorted(sorted.size - 2) // Second from top
        }
        // 6. Have non-trump tiles — lead those to preserve trumps
        //    Avoid vulnerable mulas (the matching N:trump tile can beat them by suit).
        else if (nonTrumps.nonEmpty) {
          val safeNonTrumps = nonTrumps.filterNot(vulnerableMulas.contains)
          if (safeNonTrumps.nonEmpty) {
            safeNonTrumps.sortBy(f => (probTileGetsBeaten(f, game, analysis), -f.value)).head
          } else {
            nonTrumps.sortBy(f => (probTileGetsBeaten(f, game, analysis), -f.value)).head
          }
        }
        // 7. Fallback: only trumps left, lead highest
        else {
          jugador.fichas.maxBy(f => if (f.esMula) 100 else f.other(num).value)
        }

      case Some(SinTriunfos) =>
        // Lead mulas first (guaranteed wins), then least likely to be beaten
        val mulas = jugador.fichas.filter(_.esMula)
        if (mulas.nonEmpty) {
          mulas.maxBy(_.value)
        } else {
          jugador.fichas.minBy(f => probTileGetsBeaten(f, game, analysis))
        }

      case None => jugador.fichas.head // Should not happen
    }
  }

  private def chooseLeadAsDefender(
    jugador:  Jugador,
    game:     Game,
    analysis: GameStateAnalysis
  ): Ficha = {
    game.triunfo match {
      case Some(TriunfoNumero(num)) =>
        // Check if cantante has known voids — lead those numbers
        val cantanteVoids = analysis.cantanteId.flatMap(analysis.playerVoids.get).getOrElse(Set.empty)
        val voidLeads = jugador.fichas.filter(f => cantanteVoids.exists(v => f.es(v)) && !f.es(num))
        if (voidLeads.nonEmpty) {
          // Lead into cantante void (forces discard or trump waste)
          voidLeads.maxBy(_.value)
        } else {
          // Lead numbers where few tiles remain (limits cantante options)
          val scored = jugador.fichas.map { f =>
            val n = if (f.es(num)) num else f.arriba
            val remainingOfN = 7 - analysis.numberPlayedCount.getOrElse(n, 0)
            (f, remainingOfN)
          }
          scored.minBy(_._2)._1
        }

      case Some(SinTriunfos) =>
        // Lead numbers with few remaining tiles
        val scored = jugador.fichas.map { f =>
          val remainingOfN = 7 - analysis.numberPlayedCount.getOrElse(f.arriba, 0)
          (f, remainingOfN)
        }
        scored.minBy(_._2)._1

      case None => jugador.fichas.head
    }
  }

  private def buildLeadReasoning(
    ficha:    Ficha,
    jugador:  Jugador,
    game:     Game,
    analysis: GameStateAnalysis
  ): String = {
    val role = if (analysis.iAmCantante) "Cantante" else "Defender"
    val prob = f"${probTileGetsBeaten(ficha, game, analysis) * 100}%.0f"
    val trumpInfo = game.triunfo match {
      case Some(TriunfoNumero(num)) =>
        val extra = if (analysis.iHaveHighestTrump) ", I hold highest trump" else ""
        s"trump $num, ${analysis.myTrumps.size} trumps in hand$extra"
      case Some(SinTriunfos) => "no trump"
      case None              => "unknown trump"
    }
    s"$role leading $ficha ($prob% beaten chance). $trumpInfo. ${formatHand(jugador)}"
  }

  // ── Following (da) ─────────────────────────────────────────────────

  private def da(
    jugador: Jugador,
    game:    Game
  ): IO[GameError, PlayEvent] = {
    if (game.enJuego.isEmpty) ZIO.fail(GameError("Nuncamente"))
    else
      ZIO.succeed {
        val analysis = analyze(jugador, game)
        val (pideUserId, pideFicha) = game.enJuego.head

        game.triunfo match {
          case None => Da(jugador.fichas.head, reasoning = Option("No trump set")) // Should not happen
          case Some(triunfo) =>
            val (ficha, reason) = chooseResponse(jugador, game, analysis, pideFicha, triunfo)
            Da(ficha, reasoning = Option(reason))
        }
      }
  }

  private def chooseResponse(
    jugador:   Jugador,
    game:      Game,
    analysis:  GameStateAnalysis,
    pideFicha: Ficha,
    triunfo:   Triunfo
  ): (Ficha, String) = {
    val hand = jugador.fichas

    triunfo match {
      case SinTriunfos =>
        chooseResponseSinTriunfos(jugador, game, analysis, pideFicha, hand)
      case TriunfoNumero(trumpNum) =>
        chooseResponseConTriunfo(jugador, game, analysis, pideFicha, hand, trumpNum)
    }
  }

  private def chooseResponseSinTriunfos(
    jugador:   Jugador,
    game:      Game,
    analysis:  GameStateAnalysis,
    pideFicha: Ficha,
    hand:      List[Ficha]
  ): (Ficha, String) = {
    val pideNum = pideFicha.arriba
    val matching = hand.filter(_.es(pideNum)).sortBy(_.other(pideNum).value)

    if (matching.nonEmpty) {
      val cantanteWinning = isCantanteCurrentlyWinning(game, analysis)
      if (analysis.iAmCantante || cantanteWinning) {
        // As cantante or if cantante is winning: try to win with smallest winner, else play lowest
        val winners = matching.filter(f => game.score(pideFicha, f) >= 1000)
        if (winners.nonEmpty && (analysis.iAmCantante || cantanteWinning)) {
          val pick = winners.head
          (pick, s"Following $pideNum with $pick (smallest winner). ${formatHand(jugador)}")
        } else {
          val pick = matching.head
          (pick, s"Following $pideNum with $pick (lowest match, can't win). ${formatHand(jugador)}")
        }
      } else {
        // Ally is winning — play lowest
        val pick = matching.head
        (pick, s"Following $pideNum with $pick (lowest match, ally winning). ${formatHand(jugador)}")
      }
    } else {
      // Can't follow — dump lowest
      val pick = lowestValueTile(hand, game.triunfo)
      (pick, s"Can't follow $pideNum — dumping $pick (lowest). ${formatHand(jugador)}")
    }
  }

  private def chooseResponseConTriunfo(
    jugador:   Jugador,
    game:      Game,
    analysis:  GameStateAnalysis,
    pideFicha: Ficha,
    hand:      List[Ficha],
    trumpNum:  Numero
  ): (Ficha, String) = {
    val pideNum = if (pideFicha.es(trumpNum)) trumpNum else pideFicha.arriba
    val matching = hand.filter(_.es(pideNum)).sortBy(_.other(pideNum).value)
    val myTrumps = hand.filter(_.es(trumpNum)).sortBy(_.other(trumpNum).value)

    if (matching.nonEmpty) {
      // Can follow suit
      chooseFromMatching(jugador, game, analysis, pideFicha, matching, pideNum, trumpNum)
    } else if (myTrumps.nonEmpty && !pideFicha.es(trumpNum)) {
      // Can't follow suit but have trump — must trump
      chooseTrump(jugador, game, analysis, myTrumps, trumpNum)
    } else {
      // Free play — dump lowest
      val pick = lowestValueTile(hand, game.triunfo)
      val reason =
        if (hand.exists(_.es(trumpNum)))
          s"Can't follow $pideNum, trumping forced — $pick. ${formatHand(jugador)}"
        else
          s"Can't follow $pideNum, no trump — dumping $pick. ${formatHand(jugador)}"
      (pick, reason)
    }
  }

  private def chooseFromMatching(
    jugador:   Jugador,
    game:      Game,
    analysis:  GameStateAnalysis,
    pideFicha: Ficha,
    matching:  List[Ficha],
    pideNum:   Numero,
    trumpNum:  Numero
  ): (Ficha, String) = {
    val cantanteWinning = isCantanteCurrentlyWinning(game, analysis)
    val isLast = game.enJuego.size == 3 // I'm 4th to play

    if (analysis.iAmCantante) {
      // As cantante: win efficiently
      val winners = matching.filter(f => game.score(pideFicha, f) >= 1000)
      if (winners.nonEmpty) {
        val pick = winners.head // Smallest winner
        (pick, s"Following $pideNum with $pick (smallest winner, cantante). ${formatHand(jugador)}")
      } else {
        val pick = matching.head // Lowest match
        (pick, s"Following $pideNum with $pick (lowest match, can't win). ${formatHand(jugador)}")
      }
    } else if (isLast && cantanteWinning) {
      // 4th player, cantante winning — try to beat
      val winners = matching.filter(f => wouldBeatCurrentWinner(f, game))
      if (winners.nonEmpty) {
        val pick = winners.head
        (pick, s"4th player: $pick to beat cantante's lead. ${formatHand(jugador)}")
      } else {
        val pick = matching.head
        (pick, s"4th player: can't beat cantante, playing lowest $pick. ${formatHand(jugador)}")
      }
    } else if (cantanteWinning) {
      // Cantante winning — play highest to try to beat
      val pick = matching.last
      (pick, s"Cantante winning — playing highest $pick to contest. ${formatHand(jugador)}")
    } else {
      // Ally winning — conserve
      val pick = matching.head
      (pick, s"Ally winning — conserving with lowest $pick. ${formatHand(jugador)}")
    }
  }

  private def chooseTrump(
    jugador:  Jugador,
    game:     Game,
    analysis: GameStateAnalysis,
    myTrumps: List[Ficha],
    trumpNum: Numero
  ): (Ficha, String) = {
    val cantanteWinning = isCantanteCurrentlyWinning(game, analysis)

    if (analysis.iAmCantante) {
      // Cantante trumping: smallest trump
      val pick = myTrumps.head
      (pick, s"Trumping with $pick (cantante, smallest trump). ${formatHand(jugador)}")
    } else if (cantanteWinning) {
      // Try to beat cantante with smallest winning trump
      val currentBest = getCurrentWinningTile(game)
      val winners = myTrumps.filter(t => currentBest.forall(best => game.score(best, t) >= 1000))
      if (winners.nonEmpty) {
        val pick = winners.head
        (pick, s"Trumping with $pick to beat cantante. ${formatHand(jugador)}")
      } else {
        val pick = myTrumps.head
        (pick, s"Forced trump $pick (can't beat cantante). ${formatHand(jugador)}")
      }
    } else {
      // Ally winning, forced to trump — play smallest
      val pick = myTrumps.head
      (pick, s"Forced trump $pick (ally winning, playing smallest). ${formatHand(jugador)}")
    }
  }

  // ── Trick analysis helpers ──────────────────────────────────────────

  private def isCantanteCurrentlyWinning(
    game:     Game,
    analysis: GameStateAnalysis
  ): Boolean = {
    if (game.enJuego.size < 2) {
      // Only one tile played, the leader is "winning"
      analysis.cantanteId.contains(game.enJuego.head._1)
    } else {
      val (piderId, pideFicha) = game.enJuego.head
      val winnerEntry = game.enJuego.tail.foldLeft((piderId, pideFicha)) {
        case (current @ (_, currentBest), (uid, tile)) =>
          if (game.score(pideFicha, tile) >= 1000 && game.score(pideFicha, currentBest) < 1000)
            (uid, tile)
          else if (game.score(pideFicha, tile) >= 1000 && game.score(pideFicha, currentBest) >= 1000) {
            // Both beat the lead, compare them
            if (game.score(currentBest, tile) >= 1000) (uid, tile) else current
          } else current
      }
      analysis.cantanteId.contains(winnerEntry._1)
    }
  }

  private def getCurrentWinningTile(game: Game): Option[Ficha] = {
    if (game.enJuego.isEmpty) None
    else {
      val pideFicha = game.enJuego.head._2
      Some(game.enJuego.tail.foldLeft(pideFicha) { case (currentBest, (_, tile)) =>
        game.fichaGanadora(pideFicha, tile) match {
          case winner if winner == tile && game.score(pideFicha, tile) >= 1000 =>
            if (game.score(pideFicha, currentBest) >= 1000) {
              // Both beat lead — pick overall best
              if (game.score(currentBest, tile) >= 1000) tile else currentBest
            } else tile
          case _ => currentBest
        }
      })
    }
  }

  private def wouldBeatCurrentWinner(
    myTile: Ficha,
    game:   Game
  ): Boolean = {
    getCurrentWinningTile(game).exists { best =>
      val pideFicha = game.enJuego.head._2
      // Check if my tile beats the current best in context of what was led
      if (pideFicha == best) {
        game.score(pideFicha, myTile) >= 1000
      } else {
        // Both are responses to pideFicha
        game.score(pideFicha, myTile) >= 1000 &&
        game.score(pideFicha, myTile) >= game.score(pideFicha, best)
      }
    }
  }

  // ── Main entry point ────────────────────────────────────────────────

  override def decideTurn(
    user: User,
    game: Game
  ): IO[GameError, PlayEvent] = {
    val jugador = game.jugador(user.id)
    for {
      pretendToThink <- ZIO.randomWith(_.nextIntBetween(500, 1800))
      res <-
        (game.gameStatus match {
          case GameStatus.jugando =>
            if (game.triunfo.isEmpty && jugador.cantante && jugador.filas.isEmpty && game.enJuego.isEmpty)
              pideInicial(jugador, game)
            else if (jugador.mano && game.puedesCaerte(jugador))
              ZIO.succeed(
                Caete(reasoning = Option(s"Falling: guaranteed remaining tricks. ${formatHand(jugador)}"))
              )
            else if (jugador.mano && game.enJuego.isEmpty)
              pide(jugador, game)
            else if (
              game.enJuego.isEmpty && game.jugadores.exists(
                _.cuantasCantas == Option(CuantasCantas.CantoTodas)
              )
            )
              ZIO.succeed(NoOpPlay())
            else
              da(jugador, game)
          case GameStatus.cantando =>
            canta(jugador, game)
          case GameStatus.requiereSopa =>
            ZIO.succeed(Sopa(firstSopa = game.currentEventIndex == 0))
          case GameStatus.partidoTerminado =>
            ZIO.succeed(NoOpPlay())
          case other =>
            ZIO.logInfo(s"ClaudeBot doesn't know what to do when $other").as(NoOpPlay())
        }).delay(pretendToThink.milliseconds)
    } yield res
  }

}
