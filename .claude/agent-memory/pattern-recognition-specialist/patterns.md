# Chuti Project - Detailed Pattern Notes

## Server Architecture Patterns

### ZLayer Composition
- QuillRepository is built as `(ConfigurationService ++ FlywayMigration) >>> QuillRepository.uncached >>> ZIORepository.cached`
- CachedRepository is a Decorator pattern wrapping ZIORepository with ZIO Cache
- GameService.make() takes `Option[ClaudeBot]` to optionally inject AI bot — clean optional dependency

### GameService Internal Structure
- Anonymous class instantiated inside ZLayer.fromZIO — all state (gameEventQueues, userEventQueues Refs) is captured in closure
- `friend`/`unfriend` are static methods on the *companion object* (not the trait), leaking user social concerns into the game module
- `testRedoEvent` at line 688 is dead debug scaffolding — public method, but only called internally, throws exceptions

### checkPlayTransition (GameService ~line 624)
- Borlote detection logic (SantaClaus, ElNiñoDelCumpleaños, Helecho) is embedded directly in GameService
- This is business logic that arguably belongs in the domain model or a dedicated borlote rules evaluator

### doBotsAutoPlay (GameService ~line 223)
- Recursive function for chaining bot moves
- getNextPlayer() inner function correctly handles all GameStatus variants
- Guard against infinite recursion: checks if currentEventIndex changed

## Frontend Architecture Patterns

### ChutiState as God Object
ChutiState is a case class combining:
- Display state (gameViewMode, currentDialog, muted, isMobile, chatSidebarOpen)
- Domain data (user, wallet, gameInProgress, friends, loggedInUsers)
- Callback functions wired up by Content.Backend (flipFicha, modGameInProgress, onRequestGameRefresh, etc.)
- UI configuration (languageTag, locale)
- Localization bundle (inner object ChutiMessages)

This is the project's chosen React Context architecture. The localization bundle (ChutiMessages) embedded as an inner object is unusual.

### Content.scala as Application Shell
Content.scala is the bootstrap component responsible for:
- Initial state load (refresh(initial=true))
- Window resize listener (registered in componentDidMount, cleaned in componentWillUnmount)
- WebSocket stream lifecycle (gameStream, userStream)
- Audio queue management
- Event routing from GameEvents -> state updates

The resize listener uses a mutable var `resizeListener: Option[js.Function1[Event, Unit]]` stored on Backend — this is correct for Scala.js lifecycle management but is a mutable escape hatch.

### Audio Management in Content.scala
- `audioQueue: mutable.Queue[String]`, `audioCache: mutable.Map[String, Audio]`, `currentlyPlayingAudio: Option[Audio]` — all mutable state on Backend
- Queue size capped at 4 — defensive, but the cap is checked in two places (playSound and getOrCreateAudio.onended) with slightly different behavior
- `playNextSound()` is a Unit function calling `audio.play()` whose Promise is discarded (correct for Scala.js browser audio)

### AppRouter Layout Split
- Desktop: header + main content + inline chat component
- Mobile: header + Sidebar.Pushable wrapping Sidebar (overlay) + Sidebar.Pusher (main content)
- DialogRenderer nested inside AppRouter handles modal dialogs (cuentas, celebration)
- DialogRenderer uses `ScalaComponent.builder[Unit]("content")` — component name "content" collides with Content component

## Repository Layer Patterns

### Auth Pattern in QuillRepository
- `assertAuth()` is a private helper that accepts a predicate and error function
- Consistent pattern used across get/upsert/delete, but NOT used in search/count — inconsistency
- godSession private val in QuillRepository shadows GameService.godLayer (two separate instances)

### Quill Inline SQL Fallbacks
Several operations use raw SQL strings where Quill's type-safe DSL couldn't express the query:
- `login` (SHA2 hashing)
- `changePassword` (SHA2 hashing)
- `unfriend` (bidirectional friendship delete)
- `delete` hard-deletes

These are acceptable given Quill's limitations but represent areas of reduced type safety.

### Token Cleanup Bug
In QuillRepository.tokenOperations.cleanup (line 719):
```
sql"DELETE FROM token WHERE expireTime >= ${lift(now)}"
```
This deletes tokens that have NOT yet expired (expireTime >= now means still valid).
Should be `expireTime <= now` (expired tokens have a past expireTime).

## Cross-Cutting Observations

### Error Domain Translation
Three error domains: RepositoryError, GameError, AuthError
- GameService wraps everything with `.mapError(GameError.apply)` at 14+ call sites
- ChutiAuthServer wraps with `.mapError(AuthError(_))` at 10+ call sites
- RepositoryError is used as a convenient intermediate error then re-wrapped
- This is intentional but verbose; a `wrapAsGameError` extension on ZIO would DRY this up

### Logging: println vs ZIO.log*
Server-side: QuillRepository and GameService still use println (debug artifacts)
Frontend: println used throughout (acceptable in Scala.js, but inconsistent — some use Callback.log)
