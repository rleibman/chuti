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
import chuti.bots.llm.*
import dao.InMemoryRepository.*
import zio.*
import zio.test.*

object AIBotGameSpec extends ZIOSpecDefault {

  // Mock LLM service that uses DumbBot logic to ensure valid moves
  class DumbBotBasedLLMService extends LLMService {

    override def generate(prompt: String): IO[LLMError, String] = {
      // For testing, we'll just return an invalid response to trigger fallback
      ZIO.succeed("""{"reasoning": "Test mode - using fallback", "moveType": "invalid_will_fallback", "details": {}}""")
    }

  }

  def spec =
    suite("AIBotGameSpec")(
      test("AIBot can play through cantando phase without errors") {
        val config = OllamaConfig(
          baseUrl = "http://localhost:11434",
          modelName = "test",
          temperature = 0.7,
          maxTokens = 500,
          timeout = Duration(1, java.util.concurrent.TimeUnit.SECONDS) // Short timeout to trigger fallback
        )
        val mockLLMService = new DumbBotBasedLLMService()
        val aiBot = AIChutiBot.live(mockLLMService, config)

        for {
          game <- ZIO.succeed(TestGameHelper.createTestGame(gameStatus = GameStatus.cantando))

          // Test cantando phase
          turnoPlayer = game.jugadores.find(_.turno).get
          cantaEvent <- aiBot.decideTurn(turnoPlayer.user, game)

        } yield assertTrue(
          cantaEvent.isInstanceOf[Canta]
        )
      },
      test("AIBot handles requiereSopa phase gracefully") {
        val config = OllamaConfig(
          baseUrl = "http://localhost:11434",
          modelName = "test",
          temperature = 0.7,
          maxTokens = 500,
          timeout = Duration(1, java.util.concurrent.TimeUnit.SECONDS)
        )
        val mockLLMService = new DumbBotBasedLLMService()
        val aiBot = AIChutiBot.live(mockLLMService, config)

        val game = TestGameHelper.createTestGame(gameStatus = GameStatus.requiereSopa)
        val turnoPlayer = game.jugadores.find(_.turno).get

        for {
          sopaEvent <- aiBot.decideTurn(turnoPlayer.user, game)
        } yield assertTrue(
          sopaEvent.isInstanceOf[Sopa]
        )
      },
      test("AIBot makes valid moves that don't violate game rules") {
        val config = OllamaConfig(
          baseUrl = "http://localhost:11434",
          modelName = "test",
          temperature = 0.7,
          maxTokens = 500,
          timeout = Duration(1, java.util.concurrent.TimeUnit.SECONDS)
        )
        val mockLLMService = new DumbBotBasedLLMService()
        val aiBot = AIChutiBot.live(mockLLMService, config)

        for {
          game <- ZIO.succeed(TestGameHelper.createTestGame(gameStatus = GameStatus.cantando))

          // Test that turno player never bids Buenas
          turnoPlayer = game.jugadores.find(_.turno).get
          cantaEvent <- aiBot.decideTurn(turnoPlayer.user, game)
          canta = cantaEvent.asInstanceOf[Canta]

        } yield assertTrue(
          canta.cuantasCantas != CuantasCantas.Buenas // Turno player must not bid Buenas
        )
      }
    ).provide(
      Runtime.removeDefaultLoggers
    )

}
