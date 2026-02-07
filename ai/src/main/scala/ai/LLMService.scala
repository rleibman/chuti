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

package ai

import dev.langchain4j.data.message.{SystemMessage, UserMessage}
import dev.langchain4j.model.chat.request.*
import dev.langchain4j.model.ollama.OllamaChatModel
import zio.*

import scala.io.Source
import scala.language.unsafeNulls

case class LLMError(message: String, cause: Option[Throwable] = None) extends Throwable(message, cause.orNull)


trait LLMService {

  def generate(prompt: String): IO[LLMError, String]

}

object LLMService {

  // Load condensed game rules from resources (fallback mode)
  private lazy val gameRules: String = {
    val stream = getClass.getResourceAsStream("/chuti_game_rules.txt")
    if (stream == null) {
      throw new RuntimeException("Game rules resource not found at /chuti_game_rules.txt")
    }
    try {
      Source.fromInputStream(stream).mkString
    } finally {
      stream.close()
    }
  }

  def live(
    config:       OllamaConfig
  ): LLMService =
    new LLMService {

      private val model: OllamaChatModel = OllamaChatModel
        .builder()
        .baseUrl(config.baseUrl)
        .modelName(config.modelName)
        .temperature(config.temperature)
        .timeout(java.time.Duration.ofMillis(config.timeout.toMillis))
        .maxRetries(0)
        .build()

      override def generate(prompt: String): IO[LLMError, String] = {

        ZIO
          .attemptBlocking {
            // Build message list based on configuration
            val messages = if (config.useSystemMessage && !config.customModel) {
              // Standard model: send rules via SystemMessage (fallback mode)
              java.util.List.of(
                SystemMessage.from(gameRules),
                UserMessage.from(prompt)
              )
            } else {
              // Custom model: rules already baked in
              java.util.List.of(UserMessage.from(prompt))
            }

            val request =
              ChatRequest
                .builder()
                .parameters(
                  ChatRequestParameters
                    .builder()
                    .responseFormat(ResponseFormat.JSON)
                    .build()
                )
                .messages(messages)
                .build()

            model.chat(request).aiMessage().text()
          }
          .mapError(e => LLMError("LLM generation failed", Option(e)))
      }

    }

}
