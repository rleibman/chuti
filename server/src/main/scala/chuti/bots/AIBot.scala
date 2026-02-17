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

import ai.{LLMService, OllamaConfig}
import chuti.CuantasCantas.{Buenas, Casa}
import chuti.Triunfo.{SinTriunfos, TriunfoNumero}
import chuti.{*, given}
import zio.*
import zio.json.*
import zio.json.ast.Json

case class LLMStats(
  successes: Long = 0,
  timeouts:  Long = 0,
  errors:    Long = 0
) {

  def total: Long = successes + timeouts + errors

  def summary: String =
    s"LLM calls: $total total — $successes ok, $timeouts timeouts, $errors errors" +
      (if (total > 0) f" (${successes * 100.0 / total}%.0f%% success rate)" else "")

}

case class AIBot(
  config:     OllamaConfig,
  llmService: LLMService,
  stats:      Ref[LLMStats]
) extends ChutiBot {

  given JsonEncoder[Map[Jugador, Seq[Numero]]] =
    JsonEncoder
      .map[String, Seq[Numero]].contramap(
        _.map(
          (
            jugador,
            numeros
          ) => (jugador.user.id.toString, numeros)
        )
      )

  // We don't want to give bots perfect memory, but this is the information that we expect most humans would likely know,
  // it's based only on what's been played and contains no information from other player's hand.
  case class MemorySummary(
    trump:            Triunfo,
    leadingNumber:    Numero,
    trumpsSeen:       Seq[Ficha],
    doublesSeen:      Seq[Ficha],
    exhaustedNumbers: Option[Seq[Numero]],
    scarceNumbers:    Option[Seq[Numero]],
    playerVoids:      Option[Map[Jugador, Seq[Numero]]]
  ) derives JsonEncoder

  /** Calculates an imperfect memory summary for the bot, based only on the tiles that have been played so far, and the
    * current game state. This is the information that we expect most humans would likely know, it's based only on
    * what's been played and contains no information from other player's hand. Take a look at game.botDifficultyLevel:
    *
    * Beginner bot Remembers only:
    *   - trump
    *   - current ask
    *   - last 1–2 tricks
    *   - No player void inference
    *
    * Intermediate bot Remembers:
    *   - all doubles played
    *   - trumps seen
    *   - inferred voids (binary)
    *
    * Advanced bot remembers:
    *   - Everything in MemorySummary
    * @param game
    * @param jugador
    * @return
    */
  def calculateMemorySummary(
    game:    Game,
    jugador: Jugador
  ): MemorySummary = {
    val allPlayedTiles = game.jugadores.flatMap(_.filas.flatMap(_.fichas))

    // Get current ask (pidieron) from the first tile on the table
    val pidieron = game.enJuego.headOption.map(_._2) match {
      case Some(ficha) => ficha.arriba // The led number
      case None        => Numero.Numero0 // Default if no tile on table
    }

    // Get current trump (or SinTriunfos as default)
    val triunfo = game.triunfo.getOrElse(SinTriunfos)

    game.botDifficultyLevel match {
      case BotDifficultyLevel.easy =>
        // Beginner: only remembers trump, current ask, and last 1-2 tricks
        val recentTricks = game.jugadores
          .flatMap(_.filas.takeRight(2)) // Last 1-2 tricks
          .flatMap(_.fichas)
          .distinct

        val mulasVistas = recentTricks.filter(_.esMula)
        val triunfosVistos = triunfo match {
          case TriunfoNumero(num) => recentTricks.filter(_.es(num))
          case SinTriunfos        => mulasVistas // With no trump, only mulas matter
        }

        MemorySummary(
          trump = triunfo,
          leadingNumber = pidieron,
          trumpsSeen = triunfosVistos,
          doublesSeen = mulasVistas,
          exhaustedNumbers = None, // Doesn't track exhausted numbers
          scarceNumbers = None, // Doesn't track scarcity
          playerVoids = None // No void inference
        )

      case BotDifficultyLevel.intermediate =>
        // Intermediate: remembers all doubles, trumps seen, and inferred voids (binary)
        val mulasVistas = allPlayedTiles.filter(_.esMula).distinct
        val triunfosVistos = triunfo match {
          case TriunfoNumero(num) => allPlayedTiles.filter(_.es(num)).distinct
          case SinTriunfos        => mulasVistas
        }

        // Infer voids: if a player didn't follow suit when they should have
        val voids = inferVoids(game, jugador)

        MemorySummary(
          trump = triunfo,
          leadingNumber = pidieron,
          trumpsSeen = triunfosVistos,
          doublesSeen = mulasVistas,
          exhaustedNumbers = None, // Doesn't track yet
          scarceNumbers = None, // Doesn't track yet
          playerVoids = Some(voids)
        )

      case BotDifficultyLevel.advanced =>
        // Advanced: remembers everything
        val mulasVistas = allPlayedTiles.filter(_.esMula).distinct
        val triunfosVistos = triunfo match {
          case TriunfoNumero(num) => allPlayedTiles.filter(_.es(num)).distinct
          case SinTriunfos        => mulasVistas
        }

        // Calculate which numbers are completely exhausted (all 8 occurrences played)
        val numeroCounts = allPlayedTiles
          .flatMap(f => Seq(f.arriba, f.abajo))
          .groupBy(identity)
          .view
          .mapValues(_.size)
          .toMap

        val numerosQueYaNoHay = Numero.values.toSeq.filter { num =>
          numeroCounts.getOrElse(num, 0) >= 8 // All 8 tiles with this number played
        }

        // Numbers where few tiles remain (< 3 tiles left)
        val numerosQueHayPocos = Numero.values.toSeq.filter { num =>
          val count = numeroCounts.getOrElse(num, 0)
          count >= 5 && count < 8 // 5-7 played, so 1-3 remain
        }

        // Infer all voids
        val voids = inferVoids(game, jugador)

        MemorySummary(
          trump = triunfo,
          leadingNumber = pidieron,
          trumpsSeen = triunfosVistos,
          doublesSeen = mulasVistas,
          exhaustedNumbers = Some(numerosQueYaNoHay),
          scarceNumbers = Some(numerosQueHayPocos),
          playerVoids = Some(voids)
        )
    }
  }

  /** Infers which numbers each player doesn't have (voids) by analyzing played tricks. If a player didn't follow suit
    * when they should have, they must not have that number.
    *
    * Note: This implementation is simplified because Fila doesn't track which player played which tile. We analyze
    * tricks to detect when players couldn't follow suit, but can't definitively map tiles to specific players without
    * access to the full event history. This gives us probabilistic void information that's still useful for strategic
    * decisions.
    */
  private def inferVoids(
    game:    Game,
    jugador: Jugador
  ): Map[Jugador, Seq[Numero]] = {
    // For a more accurate implementation, we'd need either:
    // 1. Access to game events (Pide/Da) which have userId
    // 2. Or Fila to track List[(UserId, Ficha)] instead of just Seq[Ficha]
    //
    // For now, we'll do a conservative approach: only infer voids for the current trick (enJuego)
    // where we can see who played what

    val voidMap = scala.collection.mutable.Map[Jugador, Set[Numero]]()

    // Analyze current trick in progress (enJuego has (UserId, Ficha) pairs!)
    if (game.enJuego.nonEmpty) {
      val firstPlay = game.enJuego.head
      val pideFicha = firstPlay._2
      val pedido = pideFicha.arriba

      // Check subsequent plays in this trick
      game.enJuego.tail.foreach { case (userId, ficha) =>
        // If they didn't follow suit and it wasn't a mula lead
        if (!ficha.es(pedido) && !pideFicha.esMula) {
          // This player has a void in 'pedido'
          game.jugadores.find(_.user.id == userId).foreach { player =>
            if (player.user.id != jugador.user.id) { // Don't infer our own voids
              voidMap.updateWith(player) {
                case Some(voids) => Some(voids + pedido)
                case None        => Some(Set(pedido))
              }
            }
          }
        }
      }
    }

    // Could also analyze completed tricks, but without userId mapping, it's less reliable
    // For now, we'll keep it simple and only use the current trick

    voidMap.view.mapValues(_.toSeq.sorted(Ordering.by[Numero, Int](_.value))).toMap
  }

  private def triunfoName(t: Triunfo): String =
    t match {
      case SinTriunfos        => "no trump"
      case TriunfoNumero(num) => s"trump=${num.value}"
    }

  extension (event: PlayEvent) {

    // Writes simplified json of the play event that can be used by ai (don't need to include all the details)
    def toSimplifiedJson: Either[String, Json] = {
      // These is simplified json that doesn't have all that a PlayEvent needs, and we don't need to address every playEvent
      event match {
        case canta: Canta =>
          s"""{
              "type"           : "canta",
              "cuantasCantas"  : ${canta.cuantasCantas.numFilas},
              "reasoning"      : "[Why you chose this bid number]"
            }""".fromJson[Json]
        case pide: Pide =>
          s"""{
              "type"           : "pide",
              "ficha"          : "${pide.ficha.toString}",
              "estrictaDerecha": ${pide.estrictaDerecha},
              "trump"          : "${pide.triunfo.getOrElse(SinTriunfos).toString}",
              "reasoning"      : "[Why you led this tile]"
            }""".fromJson[Json]
        case da: Da =>
          s"""{
              "type"           : "da",
              "ficha"          : "${da.ficha.toString}",
              "reasoning"      : "[Why you played this tile]"
            }""".fromJson[Json]
        case caete: Caete =>
          s"""{
              "type"           : "caete",
              "reasoning"      : "[Why you can win all remaining tricks]"
            }""".fromJson[Json]
        case meRindo: MeRindo =>
          s"""{
              "type"           : "meRindo",
              "reasoning"      : "[Why you are surrendering]"
            }""".fromJson[Json]
        case _ => throw new RuntimeException("Unsupported event type for simplified json")
      }
    }

  }

  // Simplified json from AI, that only includes the type of move, and the reasoning, and any details needed for that move, but not all the details that a PlayEvent would need
  def fromSimplifiedJson(
    json:    Json,
    game:    Game,
    jugador: Jugador
  ): IO[GameError, PlayEvent] = {

    (for {
      obj   <- json.asObject
      field <- obj.fields.find(_._1 == "type")
      str   <- field._2.asString
    } yield str) match {
      case Some("canta") =>
        case class Details(
          cuantasCantas: Int,
          reasoning:     Option[String]
        ) derives JsonDecoder
        ZIO
          .fromEither(json.as[Details]).map(d =>
            Canta(
              gameId = game.id,
              userId = jugador.user.id,
              cuantasCantas = CuantasCantas.byNum(d.cuantasCantas),
              reasoning = d.reasoning.map(_ + s" ${formatHand(jugador)}")
            )
          ).mapError(e => GameError(s"Failed to parse Canta details: $e"))

      case Some("pide") =>
        case class Details(
          ficha:           String,
          trump:           Option[String],
          estrictaDerecha: Boolean,
          reasoning:       Option[String]
        ) derives JsonDecoder
        ZIO
          .fromEither(json.as[Details]).flatMap { d =>
            for {
              ficha <- ZIO
                .attempt(Ficha.fromString(d.ficha))
                .mapError(e => GameError(s"Invalid ficha format: ${d.ficha}", Some(e)))
              triunfo <- d.trump match {
                case Some("SinTriunfos") => ZIO.succeed(Option(SinTriunfos))
                case Some(numStr) =>
                  ZIO
                    .fromOption(numStr.toIntOption).mapBoth(
                      _ => GameError(s"Invalid trump value: $numStr"),
                      n => Option(TriunfoNumero(Numero(n)))
                    )
                case None => ZIO.succeed(game.triunfo) // Keep existing trump
              }
            } yield Pide(
              gameId = game.id,
              userId = jugador.user.id,
              ficha = ficha,
              triunfo = triunfo,
              estrictaDerecha = d.estrictaDerecha,
              reasoning = d.reasoning.map(_ + s" ${formatHand(jugador)}")
            )
          }.mapError(e => GameError(s"Failed to parse Pide details: $e"))

      case Some("da") =>
        case class Details(
          ficha:     String,
          reasoning: Option[String]
        ) derives JsonDecoder
        ZIO
          .fromEither(json.as[Details]).flatMap { d =>
            ZIO
              .attempt(Ficha.fromString(d.ficha))
              .mapError(e => GameError(s"Invalid ficha format: ${d.ficha}", Some(e)))
              .map(ficha =>
                Da(
                  gameId = game.id,
                  userId = jugador.user.id,
                  ficha = ficha,
                  reasoning = d.reasoning.map(_ + s" ${formatHand(jugador)}")
                )
              )
          }.mapError(e => GameError(s"Failed to parse Da details: $e"))

      case _ => ZIO.fail(GameError(s"Unsupported or missing type field in JSON: $json"))
    }
  }

  private given Ordering[Triunfo] =
    Ordering.by {
      case SinTriunfos        => -1
      case TriunfoNumero(num) => num.value
    }

  /** Detects special guaranteed Chuti (7-trick) hands that should always bid all tricks.
    *
    * Returns Some(triunfo) if a special Chuti hand is detected, None otherwise.
    *
    * Special cases:
    *   1. Top n consecutive sixes + remaining tiles are mulas (e.g., 6:6, 6:5, 6:4 + mulas)
    *   2. All 6 mulas (6:6, 5:5, 4:4, 3:3, 2:2, 1:1) + 1:0
    *   3. 5 tiles of any trump (except 1) + 1:1 + 1:0 (campanita base)
    */
  private def detectSpecialChuti(fichas: Seq[Ficha]): Option[Triunfo] = {

    val mulas = fichas.filter(_.esMula)

    // Case 1: Top n consecutive sixes starting from 6:6, rest are mulas
    // Examples: [6:6, 6:5, mulas...], [6:6, 6:5, 6:4, mulas...], etc.
    val case1 = if (fichas.exists(f => f.arriba == Numero.Numero6 && f.abajo == Numero.Numero6)) {
      val sixes = fichas.filter(f => f.arriba == Numero.Numero6 || f.abajo == Numero.Numero6).sortBy(-_.value)
      val consecutiveSixes = sixes.takeWhile { f =>
        val expected = Numero.Numero6.value - sixes.indexOf(f)
        f.arriba == Numero.Numero6 && f.abajo.value == expected
      }

      // Check if remaining tiles are all mulas
      val remaining = fichas.diff(consecutiveSixes)
      if (consecutiveSixes.nonEmpty && remaining.forall(_.esMula)) {
        Some(TriunfoNumero(Numero.Numero6))
      } else None
    } else None

    // Case 2: All 6 top mulas (6:6, 5:5, 4:4, 3:3, 2:2, 1:1) + 1:0
    val case2 = {
      val topSixMulas = Set(
        Ficha(Numero.Numero6, Numero.Numero6),
        Ficha(Numero.Numero5, Numero.Numero5),
        Ficha(Numero.Numero4, Numero.Numero4),
        Ficha(Numero.Numero3, Numero.Numero3),
        Ficha(Numero.Numero2, Numero.Numero2),
        Ficha(Numero.Numero1, Numero.Numero1)
      )
      val has1_0 = fichas.contains(Ficha(Numero.Numero1, Numero.Numero0))
      if (mulas.toSet == topSixMulas && has1_0) {
        Some(SinTriunfos)
      } else None
    }

    // Case 3: 5 tiles of any trump (except 1) + 1:1 + 1:0 (campanita base)
    val case3 = {
      val has1_1 = fichas.contains(Ficha(Numero.Numero1, Numero.Numero1))
      val has1_0 = fichas.contains(Ficha(Numero.Numero1, Numero.Numero0))
      if (has1_1 && has1_0) {
        // Check each number (except 1) for 5 tiles
        val candidates = Seq(
          Numero.Numero6,
          Numero.Numero5,
          Numero.Numero4,
          Numero.Numero3,
          Numero.Numero2,
          Numero.Numero0
        )

        candidates
          .find { num =>
            val tilesWithNum = fichas.filter(_.es(num))
            if (tilesWithNum.size == 5) {
              // Must have exactly 5 tiles with this number, plus 1:1 and 1:0
              val expected = Set(
                Ficha(Numero.Numero1, Numero.Numero1),
                Ficha(Numero.Numero1, Numero.Numero0)
              )
              (fichas.toSet -- tilesWithNum.toSet) == expected
            } else false
          }.map(TriunfoNumero.apply)
      } else None
    }

    case1.orElse(case2).orElse(case3)
  }

  // Computes all possible trump options ranked by guaranteed trick count
  private def possibleMoves(
    jugador: Jugador,
    game:    Game
  ): Seq[(triunfo: Triunfo, cuantasDeCaida: Int, cuantosTriunfosYMulas: Int)] = {
    val numerosQueTengo: Seq[Numero] = jugador.fichas
      .flatMap(f => Seq(f.arriba, f.abajo))
      .distinct

    val fichas = jugador.fichas

    val fichasDeOtros = Game.todaLaFicha.diff(fichas)

    val conTriunfos = numerosQueTengo
      .map { num =>
        val triunfo = TriunfoNumero(num)
        (
          triunfo,
          game
            .copy(triunfo = Option(triunfo)).cuantasDeCaida(
              fichas,
              fichasDeOtros
            ).size,
          fichas.count(f => f.es(num)) + fichas.count(f => f.esMula && !f.es(num))
        )
      }

    val ret: Seq[(triunfo: Triunfo, cuantasDeCaida: Int, cuantosTriunfosYMulas: Int)] = conTriunfos ++ {

      val sinTriunfos = fichas.count(_.esMula)

      Seq(
        (
          SinTriunfos,
          sinTriunfos,
          sinTriunfos
        )
      )
    }

    ret.sortBy(t => (-t.cuantasDeCaida, -t.cuantosTriunfosYMulas, t.triunfo))
  }

  /** Calculate the value of a ficha given the trump suit, for comparison purposes. Higher values beat lower values.
    * Based on the logic from Game.highestValueByTriunfo.
    */
  private def fichaValue(
    ficha:   Ficha,
    triunfo: Option[Triunfo]
  ): Int = {
    triunfo match {
      case None => throw GameError("No trump set!")
      case Some(SinTriunfos) =>
        if (ficha.esMula) 100 else ficha.arriba.value
      case Some(TriunfoNumero(num)) =>
        if (ficha.es(num) && ficha.esMula)
          300
        else if (ficha.es(num))
          200 + ficha.other(num).value
        else if (ficha.esMula)
          100
        else
          ficha.arriba.value
    }
  }

  def promptRules(): String = {
    """
    |CHUTI GAME RULES (Quick Reference):
    |
    |COMPONENTS: 28 domino tiles (0:0 to 6:6), 4 players, first to 21 points wins.
    |  - A "double" tile has the same number on both sides (e.g., 6:6). These are the strongest tiles.
    |  - "Trump" = the chosen suit for a round. Trump tiles beat all non-trump tiles.
    |  - "Cantante" = the player who made the bid and must meet it.
    |  - "Hoyo" = failed bid — the cantante LOSES their bid amount from their score (can go negative).
    |
    |PHASES PER ROUND:
    |  1. Deal: One player deals 7 tiles to each player.
    |  2. Bidding: Players bid how many tricks they will win.
    |     - Bid 4 (minimum), 5, 6, or 7 (all tricks = instant win).
    |     - "Buenas" = pass. The dealer MUST bid at least 4 — passing is not allowed for the dealer.
    |     - The cantante is whoever ends up with the highest bid.
    |  3. Play: Cantante leads first, declaring trump with their first tile.
    |     - You MUST follow the led suit if you have it.
    |     - If you cannot follow suit, you MUST play trump if you have it.
    |     - Only if you have neither may you play any tile.
    |     - Highest tile of the led suit wins, unless a trump was played (trump wins).
    |     - If a double is led, ONLY that exact double can win the trick.
    |
    |CRITICAL RULES:
    |  - MUST follow suit/trump when able — breaking this rule = immediate penalty (you lose the round).
    |  - MUST declare you can win all remaining tricks when you can prove it (you cannot hold back).
    |  - Only play tiles in YOUR hand. Choose from the LEGAL MOVES provided.
    |
    |STRATEGY:
    |  - Play conservatively when close to 21 points (protect your lead).
    |  - Play aggressively when far behind (take risks to catch up).
    |  - The cantante risks the most — a failed bid loses points. Bid carefully.
    |  - Strong tiles: high doubles (6:6, 5:5) and high trumps. Weak tiles: low non-trump tiles.
    |
    """.stripMargin
  }

  def promptFooter(): String = {
    s"""
   |Think step-by-step about:
   |1. What tiles are in my hand? (Look at "Your hand" in Game State)
   |2. What are my legal moves? (Look at the Legal Moves list - you can ONLY choose from this list, they are in json format.)
   |3. What is my position in the match? (am I winning/losing?)
   |4. What are the risks? (can I make my bid? will opponents beat me?)
   |5. What is the best strategy given the score? (conservative/aggressive)
   |6. Which legal move best fits my strategy?
   |
   |IMPORTANT: Respond with ONLY a valid JSON object matching the format of one of the valid options above.
   |
   |The "reasoning" field must contain YOUR ACTUAL step-by-step thinking process, NOT placeholder text.
   |Explain WHY you chose this move, considering:
   | - Your hand strength and tiles
   | - The current score and match situation
   | - Your strategy (conservative if leading, aggressive if behind)
   | - What you're trying to accomplish with this move
   |
   |DO NOT use placeholder text like "Step-by-step explanation" or "[Explain why...]".
   |""".stripMargin
  }

  // Derive a stable playing personality from the combination of player and game IDs.
  // Same player has the same personality throughout a game but may differ across games.
  // Different players in the same game are likely to have different styles.
  private def playerPersonality(
    jugador: Jugador,
    game:    Game
  ): String = {
    val index = math.abs((jugador.user.id.value ^ game.id.value) % 5).toInt
    index match {
      case 0 =>
        """|Playing style: VERY CONSERVATIVE.
           |You minimize hoyo risk above everything else. Always bid the lowest amount you can
           |guarantee (Casa whenever possible). In play, always choose the safest tile — the
           |lowest-value option that fulfills your obligation. Never over-bid or take risks,
           |even when you're far behind.""".stripMargin
      case 1 =>
        """|Playing style: CONSERVATIVE.
           |You prefer safe plays over high reward. Bid only what you can guarantee.
           |Protect your strong tiles and fold on uncertain tricks. Only take risks when
           |the math clearly favors you.""".stripMargin
      case 2 =>
        """|Playing style: MODERATE.
           |You balance risk and reward. Bid what you can guarantee and adapt your play
           |to the current score — more aggressive when behind, more cautious when ahead.
           |Take tricks when it makes strategic sense.""".stripMargin
      case 3 =>
        """|Playing style: AGGRESSIVE.
           |You prefer bold moves that maximize your score. Bid one level higher than guaranteed
           |when you have strong trumps. Take tricks assertively, pressure opponents with strong
           |leads, and don't hesitate to spend trumps early to control the game.""".stripMargin
      case _ =>
        """|Playing style: VERY AGGRESSIVE.
           |You play fearlessly. Over-bid to control the cantante role whenever you can.
           |Take high-risk plays for maximum points and try to win every trick possible.
           |You would rather suffer a hoyo trying boldly than play it safe and score little.""".stripMargin
    }
  }

  def promptHeader(
    jugador: Jugador,
    game:    Game
  ): String = {
    val cuentasCalculadas = game.cuentasCalculadas

    val formatCuentas: String = "Player - Points - Hoyos\n" +
      cuentasCalculadas.map { case (j, points, _) =>
        s"""  ${if (j.user.id == jugador.user.id) "You" else j.user.name} - $points - ${j.cuenta.count(_.esHoyo)}\n"""
      }.mkString

    val sortedPlayers = game.jugadores.sortBy(-_.cuenta.map(_.puntos).sum)
    val leader = sortedPlayers.head
    val myRank = sortedPlayers.indexOf(jugador) + 1

    val myScore = jugador.cuenta.map(_.puntos).sum
    val myDistance = 21 - myScore
    val leaderDistance = 21 - leader.cuenta.map(_.puntos).sum

    val inDangerZone = game.jugadores.exists(_.cuenta.map(_.puntos).sum >= 18)
    val dangerPlayers =
      if (inDangerZone)
        game.jugadores.filter(_.cuenta.map(_.puntos).sum >= 18).map(_.user.name).mkString(", ")
      else "none"

    val leaderScore = leader.cuenta.map(_.puntos).sum

    val strategicContext =
      s"""- Your score: $myScore points (rank $myRank/4, need $myDistance more to win)
         | - Leading player: ${leader.user.name} with $leaderScore points
         | - Players in danger zone (18+): $dangerPlayers
         | - Match situation: ${
          if (myScore >= 18) "You're close to winning!"
          else if (leaderScore >= 18 && leader != jugador) "Leader is close to winning!"
          else if (myScore >= leaderScore - 3) "Close race"
          else if (myScore <= leaderScore - 10) "You're far behind"
          else "Normal game flow"
        }
      |""".stripMargin

    s"""${playerPersonality(jugador, game)}
       |
       |YOU ARE: ${jugador.user.name} (when the score table says "You" that means ${jugador.user.name})
       |
       |${promptRules()}
       |
       |You are playing Chuti, a 4-player domino game. Match is played to 21 points.
       | The overall game score is currently (YOU are "${jugador.user.name}"):
       |
       | $formatCuentas
       |
       | $strategicContext
       |
       | Your current hand is
       |
       | ${formatHand(jugador)}
       |
       """
  }

  def formatHand(jugador: Jugador): String = jugador.fichas.map(_.toString).mkString(", ")

  def canta(
    jugador: Jugador,
    game:    Game
  ): IO[GameError, PlayEvent] = {
    // Check for special guaranteed Chuti hands first
    detectSpecialChuti(jugador.fichas) match {
      case Some(specialTriunfo) =>
        // Auto-bid Chuti for special hands
        ZIO.succeed(
          Canta(
            CuantasCantas.CantoTodas,
            gameId = game.id,
            userId = jugador.user.id,
            reasoning = Option(
              s"Special Chuti hand detected with trump $specialTriunfo! Guaranteed to win all 7 tricks. Bidding Chuti automatically. ${formatHand(jugador)}"
            )
          )
        )
      case None =>
        // Normal bidding logic
        val moves = possibleMoves(jugador, game)
        def formatValidOptions(maxByOthers: Int): String = {
          Json
            .Arr((maxByOthers to 7).map { count =>
              Canta(cuantasCantas = CuantasCantas.byNum(count)).toSimplifiedJson
                .getOrElse(Json.Null)
            }*).toString
        }

        // Pre-compute hand analysis as clear text for the LLM
        val handAnalysis = moves
          .take(5).map { case (triunfo, cuantasDeCaida, cuantosTriunfosYMulas) =>
            s"  ${triunfoName(triunfo)}: $cuantasDeCaida tricks GUARANTEED, $cuantosTriunfosYMulas trump+double tiles"
          }.mkString("\n")
        val bestTriunfo = moves.headOption.map(m => triunfoName(m.triunfo)).getOrElse("unknown")
        val bestGuaranteed = moves.headOption.map(_.cuantasDeCaida).getOrElse(0)

        if (jugador.turno) {
          moves.headOption.fold {
            ZIO.succeed(
              MeRindo(
                gameId = game.id,
                userId = jugador.user.id,
                reasoning = Option(s"Nothing playable — surrendering. Hand: ${formatHand(jugador)}")
              ): PlayEvent
            )
          } { max =>
            if (max.cuantasDeCaida <= 3) {
              // Turno player must bid at least Casa, even with a weak hand
              ZIO.succeed(
                Canta(
                  Casa,
                  gameId = game.id,
                  userId = jugador.user.id,
                  reasoning =
                    Option(s"Forced to bid Casa (4) — only ${max.cuantasDeCaida} guaranteed tricks. Hoping someone saves me. Hand: ${formatHand(jugador)}")
                )
              )
            } else {

              val prompt = promptHeader(jugador, game) +
                s"""
                |=== BIDDING DECISION ===
                | YOU (${jugador.user.name}) MUST PLACE A BID NOW.
                | You are the turno player — you MUST bid at least 4. Passing is not allowed.
                |
                | YOUR HAND ANALYSIS (pre-computed guaranteed tricks per trump choice):
                |$handAnalysis
                |
                | RECOMMENDED: bid $bestGuaranteed with $bestTriunfo ($bestGuaranteed tricks guaranteed)
                |
                | "Guaranteed tricks" = tricks you are mathematically CERTAIN to win no matter what opponents play.
                | A higher guaranteed count = stronger hand. DO NOT bid higher than your guaranteed count unless you accept the risk of losing points.
                |
                | RISK: If you fail to win the number of tricks you bid, you LOSE that many points.
                |
                | Valid bids (you MUST choose one of these bid numbers — no other values are valid):
                | ${formatValidOptions(4)}
                |
                | RULES:
                | 1. Choose ONLY from the valid bids above (cuantasCantas field must be 4, 5, 6, or 7)
                | 2. The bid number is your COMMITMENT — you must win at least that many tricks
                | 3. Bid conservatively if close to winning; bid aggressively if far behind
                |
                |""".stripMargin + promptFooter()

              runPrompt(prompt, jugador, game)

            }
          }
        } else {
          // Not the turno player — may optionally outbid to take cantante role
          val maxPlayer: Option[Jugador] = game.jugadores
            .filter(_.user.id != jugador.user.id)
            .maxByOption(_.cuantasCantas.map(_.numFilas).getOrElse(4))

          val maxBidByOthers = maxPlayer.flatMap(_.cuantasCantas).fold(4)(_.numFilas)
          val topMove = moves.headOption.map(_.cuantasDeCaida).getOrElse(4)

          /*
      maxBidByOthers | topMove | Interpretation
       5 vs 5 => 0  // Can't outbid
       5 vs 4 => 1  // Can't outbid
       5 vs 3 => 2  // Can't outbid
       5 vs 6 => -1 // Can outbid
       4 vs 6 => -2 // Can outbid (good hand)
           */
          val diff = maxBidByOthers - topMove

          if (topMove == 7) {
            ZIO.succeed(
              Canta(
                CuantasCantas.CantoTodas,
                gameId = game.id,
                userId = jugador.user.id,
                reasoning = Option(s"7 tricks guaranteed — bidding Chuti. Hand: ${formatHand(jugador)}")
              )
            )
          } else if (maxBidByOthers == 7) {
            ZIO.succeed(
              Canta(
                Buenas,
                gameId = game.id,
                userId = jugador.user.id,
                reasoning = Option(s"Cannot outbid Chuti — passing. Hand: ${formatHand(jugador)}")
              )
            )
          } else if (diff >= 0) {
            // My guaranteed tricks don't beat the current bid — pass
            ZIO.succeed(
              Canta(
                Buenas,
                gameId = game.id,
                userId = jugador.user.id,
                reasoning =
                  Option(s"Passing — my guaranteed tricks ($topMove) don't beat current bid ($maxBidByOthers). Hand: ${formatHand(jugador)}")
              )
            )
          } else if (maxBidByOthers > 4 && diff < 0) {
            // Already saved beyond Casa — outbid to take the points
            ZIO.succeed(
              Canta(
                CuantasCantas.byNum(topMove),
                gameId = game.id,
                userId = jugador.user.id,
                reasoning = Option(
                  s"Bid is already above 4 — outbidding to $topMove and taking the points. Hand: ${formatHand(jugador)}"
                )
              )
            )
          } else {
            val maxBidderName = maxPlayer.map(_.user.name).getOrElse("someone")
            val prompt = promptHeader(jugador, game) +
              s"""
             |=== BIDDING DECISION ===
             | YOU (${jugador.user.name}) may optionally outbid the current bid to take the cantante role.
             |
             | Current highest bid: $maxBidByOthers tricks by $maxBidderName
             | To outbid, you must bid MORE than $maxBidByOthers. Or you can pass (Buenas = bid 0 in the options).
             |
             | YOUR HAND ANALYSIS (pre-computed guaranteed tricks per trump choice):
             |$handAnalysis
             |
             | RECOMMENDED: bid $bestGuaranteed with $bestTriunfo ($bestGuaranteed tricks guaranteed)
             |
             | Should you outbid?
             | - YES if your guaranteed tricks beat $maxBidByOthers AND it helps your score position
             | - YES if you're far behind and need the points
             | - NO if your guaranteed tricks do not exceed $maxBidByOthers (you can't safely outbid)
             | - NO if you're comfortably ahead and don't want to risk losing points
             |
             | Valid bids (you MUST choose one — "Buenas" = pass, shown as 0 in the options):
             | ${formatValidOptions(maxBidByOthers)}
             |
             | RULES:
             | 1. Choose ONLY from the valid bids above
             | 2. If you bid, you commit to winning that many tricks — fail = hoyo (lose those points)
             | 3. You can ONLY outbid if your bid number is STRICTLY GREATER than $maxBidByOthers
             |
             |""".stripMargin + promptFooter()

            runPrompt(prompt, jugador, game)
          }
        }
    }
  }

  def pideInicial(
    jugador: Jugador,
    game:    Game
  ): IO[GameError, PlayEvent] = {
    val bid = jugador.cuantasCantas.fold(4)(_.numFilas)

    val allMoves = possibleMoves(jugador, game)

    // Check if any trump option makes us de caida (can guarantee bid tricks or more)
    val deCaidaOpt = allMoves.find(_.cuantasDeCaida >= bid)
    if (deCaidaOpt.isDefined) {
      val bestTrump = deCaidaOpt.get.triunfo
      ZIO.succeed(
        Caete(
          triunfo = Some(bestTrump),
          gameId = game.id,
          userId = jugador.user.id,
          reasoning =
            Option(s"Auto-fall: guaranteed all tricks with trump $bestTrump (${deCaidaOpt.get.cuantasDeCaida} of $bid tricks). Hand: ${formatHand(jugador)}")
        )
      )
    } else {

      val moves: Seq[(triunfo: Triunfo, pide: Ficha, cuantasDeCaida: Int, cuantosTriunfosYMulas: Int)] =
        allMoves.zipWithIndex
          .filter(
            (
              t,
              i
            ) =>
              // We have to at least take one option
              i == 0 || t.cuantasDeCaida >= bid
          )
          .map(_._1)
          .take(3)
          .map { case (triunfo, cuantasDeCaida, cuantosTriunfosYMulas) =>
            val hypotheticalGame = game.copy(triunfo = Option(triunfo))
            val pide = hypotheticalGame.highestValueByTriunfo(jugador.fichas).get
            (triunfo, pide, cuantasDeCaida, cuantosTriunfosYMulas)
          }

      // The trump and opening tile were already chosen during canta via possibleMoves().
      // allMoves is already sorted best-first, and moves is filtered to legal options.
      // Just execute the best option deterministically — no LLM needed.
      moves.headOption match {
        case None =>
          // No moves at all (shouldn't happen — possibleMoves always has at least SinTriunfos)
          ZIO.succeed(
            MeRindo(gameId = game.id, userId = jugador.user.id, reasoning = Option("No valid moves in pideInicial"))
          )
        case Some(best) =>
          ZIO.succeed(
            Pide(
              ficha = best.pide,
              triunfo = Option(best.triunfo),
              estrictaDerecha = false,
              gameId = game.id,
              userId = jugador.user.id,
              reasoning = Option(
                s"PideInicial: playing trump ${best.triunfo} with ${best.pide} " +
                  s"(${best.cuantasDeCaida} guaranteed tricks, ${best.cuantosTriunfosYMulas} trump+double tiles) Hand: ${formatHand(jugador)}"
              )
            )
          )
      }
    }
  }

  def pide(
    jugador: Jugador,
    game:    Game
  ): IO[GameError, PlayEvent] = {
    // Check if hoyo is imminent and we should surrender (only allowed before second trick)
    val canStillSurrender = jugador.fichas.size >= 6 // Before second trick (6-7 tiles remaining)
    val bidAmount = jugador.cuantasCantas.fold(4)(_.numFilas)
    val tricksWonSoFar = jugador.filas.size
    val tricksRemaining = jugador.fichas.size
    val tricksNeeded = bidAmount - tricksWonSoFar

    val imScrewed: Boolean = if (jugador.cantante && canStillSurrender) {

      // Analyze tiles that have ACTUALLY been played so far
      val fichasJugadas = game.jugadores.flatMap(_.filas.flatMap(_.fichas))
      // TODO should check to see how little we got, if we didn't get a big showing, maybe we should give up

      val triunfosYMulasEnMano = jugador.fichas.count(f =>
        f.esMula || game.triunfo.exists {
          case TriunfoNumero(num) => f.es(num)
          case SinTriunfos        => false
        }
      )

      // Surrender if:
      // 1. Mathematically impossible (need more tricks than remaining)
      // 2. Very unlikely (need more tricks than we can guarantee + 1 buffer)
      tricksNeeded > tricksRemaining ||
      tricksNeeded > triunfosYMulasEnMano + 1
    } else {
      false
    }

    if (imScrewed) {
      ZIO.succeed(
        MeRindo(
          gameId = game.id,
          userId = jugador.user.id,
          reasoning = Option(
            s"Surrender: Need ${jugador.cuantasCantas.fold(4)(_.numFilas) - jugador.filas.size} more tricks with ${jugador.fichas.size} tiles remaining, hoyo is imminent. Better to cut losses now. ${formatHand(jugador)}"
          )
        )
      )
    } else if (jugador.fichas.size == 1) {
      // Only one tile left - check if can caerse, otherwise just play it
      val singleFicha = jugador.fichas.head
      val currentTriunfo = game.triunfo.getOrElse(SinTriunfos)

      if (game.puedesCaerte(jugador)) {
        ZIO.succeed(
          Caete(
            triunfo = Some(currentTriunfo),
            gameId = game.id,
            userId = jugador.user.id,
            reasoning = Option(s"Auto-play: Last tile ($singleFicha) and can caerse")
          )
        )
      } else {
        ZIO.succeed(
          Pide(
            ficha = singleFicha,
            estrictaDerecha = false,
            triunfo = Some(currentTriunfo),
            gameId = game.id,
            userId = jugador.user.id,
            reasoning = Option(s"Auto-play: Last tile remaining ($singleFicha)")
          )
        )
      }
    } else if (jugador.cantante) {
      // You're the cantante (bidder) and have the lead - focus on making your bid
      val legalMoves: Seq[PlayEvent] = {
        // Check if this is the initial pide (cantante's first move)
        if (game.triunfo.isEmpty && jugador.cantante && jugador.filas.isEmpty && game.enJuego.isEmpty) {
          // Generate all possible trump options
          val triunfos: Seq[Triunfo] =
            SinTriunfos +: Numero.values.filter(n => jugador.fichas.exists(_.es(n))).map(TriunfoNumero.apply).toSeq

          // For each trump, calculate possible plays
          val res: Seq[PlayEvent] = triunfos.flatMap { triunfo =>
            // All fichas are valid for initial pide
            jugador.fichas.map { ficha =>
              val hypotheticalGame = game.copy(triunfo = Option(triunfo))
              if (hypotheticalGame.puedesCaerte(jugador)) {
                Caete(
                  triunfo = Option(triunfo),
                  gameId = game.id,
                  userId = jugador.user.id
                )
              } else {

                Pide(ficha = ficha, triunfo = Option(triunfo), estrictaDerecha = false)
              }
            }
          }
          res

        } else if (jugador.mano && game.enJuego.isEmpty) {
          // Player has mano and needs to pide
          val currentTriunfo = game.triunfo.getOrElse(SinTriunfos)
          val res: Seq[PlayEvent] = if (game.puedesCaerte(jugador)) {
            Seq(
              Caete(
                triunfo = Some(currentTriunfo),
                gameId = game.id,
                userId = jugador.user.id
              )
            ) // If you can caerte, do it instead of pide
          } else {
            val pides = jugador.fichas.map { ficha =>
              Pide(ficha = ficha, triunfo = Some(currentTriunfo), estrictaDerecha = false)
            }
            // MeRindo is valid before the second trick (filas.size <= 1)
            val surrender =
              if (jugador.filas.size <= 1)
                Seq(MeRindo(gameId = game.id, userId = jugador.user.id))
              else Seq.empty
            pides ++ surrender
          }
          res
        } else Seq.empty // No legal moves
      }

      val memory = calculateMemorySummary(game, jugador)

      val fichasJugadas = game.jugadores.flatMap(_.filas.flatMap(_.fichas)).toSet
      val fichasRestantes = Game.todaLaFicha.diff(jugador.fichas).diff(fichasJugadas.toSeq)
      val deCaida = game.cuantasDeCaida(jugador.fichas, fichasRestantes).size

      // Trump-protection analysis: if there are unplayed trumps in opponents' hands that
      // outrank our best trump, leading ANY of our trumps is a guaranteed loss.
      // In that case, remove trump leads from the options so the LLM cannot pick them.
      val (safeLegalMoves, trumpWarning) = game.triunfo match {
        case Some(triunfoOpt @ TriunfoNumero(triunfoNum)) =>
          val myTrumps = legalMoves.collect { case p: Pide if p.ficha.es(triunfoNum) => p }
          val myBestTrumpValue = myTrumps.map(p => fichaValue(p.ficha, game.triunfo)).maxOption.getOrElse(-1)
          val unplayedOpponentTrumps = Game.todaLaFicha
            .filter(_.es(triunfoNum))
            .filterNot(fichasJugadas.contains)
            .filterNot(jugador.fichas.contains)
          val highestOpponentTrump = unplayedOpponentTrumps.map(fichaValue(_, game.triunfo)).maxOption.getOrElse(-1)

          if (highestOpponentTrump > myBestTrumpValue && myTrumps.nonEmpty) {
            val nonTrumpMoves = legalMoves.filterNot { case p: Pide => p.ficha.es(triunfoNum); case _ => false }
            if (nonTrumpMoves.nonEmpty)
              (
                nonTrumpMoves,
                Some(
                  s"TRUMP WARNING: There are unplayed trumps in opponents' hands (highest value $highestOpponentTrump) " +
                    s"that outrank your best trump (value $myBestTrumpValue). " +
                    s"Leading trump now guarantees you LOSE it. Trump leads have been removed from your options. " +
                    s"Lead a non-trump tile instead to preserve your trumps."
                )
              )
            else
              (
                legalMoves,
                Some(s"You only have trumps left — you must lead one, but beware: opponents have higher trumps (value $highestOpponentTrump) vs your best (value $myBestTrumpValue).")
              )
          } else
            (legalMoves, None)

        case _ => (legalMoves, None)
      }

      val formatValidOptions = Json.Arr(safeLegalMoves.map(_.toSimplifiedJson.getOrElse(Json.Null))*).toString

      val prompt = promptHeader(jugador, game) +
        s"""
           | Memory (what you remember from play so far):
           | ${memory.toJson}
           |
           | Current Situation:
           | - You are the cantante (bidder)
           | - You bid $bidAmount tricks and have won $tricksWonSoFar so far
           | - You need $tricksNeeded more tricks from $tricksRemaining remaining
           | - Calculated guaranteed wins: $deCaida
           | - Trump: ${game.triunfo.getOrElse("not set")}
           |${trumpWarning.fold("")(w => s"\n | ⚠️  $w\n")}
           | Valid tiles you can lead:
           | $formatValidOptions
           |
           | Strategy:
           | - You have the lead - choose wisely to win tricks
           | - Lead with strong tiles when you need to win this trick
           | - Lead with weak tiles when you want to preserve strength for later
           | - Consider which numbers opponents might be void in (from memory)
           | - If you have $tricksNeeded guaranteed wins, you can play more conservatively
           | - If $tricksNeeded > $deCaida, you need to take risks to win extra tricks
           | - If all the trumps have been played and you're not likely to win this ask, then play a small tile and hope the hand comes back to you.
           | - Only lead trump when YOUR trump is the HIGHEST remaining — then it wins guaranteed.
           |
           | CRITICAL:
           | 1. Choose from valid tiles above
           | 2. Only tiles from your hand are valid
           | 3. Remember: you MUST make your bid or suffer a hoyo (lose points)
           |
           |""".stripMargin + promptFooter()

      runPrompt(prompt, jugador, game)

    } else {
      // You're NOT the cantante - try to make them fail (hoyo)
      // Inline getLegalPide logic
      val currentTriunfo = game.triunfo.getOrElse(SinTriunfos)
      val legalMoves = jugador.fichas.map { ficha =>
        Pide(ficha = ficha, estrictaDerecha = false, triunfo = Option(currentTriunfo)) // Use game's triunfo, estrictaDerecha defaults to false
      } ++ {
        if (game.puedesCaerte(jugador)) {
          Seq(Caete(triunfo = Option(currentTriunfo), gameId = game.id, userId = jugador.user.id))
        } else Seq.empty
      }
      val memory = calculateMemorySummary(game, jugador)

      val cantante = game.jugadores.find(_.cantante).get
      val cantanteBid = cantante.cuantasCantas.fold(4)(_.numFilas)
      val cantanteTricks = cantante.filas.size
      val cantanteNeeds = cantanteBid - cantanteTricks

      val formatValidOptions = Json.Arr(legalMoves.map(_.toSimplifiedJson.getOrElse(Json.Null))*).toString

      val prompt = promptHeader(jugador, game) +
        s"""
           | Memory (what you remember from play so far):
           | ${memory.toJson}
           |
           | Current Situation:
           | - ${cantante.user.name} is the cantante (bidder)
           | - They bid $cantanteBid tricks and have won $cantanteTricks so far
           | - They need $cantanteNeeds more tricks to make their bid
           | - You have won $tricksWonSoFar tricks
           | - Trump: ${game.triunfo.getOrElse("not set")}
           |
           | Valid tiles you can lead:
           | $formatValidOptions
           |
           | Strategy - Make Them Fail (Hoyo):
           | - Your goal is to DENY the cantante tricks
           | - If they need $cantanteNeeds tricks and there are $tricksRemaining remaining, look for ways to win tricks yourself
           | - Lead with numbers you think they might be void in (check memory for voids)
           | - If you have strong tiles, use them strategically to win tricks away from the cantante
           | - If the cantante is close to failing, be aggressive
           | - If they're comfortably ahead, focus on winning your own tricks for points
           | - Don't help the cantante by leading tiles they can easily win
           |
           | CRITICAL:
           | 1. Choose from valid tiles above
           | 2. Only tiles from your hand are valid
           | 3. Making the cantante fail (hoyo) is strategically valuable - they lose points!
           |
           |""".stripMargin + promptFooter()

      runPrompt(prompt, jugador, game)
    }
  }

  def da(
    jugador: Jugador,
    game:    Game
  ): IO[GameError, PlayEvent] = {
    val pidePlay = game.enJuego.head
    val pide = pidePlay._2
    val pedido = game.triunfo match {
      case Some(SinTriunfos) => pide.arriba
      case Some(TriunfoNumero(triunfo)) =>
        if (pide.es(triunfo)) triunfo else pide.arriba
      case None => throw GameError("No trump set!")
    }

    def legalMoves = {
      game.triunfo match {
        case Some(SinTriunfos) =>
          val validFichas = jugador.fichas.filter(_.es(pedido))
          if (validFichas.nonEmpty) validFichas
          else jugador.fichas // Can play any ficha if can't follow
        case Some(TriunfoNumero(triunfo)) =>
          val validFichas = jugador.fichas.filter(_.es(pedido))
          if (validFichas.nonEmpty) validFichas
          else {
            // Must play trump if you have it; only free choice if you have neither
            val trumpFichas = jugador.fichas.filter(_.es(triunfo))
            if (trumpFichas.nonEmpty) trumpFichas
            else jugador.fichas
          }
        case None => throw GameError("No trump set!")
      }
    }

    // Auto-play if only one legal option
    if (jugador.fichas.size == 1) {
      ZIO.succeed(
        Da(
          ficha = jugador.fichas.head,
          gameId = game.id,
          userId = jugador.user.id,
          reasoning = Option("No thinking needed, only one tile left to play")
        )
      )
    } else if (legalMoves.size == 1) {
      ZIO.sleep(1.second) *> ZIO.succeed(
        Da(
          ficha = legalMoves.head,
          gameId = game.id,
          userId = jugador.user.id,
          reasoning = Option(
            s"Only one legal tile to play ${legalMoves.head.toString}, playing it automatically ${formatHand(jugador)}"
          )
        )
      )
      ///////////////////////////////////////////////////////////////////////////
      // 4th-to-play optimization: when three tiles are already on the table the
      // trick winner is already determined — no LLM reasoning needed.
      // Against cantante: beat them with the smallest winning tile, or dump lowest.
      // Cantante is not winning: save your resources, dump the lowest legal tile.
      ///////////////////////////////////////////////////////////////////////////
    } else if (game.enJuego.size == 3) {
      val currentWinner = game.enJuego.map(_._2).maxBy(f => fichaValue(f, game.triunfo))
      val cantanteWinning = game.jugadores.find(_.cantante).exists { c =>
        game.enJuego.exists { case (uid, ficha) => uid == c.user.id && ficha == currentWinner }
      }
      if (cantanteWinning) {
        val smallestBeater = legalMoves
          .filter(f => fichaValue(f, game.triunfo) > fichaValue(currentWinner, game.triunfo))
          .minByOption(_.value)
        smallestBeater match {
          case Some(tile) =>
            ZIO.sleep(1.second) *> ZIO.succeed(
              Da(
                ficha = tile,
                gameId = game.id,
                userId = jugador.user.id,
                reasoning = Option(s"4th to play: beating cantante with smallest winning tile ${formatHand(jugador)}")
              )
            )
          case None =>
            ZIO.sleep(1.second) *> ZIO.succeed(
              Da(
                ficha = legalMoves.minBy(_.value),
                gameId = game.id,
                userId = jugador.user.id,
                reasoning = Option(s"4th to play: can't beat cantante, dumping lowest ${formatHand(jugador)}")
              )
            )
        }
      } else {
        ZIO.sleep(1.second) *> ZIO.succeed(
          Da(
            ficha = legalMoves.minBy(_.value),
            gameId = game.id,
            userId = jugador.user.id,
            reasoning = Option(s"4th to play: cantante not winning, dumping lowest ${formatHand(jugador)}")
          )
        )
      }
      ///////////////////////////////////////////////////////////////////////////
    } else if (jugador.cantante) {
      // You're the cantante but lost the lead - goal is to RECOVER the lead
      val canFollowSuit = jugador.fichas.exists(_.es(pedido))
      val trumps = game.triunfo match {
        case Some(TriunfoNumero(num)) => jugador.fichas.filter(_.es(num))
        case _                        => jugador.fichas.filter(_.esMula)
      }

      if (!canFollowSuit && trumps.isEmpty) {
        // Can't follow, no trumps - play lowest tile (least valuable)
        val lowestTile = legalMoves.minBy(_.value)
        ZIO.sleep(1.second) *> ZIO.succeed(
          Da(
            ficha = lowestTile,
            gameId = game.id,
            userId = jugador.user.id,
            reasoning =
              Option(s"Can't follow suit or trump, playing lowest tile to minimize loss ${formatHand(jugador)}")
          )
        )
      } else {
        ///////////////////////////////////////////////////////////////////////////
        // Cantante "can't beat winner" optimization: if none of the legal tiles
        // outrank the current winning tile, the cantante will lose this trick
        // regardless — skip the LLM and dump the lowest tile.
        ///////////////////////////////////////////////////////////////////////////
        val currentWinningTile = game.enJuego.map(_._2).maxBy(f => fichaValue(f, game.triunfo))
        val canBeatWinner =
          legalMoves.exists(f => fichaValue(f, game.triunfo) > fichaValue(currentWinningTile, game.triunfo))
        if (!canBeatWinner) {
          ZIO.sleep(1.second) *> ZIO.succeed(
            Da(
              ficha = legalMoves.minBy(_.value),
              gameId = game.id,
              userId = jugador.user.id,
              reasoning = Option(s"Cantante: no legal tile beats current winner, dumping lowest ${formatHand(jugador)}")
            )
          )
        } else {
          ///////////////////////////////////////////////////////////////////////////
          // Have a chance to win — ask the LLM
          val memory = calculateMemorySummary(game, jugador)
          val bidAmount = jugador.cuantasCantas.fold(4)(_.numFilas)
          val tricksWon = jugador.filas.size
          val tricksNeeded = bidAmount - tricksWon

          val formatValidOptions = Json
            .Arr(legalMoves.map { ficha =>
              Da(ficha = ficha).toSimplifiedJson.getOrElse(Json.Null)
            }*).toString
          val tilesOnTable = game.enJuego
            .map { case (userId, ficha) =>
              val playerName = game.jugadores.find(_.user.id == userId).map(_.user.name).getOrElse("Unknown")
              s"$playerName played ${ficha.toString}"
            }.mkString(", ")

          val prompt = promptHeader(jugador, game) +
            s"""
             | Memory (what you remember from play so far):
             | ${memory.toJson}
             |
             | Current Situation - RECOVER THE LEAD:
             | - You are the cantante (bidder) but lost the lead
             | - You bid $bidAmount, won $tricksWon, need $tricksNeeded more tricks
             | - They asked for: $pedido
             | - Tiles already played this trick: $tilesOnTable
             | - Current winning tile on table: ${currentWinningTile.toString}
             | - Your goal: RECOVER the lead by winning this trick if possible
             |
             | Valid tiles you can play:
             | $formatValidOptions
             |
             | Strategy - Recover the Lead:
             | - If you can WIN this trick, do it with your smallest winning tile
             | - Winning the lead means you control the next play
             | - If you can't win, play your least valuable tile
             | - Consider: can you win with a non-trump before using trumps?
             | - Save your strongest tiles for when you have the lead again
             |
             | CRITICAL:
             | 1. Choose from valid tiles above
             | 2. Priority: WIN this trick to recover lead
             | 3. If you can't win, minimize loss
             |
             |""".stripMargin + promptFooter()

          runPrompt(prompt, jugador, game)
        } // end canBeatWinner else
      }
    } else {
      // You're NOT the cantante - goal is to DENY them tricks
      val cantante = game.jugadores.find(_.cantante).get
      val canFollowSuit = jugador.fichas.exists(_.es(pedido))
      val trumpNum = game.triunfo match {
        case Some(TriunfoNumero(num)) => Some(num)
        case _                        => None
      }
      val trumps = trumpNum.map(num => jugador.fichas.filter(_.es(num))).getOrElse(Seq.empty)

      val askingForTrump = trumpNum.contains(pedido)
      val isNonTrumpDouble = pide.esMula && !askingForTrump
      val is6_5_WithDifferentTrump =
        pide == Ficha(Numero.Numero6, Numero.Numero5) &&
          !trumpNum.exists(n => n == Numero.Numero6 || n == Numero.Numero5)

      // Auto-play scenarios for non-cantante
      if (!canFollowSuit && trumps.isEmpty) {
        // Can't follow, no trumps - dump lowest/worthless tile
        val lowestTile = legalMoves.minBy(_.value)
        ZIO.sleep(1.second) *> ZIO.succeed(
          Da(
            ficha = lowestTile,
            gameId = game.id,
            userId = jugador.user.id,
            reasoning = Option(s"Can't follow or trump, dumping lowest tile ${formatHand(jugador)}")
          )
        )
      } else if (askingForTrump) {
        // They're asking for trump
        val currentWinningTile = game.enJuego.map(_._2).maxBy(f => fichaValue(f, game.triunfo))
        val canWin =
          legalMoves.exists(f => fichaValue(f, game.triunfo) > fichaValue(currentWinningTile, game.triunfo))

        if (!canWin) {
          // Can't win - play smallest trump
          val smallestTrump = legalMoves.filter(f => trumpNum.exists(f.es)).minByOption(_.value)
          smallestTrump match {
            case Some(tile) =>
              ZIO.sleep(1.second) *> ZIO.succeed(
                Da(
                  ficha = tile,
                  gameId = game.id,
                  userId = jugador.user.id,
                  reasoning = Option(s"Asking for trump, can't win, playing smallest trump ${formatHand(jugador)}")
                )
              )
            case None =>
              // No trumps, play smallest tile
              val smallestTile = legalMoves.minBy(_.value)
              ZIO.sleep(1.second) *> ZIO.succeed(
                Da(
                  ficha = smallestTile,
                  gameId = game.id,
                  userId = jugador.user.id,
                  reasoning = Option(s"Asking for trump, don't have it, playing smallest tile ${formatHand(jugador)}")
                )
              )
          }
        } else {
          // Can win - check if someone already beat the cantante
          val cantanteAlreadyBeaten = game.enJuego.exists { case (userId, ficha) =>
            userId != cantante.user.id &&
            fichaValue(ficha, game.triunfo) > fichaValue(
              game.enJuego.find(_._1 == cantante.user.id).map(_._2).getOrElse(pide),
              game.triunfo
            )
          }

          if (cantanteAlreadyBeaten) {
            // Someone already beat cantante, save your trumps
            val smallestTrump =
              legalMoves.filter(f => trumpNum.exists(f.es)).minByOption(_.value).getOrElse(legalMoves.minBy(_.value))
            ZIO.sleep(1.second) *> ZIO.succeed(
              Da(
                ficha = smallestTrump,
                gameId = game.id,
                userId = jugador.user.id,
                reasoning = Option(s"Cantante already beaten, saving trumps, playing smallest ${formatHand(jugador)}")
              )
            )
          } else {
            // Win with smallest winning trump
            val winningTrumps =
              legalMoves.filter(f => fichaValue(f, game.triunfo) > fichaValue(currentWinningTile, game.triunfo))
            val smallestWinning = winningTrumps.minBy(_.value)
            ZIO.sleep(1.second) *> ZIO.succeed(
              Da(
                ficha = smallestWinning,
                gameId = game.id,
                userId = jugador.user.id,
                reasoning = Option(s"Winning with smallest trump to deny cantante ${formatHand(jugador)}")
              )
            )
          }
        }
      } else if (isNonTrumpDouble || is6_5_WithDifferentTrump) {
        // Asking with non-trump double or 6:5 with different trumps - play smallest
        val smallestMatching = legalMoves.filter(_.es(pedido)).minByOption(_.value)
        smallestMatching match {
          case Some(tile) =>
            ZIO.sleep(1.second) *> ZIO.succeed(
              Da(
                ficha = tile,
                gameId = game.id,
                userId = jugador.user.id,
                reasoning = Option(
                  s"Non-trump double or 6:5 with different trumps, playing smallest matching ${formatHand(jugador)}"
                )
              )
            )
          case None =>
            // Shouldn't happen if legal moves are correct, but fallback
            val smallestTile = legalMoves.minBy(_.value)
            ZIO.sleep(1.second) *> ZIO.succeed(
              Da(
                ficha = smallestTile,
                gameId = game.id,
                userId = jugador.user.id,
                reasoning = Option(s"Playing smallest tile ${formatHand(jugador)}")
              )
            )
        }
      } else {
        // Have choices - ask AI about denying cantante
        val memory = calculateMemorySummary(game, jugador)
        val cantanteBid = cantante.cuantasCantas.fold(4)(_.numFilas)
        val cantanteTricks = cantante.filas.size
        val cantanteNeeds = cantanteBid - cantanteTricks

        val formatValidOptions = Json
          .Arr(legalMoves.map { ficha =>
            Da(ficha = ficha).toSimplifiedJson.getOrElse(Json.Null)
          }*).toString

        val currentWinningTile = game.enJuego.map(_._2).maxBy(f => fichaValue(f, game.triunfo))
        val tilesOnTable = game.enJuego
          .map { case (userId, ficha) =>
            val playerName = game.jugadores.find(_.user.id == userId).map(_.user.name).getOrElse("Unknown")
            s"$playerName played ${ficha.toString}"
          }.mkString(", ")

        val prompt = promptHeader(jugador, game) +
          s"""
             | Memory (what you remember from play so far):
             | ${memory.toJson}
             |
             | Current Situation - DENY THE CANTANTE:
             | - ${cantante.user.name} is the cantante (bidder)
             | - They bid $cantanteBid, won $cantanteTricks, need $cantanteNeeds more tricks
             | - They asked for: $pedido
             | - Tiles already played this trick: $tilesOnTable
             | - Current winning tile on table: ${currentWinningTile.toString}
             | - Your goal: PREVENT the cantante from winning tricks
             |
             | Valid tiles you can play:
             | $formatValidOptions
             |
             | Strategy - Deny Them Tricks:
             | - Most important: Don't give the cantante good tiles
             | - If you can WIN this trick away from cantante, consider it
             | - If cantante is currently winning, try to beat them with smallest winning tile
             | - If someone else is winning, don't waste strong tiles
             | - When in doubt, play tiles that are least helpful to them
             | - Remember: Making them fail (hoyo) is more valuable than your own points
             |
             | CRITICAL:
             | 1. Choose from valid tiles above
             | 2. Priority: DENY cantante tricks
             | 3. Don't give them valuable tiles or help them recover
             |
             |""".stripMargin + promptFooter()

        runPrompt(prompt, jugador, game)
      }
    }
  }

  override def decideTurn(
    user: User,
    game: Game
  ): IO[GameError, PlayEvent] = {
    val jugador = game.jugador(user.id)
    game.gameStatus match {
      case GameStatus.cantando =>
        canta(jugador, game)
      case GameStatus.jugando =>
        if (jugador.mano && game.triunfo.isDefined && game.puedesCaerte(jugador))
          ZIO.sleep(1.second) *> ZIO.succeed(
            Caete(
              triunfo = game.triunfo,
              gameId = game.id,
              userId = jugador.user.id
            )
          )
        else if (game.triunfo.isEmpty && jugador.cantante && jugador.filas.isEmpty && game.enJuego.isEmpty)
          pideInicial(jugador, game)
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

  private def runPrompt(
    prompt:  String,
    jugador: Jugador,
    game:    Game
  ): IO[GameError, PlayEvent] = {
    val finalPrompt = prompt
    ZIO.logDebug(s"🎮 Bot ${jugador.user.name} starting decision (Game ${game.id}, Status: ${game.gameStatus})") *>
      (for {
        _ <- ZIO.logDebug(s"⏰ LLM timeout set to ${config.timeout.toSeconds} seconds")
        responseOpt <- llmService
          .generate(finalPrompt)
          .timeout(config.timeout)
          .mapError(GameError.apply)
        response <- responseOpt match {
          case None =>
            for {
              updated <- stats.updateAndGet(s => s.copy(timeouts = s.timeouts + 1))
              _       <- ZIO.logWarning(s"⏱️ LLM timeout for ${jugador.user.name}. ${updated.summary}")
              result  <- ZIO.fail(GameError("LLM timeout"))
            } yield result
          case Some(r) =>
            stats.update(s => s.copy(successes = s.successes + 1)).as(r)
        }
        _ <- ZIO.logDebug(s"🔍 Parsing LLM response for ${jugador.user.name}")
        decision <- ZIO
          .fromEither(response.fromJson[Json]).mapError(e =>
            GameError(s"Failed to parse LLM response as JSON: $e, response was: $response")
          )
        _         <- ZIO.logDebug(s"🎯 Converting JSON to PlayEvent for ${jugador.user.name}")
        playEvent <- fromSimplifiedJson(decision, game, jugador)
        _         <- ZIO.logDebug(s"✨ Bot ${jugador.user.name} decided: ${playEvent.getClass.getSimpleName}")
      } yield playEvent)
        .catchAll { error =>
          for {
            updated <- stats.updateAndGet(s =>
              if (error.msg.contains("timeout")) s // already counted above
              else s.copy(errors = s.errors + 1)
            )
            _      <- ZIO.logError(s"❌ LLM error for bot ${jugador.user.name}: $error. ${updated.summary}")
            _      <- ZIO.logInfo(s"🔄 Falling back to DumbBot for ${jugador.user.name}")
            result <- ZIO.sleep(1.second) *> DumbChutiBot.decideTurn(jugador.user, game)
          } yield result
        }
  }

}
