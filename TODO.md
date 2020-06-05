# TODO 

- DONE. Paint all dominos
- DONE. Finalize wireframe
    - DONE Where does the "table" go?
- DONE. Database
    - DONE mysql with json column 
- DONE. Server
    - decide server technology.
      - DONE Zio, akka-http, caliban, circe
    - 
- DONE. Client
    - DONE decide client technology
        - DONE Scalajs, scalablytyped, websockets, caliban, circe
    - code pages
        - Set up table
        - Game page
        - Cuentas
- User system
    - DONE Registration
    - DONE Login
    - DONE Lost password
    - Set up table
- DONE. Code game engine     
- FUTURE. Chose domino back logo

##Random
- Consistent language
- Translate to English
- Versioning and upgrade in Game object

##Server
- Rehydrate the system after restart
    - Tokens
    - Games
    - Logged in User repo
    - (in client) load last 10 minutes of messages

## Web client
- Clean up GameException errors
- Clean up presentation of really bad errors

### Pregame
DONE - Create new game
DONE - Join random game (first user)
DONE - Join random game (second and third users)
- Join random game (fourth user)
DONE - Abandon unstarted game
- Abandon a started game
DONE - Invite one existing user to game
- Invite non-existing user to game
DONE - Accept game invite
DONE - Reject game invite

### Game
- Main screen setup
- Decide what can be done depending on where you are (look at bot)
- Animations of Borlotes
    - Sopa
    - Poner ficha
    - Hoyo
    - Chuti
    - El hoyo que camina
    - Tecnico

### All these graphql calls should be exercised

```def broadcastGameEvent(gameEvent: GameEvent): ZIO[GameLayer, GameException, GameEvent]```

```def joinRandomGame(): ZIO[GameLayer, GameException, Game]```

```def newGame():        ZIO[GameLayer, GameException, Game]```

```def play(gameId:    GameId,playEvent: PlayEvent):                  ZIO[GameLayer, GameException, Game]```

```def getGameForUser: ZIO[GameLayer, GameException, Option[Game]]```

```def getGame(gameId:     GameId): ZIO[GameLayer, GameException, Option[Game]]```

```def abandonGame(gameId: GameId): ZIO[GameLayer, GameException, Boolean]```

```def getFriends:       ZIO[GameLayer, GameException, Seq[UserId]]```

```def getGameInvites:   ZIO[GameLayer, GameException, Seq[Game]]```

```def getLoggedInUsers: ZIO[GameLayer, GameException, Seq[User]]```

```def inviteToGame(userId: UserId,gameId: GameId): ZIO[GameLayer, GameException, Boolean]```

```def inviteFriend(friend:          User):   ZIO[GameLayer, GameException, Boolean]```

```def acceptGameInvitation(gameId:  GameId): ZIO[GameLayer, GameException, Game]```

```def declineGameInvitation(gameId: GameId): ZIO[GameLayer, GameException, Boolean]```

```def acceptFriendship(friend:      User):   ZIO[GameLayer, GameException, Boolean]```

```def unfriend(enemy:               User):   ZIO[GameLayer, GameException, Boolean]```

```def gameStream(gameId: GameId): ZStream[GameLayer, GameException, GameEvent]```

```def userStream: ZStream[GameLayer, GameException, UserEvent]```

## Admin screen
Games playing, game index, event list
Glimpse into queues

## Bugs
"Juega con quien sea" is not sending out a game event when joining 

## To test
- Invite by email, unhappy paths
- Transfer of ownership when original user abandons game
- Add unique constraint to friends