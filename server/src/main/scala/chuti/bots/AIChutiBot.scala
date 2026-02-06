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
import chuti.*
import chuti.CuantasCantas.*
import chuti.Triunfo.*
import chuti.bots.llm.*
import zio.*
import zio.json.*
import zio.json.ast.Json

case class BotDecision(
  reasoning: String,
  moveType:  String,
  details:   Json
) derives JsonDecoder
case class CantaDetails(cuantasCantas: Int) derives JsonDecoder
case class PideDetails(
  ficha:           String,
  triunfo:         Option[String],
  estrictaDerecha: Boolean
) derives JsonDecoder
case class DaDetails(ficha: String) derives JsonDecoder
case class SopaDetails(firstSopa: Boolean) derives JsonDecoder

class AIChutiBot(
  llmService: LLMService,
  config:     OllamaConfig
) extends ChutiBot {

  override def decideTurn(
    user: User,
    game: Game
  ): IO[GameError, PlayEvent] = {
    val jugador = game.jugador(user.id)

    for {
      // 1. Generate legal moves
      legalMoves <- ZIO.succeed(MoveValidator.getLegalMoves(jugador, game))

      // 2. Check if there's only one legal move - if so, skip LLM
      event <- countLegalMoves(legalMoves) match {
        case 1 =>
          for {
            _          <- ZIO.logInfo(s"Only one legal move for ${jugador.user.name}, skipping LLM")
            singleMove <- getSingleLegalMove(legalMoves, game)
          } yield singleMove

        case _ =>
          // 3. Get DumbBot recommendation as conservative baseline
          for {
            dumbBotRecommendation <- DumbChutiBot.decideTurn(user, game)
            _                     <- ZIO.log(s"DumbBot recommends: $dumbBotRecommendation")

            // 4. Build prompt with DumbBot recommendation
            prompt = PromptBuilder.buildPrompt(jugador, game, legalMoves, Some(dumbBotRecommendation))

            // 5. Call LLM with timeout and fallback
            event <- llmService
              .generate(prompt)
              .timeout(config.timeout)
              .flatMap {
                case Some(response) =>
                  for {
                    _         <- ZIO.log(s"LLM raw response: $response")
                    decision  <- parseResponse(response, legalMoves)
                    playEvent <- convertToPlayEvent(decision, legalMoves, game, user)
                  } yield playEvent
                case None =>
                  ZIO.logWarning(s"LLM timeout for bot ${jugador.user.name}, using DumbBot recommendation") *>
                    ZIO.succeed(dumbBotRecommendation)
              }
              .catchAll { error =>
                ZIO.logError(s"LLM error for bot ${jugador.user.name}: $error, using DumbBot recommendation") *>
                  ZIO.succeed(dumbBotRecommendation)
              }
          } yield event
      }

      _ <- ZIO.log(s"AIBot ${jugador.user.name} decided: $event")
    } yield event
  }

  private def countLegalMoves(legalMoves: LegalMoves): Int = {
    legalMoves.cantas.size +
      legalMoves.pides.size +
      legalMoves.das.size +
      (if (legalMoves.caete) 1 else 0) +
      (if (legalMoves.sopa) 1 else 0)
  }

  private def getSingleLegalMove(
    legalMoves: LegalMoves,
    game:       Game
  ): IO[GameError, PlayEvent] = {
    if (legalMoves.cantas.size == 1) {
      ZIO.succeed(Canta(legalMoves.cantas.head, reasoning = Option("Only legal move")))
    } else if (legalMoves.pides.size == 1) {
      val (ficha, triunfo, estrictaDerecha) = legalMoves.pides.head
      ZIO.succeed(
        Pide(
          ficha = ficha,
          triunfo = Option(triunfo),
          estrictaDerecha = estrictaDerecha,
          reasoning = Option("Only legal move")
        )
      )
    } else if (legalMoves.das.size == 1) {
      ZIO.succeed(Da(legalMoves.das.head, reasoning = Option("Only legal move")))
    } else if (legalMoves.caete) {
      ZIO.succeed(Caete())
    } else if (legalMoves.sopa) {
      ZIO.succeed(Sopa(firstSopa = game.currentEventIndex == 0))
    } else {
      ZIO.fail(GameError("No single legal move found"))
    }
  }

  private def parseResponse(
    response:   String,
    legalMoves: LegalMoves
  ): IO[GameError, BotDecision] = {
    // Try to extract JSON from response (LLM might add extra text)
    val jsonPattern = """\{[\s\S]*\}""".r
    val jsonStr = jsonPattern.findFirstIn(response).getOrElse(response)

    ZIO
      .fromEither(jsonStr.fromJson[BotDecision])
      .mapError(err => GameError(s"Failed to parse LLM response: $err\nResponse: $response"))
  }

  private def convertToPlayEvent(
    decision:   BotDecision,
    legalMoves: LegalMoves,
    game:       Game,
    user:       User
  ): IO[GameError, PlayEvent] = {
    // Include reasoning if game setting is enabled OR user is admin (userId == 1)
    val reasoning = if (game.explainReasoning || user.id.value == 1) Some(decision.reasoning) else None

    ZIO.log(
      s"Converting to PlayEvent: moveType=${decision.moveType}, explainReasoning=${game.explainReasoning}, userId=${user.id.value}, reasoning=${reasoning.map(_.take(50))}"
    ) *>
      (decision.moveType.toLowerCase match {
        case "canta" =>
          for {
            details <- ZIO
              .fromEither(decision.details.toString.fromJson[CantaDetails])
              .mapError(err => GameError(s"Failed to parse Canta details: $err"))
            cuantasCantas <- parseCuantasCantas("details.cuantasCantas")
            _ <- ZIO
              .when(!legalMoves.cantas.contains(cuantasCantas))(
                ZIO.fail(GameError(s"Invalid Canta: $cuantasCantas not in legal moves"))
              )
          } yield Canta(cuantasCantas, reasoning = reasoning)

        case "pide" =>
          for {
            _ <- ZIO.logDebug(s"Legal Moves for Pide: ${legalMoves.pides.map(_._1)}")
            details <- ZIO
              .fromEither(decision.details.toString.fromJson[PideDetails])
              .mapError(err => GameError(s"Failed to parse Pide details: $err"))
            ficha <- parseFicha(details.ficha)
            triunfo <- details.triunfo match {
              case _ if game.jugadores.headOption.exists(_.fichas.size < 7) => ZIO.succeed(game.triunfo) // Cannot change triunfo after initial pide
              case Some(t) => parseTriunfo(t).map(Option(_))
              case None    => ZIO.none
            }
            _ <- ZIO
              .fail(GameError(s"Invalid Pide ficha: $ficha not in legal moves"))
              .when(!legalMoves.pides.exists { case (f, _, _) =>
                f == ficha
              })
          } yield Pide(
            ficha = ficha,
            estrictaDerecha = details.estrictaDerecha,
            triunfo = triunfo,
            reasoning = reasoning
          )

        case "da" =>
          for {
            details <- ZIO
              .fromEither(decision.details.toString.fromJson[DaDetails])
              .mapError(err => GameError(s"Failed to parse Da details: $err"))
            ficha <- parseFicha(details.ficha)
            _ <- ZIO
              .fail(GameError(s"Invalid Da: $ficha not in legal moves"))
              .when(!legalMoves.das.contains(ficha))
          } yield Da(ficha, reasoning = reasoning)

        case "caete" =>
          if (legalMoves.caete) ZIO.succeed(Caete(reasoning = reasoning))
          else ZIO.fail(GameError("Caete not allowed"))

        case "sopa" =>
          if (legalMoves.sopa) ZIO.succeed(Sopa(firstSopa = game.currentEventIndex == 0, reasoning = reasoning))
          else ZIO.fail(GameError("Sopa not allowed"))

        case other =>
          ZIO.fail(GameError(s"Unknown move type: $other"))
      })
  }

  private def parseCuantasCantas(s: String): IO[GameError, CuantasCantas.CuantasCantas] = {
    s.toLowerCase match {
      case "casa"       => ZIO.succeed(Casa)
      case "cinco"      => ZIO.succeed(Canto5)
      case "canto5"     => ZIO.succeed(Canto5)
      case "seis"       => ZIO.succeed(Canto6)
      case "canto6"     => ZIO.succeed(Canto6)
      case "cantotodas" => ZIO.succeed(CantoTodas)
      case "chuti"      => ZIO.succeed(CantoTodas)
      case "buenas"     => ZIO.succeed(Buenas)
      case other        => ZIO.fail(GameError(s"Unknown CuantasCantas: $other"))
    }
  }

  private def parseFicha(s: String): IO[GameError, Ficha] = {
    // Parse format like "6:6" or "6-6"
    val parts = s.split("[:-]")
    if (parts.length != 2) {
      ZIO.fail(GameError(s"Invalid ficha format: $s"))
    } else {
      for {
        arriba <- ZIO
          .attempt(parts(0).toInt)
          .mapError(e => GameError(s"Invalid ficha arriba: ${parts(0)}"))
        abajo <- ZIO
          .attempt(parts(1).toInt)
          .mapError(e => GameError(s"Invalid ficha abajo: ${parts(1)}"))
        _ <- ZIO
          .when(arriba < 0 || arriba > 6 || abajo < 0 || abajo > 6)(
            ZIO.fail(GameError(s"Ficha values must be 0-6: $s"))
          )
      } yield Ficha(Numero(arriba), Numero(abajo))
    }
  }

  private def parseTriunfo(s: String): IO[GameError, Triunfo] = {
    s.toLowerCase match {
      case "sintriunfos" => ZIO.succeed(SinTriunfos)
      case num if num.matches("\\d") =>
        ZIO
          .attempt(num.toInt)
          .mapError(e => GameError(s"Invalid triunfo number: $num"))
          .flatMap { n =>
            if (n >= 0 && n <= 6) ZIO.succeed(TriunfoNumero(Numero(n)))
            else ZIO.fail(GameError(s"Triunfo number must be 0-6: $n"))
          }
      case other => ZIO.fail(GameError(s"Unknown triunfo: $other"))
    }
  }

}

object AIChutiBot {

  def live(
    llmService: LLMService,
    config:     OllamaConfig
  ): AIChutiBot = new AIChutiBot(llmService, config)

  def liveWithConfig(config: OllamaConfig): AIChutiBot = {
    val llmService = LLMService.live(config)
    new AIChutiBot(llmService, config)
  }

}
