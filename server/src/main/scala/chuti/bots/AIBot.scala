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

case class AIBot(
  config:     OllamaConfig,
  llmService: LLMService
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
    triunfo:                        Triunfo,
    pidieron:                       Numero,
    triunfosVistos:                 Seq[Ficha],
    mulasVistas:                    Seq[Ficha],
    numerosQueYaNoHay:              Option[Seq[Numero]],
    numerosQueHayPocos:             Option[Seq[Numero]],
    numerosQueLosJugadoresNoTienen: Option[Map[Jugador, Seq[Numero]]]
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
          triunfo = triunfo,
          pidieron = pidieron,
          triunfosVistos = triunfosVistos,
          mulasVistas = mulasVistas,
          numerosQueYaNoHay = None, // Doesn't track exhausted numbers
          numerosQueHayPocos = None, // Doesn't track scarcity
          numerosQueLosJugadoresNoTienen = None // No void inference
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
          triunfo = triunfo,
          pidieron = pidieron,
          triunfosVistos = triunfosVistos,
          mulasVistas = mulasVistas,
          numerosQueYaNoHay = None, // Doesn't track yet
          numerosQueHayPocos = None, // Doesn't track yet
          numerosQueLosJugadoresNoTienen = Some(voids)
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
          triunfo = triunfo,
          pidieron = pidieron,
          triunfosVistos = triunfosVistos,
          mulasVistas = mulasVistas,
          numerosQueYaNoHay = Some(numerosQueYaNoHay),
          numerosQueHayPocos = Some(numerosQueHayPocos),
          numerosQueLosJugadoresNoTienen = Some(voids)
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

  extension (event: PlayEvent) {

    // Writes simplified json of the play event that can be used by ai (don't need to include all the details)
    def toSimplifiedJson: Either[String, Json] = {
      // These is simplified json that doesn't have all that a PlayEvent needs, and we don't need to address every playEvent
      event match {
        case canta: Canta =>
          s"""{
              "type"           : "canta",
              "cuantasCantas"  : ${canta.cuantasCantas.numFilas},
              "reasoning"      : "[Explain why you chose this bid based on your hand strength and de caída count. End with your hand: [1:1, 2:2, ...]]"
            }""".fromJson[Json]
        case pide: Pide =>
          s"""{
              "type"           : "pide",
              "ficha"          : "${pide.ficha.toString}",
              "estrictaDerecha": ${pide.estrictaDerecha},
              "triunfo"        : "${pide.triunfo.getOrElse(SinTriunfos).toString}",
              "reasoning"      : "[Explain why you chose this tile and trump. End with your hand: [1:1, 2:2, ...]]"
            }""".fromJson[Json]
        case da: Da =>
          s"""{
              "type"           : "da",
              "ficha"          : "${da.ficha.toString}",
              "reasoning"      : "[Explain your strategy for this tile. End with your hand: [1:1, 2:2, ...]]"
            }""".fromJson[Json]
        case caete: Caete =>
          s"""{
              "type"           : "caete",
              "reasoning"      : "[Explain why you can win all remaining tricks. End with your hand: [1:1, 2:2, ...]]"
            }""".fromJson[Json]
        case meRindo: MeRindo =>
          s"""{
              "type"           : "meRindo",
              "reasoning"      : "[Explain why you're surrendering. End with your hand: [1:1, 2:2, ...]]"
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
          triunfo:         Option[String],
          estrictaDerecha: Boolean,
          reasoning:       Option[String]
        ) derives JsonDecoder
        ZIO
          .fromEither(json.as[Details]).flatMap { d =>
            for {
              ficha <- ZIO
                .attempt(Ficha.fromString(d.ficha))
                .mapError(e => GameError(s"Invalid ficha format: ${d.ficha}", Some(e)))
              triunfo <- d.triunfo match {
                case Some("SinTriunfos") => ZIO.succeed(Option(SinTriunfos))
                case Some(numStr) =>
                  ZIO
                    .fromOption(numStr.toIntOption).mapBoth(
                      _ => GameError(s"Invalid triunfo: $numStr"),
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

  // Calcula todas las posibles jugadas que puede hacer el bot, regresando el triunfo, cuantas serian de caida
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
    |  - Mula = double tile (e.g., 6:6). La Mulota (6:6) = first dealer. Campanita (0:1) = special event.
    |
    |PHASES PER ROUND (Juego):
    |  1. SOPA (Deal): Turno player deals 7 tiles to each player
    |  2. CANTANDO (Bid): Players bid tricks they'll win:
    |     - Casa(4), Canto5(5), Canto6(6), Chuti(7=all tricks, instant win)
    |     - Buenas = pass. Turno MUST bid at least Casa.
    |     - Cantante = player who bid (or was "saved" by higher bid)
    |  3. JUGANDO (Play): Cantante leads first, declaring trump (0-6 or SinTriunfos)
    |     - Must follow suit if able, else must play trump if able
    |     - Highest matching number wins (or highest trump if trump played)
    |     - Mulas: If mula led, only that exact mula wins
    |
    |CRITICAL RULES:
    |  - MUST follow suit/trump when able (or HOYO TÉCNICO = automatic penalty)
    |  - MUST "caerse" (fall) when you can prove winning all remaining tricks
    |  - Hoyo = failed bid, cantante LOSES bid points (can go negative)
    |  - Only play tiles in YOUR hand, choose from LEGAL moves provided
    |
    |STRATEGY:
    |  - Conservative when leading near 21 (protect your lead)
    |  - Aggressive when behind (take risks to catch up)
    |  - Cantante risks most (hoyo penalty), bid carefully
    |  - Track tiles played to calculate "de caída" (guaranteed wins)
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

    s"""${promptRules()}
       |
       |You are playing Chuti, a 4-player domino game. Match is played to 21 points.
       | The overall game score is currently
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

        // Si te toca cantar, y estas "hecho" con casa o mas, preguntale a AI, con una fuerte recomendacion
        val formatTopOptions = moves
          .take(3).map { case (triunfo, cuantasDeCaida, cuantosTriunfosYMulas) =>
            s"Cantar $triunfo con $cuantasDeCaida de caida y $cuantosTriunfosYMulas triunfos y mulas\n"
          }.mkString

        if (jugador.turno) {
          moves.headOption.fold {
            ZIO.succeed(
              MeRindo(
                gameId = game.id,
                userId = jugador.user.id,
                reasoning = Option(s"No tengo nada de nada! me rindo ${formatHand(jugador)}")
              ): PlayEvent
            )
          } { max =>
            if (max.cuantasDeCaida <= 3) {
              // Si te toca cantar, y tu mano es pinche, canta casa automaticamente.
              ZIO.succeed(
                Canta(
                  Casa,
                  gameId = game.id,
                  userId = jugador.user.id,
                  reasoning =
                    Option(s"Canto Casa porque tengo ${max.cuantasDeCaida} de caida, no me queda de otra, a lo mejor alguien me salva ${formatHand(jugador)}")
                )
              )
            } else {

              val prompt = promptHeader(jugador, game) +
                s"""
                | I calculate that the top options to bid are, in order of risk (most conservative to most aggressive):
                |
                | $formatTopOptions
                |
                | Valid options (options that are legal, without consideration of good or bad) are:
                |
                | ${formatValidOptions(4)}
                |
                | Strategic Heuristics (consider these when deciding):
                |
                | It is currently your turn to start bidding, you have to bid 4 (casa) at the very least, but you can bid more.
                |
                | CRITICAL RULES:
                | 1. You MUST choose a bid from the valid options list above. NO other bids are valid.
                | 2. Only consider the tiles in your hand, no others.
                | 3. DO NOT consider tiles you don't have - check your hand carefully!
                | 4. A hoyo (failed bid) costs you heavily - be conservative on close calls
                |
                |""".stripMargin + promptFooter()

              runPrompt(prompt, jugador, game)

            }
          }
        } else {
          // Si no te toca cantar, y tienes una mano decente, preguntale a AI, parte de la informacion debe de incluir las cuentas actuales
          val maxPlayer: Option[Jugador] = game.jugadores
            .filter(_.user.id != jugador.user.id)
            .maxByOption(_.cuantasCantas.map(_.numFilas).getOrElse(4))

          val maxBidByOthers = maxPlayer.flatMap(_.cuantasCantas).fold(4)(_.numFilas)
          val topMove = moves.headOption.map(_.cuantasDeCaida).getOrElse(4)

          /*
      maxBidByOthers | topMove | Interpretation
       5 y 5 => 0 //No
       5 y 4 => 1 //No
       5 y 3 => 2 //No
       5 y 6 => -1 //Good
       4 y 6 => -2 //Very Good
           */
          val diff = maxBidByOthers - topMove

          if (topMove == 7) {
            ZIO.succeed(
              Canta(
                CuantasCantas.CantoTodas,
                gameId = game.id,
                userId = jugador.user.id,
                reasoning =
                  Option(s"Siempre que es de caida, canta chuti, asi es mas emocionante ${formatHand(jugador)}")
              )
            )
          } else if (maxBidByOthers == 7) {
            ZIO.succeed(
              Canta(
                Buenas,
                gameId = game.id,
                userId = jugador.user.id,
                reasoning = Option(s"No puedes salvar a alguien que ya canto chuti ${formatHand(jugador)}")
              )
            )
          } else if (diff >= 0) {
            // Si no te toca cantar, y tienes una mano mas pinche que el maximo, automaticamente di buenas
            ZIO.succeed(
              Canta(
                Buenas,
                gameId = game.id,
                userId = jugador.user.id,
                reasoning = Option(s"Buenas, porque no tengo mejor juego que el mejor. ${formatHand(jugador)}")
              )
            )
          } else if (maxBidByOthers > 4 && diff < 0) {
            // Alguien ya lo salvo, te conviene salvarlo.
            ZIO.succeed(
              Canta(
                CuantasCantas.byNum(topMove),
                gameId = game.id,
                userId = jugador.user.id,
                reasoning =
                  Option(s"La cantada ya es mas de cuatro, mejor yo me quedo con los puntos. ${formatHand(jugador)}")
              )
            )
          } else {
            val prompt = promptHeader(jugador, game) +
              s"""
             | I calculate that the top options to bid are, in order of risk (most conservative to most aggressive):
             |
             | $formatTopOptions
             |
             | Valid options (options that are legal, without consideration of good or bad) are:
             |
             | ${formatValidOptions(maxBidByOthers)}
             |
             | Strategic Heuristics (consider these when deciding):
             |
             | It is not currently your turn to bid, so you're safe. But you could bid more than the current max bid of $maxBidByOthers if it's strategically advantageous.
             | In general, the safe bet is to bid the top of what you can make "de caida"
             | If you're close to winning, you may want to bid a little more aggresively, particularly if you can bid more than $maxBidByOthers with certainty.
             | If you have a lot of hoyos, or are currenly negative, you may want to bid more agressively, particularly if you can bid more than $maxBidByOthers with certainty.
             | If you're not close to winning, and you don't have a lot of holes, you may want to bid more conservatively, particularly if you can't bid more than $maxBidByOthers with certainty.
             |
             | CRITICAL RULES:
             | 1. You MUST choose a bid from the valid options list above. NO other bids are valid.
             | 2. Only consider the tiles in your hand, no others.
             | 3. DO NOT consider tiles you don't have - check your hand carefully!
             | 4. A hoyo (failed bid) costs you heavily - be conservative on close calls
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
      return ZIO.succeed(
        Caete(
          triunfo = Some(bestTrump),
          gameId = game.id,
          userId = jugador.user.id,
          reasoning =
            Option(s"Auto-caete: de caida with trump $bestTrump (${deCaidaOpt.get.cuantasDeCaida} of $bid tricks guaranteed)  ${formatHand(jugador)}")
        )
      )
    }

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

    val formatTopOptions = moves
      .take(3).map { case (triunfo, ficha, cuantasDeCaida, cuantosTriunfosYMulas) =>
        s"Triunfan $triunfo, pide $ficha (calculo $cuantasDeCaida de caida y $cuantosTriunfosYMulas triunfos y mulas)\n"
      }.mkString

    def formatValidOptions: String = {
      val pideOptions = moves.map { move =>
        Pide(
          ficha = move.pide,
          triunfo = Option(move.triunfo),
          estrictaDerecha = false
        ).toSimplifiedJson.getOrElse(Json.Null)
      }
      val surrenderOption =
        MeRindo(gameId = game.id, userId = jugador.user.id).toSimplifiedJson.getOrElse(Json.Null)
      Json.Arr((pideOptions :+ surrenderOption)*).toString
    }

    // Preguntale a AI, calcula las mejores opciones de triunfo, no le des todas las opciones
    val memory = calculateMemorySummary(game, jugador)
    val memoryJson = memory.toJson

    val prompt = promptHeader(jugador, game) +
      s"""
         | Memory (what you remember from play so far):
         | $memoryJson
         |
         | I calculate that the top options to play are, in order of risk (most conservative to most aggressive):
         |
         | $formatTopOptions
         |
         | Valid options (options that are legal, without consideration of good or bad) are:
         |
         | $formatValidOptions
         |
         | Context and strategy:
         | - You won the bid and bid ${jugador.cuantasCantas.fold(4)(_.numFilas)} tricks
         | - Trump is not yet set - you must choose now (cannot change later)
         | - Choose the trump that maximizes your "de caída" (guaranteed wins)
         | - Lead with your strongest tile in that suit  to take control
         | - Don't elect SinTriunfos unless you have a very strong hand of mulas and high tiles, particularly if you're trying to just make Casa (4)
         | - Remember, if you elect SinTriunfos, and you lose the hand, it's very hard to recover it, so you should choose this sparingly.
         | - If you have at least four trumps, but none of the large ones, you might want to choose that as a trump but ask for something else, usually a number for which trump you don't have, hoping somebody answers with that trump and thus having one less to worry about it, you'll likely get the hand back later.
         |
         | CRITICAL RULES:
         | 1. You MUST choose a play from the valid options list above. NO other plays are valid.
         | 2. Only consider the tiles in your hand, no others.
         | 3. DO NOT consider tiles you don't have - check your hand carefully!
         | 4. A hoyo (failed bid) costs you heavily - be conservative on close calls
         |
         |""".stripMargin + promptFooter()

    runPrompt(prompt, jugador, game)
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

      val fichasJugadas = game.jugadores.flatMap(_.filas.flatMap(_.fichas))
      val fichasRestantes = Game.todaLaFicha.diff(jugador.fichas).diff(fichasJugadas)
      val deCaida = game.cuantasDeCaida(jugador.fichas, fichasRestantes).size

      val formatValidOptions = Json.Arr(legalMoves.map(_.toSimplifiedJson.getOrElse(Json.Null))*).toString

      val prompt = promptHeader(jugador, game) +
        s"""
           | Memory (what you remember from play so far):
           | ${memory.toJson}
           |
           | Current Situation:
           | - You are the cantante (bidder)
           | - You bid $bidAmount tricks and have won $tricksWonSoFar so far
           | - You need $tricksNeeded more tricks from $tricksRemaining remaining
           | - Calculated de caída: $deCaida guaranteed wins
           | - Trump: ${game.triunfo.getOrElse("not set")}
           |
           | Valid tiles you can lead:
           | $formatValidOptions
           |
           | Strategy:
           | - You have the lead - choose wisely to win tricks
           | - Lead with strong tiles when you need to win this trick
           | - Lead with weak tiles when you want to preserve strength for later
           | - Consider which numbers opponents might be void in (from memory)
           | - If you have $tricksNeeded de caída, you can play more conservatively
           | - If $tricksNeeded > $deCaida, you need to take risks to win extra tricks
           | - If all the trumps have been played and you're not likely to win this ask, then play a small tile and hope the hand comes back to you.
           | - If there's still trumps out there that are larger than yours, don't waste your trumps, keep them and try to ask for the number with the trump they have, that way forcing them to use it.
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
      ZIO.sleep(2.seconds) *> ZIO.succeed(
        Da(
          ficha = legalMoves.head,
          gameId = game.id,
          userId = jugador.user.id,
          reasoning = Option(
            s"Only one legal tile to play ${legalMoves.head.toString}, playing it automatically ${formatHand(jugador)}"
          )
        )
      )
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
        ZIO.sleep(2.seconds) *> ZIO.succeed(
          Da(
            ficha = lowestTile,
            gameId = game.id,
            userId = jugador.user.id,
            reasoning =
              Option(s"Can't follow suit or trump, playing lowest tile to minimize loss ${formatHand(jugador)}")
          )
        )
      } else {
        // Have options - ask AI about recovering the lead
        val memory = calculateMemorySummary(game, jugador)
        val bidAmount = jugador.cuantasCantas.fold(4)(_.numFilas)
        val tricksWon = jugador.filas.size
        val tricksNeeded = bidAmount - tricksWon

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

      val askingForTrump = trumpNum.exists(_ == pedido)
      val isNonTrumpDouble = pide.esMula && !askingForTrump
      val is6_5_WithDifferentTrump =
        pide == Ficha(Numero.Numero6, Numero.Numero5) &&
          !trumpNum.exists(n => n == Numero.Numero6 || n == Numero.Numero5)

      // Auto-play scenarios for non-cantante
      if (!canFollowSuit && trumps.isEmpty) {
        // Can't follow, no trumps - dump lowest/worthless tile
        val lowestTile = legalMoves.minBy(_.value)
        ZIO.sleep(2.seconds) *> ZIO.succeed(
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
              ZIO.sleep(2.seconds) *> ZIO.succeed(
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
              ZIO.sleep(2.seconds) *> ZIO.succeed(
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
            ZIO.sleep(2.seconds) *> ZIO.succeed(
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
            ZIO.sleep(2.seconds) *> ZIO.succeed(
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
            ZIO.sleep(2.seconds) *> ZIO.succeed(
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
            ZIO.sleep(2.seconds) *> ZIO.succeed(
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
          ZIO.sleep(2.seconds) *> ZIO.succeed(
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
    ZIO.logDebug(s"🎮 Bot ${jugador.user.name} starting decision (Game ${game.id}, Status: ${game.gameStatus})") *>
      (for {
        _ <- ZIO.logDebug(s"⏰ LLM timeout set to ${config.timeout.toSeconds} seconds")
        response <- llmService
          .generate(prompt)
          .timeout(config.timeout)
          .mapError(GameError.apply)
          .flatMap(o =>
            ZIO
              .fromOption(o)
              .orElseFail(GameError("LLM timeout"))
          )
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
          ZIO.logError(s"❌ LLM error for bot ${jugador.user.name}: $error") *>
            ZIO.logInfo(s"🔄 Falling back to DumbBot for ${jugador.user.name}") *>
            ZIO.sleep(2.seconds) *> DumbChutiBot.decideTurn(jugador.user, game)
        }
  }

}
