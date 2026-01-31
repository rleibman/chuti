# Bugs
DONE - I'm playing with bots only, and at the end of the game it seems like it's trying to update the wallet (games against bots don't use wallet). On top of that it gets the error:
  Execution Error: GameError: (conn=26710) Cannot add or update a child row: a foreign key constraint fails (`chuti`.`userWallet`, CONSTRAINT `wallet_user_1` FOREIGN KEY (`userId`) REFERENCES `user` (`id`)) dao.RepositoryError: (conn=26710) Cannot add or update a child row: a foreign key constraint fails (`chuti`.`userWallet`, CONSTRAINT `wallet_user_1` FOREIGN KEY (`userId`) REFERENCES `user` (`id`))
  dao.RepositoryError: (conn=26710) Cannot add or update a child row: a foreign key constraint fails (`chuti`.`userWallet`, CONSTRAINT `wallet_user_1` FOREIGN KEY (`userId`) REFERENCES `user` (`id`))
  at dao.RepositoryError$.apply(RepositoryError.scala:26)
  at dao.quill.QuillRepository.dao$quill$QuillRepository$$anon$1$$_$getWallet$$anonfun$8(QuillRepository.scala:447)
  at zio.Cause$Fail.map(Cause.scala:998)
  at zio.ZIO.mapError$$anonfun$1(ZIO.scala:1003)
  at zio.ZIO.mapErrorCause$$anonfun$1(ZIO.scala:1015)
  at zio.internal.FiberRuntime.runLoop(FiberRuntime.scala:1235)
  at zio.internal.FiberRuntime.runLoop(FiberRuntime.scala:1195)
  at zio.internal.FiberRuntime.runLoop(FiberRuntime.scala:1311)

DONE - In GameService:
DONE - After starting a game, the game should immediately allow bots to play. 
DONE - After anyone (bot or human) plays, the game shoud allow bots to play.

- When I get to the lobby I see: 
- Initializing LobbyComponent
  installHook.js:1 Decoding Error: Expected an object
  overrideMethod @ installHook.js:1
  $p_jl_JSConsoleBasedPrintStream__doWriteLine__T__V @ System.scala:381
  (anonymous) @ System.scala:355
  (anonymous) @ System.scala:340
  printStackTrace__Ljava_io_PrintStream__V @ Throwables.scala:79
  (anonymous) @ Throwables.scala:76
  $p_s_concurrent_impl_Promise$Transformation__handleFailure__jl_Throwable__s_concurrent_ExecutionContext__V @ Promise.scala:485
  (anonymous) @ Promise.scala:542
  (anonymous) @ QueueExecutionContext.scala:52

- Abandon juego causes this error:
  Execution Error: GameError: update players Some(User(Some(1),roberto+aoeu@leibman.net,Roberto Leibman,2026-01-27T18:53:16Z,2026-01-27T18:53:30Z,true,false,false,es)) Not authorized dao.RepositoryPermissionError: update players Some(User(Some(1),roberto+aoeu@leibman.net,Roberto Leibman,2026-01-27T18:53:16Z,2026-01-27T18:53:30Z,true,false,false,es)) Not authorized
  dao.RepositoryPermissionError: update players Some(User(Some(1),roberto+aoeu@leibman.net,Roberto Leibman,2026-01-27T18:53:16Z,2026-01-27T18:53:30Z,true,false,false,es)) Not authorized
  at dao.RepositoryPermissionError$.apply(RepositoryError.scala:44)
  at dao.quill.QuillRepository.assertAuth$$anonfun$1$$anonfun$1(QuillRepository.scala:164)

DONE - (After fixing above) Abandona juego is not clearing the page correctly.
DONE - _ <- ZIO.logDebug("Game started"), is not showingv in the logs.
DONE - Drop down for "cuantas cantas" is not working.
BETTER - Sounds don't always play.
- Starting a game should automatically bring the user to the game page.
BETTER - Entrar al juego should be removed from the menu if you're already in the game.

# Enhancements
DONE - Make sure the domino images are cached by the browser so they load instantly and you don't have to get them again.
- Figure out how to make translations work. Right now everything is in spanish, we've put in a lot of `TODO i8n` throughut. Look at https://taig.github.io/babel/ and see if we can use that library
INPROG - Cuentas: aside from a menu item, it should also be a hover on an image in the center console.
