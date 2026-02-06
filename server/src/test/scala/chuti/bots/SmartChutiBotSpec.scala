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

import ai.{LLMError, LLMService, OllamaConfig}
import chuti.*
import chuti.CuantasCantas.*
import chuti.Triunfo.*
import chuti.bots.llm.*
import dao.InMemoryRepository.*
import zio.*
import zio.test.*

object SmartChutiBotSpec extends ZIOSpecDefault {

  // Mock LLM service that returns predefined responses
  class MockLLMService(responses: Ref[List[String]]) extends LLMService {

    override def generate(prompt: String): IO[LLMError, String] = {
      for {
        list <- responses.get
        response <- list match {
          case head :: tail =>
            responses.set(tail) *> ZIO.succeed(head)
          case Nil =>
            ZIO.fail(LLMError("No more mock responses"))
        }
      } yield response
    }

  }

  def spec =
    suite("SmartChutiBotSpec")(
      test("parses valid Canta response and falls back to DumbBot") {
        val json =
          """{"reasoning": "I have a strong hand", "moveType": "canta", "details": {"cuantasCantas": "Casa"}}"""

        for {
          responses <- Ref.make(List(json))
          mockService = new MockLLMService(responses)
          config = OllamaConfig(
            baseUrl = "http://localhost:11434",
            modelName = "test",
            temperature = 0.7,
            maxTokens = 500,
            timeout = Duration(10, java.util.concurrent.TimeUnit.SECONDS)
          )
          bot = AIChutiBot.live(mockService, config)
          game = TestGameHelper.createTestGame(gameStatus = GameStatus.cantando)
          jugador = game.jugadores.find(_.turno).get
          event <- bot.decideTurn(jugador.user, game)
        } yield assertTrue(
          event.isInstanceOf[Canta]
        )
      },
      test("falls back to DumbBot on invalid JSON") {
        val invalidJson = """this is not valid JSON"""

        for {
          responses <- Ref.make(List(invalidJson))
          mockService = new MockLLMService(responses)
          config = OllamaConfig(
            baseUrl = "http://localhost:11434",
            modelName = "test",
            temperature = 0.7,
            maxTokens = 500,
            timeout = Duration(10, java.util.concurrent.TimeUnit.SECONDS)
          )
          bot = AIChutiBot.live(mockService, config)
          game = TestGameHelper.createTestGame(gameStatus = GameStatus.cantando)
          jugador = game.jugadores.find(_.turno).get
          event <- bot.decideTurn(jugador.user, game)
        } yield assertTrue(
          event.isInstanceOf[Canta] // DumbBot should still return a valid Canta
        )
      },
      test("falls back to DumbBot on timeout") {
        for {
          responses <- Ref.make(List.empty[String]) // No responses, will cause error
          mockService = new MockLLMService(responses)
          config = OllamaConfig(
            baseUrl = "http://localhost:11434",
            modelName = "test",
            temperature = 0.7,
            maxTokens = 500,
            timeout = Duration(10, java.util.concurrent.TimeUnit.SECONDS)
          )
          bot = AIChutiBot.live(mockService, config)
          game = TestGameHelper.createTestGame(gameStatus = GameStatus.cantando)
          jugador = game.jugadores.find(_.turno).get
          event <- bot.decideTurn(jugador.user, game)
        } yield assertTrue(
          event.isInstanceOf[Canta] // DumbBot fallback
        )
      }
    )

}
