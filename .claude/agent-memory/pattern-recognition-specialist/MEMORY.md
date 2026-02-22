# Pattern Recognition Specialist - Chuti Project Memory

## Codebase Architecture Summary
- ZIO 2.x server (ZLayer composition), Quill for DB, Caliban for GraphQL
- scalajs-react frontend with ChutiState as a React Context (data + callbacks)
- ChutiSession is the auth context threaded through the ZIO effect stack
- Repository pattern: ZIORepository trait -> QuillRepository (real) / InMemoryRepository (tests)
- CachedRepository decorator wraps ZIORepository with a ZIO Cache for games
- GameService is the primary service; `friend`/`unfriend` live as static methods on its companion object (SoC smell)

## Recurring Patterns

### God Session Injection
`ChutiSession.godSession.toLayer` / `.provideLayer(godLayer)` appears ~10 times in ChutiAuthServer alone and throughout GameService. This is a recognized and accepted pattern for bypassing normal auth checks when acting on behalf of the system, but is repeated rather than abstracted.

### mapError wrapping
`mapError(GameError.apply)` appears 14+ times throughout GameService and `mapError(AuthError(_))` throughout ChutiAuthServer. This is intentional error-domain translation but could benefit from a helper extension.

### ChutiState as Props Object Anti-Pattern
ChutiState holds both data AND callback functions (flipFicha, modGameInProgress, onSessionChanged, toggleSound, etc.). This is the established project pattern for threading state+actions through the React context.

### Tuple Indexing (_1, _2, _3)
`afterApply._1`, `afterApply._2`, `afterApply._3` used in GameService.joinGame. Established project smell.

## Known Code Smells (confirmed across multiple reviews)
- `throw` in ZIO context in newGameSameUsers (lines 331, 354, 370) and declineGameInvitation (574)
- `println` in production server code: QuillRepository line 54, GameService lines 703-704
- `println` in frontend: ChutiState line 62, Content.scala lines 230, 330, 340, 366, 382
- Token cleanup SQL has inverted comparison bug: `expireTime >= now` deletes non-expired tokens (should be `<=`)
- Large `ChutiAuthServer` inline HTML email templates — not extracted to template files
- `testRedoEvent` in GameService is debug scaffolding never removed
- `friend`/`unfriend` on GameService companion object violates SRP (user social features in game service)

## Mobile/Responsive Pattern (added recently)
- `isMobile: Boolean`, `chatSidebarOpen: Boolean`, `toggleChatSidebar: Callback` added to ChutiState
- Detection via `window.innerWidth <= 768` in Content.scala with window resize listener
- AppRouter uses isMobile to switch between desktop (inline chat) and mobile (Sidebar overlay) layouts
- Initial state set on component build; transitions triggered via `handleResize()` in Backend
- Pattern is correct but `MobileBreakpoint = 768` is a magic number with no semantic name

## Linked topic files
- See `patterns.md` for detailed structural notes
