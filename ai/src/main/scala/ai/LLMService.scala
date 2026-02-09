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
        .timeout(java.time.Duration.ofMillis(config.httpTimeout.toMillis))  // HTTP client timeout (fires first)
        .maxRetries(0)
        .build()

      override def generate(prompt: String): IO[LLMError, String] = {
        val promptPreview = if (prompt.length > 200) prompt.take(200) + "..." else prompt
        val promptLength = prompt.length
        val estimatedTokens = (promptLength / 4.0).toInt // Rough estimate: 1 token ≈ 4 chars

        ZIO.log(s"🤖 LLM Request starting - Model: ${config.modelName}") *>
        ZIO.log(s"📏 Prompt length: $promptLength chars (~$estimatedTokens tokens)") *>
        ZIO.log(s"📝 Prompt preview: $promptPreview") *>
        Clock.currentTime(java.util.concurrent.TimeUnit.MILLISECONDS).flatMap { startTime =>
          ZIO
            .attemptBlockingInterrupt {
              // Build message list based on configuration
              val messages = if (config.useSystemMessage && !config.customModel) {
                // Standard model: send rules via SystemMessage (fallback mode)
                println("📋 Using SystemMessage with game rules from resource file")
                java.util.List.of(
                  SystemMessage.from(gameRules),
                  UserMessage.from(prompt)
                )
              } else {
                // Rules included in prompt or baked into custom model
                val mode = if (config.customModel) "custom model (rules baked in)" else "prompt includes rules"
                println(s"🎯 Using $mode")
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

              println(s"⏳ Calling LLM model ${config.modelName} at ${config.baseUrl}...")
              val response = model.chat(request).aiMessage().text()
              println(s"✅ LLM response received (${response.length} chars)")
              response
            }
            .mapError {
              case e if e.getMessage != null && e.getMessage.contains("interrupt") =>
                LLMError("LLM request interrupted (timeout)", Option(e))
              case e if e.getMessage != null && e.getMessage.contains("abort") =>
                LLMError("LLM request aborted (timeout)", Option(e))
              case e =>
                LLMError("LLM generation failed", Option(e))
            }.tapBoth({
              error =>
                Clock.currentTime(java.util.concurrent.TimeUnit.MILLISECONDS).flatMap { endTime =>
                  val duration = endTime - startTime
                  ZIO.logError(s"❌ LLM Request failed after ${duration}ms: ${error.getMessage}")
                }
            }, {
              response =>
                Clock.currentTime(java.util.concurrent.TimeUnit.MILLISECONDS).flatMap { endTime =>
                  val duration = endTime - startTime
                  val responsePreview = if (response.length > 200) response.take(200) + "..." else response
                  ZIO.log(s"✅ LLM Request completed in ${duration}ms\n📤 Response preview: $responsePreview")
                }
            })
        }
      }

    }

}
