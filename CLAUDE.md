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
- **Turno** - The player who deals this round
- **Cantante** - The player who bid (or was saved)
- **Mano** - The player who has the lead
- **Ficha** - A domino tile
- **Fila** - A won trick (4 tiles)
- **Triunfo** - Trump suit (a number 0-6, or SinTriunfos)
- **Hoyo** - Failing to make your bid
- **Chuti** - Bidding to win all 7 tricks
