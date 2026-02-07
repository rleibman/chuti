# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Chuti is a multiplayer online domino-based card game written in Scala 3. The game is played to 21 points with 4 players. The codebase is a full-stack Scala application with a ZIO-based server and a Scala.js frontend.

## Build Commands

Always use the `--error` flag with sbt to reduce output. Remove it only when you need additional debugging information.

```bash
# Compile everything
sbt --error compile

# Run all tests
sbt --error server/test

# Run a single test class
sbt --error "server/testOnly chuti.FullGameSpec"

# Run a single test by name pattern
sbt --error "server/testOnly *FullGameSpec"

# Build frontend (debug)
sbt --error web/debugDist

# Build frontend (production)
sbt --error web/dist

# Format code
sbt --error scalafmt

# Build Debian package
sbt --error server/debian:packageBin
```

### First-Time Setup

The project depends on a local library `chuti-stlib`. Before building, you must publish it locally:

```bash
cd stLib && sbt --error publishLocal && cd ..
```

### AI Bot Setup (Optional)

To enable AI-powered bots using Ollama:

1. **Install Ollama**: Download from [ollama.ai](https://ollama.ai)
2. **Build custom model**: Run `./scripts/setup-chuti-llm.sh`
3. **Verify setup**: Run `ollama list | grep chuti-llama3.2`

See `ai/README.md` for detailed configuration options and troubleshooting.

## Architecture

### Project Structure

- **model/** - Cross-compiled (JVM/JS) domain model containing game rules, entities (Game, Jugador, Ficha), and events
- **server/** - ZIO HTTP server with Caliban GraphQL API, Quill database access, and game logic
- **web/** - Scala.js frontend using scalajs-react with Semantic UI components
- **ai/** - AI/bot integration using Langchain4j and Ollama
- **analytics/** - Game analytics (cross-compiled)
- **stLib/** - ScalablyTyped bindings for Semantic UI React (must be published locally first)

### Key Technologies

- **Scala 3.8.1** with explicit nulls (`-Yexplicit-nulls`) and no indent syntax (`-no-indent`, `-old-syntax`)
- **ZIO 2.x** for effects, with zio-http for the server
- **Caliban** for GraphQL (server) and client code generation
- **Quill** with MariaDB for persistence
- **scalajs-react** with ScalaCSS for the frontend
- **zio-auth** custom authentication library

### Server Architecture

The server entry point is `api.Chuti`. Key layers:
- `ChutiEnvironment` - Full application environment built via `EnvironmentBuilder`
- `ChutiSession` - User session context (provided via bearer token middleware)
- `GameService` - Game state management with ZIO streams for real-time updates
- `ChatService` - Real-time chat via WebSocket-like streaming
- `ZIORepository` - Database operations (QuillRepository implementation)

Routes are organized in `api.routes`:
- `GameRoutes` - GraphQL API for game operations
- `ChatRoutes` - GraphQL API for chat
- `CRUDRoutes` - REST endpoints for user management
- `StaticRoutes` - Static file serving

### Domain Model

The game domain is in `model/shared/src/main/scala/chuti/`:
- `Game` - Main game state with players, trump (triunfo), and game status
- `Jugador` - Player state including hand (fichas), rows won (filas), and score (cuenta)
- `Ficha` - Domino tile with two numbers (0-6)
- `GameEvent` - Events that modify game state (applied via event sourcing pattern)
- `GameStatus` - Game phases: esperandoJugadores -> cantando -> jugando -> requiereSopa -> partidoTerminado

### Frontend Architecture

The web client in `web/src/main/scala/`:
- `app/ChutiApp.scala` - Main application entry point
- `pages/GamePage.scala`, `LobbyComponent.scala` - Main UI components
- `caliban/client/scalajs/` - Generated GraphQL client code
- `service/RESTClient.scala` - HTTP client for non-GraphQL endpoints

### GraphQL Client Generation

To regenerate GraphQL clients after schema changes:

```bash
scripts/genClient.sh
```

### Testing

Tests use ZIO Test and ScalaTest. Game tests use `InMemoryRepository` and test users defined in `dao/InMemoryRepository.scala` (user1, user2, user3, user4).

Test base class `GameAbstractSpec` provides helpers for:
- Creating and starting games
- Simulating player turns via `DumbChutiBot`
- Playing complete games programmatically

### Database

MariaDB with Flyway migrations. The Quill repository is in `dao/quill/QuillRepository.scala`. Test containers are used for integration tests (`ChutiContainer`).

## Game Terminology (Spanish)

- **Juego** - A single round from dealing to scoring
- **Partido** - A match played to 21 points
- **Turno** - The player whose turn it is to deal/make sopa this round
- **Cantante** - The player who bid (or was saved) and must make their bid
- **Mano** - The player who currently has the lead and plays first
- **Ficha** - A domino tile with two numbers (arriba/abajo, each 0-6)
- **Fila** - A won trick (4 tiles, one from each player)
- **Triunfo** - Trump suit (a number 0-6, or SinTriunfos for no trump)
- **Hoyo** - Failing to make your bid (negative points)
- **Chuti** - Bidding to win all 7 tricks (instant 21 points if successful)
- **Sopa** - Shuffling and dealing the tiles
- **Caerse** - "Falling down" - ending play early when you can prove you'll win remaining tricks
- **Estricta Derecha** - Strict right - play must proceed clockwise, no skipping

## Game Rules

### The Tiles (Fichas)
- Standard domino set: 28 tiles with values from 0:0 to 6:6
- Each tile has two numbers (arriba and abajo), normalized so arriba >= abajo
- A "mula" is a double (same number on both sides, e.g., 6:6)
- Special tiles:
  - **La Mulota** (6:6) - Determines first dealer in a new partido
  - **Campanita** (0:1) - Special event when played

### Match Structure (Partido)
- 4 players required
- First to reach 21 points wins the partido
- Multiple juegos (rounds) are played until someone reaches 21

### Round Structure (Juego)

#### 1. Sopa (Dealing)
- The player with `turno=true` shuffles and deals 7 tiles to each player
- In the first juego of a partido, whoever has La Mulota (6:6) deals and bids first
- In subsequent juegos, the player after the previous turno makes sopa

#### 2. Cantando (Bidding)
- Starting with the cantante (initially who has La Mulota, then rotates), each player bids or passes
- Bids (CuantasCantas):
  - **Casa** (4): Must win 4+ tricks, scores 4 points
  - **Canto5** (5): Must win 5+ tricks, scores 5 points
  - **Canto6** (6): Must win 6+ tricks, scores 6 points
  - **CantoTodas/Chuti** (7): Must win all 7 tricks, scores 21 points (instant win)
  - **Buenas**: Pass (accept current bid)
- A player can "salvar" (save) another by bidding higher, becoming the new cantante
- Bidding continues clockwise until it returns to the turno player or someone bids Chuti

#### 3. Jugando (Playing)
- The cantante plays first, declaring triunfo (trump) with their first tile
- Trump options: any number 0-6, or SinTriunfos (no trump)
- Play proceeds clockwise
- Each player must follow the led number if possible, or play trump if they have it
- Highest tile of the led suit wins, unless trumped

#### Winning Tricks
- If no trump: highest tile matching the led number wins
- If mula (double) is led: only that exact mula can win
- With trump: trump beats non-trump; highest trump wins among trumps

#### 4. Ending a Round
- Round ends when:
  - All 7 tricks are played
  - A player "se cae" (falls) - proves they'll win remaining tricks
  - A player "se rinde" (surrenders) - only allowed before second trick

#### Scoring
- Cantante: If made bid, scores number of tricks won (or 21 for Chuti). If failed (hoyo), loses bid amount
- Other players: Score number of tricks won
- Special bonuses at partido end for hoyos, reaching exactly 0 or negative points

### Special Events (Borlotes)

- **Hoyo**: Cantante failed to make their bid
- **Hoyo Técnico**: Technical foul (e.g., not following suit when able, not falling when required)
- **Campanita**: Playing the 0:1 tile (special sound)
- **Santa Claus**: Special event
- **El Niño del Cumpleaños**: Special event
- **Helecho**: Special event related to game end

### Caerse (Falling)
When a player can prove they'll win all remaining tricks (based on cards in play and cards remaining), they can "caerse":
- All their remaining tiles become automatic wins
- Remaining tiles from other players become "regalos" (gifts) distributed by tile strength
- A player MUST fall if they can - failing to do so is a hoyo técnico

### Game States (GameStatus)
1. `esperandoJugadoresInvitados` / `esperandoJugadoresAzar` - Waiting for players
2. `comienzo` - Initial state
3. `cantando` - Bidding phase
4. `jugando` - Playing phase
5. `requiereSopa` - Needs new deal (between rounds)
6. `partidoTerminado` - Match complete
7. `abandonado` - Game abandoned

### Key Game Events
- `Sopa` - Deal tiles
- `Canta` - Make a bid
- `Pide` - Lead a tile (request others to follow)
- `Da` - Play a tile in response
- `Caete` - Fall down to end round early
- `MeRindo` - Surrender (before second trick)
- `TerminaJuego` - End the round
- `HoyoTecnico` - Technical foul

### Code Locations
- Game model: `model/shared/src/main/scala/chuti/model.scala`
- Game events: `model/shared/src/main/scala/chuti/events.scala`
- Event JSON codecs: `model/shared/src/main/scala/chuti/Codecs.scala`
- Game service: `server/src/main/scala/game/GameService.scala`
- Bot logic: `server/src/main/scala/game/DumbChutiBot.scala`
