package chuti.bots

import ai.{LLMService, OllamaConfig}
import chuti.*
import chuti.CuantasCantas.{Buenas, Casa}
import chuti.Triunfo.{SinTriunfos, TriunfoNumero}
import zio.*
import zio.json.*
import zio.json.ast.Json

case class NewAIBot(
  config:     OllamaConfig,
  llmService: LLMService
) extends ChutiBot {

  extension (event: PlayEvent) {

    // Writes simplified json of the play event that can be used by ai (don't need to include all the details)
    def toSimplifiedJson: Either[String, Json] = {
      // These is simplified json that doesn't have all that a PlayEvent needs, and we don't need to address every playEvent
      event match {
        case canta: Canta =>
          s"""{
              "type"           : "canta",
              "reasoning"      : "<Step-by-step explanation of your thinking>"
            }""".fromJson[Json]
        case pide: Pide =>
          s"""{
              "type"           : "pide",
              "ficha"          : ${pide.ficha.toString},
              "estrictaDerecha": ${pide.estrictaDerecha},
              "triunfo"        : ${pide.triunfo.getOrElse(SinTriunfos).toString},
              "reasoning"      : "<Step-by-step explanation of your thinking>"
            }""".fromJson[Json]
        case da: Da =>
          s"""{
              "type"           : "da",
              "reasoning"      : "<Step-by-step explanation of your thinking>"
            }""".fromJson[Json]
        case caete: Caete =>
          s"""{
              "type"           : "caete",
              "reasoning"      : "<Step-by-step explanation of your thinking>"
            }""".fromJson[Json]
        case meRindo: MeRindo =>
          s"""{
              "type"           : "meRindo",
              "reasoning"      : "<Step-by-step explanation of your thinking>"
            }""".fromJson[Json]
        case _ => throw new RuntimeException("Unsupported event type for simplyfied json")
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
              reasoning = if (game.explainReasoning) d.reasoning else None
            )
          ).mapError(e => GameError(s"Failed to parse Canta details: $e"))
      case _ => ZIO.fail(GameError(s"Unsupported or missing type field in JSON: $json"))
    }
  }

  private def frequency(fichas: Seq[Ficha]): Seq[(numero: Numero, fichas: Seq[Ficha])] = {
    Numero.values.toSeq
      .map(n => (n, fichas.filter(_.es(n))): (numero: Numero, fichas: Seq[Ficha]))
      .sortBy(-_.fichas.size)
  }

  private given Ordering[Triunfo] =
    Ordering.by {
      case SinTriunfos        => -1
      case TriunfoNumero(num) => num.value
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
    } ++
      // TODO Hay que ver los casos especiales de sin triunfos,
      // - si tengo 6:6, y 6:5, cuenta como 2, y si tengo 6:6, 6:5, 6:4, cuenta como 3, etc.
      // - si tengo 6:6, 5:5, 4:4, 3:3, 2:2, 1:1 y 1:0, es buena idea cantar todas, sin triunfos
      // - si tengo 5 triunfos, uno de ellos es el x:1 y tengo aparte 1:1 y 1:0, es buena idea cantar todas, haciendo campanita base
      Seq.empty

    ret.sortBy(t => (-t.cuantasDeCaida, -t.cuantosTriunfosYMulas, t.triunfo))
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
   |Respond with ONLY a valid JSON object that is equivalent to the format given in the valid options:
   |{
   |  "reasoning": "Step-by-step explanation of your thinking",
   |  "type": "canta|pide|da|caete|meRindo",
   |  ... additional fields depending on the type
   |}
   |""".stripMargin
  }

  def promptHeader(
    jugador: Jugador,
    game:    Game
  ): String = {
    val cuentasCalculadas = game.cuentasCalculadas

    val formatCuentas: String = "Player - Points - Hoyos\n" +
      cuentasCalculadas.map { case (j, points, _) =>
        s"""  ${if (j.user.id == jugador.user.id) "You" else j.user.name} - $points - ${jugador.cuenta.count(
            _.esHoyo
          )}\n"""
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

    s"""You are playing Chuti, a 4-player domino game. Match is played to 21 points.
       | The overall game score is currently
       |
       | $formatCuentas
       |
       | $strategicContext
       |
       | Your current hand is
       |
       | $formatHand
       |
       """
  }

  def formatHand(jugador: Jugador): String = jugador.fichas.map(_.toString).mkString(", ")

  def canta(
    jugador: Jugador,
    game:    Game
  ): IO[GameError, PlayEvent] = {
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
            reasoning = Option("No tengo nada de nada! me rindo")
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
                Option(s"Canto Casa porque tengo ${max.cuantasDeCaida} de caida, no me queda de otra, a lo mejor alguien me salva")
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
                | In general, the safe bet is to bid the top of what you can make "de caida"
                | If you're close to winning, you may want to bid a little more aggresively, particularly if you can bid more than 4 with certainty.
                | If you have a lot of hoyos, or are currenly negative, you may want to bid more agressively, particularly if you can bid more than 4 with certainty.
                | If you're not close to winning, and you don't have a lot of holes, you may want to bid more conservatively, particularly if you can't bid more than 4 with certainty.
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

      val manoBuena = topMove < 0

      if (topMove == 7) {
        ZIO.succeed(
          Canta(
            CuantasCantas.CantoTodas,
            gameId = game.id,
            userId = jugador.user.id,
            reasoning = Option(s"Siempre que es de caida, canta chuti, asi es mas emocionante")
          )
        )
      } else if (maxBidByOthers == 7) {
        ZIO.succeed(
          Canta(
            Buenas,
            gameId = game.id,
            userId = jugador.user.id,
            reasoning = Option(s"No puedes salvar a alguien que ya canto chuti")
          )
        )
      } else if (diff >= 0) {
        // Si no te toca cantar, y tienes una mano mas pinche que el maximo, automaticamente di buenas
        ZIO.succeed(
          Canta(
            Casa,
            gameId = game.id,
            userId = jugador.user.id,
            reasoning = Option(s"Canto buenas, porque no tengo mejor juego que el mejor.")
          )
        )
      } else if (maxBidByOthers > 4 && diff < 0) {
        // Alguien ya lo salvo, te conviene salvarlo.
        ZIO.succeed(
          Canta(
            CuantasCantas.byNum(topMove),
            gameId = game.id,
            userId = jugador.user.id,
            reasoning = Option(s"La cantada ya es mas de cuatro, mejor yo me quedo con los puntos.")
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
             |             |
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

  def pideInicial(
    jugador: Jugador,
    game:    Game
  ): IO[GameError, PlayEvent] = {

    val moves: Seq[(triunfo: Triunfo, pide: Ficha, cuantasDeCaida: Int)] =
      possibleMoves(jugador, game).zipWithIndex
        .filter(
          (
            t,
            i
          ) =>
            // We have to at least take one option
            i == 0 || t.cuantasDeCaida >= jugador.cuantasCantas.fold(4)(_.numFilas)
        )
        .map(_._1)
        .take(3)
        .map { case (triunfo, cuantasDeCaida, cuantosTriunfosYMulas) =>
          val hypotheticalGame = game.copy(triunfo = Option(triunfo))
          val pide = hypotheticalGame.highestValueByTriunfo(jugador.fichas).get
          (triunfo, pide, cuantasDeCaida)
        }

//    moves.headOption.fold(ZIO.succeed(MeRindo(game.id, jugador.user.id, reasoning = Option("there are no options"))))
//    { move => }

    val formatTopOptions = moves
      .take(3).map { case (triunfo, cuantasDeCaida, cuantosTriunfosYMulas) =>
        s"Triunfan $triunfo, pide ${} (calculo $cuantasDeCaida de caida y $cuantosTriunfosYMulas triunfos y mulas)\n"
      }.mkString

    def formatValidOptions: String = {
      Json
        .Arr(moves.map { move =>
          Pide(
            ficha = move.pide,
            triunfo = Option(move.triunfo),
            estrictaDerecha = false
          ).toSimplifiedJson.getOrElse(Json.Null)
        }*).toString
    }

    // Preguntale a AI, calcula las mejores opciones de triunfo, no le des todas las opciones
    val prompt = promptHeader(jugador, game) +
      s"""
         | I calculate that the top options to play are, in order of risk (most conservative to most aggressive):
         |
         | $formatTopOptions
         |
         | Valid options (options that are legal, without consideration of good or bad) are:
         |
         | ${formatValidOptions(4)}
         |
         | Strategic Heuristics (consider these when deciding):
         |
         | You won the bid, and you bid ${jugador.cuantasCantas.fold(4)(_.numFilas)}
         | The trump is currently not set, you can choose any trump you want, but you cannot change it later, so choose wisely.
         | In general, the safe bet is to bid the top of what you can make "de caida"
         | If you're close to winning, you may want to bid a little more aggresively, particularly if you can bid more than 4 with certainty.
         | If you have a lot of hoyos, or are currenly negative, you may want to bid more agressively, particularly if you can bid more than 4 with certainty.
         | If you're not close to winning, and you don't have a lot of holes, you may want to bid more conservatively, particularly if you can't bid more than 4 with certainty.
         |
         | CRITICAL RULES:
         | 1. You MUST choose a bid from the valid options list above. NO other bids are valid.
         | 2. Only consider the tiles in your hand, no others.
         | 3. DO NOT consider tiles you don't have - check your hand carefully!
         | 4. A hoyo (failed bid) costs you heavily - be conservative on close calls
         |
         |""".stripMargin
    ???
  }

  def pide(
    jugador: Jugador,
    game:    Game
  ): IO[GameError, PlayEvent] = {
    // Si estas cantando, cantaste casa y es la segunda mano, y el hoyo es iminente, date por vencido (no des regalos)
    // Si estas cantando, preguntale a AI, explicale que opciones tienes,
    // Si no estas cantando, preguntale a AI, explicale que el chiste del juego es hacerle hoyo al companero que canto, explicale como evitar ayudarle.
    ???
  }

  def da(
    jugador: Jugador,
    game:    Game
  ): IO[GameError, PlayEvent] = {

    // Si estas cantando
    //   - no tienes opcion: da automaticamente
    //   - tienes opcion: preguntale a AI, si puedes recuperar la mano, explicale que opciones tienes, si no, explicale con que te conviene quedarte

    // Si no estas cantando
    //   - no tienes opcion: da automaticamente
    //   - Pidieron triunfos
    //   - tienes opciones, preguntale a AI, explicale que el chiste del juego es hacerle hoyo al companero que canto. Conviene quitarle la mano, pero no se la quites si alguien mas ya jugo y se la va a quitar
    ???
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
        if (jugador.mano && game.puedesCaerte(jugador))
          ZIO.succeed(Caete())
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

//  private def convertToPlayEvent(
//    decision: BotDecision,
//    jugador:  Jugador,
//    game:     Game
//  ): IO[GameError, PlayEvent] = {
//    val user = jugador.user
//    // Include reasoning if game setting is enabled OR user is admin (userId == 1)
//    val reasoning = if (game.explainReasoning) Some(decision.reasoning) else None
//
//    ZIO.log(
//      s"Converting to PlayEvent: moveType=${decision.moveType}, explainReasoning=${game.explainReasoning}, userId=${user.id.value}, reasoning=${reasoning.map(_.take(50))}"
//    ) *>
//      (decision.moveType.toLowerCase match {
//        case "canta" =>
//          for {
//            details <- ZIO
//              .fromEither(decision.details.toString.fromJson[CantaDetails])
//              .mapError(err => GameError(s"Failed to parse Canta details: $err"))
//          } yield Canta(CuantasCantas.byNum(details.cuantasCantas), reasoning = reasoning)

//        case "pide" =>
//          for {
//            _ <- ZIO.logDebug(s"Legal Moves for Pide: ${legalMoves.pides.map(_._1)}")
//            details <- ZIO
//              .fromEither(decision.details.toString.fromJson[PideDetails])
//              .mapError(err => GameError(s"Failed to parse Pide details: $err"))
//            ficha <- parseFicha(details.ficha)
//            triunfo <- details.triunfo match {
//              case _ if game.jugadores.headOption.exists(_.fichas.size < 7) => ZIO.succeed(game.triunfo) // Cannot change triunfo after initial pide
//              case Some(t) => parseTriunfo(t).map(Option(_))
//              case None    => ZIO.none
//            }
//            _ <- ZIO
//              .fail(GameError(s"Invalid Pide ficha: $ficha not in legal moves"))
//              .when(!legalMoves.pides.exists { case (f, _, _) =>
//                f == ficha
//              })
//          } yield Pide(
//            ficha = ficha,
//            estrictaDerecha = details.estrictaDerecha,
//            triunfo = triunfo,
//            reasoning = reasoning
//          )
//
//        case "da" =>
//          for {
//            details <- ZIO
//              .fromEither(decision.details.toString.fromJson[DaDetails])
//              .mapError(err => GameError(s"Failed to parse Da details: $err"))
//            ficha <- parseFicha(details.ficha)
//            _ <- ZIO
//              .fail(GameError(s"Invalid Da: $ficha not in legal moves"))
//              .when(!legalMoves.das.contains(ficha))
//          } yield Da(ficha, reasoning = reasoning)
//
//        case "caete" =>
//          if (legalMoves.caete) ZIO.succeed(Caete(reasoning = reasoning))
//          else ZIO.fail(GameError("Caete not allowed"))
//
//        case "sopa" =>
//          if (legalMoves.sopa) ZIO.succeed(Sopa(firstSopa = game.currentEventIndex == 0, reasoning = reasoning))
//          else ZIO.fail(GameError("Sopa not allowed"))
//
//        case other =>
//          ZIO.fail(GameError(s"Unknown move type: $other"))
//      })
//  }

  private def runPrompt(
    prompt:  String,
    jugador: Jugador,
    game:    Game
  ): IO[GameError, PlayEvent] = {
    (for {
      response <- llmService
        .generate(prompt)
        .timeout(config.timeout)
        .mapError(GameError.apply)
        .flatMap(o =>
          ZIO
            .fromOption(o)
            .orElseFail(GameError("LLM timeout"))
        )
      decision <- ZIO
        .fromEither(response.fromJson[Json]).mapError(e =>
          GameError(s"Failed to parse LLM response as JSON: $e, response was: $response")
        )
      playEvent <- fromSimplifiedJson(decision, game, jugador)
    } yield playEvent)
      .catchAll { error =>
        ZIO.logError(s"LLM error for bot ${jugador.user.name}: $error, using DumbBot recommendation") *>
          DumbChutiBot.decideTurn(jugador.user, game)
      }
  }

}
