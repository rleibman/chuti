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

import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.chat.request.*
import dev.langchain4j.model.ollama.OllamaChatModel
import zio.*

import scala.language.unsafeNulls

trait LLMService[E] {

  def generate(prompt: String): IO[E, String]

}

object LLMService {

  def live[E](
    config:       OllamaConfig,
    errorHandler: Throwable => E
  ): LLMService[E] =
    new LLMService[E] {

      private val model: OllamaChatModel = OllamaChatModel
        .builder()
        .baseUrl(config.baseUrl)
        .modelName(config.modelName)
        .temperature(config.temperature)
        .timeout(java.time.Duration.ofMillis(config.timeout.toMillis))
        .maxRetries(0)
        .build()

      override def generate(prompt: String): IO[E, String] = {

        ZIO
          .attemptBlocking {
            val request =
              ChatRequest
                .builder()
                .parameters(
                  ChatRequestParameters
                    .builder()
                    .responseFormat(ResponseFormat.JSON)
                    .build()
                )
                .messages(UserMessage(prompt))
                .build()

            model.chat(request).aiMessage().text()
          }
          .mapError(errorHandler)
      }

    }

}
