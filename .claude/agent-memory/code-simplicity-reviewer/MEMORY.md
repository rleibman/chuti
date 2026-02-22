# Code Simplicity Reviewer - Chuti Project Memory

## Project Conventions
- Scala 3 with `-no-indent` and `-old-syntax` (braces required everywhere)
- ZIO 2.x effects; use for-comprehensions over nested flatMaps
- scalajs-react with Props/State/Backend pattern
- Quill for DB; ChutiSession is the ambient auth context
- Opaque types for IDs (UserId, GameId) — never suggest removing
- Game domain in Spanish (jugador, ficha, etc.) — respect naming

## Key Patterns Found
- **`assertAuth` helper** in QuillRepository used consistently for auth checks
- **`provideSomeLayer[ChutiSession](dataSourceLayer)`** pattern repeated on nearly every Quill query
- **godSession** injected inline using `.provideLayer(godLayer)` when elevated access needed
- **Celebration show/hide pattern** in Content.scala: `modState` to show + `timers.setTimeout(3000)` to auto-dismiss — repeated 3x verbatim

## Known Duplication Hotspots
- `Content.scala`: The celebration overlay show/dismiss pattern (lines ~100-130, ~175-205) is copy-pasted for `TerminaJuego` and `MeRindo`. Extract as `showTimedCelebration(data: CelebrationData): Callback`.
- `InviteToGame` event (events.scala line 348-350): two redundant existence checks for the same condition.
- `AppRouter.scala` `layout()`: `channelId` and `chatComponent` are defined as inner `def`s instead of extracted methods — minor.

## Code Style Notes
- `UserEventType` in `user.scala` defines both `derives JsonCodec` AND manual encoder/decoder in companion — the manual ones override the derived ones. The `derives JsonCodec` on the enum is dead.
- `OAuthUserData.dataAsJson` and `withJsonData` helpers are clean and appropriate.
- Debug `println` statements remain in production code in `events.scala` (Canta, Pide, Da, TerminaJuego) — these should become ZIO logging.

## Complexity Areas
- `events.scala Canta.doEvent` (~80 lines, high cyclomatic complexity)
- `events.scala Da.doEvent` (~70 lines)
- `events.scala Caete.doEvent` (~80 lines with recursive `regaloLoop`)
- `GameService.scala doBotsAutoPlay` (~90 lines) — well-structured but long
- `Content.scala refresh()` (~80 lines) — complex but justified by init logic

## Frontend Mobile Pattern (new, Feb 2026)
- `isMobile`, `chatSidebarOpen`, `toggleChatSidebar` added to `ChutiState`
- `MobileBreakpoint = 768` constant in `Content.scala`
- `handleResize()` manages transition state (mobile<->desktop auto-opens/closes chat)
- `AppRouter.layout()` branches on `chutiState.isMobile` for desktop vs. Sidebar layout
- resize listener lifecycle: stored in `var resizeListener`, added in `componentDidMount`, removed in `componentWillUnmount`

## See Also
- `patterns.md` — detailed pattern notes
