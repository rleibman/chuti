# Coverage Patterns and Recommended Tests

## Pattern: JoinGame Bug Fix Test (CRITICAL)

The bug fix changed `user` -> `joinedUser` in JoinGame.doEvent. The scenario that exposes
the old bug is: player A invites player B, then player B accepts (JoinGame is called with
`user=B, joinedUser=B`). But the original bug would be visible when these differ (e.g. god
applying JoinGame on behalf of another user). No tests currently validate this path.

### Recommended Test (add to a new JoinGameSpec or PreGameServiceSpec):
```scala
test("JoinGame uses joinedUser not user when they differ") {
  for {
    gameOperations <- ZIO.serviceWith[ZIORepository](_.gameOperations)
    // Create game with user1
    freshGame <- gameOperations.upsert(
      Game(GameId.empty, ...).applyEvent(user1, JoinGame(user1, JugadorType.human))._1
    ).provideSomeLayer[ChutiEnvironment](godLayer)
    // god applies JoinGame on behalf of user2 (user != joinedUser)
    (withUser2, event) = freshGame.applyEvent(chuti.god, JoinGame(user2, JugadorType.human))
  } yield assertTrue(
    withUser2.jugadores.exists(_.id == user2.id),  // user2 joined, not god
    !withUser2.jugadores.exists(_.id == chuti.god.id),
    event.asInstanceOf[JoinGame].userId == user2.id  // event attributed to user2
  )
}
```

## Pattern: OAuth Test (HIGH)

No tests exist for the OAuth authentication flow. The InMemoryRepository
`userByOAuthProvider` returns `???` so in-memory tests would need it implemented.
QuillUserSpec (real DB) is the right place for this.

### Recommended Test for QuillUserSpec:
```scala
test("userByOAuthProvider finds user by provider and providerId") {
  (for {
    repo     <- ZIO.serviceWith[ZIORepository](_.userOperations)
    testUser <- testUserZIO
    oauthUser = testUser.copy(
      active = true,
      oauth = Some(OAuthUserData("google", "oauth-provider-id-123", None))
    )
    inserted   <- repo.upsert(oauthUser)
    foundOpt   <- repo.userByOAuthProvider("google", "oauth-provider-id-123")
    notFoundOpt <- repo.userByOAuthProvider("google", "wrong-id")
    wrongProv  <- repo.userByOAuthProvider("github", "oauth-provider-id-123")
  } yield assertTrue(foundOpt.contains(inserted)) &&
    assertTrue(notFoundOpt.isEmpty) &&
    assertTrue(wrongProv.isEmpty)).withClock(fixedClock)
}
```

## Pattern: PreGameServiceSpec Recovery (HIGH)

All game lifecycle tests are commented out. The framework has changed to ZIO Test.
These need to be rewritten following the CantandoSpec pattern (fresh game per test,
`upsert(game.copy(id = GameId.empty))`).

### Key scenarios needed:
1. Creating a new game -> esperandoJugadoresInvitados
2. 1 random user joins -> game stays open
3. 3 random users join -> game starts (requiereSopa)
4. Abandon unstarted game -> no penalty
5. Abandon started game -> penalty, game abandoned
6. Invite 1 person
7. Invite 3, all accept -> game starts
8. Invite 3, one declines -> game stays open

## Pattern: GameService.newGameSameUsers (MEDIUM)

The newGameSameUsers method separates human vs bot players (new in this diff).
No test validates: (a) human players get invitations, (b) bots join directly,
(c) game starts immediately when only bots remain.

## Pattern: FullGameSpec @@ TestAspect.ignore (MEDIUM)

5 scenario-specific games are defined but all marked `@@ TestAspect.ignore`.
These should be un-ignored once their expected behavior is stable.
The "Play a bunch of games" stress test is also ignored.
