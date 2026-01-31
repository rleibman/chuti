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

package chuti.bots.llm

import chuti.*

object PromptBuilder {

  def buildPrompt(
    jugador:    Jugador,
    game:       Game,
    legalMoves: LegalMoves
  ): String = {
    val strategicContext = calculateStrategicContext(jugador, game)
    val gameState = formatGameState(jugador, game)
    val legalMovesJson = MoveValidator.toJsonOptions(legalMoves)
    val examples = getFewShotExamples(game.gameStatus)

    s"""You are playing Chuti, a 4-player domino game. Match is played to 21 points.

Game State:
$gameState

Strategic Context:
$strategicContext

Strategic Heuristics (consider these when deciding):
1. **Position in Match**:
   - If you're at 18+ points and leading: Play CONSERVATIVE (don't overbid, protect lead)
   - If opponent is at 18+ points: Play AGGRESSIVE (must take risks to catch up)
   - If close race (all players within 5 points): BALANCED approach

2. **Bidding Strategy**:
   - Leading by 10+ points: Bid minimum (Casa) unless very strong hand
   - Behind by 10+ points: Bid aggressively to catch up, take calculated risks
   - Opponent close to 21: Don't save them! Let them fail their bid if possible

3. **Playing Strategy**:
   - Protect your bid if you're cantante (don't waste your highest value tiles)
   - If opponent is cantante and close to 21: Try to make them fail (hoyar)
   - Save your strongest tiles (doubles and high value tiles) for critical moments

4. **Risk Management**:
   - Making your bid >> winning maximum tricks
   - A hoyo (failed bid) costs you heavily - be conservative on close calls
   - If you can Caete (drop) safely, consider it rather than risk hoyo

Legal Moves (choose ONE from the list below):
$legalMovesJson

CRITICAL RULES:
1. You MUST choose a move from the Legal Moves list above. NO other moves are valid.
2. For "pide" and "da" moves, you can ONLY play tiles that are in YOUR HAND (shown in Game State).
3. If trump is already set (shown in Game State above), you CANNOT change it.
4. DO NOT play tiles you don't have - check your hand carefully!

$examples

Think step-by-step about:
1. What tiles are in my hand? (Look at "Your hand" in Game State)
2. What are my legal moves? (Look at the Legal Moves list - you can ONLY choose from this list)
3. What is my position in the match? (am I winning/losing?)
4. What are the risks? (can I make my bid? will opponents beat me?)
5. What is the best strategy given the score? (conservative/aggressive)
6. Which legal move best fits my strategy?

Respond with ONLY a valid JSON object:
{
  "reasoning": "Step-by-step explanation of your thinking",
  "moveType": "canta|pide|da|caete|sopa",
  "details": {...}
}

Your response:"""
  }

  private def calculateStrategicContext(
    jugador: Jugador,
    game:    Game
  ): String = {
    val myScore = jugador.cuenta.map(_.puntos).sum
    val scores = game.jugadores.map { j =>
      s"${j.user.name}: ${j.cuenta.map(_.puntos).sum} points"
    }.mkString(", ")

    val sortedPlayers = game.jugadores.sortBy(-_.cuenta.map(_.puntos).sum)
    val leader = sortedPlayers.head
    val myRank = sortedPlayers.indexOf(jugador) + 1

    val myDistance = 21 - myScore
    val leaderDistance = 21 - leader.cuenta.map(_.puntos).sum

    val inDangerZone = game.jugadores.exists(_.cuenta.map(_.puntos).sum >= 18)
    val dangerPlayers =
      if (inDangerZone)
        game.jugadores.filter(_.cuenta.map(_.puntos).sum >= 18).map(_.user.name).mkString(", ")
      else "none"

    val leaderScore = leader.cuenta.map(_.puntos).sum
    s"""- Your score: $myScore points (rank $myRank/4, need $myDistance more to win)
- All scores: $scores
- Leading player: ${leader.user.name} with $leaderScore points
- Players in danger zone (18+): $dangerPlayers
- Match situation: ${
      if (myScore >= 18) "You're close to winning!"
      else if (leaderScore >= 18 && leader != jugador) "Leader is close to winning!"
      else if (myScore >= leaderScore - 3) "Close race"
      else if (myScore <= leaderScore - 10) "You're far behind"
      else "Normal game flow"
    }"""
  }

  private def formatGameState(
    jugador: Jugador,
    game:    Game
  ): String = {
    val hand = jugador.fichas.map(_.toString).mkString(", ")
    val trump = game.triunfo.map(_.toString).getOrElse("not set")
    val myBid = jugador.cuantasCantas.map(_.toString).getOrElse("none")
    val cantante = game.jugadores.find(_.cantante).map(_.user.name).getOrElse("none")
    val enJuego =
      if (game.enJuego.isEmpty) "none"
      else game.enJuego.map { case (userId, ficha) =>
        val player = game.jugadores.find(_.user.id == userId).get
        s"${player.user.name}: $ficha"
      }.mkString(", ")

    s"""- Your hand: [$hand]
- Trump: $trump
- Your bid: $myBid
- Who's cantante (bidder): $cantante
- Game phase: ${game.gameStatus}
- Dominoes on table: $enJuego
- Your won tricks: ${jugador.filas.size}"""
  }

  private def getFewShotExamples(gameStatus: GameStatus): String = {
    gameStatus match {
      case GameStatus.cantando =>
        """Examples with reasoning:

**Example 1 - Conservative bidding when leading:**
Input: hand=[6:6, 6:5, 5:3, 4:2, 3:1, 2:0, 1:0], your_score=18, opponent_scores=[12, 14, 15]
{
  "reasoning": "I have 18 points, leading the match. Opponents have 12, 14, 15 points. My hand has 6:6, 6:5 - decent but not strong. Strategic heuristic: I'm close to 21 and leading, so play CONSERVATIVE. I'll bid minimum (Casa = 4 tricks) to protect my lead. Even a small gain keeps me ahead.",
  "moveType": "canta",
  "details": {"cuantasCantas": "Casa"}
}

**Example 2 - Aggressive bidding when behind:**
Input: hand=[6:6, 6:4, 6:2, 6:1, 5:3, 4:0, 2:1], your_score=8, opponent_scores=[19, 12, 10]
{
  "reasoning": "I have 8 points but opponent has 19 points - they're one good hand from winning! My hand has 6:6, 6:4, 6:2, 6:1 - very strong in 6s. Strategic heuristic: Must play AGGRESSIVE to catch up. I'll bid Seis (6 tricks) with trump 6. This is risky but necessary - if opponent wins next hand, game is over.",
  "moveType": "canta",
  "details": {"cuantasCantas": "Seis"}
}

**Example 3 - Passing when hand is weak:**
Input: hand=[5:3, 4:2, 3:1, 2:0, 1:0, 4:1, 3:0], your_score=12, opponent_scores=[14, 10, 9], previous_bid=Casa
{
  "reasoning": "My hand is very weak - no mules, no high tiles, scattered numbers. Someone already bid Casa (4 tricks). I can't make even 4 tricks with this hand. Strategic heuristic: Don't overbid with weak hand - a hoyo (failed bid) would cost me points. I'll say Buenas (pass).",
  "moveType": "canta",
  "details": {"cuantasCantas": "Buenas"}
}"""

      case GameStatus.jugando =>
        """Examples with reasoning:

**Example 1 - Playing to deny opponent:**
Input: hand=[4:2, 4:5], table=[opponent:3:3], trump=6, cantante=opponent, opponent_score=17, legal_moves=[4:2, 4:5]
{
  "reasoning": "First, I check my hand: 4:2, 4:5. Opponent is cantante with 17 points and bid Casa (4 tricks). They've won 3 tricks already - one more and they hit 21 and win the match! The 4 is requested suit. My legal moves are 4:2 and 4:5 - I must play a 4. Strategic heuristic: Deny opponent their win. Play my strongest 4 (4:5) to try to win this trick.",
  "moveType": "da",
  "details": {"ficha": "4:5"}
}

**Example 2 - Saving strong tiles:**
Input: hand=[6:6, 5:2, 3:1], table=[opponent:4:2], trump=6, cantante=me, my_bid=Casa, won_tricks=3, legal_moves=[6:6, 5:2, 3:1]
{
  "reasoning": "First, I check my hand: 6:6, 5:2, 3:1. I'm cantante and already won 3 tricks - I only need 1 more to make my Casa bid. Opponent played 4:2. I don't have a 4, so my legal moves show I can play any of my tiles: 6:6, 5:2, or 3:1. Strategic heuristic: Protect my high tiles for later. Play my weakest tile (3:1) since I don't need to win this trick.",
  "moveType": "da",
  "details": {"ficha": "3:1"}
}

**Example 3 - Initial pide choosing trump:**
Input: hand=[6:6, 6:5, 6:4, 6:3, 5:2, 4:1, 3:0], cantante=me, bid=Cinco, legal_moves=[6:6, 6:5, 6:4, 6:3, 5:2, 4:1, 3:0]
{
  "reasoning": "First, I check my hand: 6:6, 6:5, 6:4, 6:3, 5:2, 4:1, 3:0. I'm cantante and bid Cinco (5 tricks). I have four 6s which is my strongest suit. Strategic heuristic: Choose trump that maximizes my winning potential. Trump 6 gives me 4 strong trump tiles. Looking at my legal moves, I can pide any tile from my hand. I'll pide my strongest tile 6:6 to establish trump 6.",
  "moveType": "pide",
  "details": {"ficha": "6:6", "triunfo": "6", "estrictaDerecha": false}
}"""

      case GameStatus.requiereSopa =>
        """Example with reasoning:

**Example - Shuffling deck:**
Input: game_phase=requiereSopa, turno=true
{
  "reasoning": "It's my turn to shuffle and deal the tiles. This is required before we can start the next round.",
  "moveType": "sopa",
  "details": {"firstSopa": false}
}"""

      case _ => ""
    }
  }

}
