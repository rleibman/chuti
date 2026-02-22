# Performance Review — Feb 2026

## Files Reviewed
1. server/src/main/scala/chuti/db/quill/QuillRepository.scala
2. server/src/main/scala/chuti/game/GameService.scala
3. server/src/main/scala/chuti/api/ChutiAuthServer.scala
4. web/src/main/scala/chuti/Content.scala
5. web/src/main/scala/router/AppRouter.scala

## Critical Findings
- Token cleanup SQL inverted: `expireTime >= now` deletes valid tokens
- `resumeStuckGames` loads all games, filters in Scala — table scan
- `updateAccounting` sequential DB round-trips per player (N+1)

## Warning Findings
- Commented-out game cache in GameService.make()
- handleResize fires without debounce on every pixel
- `search(None)` in gameOperations returns unbounded result set
- `loggedInUsers` O(n) filter+append on every user event

## Info Findings
- `testRedoEvent` defined but never called (dead code with serialization cost)
- Dead code: commented `gamesWithInfo` ZIO Cache
- `layout` function recreates `channelId`/`chatComponent` on every render
- `broadcast` uses `List` (O(n) traversal); acceptable at small scale
