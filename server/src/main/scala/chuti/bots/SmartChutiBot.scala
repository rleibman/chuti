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
)

object BotDecision {

  given JsonDecoder[BotDecision] = DeriveJsonDecoder.gen[BotDecision]

}

case class CantaDetails(cuantasCantas: String)
object CantaDetails {

  given JsonDecoder[CantaDetails] = DeriveJsonDecoder.gen[CantaDetails]

}

case class PideDetails(
  ficha:           String,
  triunfo:         Option[String],
  estrictaDerecha: Boolean
)
object PideDetails {

  given JsonDecoder[PideDetails] = DeriveJsonDecoder.gen[PideDetails]

}

case class DaDetails(ficha: String)
object DaDetails {

  given JsonDecoder[DaDetails] = DeriveJsonDecoder.gen[DaDetails]

}

case class SopaDetails(firstSopa: Boolean)
object SopaDetails {

  given JsonDecoder[SopaDetails] = DeriveJsonDecoder.gen[SopaDetails]

}

class SmartChutiBot(
  llmService: LLMService[GameError],
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

      // 2. Build prompt
      prompt = PromptBuilder.buildPrompt(jugador, game, legalMoves)

      // 3. Call LLM with timeout and fallback
      event <- llmService
        .generate(prompt)
        .timeout(config.timeout)
        .flatMap {
          case Some(response) =>
            for {
              _         <- ZIO.log(s"LLM raw response: $response")
              decision  <- parseResponse(response, legalMoves)
              playEvent <- convertToPlayEvent(decision, legalMoves, game)
            } yield playEvent
          case None =>
            ZIO.logWarning(s"LLM timeout for bot ${jugador.user.name}, falling back to DumbBot") *>
              DumbChutiBot.decideTurn(user, game)
        }
        .catchAll { error =>
          ZIO.logError(s"LLM error for bot ${jugador.user.name}: $error, falling back to DumbBot") *>
            DumbChutiBot.decideTurn(user, game)
        }

      _ <- ZIO.log(s"SmartBot ${jugador.user.name} decided: $event")
    } yield event
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
    game:       Game
  ): IO[GameError, PlayEvent] = {
    decision.moveType.toLowerCase match {
      case "canta" =>
        for {
          details <- ZIO
            .fromEither(decision.details.toString.fromJson[CantaDetails])
            .mapError(err => GameError(s"Failed to parse Canta details: $err"))
          cuantasCantas <- parseCuantasCantas(details.cuantasCantas)
          _ <- ZIO
            .when(!legalMoves.cantas.contains(cuantasCantas))(
              ZIO.fail(GameError(s"Invalid Canta: $cuantasCantas not in legal moves"))
            )
        } yield Canta(cuantasCantas)

      case "pide" =>
        for {
          _ <- ZIO.logDebug(s"Legal Moves for Pide: ${legalMoves.pides.map(_._1)}")
          details <- ZIO
            .fromEither(decision.details.toString.fromJson[PideDetails])
            .mapError(err => GameError(s"Failed to parse Pide details: $err"))
          ficha <- parseFicha(details.ficha)
          triunfo <- details.triunfo match {
            case Some(t) => parseTriunfo(t).map(Option(_))
            case None    => ZIO.none
          }
          _ <- ZIO
            .fail(GameError(s"Invalid Pide ficha: $ficha not in legal moves"))
            .when(!legalMoves.pides.exists { case (f, _, _) =>
              f == ficha
            })
          _ <- ZIO
            .fail(GameError(s"Invalid Pide ficha: you can't change triunfo"))
            .when(triunfo != game.triunfo)
        } yield Pide(ficha = ficha, triunfo = triunfo, estrictaDerecha = details.estrictaDerecha)

      case "da" =>
        for {
          details <- ZIO
            .fromEither(decision.details.toString.fromJson[DaDetails])
            .mapError(err => GameError(s"Failed to parse Da details: $err"))
          ficha <- parseFicha(details.ficha)
          _ <- ZIO
            .fail(GameError(s"Invalid Da: $ficha not in legal moves"))
            .when(!legalMoves.das.contains(ficha))
        } yield Da(ficha)

      case "caete" =>
        if (legalMoves.caete) ZIO.succeed(Caete())
        else ZIO.fail(GameError("Caete not allowed"))

      case "sopa" =>
        if (legalMoves.sopa) ZIO.succeed(Sopa(firstSopa = game.currentEventIndex == 0))
        else ZIO.fail(GameError("Sopa not allowed"))

      case other =>
        ZIO.fail(GameError(s"Unknown move type: $other"))
    }
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

object SmartChutiBot {

  def live(
    llmService: LLMService[GameError],
    config:     OllamaConfig
  ): SmartChutiBot = new SmartChutiBot(llmService, config)

  def liveWithConfig(config: OllamaConfig): SmartChutiBot = {
    val llmService = LLMService.live(config, e => GameError(s"LLM error: ${e.getMessage}"))
    new SmartChutiBot(llmService, config)
  }

}
