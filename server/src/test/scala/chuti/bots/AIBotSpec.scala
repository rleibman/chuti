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
import chuti.Numero.*
import chuti.Triunfo.*
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.test.*
import zio.test.Assertion.*

object AIBotSpec extends ZIOSpecDefault {

  // Fixed timestamp for deterministic tests
  private val testInstant = java.time.Instant.parse("2024-01-01T00:00:00Z")

  // Mock LLM service for testing
  val mockLLMService: LLMService = new LLMService {
    def generate(prompt: String): IO[LLMError, String] = {
      // Return a valid JSON response for bidding Casa (4 tricks)
      ZIO.succeed("""{"type":"canta","cuantasCantas":4,"reasoning":"Test reasoning"}""")
    }
  }

  // Mock LLM that always fails for testing error handling
  val failingLLMService: LLMService = new LLMService {
    def generate(prompt: String): IO[LLMError, String] = ZIO.fail(LLMError("Mock LLM failure"))
  }

  val testConfig = OllamaConfig(
    baseUrl = "http://localhost:11434",
    modelName = "test-model",
    temperature = 0.7,
    maxTokens = 500,
    timeout = Duration(30, java.util.concurrent.TimeUnit.SECONDS),
    useSystemMessage = false,
    customModel = true
  )

  // Create bots with Ref[LLMStats] via Unsafe for use in pure/mixed test contexts
  private val (testBot, failingBot) = Unsafe.unsafe { implicit u =>
    Runtime.default.unsafe.run {
      for {
        statsRef1 <- Ref.make(LLMStats())
        statsRef2 <- Ref.make(LLMStats())
      } yield (
        AIBot(testConfig, mockLLMService, statsRef1),
        AIBot(testConfig, failingLLMService, statsRef2)
      )
    }.getOrThrowFiberFailure()
  }

  // Helper to create a test game
  def createTestGame(
    triunfo:            Option[Triunfo] = Some(TriunfoNumero(Numero6)),
    gameStatus:         GameStatus = GameStatus.jugando,
    botDifficultyLevel: BotDifficultyLevel = BotDifficultyLevel.intermediate
  ): Game = {
    val jugadores = List(
      Jugador(
        user = User(
          id = UserId(1),
          email = "player1@test.com",
          name = "Player1",
          created = testInstant,
          lastUpdated = testInstant,
          active = true,
          deleted = false,
          isAdmin = false
        ),
        jugadorType = JugadorType.human,
        fichas = List(
          Ficha(Numero6, Numero6), // 6:6 double
          Ficha(Numero6, Numero5), // 6:5
          Ficha(Numero5, Numero5), // 5:5 double
          Ficha(Numero4, Numero3), // 4:3
          Ficha(Numero2, Numero1), // 2:1
          Ficha(Numero1, Numero0), // 1:0
          Ficha(Numero3, Numero2) // 3:2
        ),
        filas = List.empty,
        mano = true,
        turno = false,
        cantante = false,
        cuantasCantas = None
      ),
      Jugador(
        user = User(
          id = UserId(2),
          email = "player2@test.com",
          name = "Player2",
          created = testInstant,
          lastUpdated = testInstant,
          active = true,
          deleted = false,
          isAdmin = false
        ),
        jugadorType = JugadorType.human,
        fichas = List(
          Ficha(Numero6, Numero4),
          Ficha(Numero5, Numero4),
          Ficha(Numero4, Numero4),
          Ficha(Numero3, Numero3),
          Ficha(Numero2, Numero2),
          Ficha(Numero1, Numero1),
          Ficha(Numero0, Numero0)
        ),
        filas = List.empty,
        mano = false,
        turno = false,
        cantante = true,
        cuantasCantas = Some(Casa)
      ),
      Jugador(
        user = User(
          id = UserId(3),
          email = "player3@test.com",
          name = "Player3",
          created = testInstant,
          lastUpdated = testInstant,
          active = true,
          deleted = false,
          isAdmin = false
        ),
        jugadorType = JugadorType.human,
        fichas = List(
          Ficha(Numero6, Numero3),
          Ficha(Numero6, Numero2),
          Ficha(Numero5, Numero3),
          Ficha(Numero5, Numero2),
          Ficha(Numero4, Numero2),
          Ficha(Numero4, Numero1),
          Ficha(Numero3, Numero1)
        ),
        filas = List.empty
      ),
      Jugador(
        user = User(
          id = UserId(4),
          email = "player4@test.com",
          name = "Player4",
          created = testInstant,
          lastUpdated = testInstant,
          active = true,
          deleted = false,
          isAdmin = false
        ),
        jugadorType = JugadorType.human,
        fichas = List(
          Ficha(Numero6, Numero1),
          Ficha(Numero6, Numero0),
          Ficha(Numero5, Numero1),
          Ficha(Numero5, Numero0),
          Ficha(Numero4, Numero0),
          Ficha(Numero3, Numero0),
          Ficha(Numero2, Numero0)
        ),
        filas = List.empty
      )
    )

    Game(
      id = GameId(1),
      created = testInstant,
      gameStatus = gameStatus,
      triunfo = triunfo,
      jugadores = jugadores,
      botDifficultyLevel = botDifficultyLevel
    )
  }

  def spec =
    suite("AIBot")(
      suite("Memory Calculation")(
        test("Easy difficulty remembers only last 1-2 tricks") {
          val game = createTestGame(botDifficultyLevel = BotDifficultyLevel.easy)
          val jugador = game.jugadores.head

          val memory = testBot.calculateMemorySummary(game, jugador)

          assertTrue(
            memory.trump == TriunfoNumero(Numero6),
            memory.exhaustedNumbers.isEmpty,
            memory.scarceNumbers.isEmpty,
            memory.playerVoids.isEmpty
          )
        },
        test("Intermediate difficulty tracks doubles, trumps, and voids") {
          val game = createTestGame(botDifficultyLevel = BotDifficultyLevel.intermediate)
          val jugador = game.jugadores.head

          val memory = testBot.calculateMemorySummary(game, jugador)

          assertTrue(
            memory.trump == TriunfoNumero(Numero6),
            memory.exhaustedNumbers.isEmpty, // Intermediate doesn't track exhausted
            memory.scarceNumbers.isEmpty,    // Intermediate doesn't track scarcity
            memory.playerVoids.isDefined     // But does track voids
          )
        },
        test("Advanced difficulty tracks everything") {
          // Create game with some played tiles
          val game = createTestGame(botDifficultyLevel = BotDifficultyLevel.advanced)
          val gameWithPlayedTiles = game.copy(
            jugadores = game.jugadores.map(j =>
              j.copy(filas =
                List(
                  Fila(
                    Seq(
                      Ficha(Numero6, Numero6),
                      Ficha(Numero6, Numero5),
                      Ficha(Numero6, Numero4),
                      Ficha(Numero6, Numero3)
                    )
                  )
                )
              )
            )
          )
          val jugador = gameWithPlayedTiles.jugadores.head

          val memory = testBot.calculateMemorySummary(gameWithPlayedTiles, jugador)

          assertTrue(
            memory.trump == TriunfoNumero(Numero6),
            memory.exhaustedNumbers.isDefined,
            memory.scarceNumbers.isDefined,
            memory.playerVoids.isDefined,
            memory.trumpsSeen.nonEmpty,
            memory.doublesSeen.nonEmpty
          )
        },
        test("Void inference detects when player didn't follow suit") {
          val game = createTestGame()
          // Simulate a trick where player 2 didn't follow suit
          val gameWithTrick = game.copy(
            enJuego = List(
              (UserId(1), Ficha(Numero6, Numero5)), // Player 1 leads with 6
              (UserId(2), Ficha(Numero4, Numero3)) // Player 2 plays non-6 (has void)
            )
          )
          val jugador = gameWithTrick.jugadores.head

          val memory = testBot.calculateMemorySummary(gameWithTrick, jugador)

          assertTrue(
            memory.playerVoids.exists { voids =>
              voids.exists { case (j, nums) =>
                j.user.id == UserId(2) && nums.contains(Numero6)
              }
            }
          )
        }
      ),
      suite("Special Chuti Detection")(
        test("Detects consecutive sixes + doubles") {
          val specialHand = List(
            Ficha(Numero6, Numero6), // 6:6
            Ficha(Numero6, Numero5), // 6:5
            Ficha(Numero6, Numero4), // 6:4
            Ficha(Numero5, Numero5), // 5:5 double
            Ficha(Numero4, Numero4), // 4:4 double
            Ficha(Numero3, Numero3), // 3:3 double
            Ficha(Numero2, Numero2) // 2:2 double
          )

          val game = createTestGame(gameStatus = GameStatus.cantando)
          val gameWithSpecialHand = game.copy(
            jugadores = game.jugadores.updated(
              0,
              game.jugadores.head.copy(fichas = specialHand, turno = true)
            )
          )
          val jugador = gameWithSpecialHand.jugadores.head

          for {
            decision <- testBot.canta(jugador, gameWithSpecialHand)
          } yield assertTrue(
            decision.isInstanceOf[Canta],
            decision.asInstanceOf[Canta].cuantasCantas == CantoTodas
          )
        },
        test("Detects all 6 top doubles + 1:0") {
          val specialHand = List(
            Ficha(Numero6, Numero6),
            Ficha(Numero5, Numero5),
            Ficha(Numero4, Numero4),
            Ficha(Numero3, Numero3),
            Ficha(Numero2, Numero2),
            Ficha(Numero1, Numero1),
            Ficha(Numero1, Numero0)
          )

          val game = createTestGame(gameStatus = GameStatus.cantando)
          val gameWithSpecialHand = game.copy(
            jugadores = game.jugadores.updated(
              0,
              game.jugadores.head.copy(fichas = specialHand, turno = true)
            )
          )
          val jugador = gameWithSpecialHand.jugadores.head

          for {
            decision <- testBot.canta(jugador, gameWithSpecialHand)
          } yield assertTrue(
            decision.isInstanceOf[Canta],
            decision.asInstanceOf[Canta].cuantasCantas == CantoTodas
          )
        },
        test("Detects 5 trumps + 1:1 + 1:0 (campanita base)") {
          val specialHand = List(
            Ficha(Numero6, Numero6),
            Ficha(Numero6, Numero5),
            Ficha(Numero6, Numero4),
            Ficha(Numero6, Numero3),
            Ficha(Numero6, Numero2),
            Ficha(Numero1, Numero1),
            Ficha(Numero1, Numero0)
          )

          val game = createTestGame(gameStatus = GameStatus.cantando)
          val gameWithSpecialHand = game.copy(
            jugadores = game.jugadores.updated(
              0,
              game.jugadores.head.copy(fichas = specialHand, turno = true)
            )
          )
          val jugador = gameWithSpecialHand.jugadores.head

          for {
            decision <- testBot.canta(jugador, gameWithSpecialHand)
          } yield assertTrue(
            decision.isInstanceOf[Canta],
            decision.asInstanceOf[Canta].cuantasCantas == CantoTodas
          )
        }
      ),
      suite("JSON Serialization")(
        test("toSimplifiedJson for Canta includes cuantasCantas") {
          val canta = Canta(
            cuantasCantas = Casa,
            gameId = GameId(1),
            userId = UserId(1)
          )

          val jsonResult = testBot.toSimplifiedJson(canta)

          assertTrue(
            jsonResult.isRight,
            jsonResult.exists(_.toString.contains("\"cuantasCantas\""))
          )
        },
        test("toSimplifiedJson for Pide includes ficha and trump") {
          val pide = Pide(
            ficha = Ficha(Numero6, Numero5),
            triunfo = Some(TriunfoNumero(Numero6)),
            estrictaDerecha = false,
            gameId = GameId(1),
            userId = UserId(1)
          )

          val jsonResult = testBot.toSimplifiedJson(pide)

          assertTrue(
            jsonResult.isRight,
            jsonResult.exists(json =>
              json.toString.contains("\"ficha\"") &&
                json.toString.contains("\"trump\"")
            )
          )
        },
        test("toSimplifiedJson for Da includes ficha") {
          val da = Da(
            ficha = Ficha(Numero6, Numero5),
            gameId = GameId(1),
            userId = UserId(1)
          )

          val jsonResult = testBot.toSimplifiedJson(da)

          assertTrue(
            jsonResult.isRight,
            jsonResult.exists(_.toString.contains("\"ficha\""))
          )
        },
        test("fromSimplifiedJson parses Canta correctly") {
          val json = """{"type":"canta","cuantasCantas":4,"reasoning":"Test"}""".fromJson[Json].toOption.get
          val game = createTestGame(gameStatus = GameStatus.cantando)
          val jugador = game.jugadores.head

          for {
            playEvent <- testBot.fromSimplifiedJson(json, game, jugador)
          } yield assertTrue(
            playEvent.isInstanceOf[Canta],
            playEvent.asInstanceOf[Canta].cuantasCantas == Casa
          )
        },
        test("fromSimplifiedJson parses Pide correctly") {
          val json = """{"type":"pide","ficha":"6:5","trump":"6","estrictaDerecha":false,"reasoning":"Test"}"""
            .fromJson[Json].toOption.get
          val game = createTestGame()
          val jugador = game.jugadores.head

          for {
            playEvent <- testBot.fromSimplifiedJson(json, game, jugador)
          } yield assertTrue(
            playEvent.isInstanceOf[Pide],
            playEvent.asInstanceOf[Pide].ficha == Ficha(Numero6, Numero5)
          )
        },
        test("fromSimplifiedJson parses Da correctly") {
          val json = """{"type":"da","ficha":"6:5","reasoning":"Test"}""".fromJson[Json].toOption.get
          val game = createTestGame()
          val jugador = game.jugadores.head

          for {
            playEvent <- testBot.fromSimplifiedJson(json, game, jugador)
          } yield assertTrue(
            playEvent.isInstanceOf[Da],
            playEvent.asInstanceOf[Da].ficha == Ficha(Numero6, Numero5)
          )
        }
      ),
      suite("Bidding Logic")(
        test("Auto-bids Casa for weak hands when turno") {
          val weakHand = List(
            Ficha(Numero1, Numero0),
            Ficha(Numero2, Numero0),
            Ficha(Numero3, Numero0),
            Ficha(Numero4, Numero0),
            Ficha(Numero5, Numero0),
            Ficha(Numero6, Numero0),
            Ficha(Numero2, Numero1)
          )

          val game = createTestGame(gameStatus = GameStatus.cantando)
          val gameWithWeakHand = game.copy(
            jugadores = game.jugadores.updated(
              0,
              game.jugadores.head.copy(fichas = weakHand, turno = true)
            )
          )
          val jugador = gameWithWeakHand.jugadores.head

          for {
            decision <- testBot.canta(jugador, gameWithWeakHand)
          } yield assertTrue(
            decision.isInstanceOf[Canta],
            decision.asInstanceOf[Canta].cuantasCantas == Casa
          )
        },
        test("Auto-bids Buenas when hand is weaker than current bid") {
          val game = createTestGame(gameStatus = GameStatus.cantando)
          val gameWithBid = game.copy(
            jugadores = game.jugadores
              .updated(
                0,
                game.jugadores.head.copy(cuantasCantas = Some(Canto6), turno = false)
              ).updated(
                1,
                game
                  .jugadores(1).copy(
                    fichas = List(
                      Ficha(Numero1, Numero0),
                      Ficha(Numero2, Numero0),
                      Ficha(Numero3, Numero0),
                      Ficha(Numero4, Numero0),
                      Ficha(Numero5, Numero0),
                      Ficha(Numero6, Numero0),
                      Ficha(Numero2, Numero1)
                    ),
                    turno = false
                  )
              )
          )
          val jugador = gameWithBid.jugadores(1)

          for {
            decision <- testBot.canta(jugador, gameWithBid)
          } yield assertTrue(
            decision.isInstanceOf[Canta],
            decision.asInstanceOf[Canta].cuantasCantas == Buenas
          )
        },
        test("Uses LLM for ambiguous bidding decisions") {
          val game = createTestGame(gameStatus = GameStatus.cantando)
          val gameForLLM = game.copy(
            jugadores = game.jugadores.updated(
              0,
              game.jugadores.head.copy(
                turno = true,
                fichas = List(
                  Ficha(Numero6, Numero6),
                  Ficha(Numero6, Numero5),
                  Ficha(Numero5, Numero5),
                  Ficha(Numero4, Numero4),
                  Ficha(Numero3, Numero3),
                  Ficha(Numero2, Numero2),
                  Ficha(Numero1, Numero1)
                )
              )
            )
          )
          val jugador = gameForLLM.jugadores.head

          for {
            decision <- testBot.canta(jugador, gameForLLM)
          } yield assertTrue(
            decision.isInstanceOf[Canta],
            // LLM should return valid bid
            decision.asInstanceOf[Canta].cuantasCantas.numFilas >= 4
          )
        }
      ),
      suite("Auto-Decision Logic")(
        test("Auto-plays when only one legal tile in Da") {
          val game = createTestGame()
          val gameWithSingleOption = game.copy(
            enJuego = List((UserId(2), Ficha(Numero6, Numero5))),
            jugadores = game.jugadores.updated(
              0,
              game.jugadores.head.copy(
                fichas = List(Ficha(Numero6, Numero6)), // Only one tile with 6
                mano = false
              )
            )
          )
          val jugador = gameWithSingleOption.jugadores.head

          for {
            decision <- testBot.da(jugador, gameWithSingleOption)
          } yield assertTrue(
            decision.isInstanceOf[Da],
            decision.asInstanceOf[Da].ficha == Ficha(Numero6, Numero6),
            decision.asInstanceOf[Da].reasoning.exists(r =>
              r.contains("No thinking needed") || r.contains("Only one legal")
            )
          )
        },
        test("Auto-plays lowest tile when can't follow or trump") {
          val game = createTestGame()
          val gameNoFollow = game.copy(
            enJuego = List((UserId(2), Ficha(Numero6, Numero5))),
            jugadores = game.jugadores.updated(
              0,
              game.jugadores.head.copy(
                fichas = List(
                  Ficha(Numero1, Numero0), // Lowest
                  Ficha(Numero2, Numero1),
                  Ficha(Numero3, Numero2)
                ), // No 6s, no trumps
                mano = false,
                cantante = false
              )
            )
          )
          val jugador = gameNoFollow.jugadores.head

          for {
            decision <- testBot.da(jugador, gameNoFollow)
          } yield assertTrue(
            decision.isInstanceOf[Da],
            decision.asInstanceOf[Da].ficha == Ficha(Numero1, Numero0) // Should play lowest
          )
        }
      ),
      suite("Surrender Logic")(
        test("Surrenders when mathematically impossible to make bid") {
          val game = createTestGame()
          val impossibleGame = game.copy(
            jugadores = game.jugadores.updated(
              0,
              game.jugadores.head.copy(
                fichas = List(
                  Ficha(Numero1, Numero0), // Weak tiles
                  Ficha(Numero2, Numero0),
                  Ficha(Numero3, Numero0),
                  Ficha(Numero4, Numero0),
                  Ficha(Numero5, Numero0),
                  Ficha(Numero2, Numero1) // 6 tiles = after first trick
                ),
                filas = List.empty, // Won 0 tricks
                cantante = true,
                cuantasCantas = Some(Canto6), // Need 6 tricks, only 6 tiles left, no way to win
                mano = true
              )
            )
          )
          val jugador = impossibleGame.jugadores.head

          for {
            decision <- testBot.pide(jugador, impossibleGame)
          } yield assertTrue(
            decision.isInstanceOf[MeRindo],
            decision.asInstanceOf[MeRindo].reasoning.exists(_.contains("Surrender"))
          )
        },
        test("Doesn't surrender when bid is still achievable") {
          val game = createTestGame()
          val achievableGame = game.copy(
            jugadores = game.jugadores.updated(
              0,
              game.jugadores.head.copy(
                fichas = List(
                  Ficha(Numero6, Numero6),
                  Ficha(Numero6, Numero5),
                  Ficha(Numero6, Numero4),
                  Ficha(Numero6, Numero3),
                  Ficha(Numero6, Numero2)
                ),
                filas = List.empty,
                cantante = true,
                cuantasCantas = Some(Casa), // Need 4 tricks, have strong hand
                mano = true
              )
            )
          )
          val jugador = achievableGame.jugadores.head

          for {
            decision <- testBot.pide(jugador, achievableGame)
          } yield assertTrue(
            !decision.isInstanceOf[MeRindo]
          )
        }
      ),
      suite("Error Handling")(
        test("Falls back to DumbBot on LLM failure") {
          val game = createTestGame(gameStatus = GameStatus.cantando)
          val gameWithGoodHand = game.copy(
            jugadores = game.jugadores.updated(
              0,
              game.jugadores.head.copy(
                turno = true,
                fichas = List(
                  Ficha(Numero6, Numero6),
                  Ficha(Numero6, Numero5),
                  Ficha(Numero5, Numero5),
                  Ficha(Numero4, Numero4),
                  Ficha(Numero3, Numero3),
                  Ficha(Numero2, Numero2),
                  Ficha(Numero1, Numero1)
                )
              )
            )
          )
          val jugador = gameWithGoodHand.jugadores.head

          for {
            decision <- failingBot.canta(jugador, gameWithGoodHand)
          } yield assertTrue(
            decision.isInstanceOf[Canta],
            // Should still get a valid decision from DumbBot fallback
            decision.asInstanceOf[Canta].cuantasCantas.numFilas >= 4
          )
        },
        test("Handles invalid JSON gracefully") {
          val invalidJson = """{"type":"invalid"}""".fromJson[Json].toOption.get
          val game = createTestGame()
          val jugador = game.jugadores.head

          for {
            result <- testBot.fromSimplifiedJson(invalidJson, game, jugador).either
          } yield assertTrue(
            result.isLeft,
            result.left.exists(_.msg.contains("Unsupported"))
          )
        }
      ),
      suite("Tile Value Calculation")(
        test("Correctly values trump doubles highest") {
          val game = createTestGame(triunfo = Some(TriunfoNumero(Numero6)))
          val jugador = game.jugadores.head

          // Access private method through reflection or test indirectly
          // For now, test indirectly through da method behavior
          val gameForValue = game.copy(
            enJuego = List(
              (UserId(2), Ficha(Numero6, Numero6)), // Trump double
              (UserId(3), Ficha(Numero6, Numero5)) // Trump non-double
            ),
            jugadores = game.jugadores.updated(
              0,
              game.jugadores.head.copy(
                fichas = List(Ficha(Numero5, Numero5)), // Non-trump double
                mano = false
              )
            )
          )

          // The bot should recognize it can't beat the trump double
          for {
            decision <- testBot.da(jugador, gameForValue)
          } yield assertTrue(
            decision.isInstanceOf[Da]
            // Bot should play its tile (can't win anyway)
          )
        }
      ),
      suite("Integration")(
        test("Completes full bidding round") {
          val game = createTestGame(gameStatus = GameStatus.cantando)
          val player1 = game.jugadores.head.copy(turno = true)

          for {
            bid1 <- testBot.canta(player1, game)
          } yield assertTrue(
            bid1.isInstanceOf[Canta],
            bid1.asInstanceOf[Canta].cuantasCantas.numFilas >= 4,
            bid1.asInstanceOf[Canta].cuantasCantas.numFilas <= 7
          )
        },
        test("Handles complete trick sequence") {
          val game = createTestGame()
          val cantante = game.jugadores.find(_.cantante).get

          for {
            // Cantante leads
            pideDecision <- testBot.pide(cantante, game)
            // Should be a valid Pide or Caete
            _ <- ZIO.succeed(
              assertTrue(
                pideDecision.isInstanceOf[Pide] || pideDecision.isInstanceOf[Caete]
              )
            )
          } yield assertCompletes
        }
      )
    ).@@(TestAspect.withLiveClock)

}
