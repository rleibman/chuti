# Chuti Codebase Patterns

## Repository Pattern
Every Quill operation ends with:
```scala
.provideSomeLayer[ChutiSession](dataSourceLayer)
.mapError(RepositoryError.apply)
```
This is intentional and consistent — don't suggest removing it.

## Auth Pattern in Quill
`assertAuth(pred, errFn)` is a clean helper that enforces session authorization.
Used consistently across userOperations and gameOperations.

## GameEvent Pattern
All GameEvents have boilerplate fields (gameId, userId, index, gameStatusString, soundUrl, jugadorStatusString).
The `copy(index = Option(game.currentEventIndex), gameId = game.id, userId = ...)` idiom is used in every `doEvent`.

## ChutiSession Elevation
When an operation requires god-level access, `.provideLayer(godLayer)` is used inline.
This is the approved pattern in this codebase.

## Debug Println Anti-pattern
As of Feb 2026, multiple `println` statements remain in `events.scala` in production code.
These should be `ZIO.logDebug` but are in pure (non-ZIO) methods. Consider extracting
to a logger that's available in the event context, or removing before production deploy.

## Frontend State Management
`ChutiState` is a large case class holding both data AND callbacks (flipFicha, toggleSound, etc.).
Callbacks are initialized as no-ops and then replaced in `Content.Backend.refresh(initial=true)`.
This is the established pattern — don't suggest splitting ChutiState.
