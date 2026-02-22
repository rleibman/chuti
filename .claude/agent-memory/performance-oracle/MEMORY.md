# Performance Oracle - Chuti Project Memory

## Architecture Notes
- Game state is stored as a JSON blob (`lastSnapshot`) in the `game` table — every `upsert` serializes the entire `Game` object to JSON. This is the dominant I/O pattern.
- `GameRow.toGame` deserializes JSON on every DB fetch — there is no server-side game cache (a `ZIO Cache` was commented out).
- `updateAccounting` runs sequential `getWallet` + `updateWallet` per player (N+1 pattern on game end).
- `resumeStuckGames` fetches ALL non-deleted games via `gameOperations.search(None)`, then filters in memory — no status filter at DB level.
- Token cleanup SQL has an inverted comparison bug: `expireTime >= now` deletes tokens that have NOT expired yet.

## Hot Paths
- `playInternal` / `play` — called on every human move. Fetches game from DB, applies event, saves, broadcasts. Performance-sensitive.
- `broadcast` — called after every game event; iterates all `List[EventQueue]` in parallel. List grows as connections accumulate.
- `gameStream` / `userStream` — WebSocket streams; each connection adds an entry to the shared `Ref[List[EventQueue]]`.

## Frontend Notes
- `handleResize` in `Content.scala` fires on every `resize` event with no debouncing, triggering `$.modState` on each pixel change.
- `onUserStreamData` rebuilds `loggedInUsers` list on every Connected/Modified/Disconnected event with `.filter(...) :+ user` — O(n) per event.
- `audioCache` uses mutable `Map` — unbounded growth (one `Audio` object per unique URL, acceptable for the small fixed set of game sounds).
- `layout` in `AppRouter.scala` is a plain function that re-executes on every render — no memoization of `channelId` or `chatComponent`.

## Known Issues Filed This Session
- See `performance-review-2026-02.md` for full findings.
