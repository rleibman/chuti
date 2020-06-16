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
- "Juega con quien sea" is not sending out a game event when joining 
- Last person joining is causing huge issues.
- onComponentUnmount... close down ws sessions 

## To test
- Invite by email, unhappy paths
- Transfer of ownership when original user abandons game
- Add unique constraint to friends

## Other
- Remove all "Repository with DatabaseProvider", Repository should stand on it's own 

#Empiezo
update game set current_index = 17, status='cantando', lastSnapshot = '{"id": {"value": 55}, "created": "2020-06-06T11:38:42.947365", "enJuego": [], "triunfo": null, "jugadores": [{"mano": false, "user": {"id": {"value": 1}, "name": "Roberto", "email": "roberto@leibman.net", "active": true, "created": "2020-04-09T00:28:28", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-04-09T00:28:28", "lastLoggedIn": "2020-05-22T15:36:12"}, "filas": [], "turno": false, "cuenta": [], "fichas": [{"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 1}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 3}}], "invited": false, "cantante": false, "cuantasCantas": null, "statusString": ""}, {"mano": true, "user": {"id": {"value": 39}, "name": "test1", "email": "roberto+test1@leibman.net", "active": true, "created": "2020-05-25T11:01:29", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-05-25T11:01:29", "lastLoggedIn": null}, "filas": [], "turno": true, "cuenta": [], "fichas": [{"type": "conocida", "abajo": {"value": 6}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 3}}], "invited": false, "cantante": true, "cuantasCantas": null, "statusString": ""}, {"mano": false, "user": {"id": {"value": 47}, "name": "aoeuaoeu", "email": "roberto+aoeuaoeu@leibman.net", "active": true, "created": "2020-06-04T09:15:58", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-06-04T09:15:58", "lastLoggedIn": null}, "filas": [], "turno": false, "cuenta": [], "fichas": [{"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 6}}], "invited": false, "cantante": false, "cuantasCantas": null, "statusString": ""}, {"mano": false, "user": {"id": {"value": 46}, "name": "aoeu", "email": "roberto+aoeu@leibman.net", "active": true, "created": "2020-06-04T09:12:10", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-06-04T09:12:10", "lastLoggedIn": null}, "filas": [], "turno": false, "cuenta": [], "fichas": [{"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 1}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 0}}], "invited": false, "cantante": false, "cuantasCantas": null, "statusString":""}], "gameStatus": {"cantando": {}}, "estrictaDerecha": false, "satoshiPerPoint": 100, "statusString": "", "currentEventIndex": 17}'
#Despues de cantar, listo para empezar
update game set current_index = 21, status='jugando',lastSnapshot = '{"id": {"value": 55}, "created": "2020-06-06T11:38:42.947365", "enJuego": [], "triunfo": null, "jugadores": [{"mano": false, "user": {"id": {"value": 1}, "name": "Roberto", "email": "roberto@leibman.net", "active": true, "created": "2020-04-09T00:28:28", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-04-09T00:28:28", "lastLoggedIn": "2020-05-22T15:36:12"}, "filas": [], "turno": false, "cuenta": [], "fichas": [{"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 1}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 3}}], "invited": false, "cantante": false, "cuantasCantas": null}, {"mano": true, "user": {"id": {"value": 39}, "name": "test1", "email": "roberto+test1@leibman.net", "active": true, "created": "2020-05-25T11:01:29", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-05-25T11:01:29", "lastLoggedIn": null}, "filas": [], "turno": true, "cuenta": [], "fichas": [{"type": "conocida", "abajo": {"value": 6}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 3}}], "invited": false, "cantante": true, "cuantasCantas": {"Casa": {}}}, {"mano": false, "user": {"id": {"value": 47}, "name": "aoeuaoeu", "email": "roberto+aoeuaoeu@leibman.net", "active": true, "created": "2020-06-04T09:15:58", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-06-04T09:15:58", "lastLoggedIn": null}, "filas": [], "turno": false, "cuenta": [], "fichas": [{"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 6}}], "invited": false, "cantante": false, "cuantasCantas": null}, {"mano": false, "user": {"id": {"value": 46}, "name": "aoeu", "email": "roberto+aoeu@leibman.net", "active": true, "created": "2020-06-04T09:12:10", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-06-04T09:12:10", "lastLoggedIn": null}, "filas": [], "turno": false, "cuenta": [], "fichas": [{"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 1}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 0}}], "invited": false, "cantante": false, "cuantasCantas": null}], "gameStatus": {"jugando": {}}, "estrictaDerecha": false, "satoshiPerPoint": 100, "currentEventIndex": 21}'