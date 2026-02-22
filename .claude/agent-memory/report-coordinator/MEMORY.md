# Report Coordinator Memory — Chuti Project

## Recurring Code Patterns to Watch For

### throw-in-ZIO (confirmed systemic)
The codebase uses `throw GameError(...)` inside ZIO for-comprehensions and domain event
`doEvent` methods throughout `events.scala` and `GameService.scala`. This creates untyped
defects that bypass ZIO error handling. Every new code review should check for this.
Key files: `server/src/main/scala/game/GameService.scala`, `model/shared/src/main/scala/chuti/events.scala`

### println in production source (confirmed systemic)
Debug `println` calls are repeatedly left in production code paths (events.scala, QuillRepository.scala).
CI has no guard against this. Flag any new `println` in `src/main` as High severity.

### Test coverage gaps for new auth/OAuth code
OAuth methods in ChutiAuthServer and QuillRepository have historically shipped without tests.
Always check coverage for: createOAuthUser, linkOAuthToUser, userByOAuthProvider, activateUserOAuth.

## Key Architectural Notes

### QuillRepository environment layer pattern
All queries must use `.provideSomeLayer[ChutiSession](...)` not `.provideLayer(...)`.
Using `.provideLayer` discards the session context and is a runtime bug.

### doEvent migration strategy
Short-term: wrap call sites with `ZIO.attempt(...).refineToOrDie[GameError]`
Long-term: migrate `doEvent` signatures to return `Either[GameError, (Game, GameEvent)]`
These are compatible incremental steps; do not treat as contradictory approaches.

## Known Fixed Bugs (track for regression)
- Token cleanup SQL: `QuillRepository.scala` near line 719, must be `<= now()` not `>= now()`
- JoinGame user reference: `user` vs `joinedUser` fix — needs regression test

## Test Migration Pattern
Commented-out tests should be migrated to ZIO Test format following `CantandoSpec.scala`.
Each test must create a unique game instance: `gameOperations.upsert(game1.copy(id = GameId.empty))`
See also project-level MEMORY.md for TestClock and parallel test patterns.
