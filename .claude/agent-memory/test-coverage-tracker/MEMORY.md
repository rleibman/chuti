# Test Coverage Tracker - Chuti Project

## Project Test Infrastructure
- **Test framework**: ZIO Test (primary) + ScalaTest (legacy, some files still use AnyFlatSpec)
- **Test layer**: `EnvironmentBuilder.testLayer()` / `EnvironmentBuilder.withContainer` (real DB)
- **Test users**: `user1..user4` in `db.InMemoryRepository` companion object
- **In-memory repo**: `server/src/main/scala/chuti/db/InMemoryRepository.scala`
- **Test resources**: `server/src/test/resources/` (startedGame.json, canto4.json, newGame.json)

## Test File Locations
- `server/src/test/scala/chuti/` - game logic tests (ZIO Test)
- `server/src/test/scala/db/quill/` - real database integration tests (need TestContainers)
- `server/src/test/scala/chuti/bots/` - AI bot tests
- `server/src/test/scala/mail/` - email tests

## Key Coverage Findings (Feb 2026)

### Well-Covered Areas
- `Canta` event: 6 tests in CantandoSpec (casa, 5, todas, with/without salve, chuti salve)
- `Da`/`Pide`/`Caete`/`TerminaJuego` events: exercised by JugandoSpec + FullGameSpec
- `ManoGanadoraSpec`: 15+ trick-winning calculation tests + 16 caida calculation tests (pure, ScalaTest)
- `AIBotSpec`: memory calc, special chuti detection, JSON serialization, bidding, error handling
- QuillRepository CRUD: QuillUserSpec (happy path, duplicates, permissions, login, friends, wallet)
- QuillGameSpec: basic CRUD for games

### Critical Gaps (as of WorkingAgain..HEAD analysis)
1. **JoinGame bug fix** (`events.scala`): joinedUser vs user - ZERO direct tests
2. **userByOAuthProvider** (`QuillRepository`): ZERO tests in any spec
3. **ChutiAuthServer OAuth methods**: createOAuthUser, linkOAuthToUser, userByOAuthProvider - ZERO tests
4. **PreGameServiceSpec**: ALL tests commented out - newGame, joinGame, abandonGame, invite lifecycle
5. **GameService.newGameSameUsers**: bot/human player separation logic - ZERO direct tests
6. **GameService.resumeStuckGames**: - ZERO tests

### Medium Gaps
- QuillUserSpec: missing `userByOAuthProvider` test
- InMemoryRepository: `userByOAuthProvider` returns `???` (NotImplementedError)
- FullGameSpec: all specific game scenario tests are `@@ TestAspect.ignore`
- GameService `doBotsAutoPlay` logic: only exercised indirectly via full game tests

## Testing Conventions
- Fresh game per test: `gameOperations.upsert(game1.copy(id = GameId.empty))`
- TestClock: `ZIO.yieldNow *> TestClock.adjust(1.second)` (NOT `Clock.sleep`)
- Sequential tests: `@@ TestAspect.sequential`
- Bot layer: `GameService.makeWithoutAIBot()` for pure game logic tests

## See Also
- `patterns.md` for detailed gap descriptions and recommended test code
