# Functional Scala Reviewer - Chuti Project Memory

## Project Conventions (Confirmed)
- Scala 3 with `-old-syntax` (brace-based) and `-Yexplicit-nulls`
- ZIO 2.x for all server-side effects; Caliban for GraphQL
- zio-json (not Circe) for serialization
- `RepositoryIO[A]` = `ZIO[ChutiSession, RepositoryError, A]`
- `GameTask[A]` = `ZIO[GameEnvironment & ChutiSession, GameError, A]`
- Repository ops provided via `dataSourceLayer` inside effect chain

## Known/Accepted Patterns
- `throw GameError(...)` inside `doEvent`/`redoEvent` methods on GameEvent case classes is the established domain event pattern; these are caught and converted by callers in GameService (see `applyEvent`). Still worth noting as a deviation from pure FP.
- `scala.util.Random.shuffle` in `Sopa.sopa` is an uncontrolled side effect (pure def that performs randomness). Accepted as domain code; randomness is inherently impure here.
- `mutable.Queue`, `mutable.Map`, `var` in `Content.scala`'s `Backend` class are acceptable Scala.js React patterns; `Backend` is a mutable OOP wrapper and `runNow()` is the idiom for bridging JS callback context back to the React component lifecycle.
- `println` calls scattered throughout are debug leftovers - should be replaced with ZIO logging on server side.
- `null` literal in `User.given JsonCodec[Locale]` is a Java interop pattern (`Locale.forLanguageTag` can return null), and is handled correctly with a match guard.

## Recurring Issues Found (2026-02-21 Review)
- `Option.get` used in several places in GameService.scala after `isDefined` guards - should use pattern matching or `map`
- `throw` inside ZIO for-comprehensions in GameService (e.g., `newGameSameUsers`) - should use `ZIO.fail`
- `isInstanceOf[Sopa]` in `testRedoEvent` - should use pattern matching
- `println` debugging left in events.scala (Canta, Pide, Da, TerminaJuego) and GameService - server side
- Trait/implementation stream signature mismatch: trait declares `ZStream[ChutiSession & ZIORepository, GameError, _]` but impl returns `ZStream[ChutiSession, Nothing, _]` - the Nothing is narrower (good) but worth documenting
- `dataSourceLayer` creation logs with `println` inside a `val` initializer (QuillRepository line 54) - uncontrolled side effect at construction time

## Architecture Notes
- `ChutiSession.godSession.toLayer` used pervasively to elevate operations to god-level auth
- `applyEvent` on `Game` wraps all `throw` calls from event classes and returns a result tuple `(Game, GameEvent)`
- Bot auto-play (`doBotsAutoPlay`) is recursive and forks as daemon - designed to not block human play responses
- `QuillRepository` is a `case class`, which is fine since ZIO wires it via `ZLayer`
- `FlywayMigration` is embedded inside `QuillRepository.uncached` layer (not in main environment)
