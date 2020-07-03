# TODO 

- DONE. Paint all dominos
- DONE. Finalize wireframe
    - DONE Where does the "table" go?
- DONE. Database
    - DONE mysql with json column 
- DONE. Server
    DONE - decide server technology.
      - DONE Zio, akka-http, caliban, circe
    - 
- DONE. Client
    - DONE decide client technology
        - DONE Scalajs, scalablytyped, websockets, caliban, circe
    - DONE code pages
        - DONE Set up table
        - DONE Game page
        - DONE Cuentas
- User system
    - DONE Registration
    - DONE Login
    - DONE Lost password
    - DONE Set up table
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
DONE - Join random game (fourth user)
DONE - Abandon unstarted game
- Abandon a started game
DONE - Invite one existing user to game
DONE - Invite non-existing user to game
DONE - Accept game invite
DONE - Reject game invite

### Lobby
- Mark users who are invited or playing in our game already
- On last accept the screen ends up in "No es el momento de esperandoJugadoresInvitados", como parte e RedoEvent.
    This happens because joining is not an event, so the system doesn't know we're ready to play

### Game
DONE - Main screen setup
DONE - Decide what can be done depending on where you are (look at bot)
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
DONE - Aplicando #C, ya esta listo para caerse pero el juego no lo detecta correctamente.
DONE - Aplicando #C, me encuentro en una posicion en la que dos personas pueden pedir... porque?
- Double pressing of buttons is messing things up!

## To test
- Invite by email, unhappy paths
- Transfer of ownership when original user abandons game
- Add unique constraint to friends

## Other
DONE - Remove all "Repository with DatabaseProvider", Repository should stand on it's own 

#Interesting games
##Por alguna razon se la deberia llevar test1, pero se la lleva aoeu
update game set current_index = 108, lastSnapshot = '{"id": {"value": 62}, "created": "2020-06-19T11:27:58.450679", "enJuego": [[{"value": 47}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 1}}], [{"value": 39}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 4}}], [{"value": 1}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 3}}]], "triunfo": {"TriunfoNumero": {"num": {"value": 2}}}, "jugadores": [{"mano": false, "user": {"id": {"value": 1}, "name": "Roberto", "email": "roberto@leibman.net", "active": true, "created": "2020-04-09T00:28:28", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-04-09T00:28:28", "lastLoggedIn": "2020-05-22T15:36:12"}, "filas": [], "turno": false, "cuenta": [{"esHoyo": true, "puntos": 0}, {"esHoyo": false, "puntos": 2}, {"esHoyo": false, "puntos": 5}], "fichas": [{"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 0}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 5}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null}, {"mano": false, "user": {"id": {"value": 39}, "name": "test1", "email": "roberto+test1@leibman.net", "active": true, "created": "2020-05-25T11:01:29", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-05-25T11:01:29", "lastLoggedIn": null}, "filas": [], "turno": false, "cuenta": [{"esHoyo": true, "puntos": 0}, {"esHoyo": false, "puntos": 6}], "fichas": [{"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 6}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 5}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null}, {"mano": true, "user": {"id": {"value": 47}, "name": "aoeuaoeu", "email": "roberto+aoeuaoeu@leibman.net", "active": true, "created": "2020-06-04T09:15:58", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-06-04T09:15:58", "lastLoggedIn": null}, "filas": [{"index": 0, "fichas": [{"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 6}}]}], "turno": true, "cuenta": [{"esHoyo": false, "puntos": 2}, {"esHoyo": false, "puntos": 1}], "fichas": [{"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 6}}], "invited": false, "cantante": true, "statusString": "", "cuantasCantas": {"Casa": {}}}, {"mano": false, "user": {"id": {"value": 46}, "name": "aoeu", "email": "roberto+aoeu@leibman.net", "active": true, "created": "2020-06-04T09:12:10", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-06-04T09:12:10", "lastLoggedIn": null}, "filas": [], "turno": false, "cuenta": [{"esHoyo": false, "puntos": 5}], "fichas": [{"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 1}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 5}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null}], "gameStatus": {"jugando": {}}, "statusString": "aoeuaoeu se llevo la ultima fila", "estrictaDerecha": false, "satoshiPerPoint": 100, "currentEventIndex": 108}';
##Tanto test1 como aoeuaoeu pueden pedir, como esta eso?
{"id": {"value": 62}, "created": "2020-06-19T11:27:58.450679", "enJuego": [], "triunfo": {"TriunfoNumero": {"num": {"value": 2}}}, "jugadores": [{"mano": false, "user": {"id": {"value": 1}, "name": "Roberto", "email": "roberto@leibman.net", "active": true, "created": "2020-04-09T00:28:28", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-04-09T00:28:28", "lastLoggedIn": "2020-05-22T15:36:12"}, "filas": [], "turno": false, "cuenta": [{"esHoyo": true, "puntos": 0}, {"esHoyo": false, "puntos": 2}, {"esHoyo": false, "puntos": 5}], "fichas": [{"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 0}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 5}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null}, {"mano": true, "user": {"id": {"value": 39}, "name": "test1", "email": "roberto+test1@leibman.net", "active": true, "created": "2020-05-25T11:01:29", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-05-25T11:01:29", "lastLoggedIn": null}, "filas": [{"index": 1, "fichas": [{"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 1}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 1}}]}, {"index": 2, "fichas": [{"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 3}}]}, {"index": 3, "fichas": [{"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 6}}]}], "turno": false, "cuenta": [{"esHoyo": true, "puntos": 0}, {"esHoyo": false, "puntos": 6}], "fichas": [{"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 6}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 6}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null}, {"mano": true, "user": {"id": {"value": 47}, "name": "aoeuaoeu", "email": "roberto+aoeuaoeu@leibman.net", "active": true, "created": "2020-06-04T09:15:58", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-06-04T09:15:58", "lastLoggedIn": null}, "filas": [{"index": 0, "fichas": [{"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 6}}]}], "turno": true, "cuenta": [{"esHoyo": false, "puntos": 2}, {"esHoyo": false, "puntos": 1}], "fichas": [{"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 6}}], "invited": false, "cantante": true, "statusString": "", "cuantasCantas": {"Casa": {}}}, {"mano": false, "user": {"id": {"value": 46}, "name": "aoeu", "email": "roberto+aoeu@leibman.net", "active": true, "created": "2020-06-04T09:12:10", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-06-04T09:12:10", "lastLoggedIn": null}, "filas": [], "turno": false, "cuenta": [{"esHoyo": false, "puntos": 5}], "fichas": [{"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 5}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null}]
##Test1 can win
{"id": {"value": 62}, "created": "2020-06-19T11:27:58.450679", "enJuego": [], "triunfo": null, "jugadores": [{"mano": false, "user": {"id": {"value": 1}, "name": "Roberto", "email": "roberto@leibman.net", "active": true, "created": "2020-04-09T00:28:28", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-04-09T00:28:28", "lastLoggedIn": "2020-05-22T15:36:12"}, "filas": [], "turno": true, "cuenta": [{"esHoyo": true, "puntos": 0}, {"esHoyo": false, "puntos": 2}, {"esHoyo": false, "puntos": 5}, {"esHoyo": true, "puntos": -4}, {"esHoyo": false, "puntos": 1}], "fichas": [{"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 1}}, {"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 1}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 0}}], "invited": false, "cantante": true, "statusString": "", "cuantasCantas": {"Casa": {}}}, {"mano": true, "user": {"id": {"value": 39}, "name": "test1", "email": "roberto+test1@leibman.net", "active": true, "created": "2020-05-25T11:01:29", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-05-25T11:01:29", "lastLoggedIn": null}, "filas": [], "turno": false, "cuenta": [{"esHoyo": true, "puntos": 0}, {"esHoyo": false, "puntos": 6}, {"esHoyo": false, "puntos": 4}, {"esHoyo": false, "puntos": 4}, {"esHoyo": false, "puntos": 2}], "fichas": [{"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 6}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 6}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null}, {"mano": false, "user": {"id": {"value": 47}, "name": "aoeuaoeu", "email": "roberto+aoeuaoeu@leibman.net", "active": true, "created": "2020-06-04T09:15:58", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-06-04T09:15:58", "lastLoggedIn": null}, "filas": [], "turno": false, "cuenta": [{"esHoyo": false, "puntos": 2}, {"esHoyo": false, "puntos": 1}, {"esHoyo": true, "puntos": -4}, {"esHoyo": false, "puntos": 3}, {"esHoyo": false, "puntos": 4}], "fichas": [{"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 2}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null}, {"mano": false, "user": {"id": {"value": 46}, "name": "aoeu", "email": "roberto+aoeu@leibman.net", "active": true, "created": "2020-06-04T09:12:10", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-06-04T09:12:10", "lastLoggedIn": null}, "filas": [], "turno": false, "cuenta": [{"esHoyo": false, "puntos": 5}, {"esHoyo": true, "puntos": 0}, {"esHoyo": true, "puntos": -4}], "fichas": [{"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 5}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null}]
### And about to win!
update game set current_index = 166, status='jugando', lastSnapshot = '{"id": {"value": 62}, "created": "2020-06-19T11:27:58.450679", "enJuego": [], "triunfo": {"TriunfoNumero": {"num": {"value": 3}}}, "jugadores": [{"mano": false, "user": {"id": {"value": 1}, "name": "Roberto", "email": "roberto@leibman.net", "active": true, "created": "2020-04-09T00:28:28", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-04-09T00:28:28", "lastLoggedIn": "2020-05-22T15:36:12"}, "filas": [], "turno": true, "cuenta": [{"esHoyo": true, "puntos": 0}, {"esHoyo": false, "puntos": 2}, {"esHoyo": false, "puntos": 5}, {"esHoyo": true, "puntos": -4}, {"esHoyo": false, "puntos": 1}], "fichas": [{"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 1}}, {"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 0}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null}, {"mano": true, "user": {"id": {"value": 39}, "name": "test1", "email": "roberto+test1@leibman.net", "active": true, "created": "2020-05-25T11:01:29", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-05-25T11:01:29", "lastLoggedIn": null}, "filas": [{"index": 0, "fichas": [{"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 1}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 5}}]}], "turno": false, "cuenta": [{"esHoyo": true, "puntos": 0}, {"esHoyo": false, "puntos": 6}, {"esHoyo": false, "puntos": 4}, {"esHoyo": false, "puntos": 4}, {"esHoyo": false, "puntos": 2}], "fichas": [{"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 6}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 6}}], "invited": false, "cantante": true, "statusString": "", "cuantasCantas": {"Canto5": {}}}, {"mano": false, "user": {"id": {"value": 47}, "name": "aoeuaoeu", "email": "roberto+aoeuaoeu@leibman.net", "active": true, "created": "2020-06-04T09:15:58", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-06-04T09:15:58", "lastLoggedIn": null}, "filas": [], "turno": false, "cuenta": [{"esHoyo": false, "puntos": 2}, {"esHoyo": false, "puntos": 1}, {"esHoyo": true, "puntos": -4}, {"esHoyo": false, "puntos": 3}, {"esHoyo": false, "puntos": 4}], "fichas": [{"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 2}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null}, {"mano": false, "user": {"id": {"value": 46}, "name": "aoeu", "email": "roberto+aoeu@leibman.net", "active": true, "created": "2020-06-04T09:12:10", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-06-04T09:12:10", "lastLoggedIn": null}, "filas": [], "turno": false, "cuenta": [{"esHoyo": false, "puntos": 5}, {"esHoyo": true, "puntos": 0}, {"esHoyo": true, "puntos": -4}], "fichas": [{"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 6}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null}], "gameStatus": {"jugando": {}}, "statusString": "test1 gano la ultima mano", "estrictaDerecha": false, "satoshiPerPoint": 100, "currentEventIndex": 233}'
where id = 62;

#Tanto test1 como aoeuaoeu pueden pedir, como esta eso?
update game set current_index = 166, status='jugando', lastSnapshot = '{"id": {"value": 62}, "created": "2020-06-19T11:27:58.450679", "enJuego": [], "triunfo": {"TriunfoNumero": {"num": {"value": 4}}}, "jugadores": [{"mano": false, "user": {"id": {"value": 1}, "name": "Roberto", "email": "roberto@leibman.net", "active": true, "created": "2020-04-09T00:28:28", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-04-09T00:28:28", "lastLoggedIn": "2020-05-22T15:36:12"}, "filas": [], "turno": true, "cuenta": [{"esHoyo": true, "puntos": 0}, {"esHoyo": false, "puntos": 2}, {"esHoyo": false, "puntos": 5}], "fichas": [{"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 0}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 4}}], "invited": false, "cantante": true, "statusString": "", "cuantasCantas": {"Casa": {}}}, {"mano": false, "user": {"id": {"value": 39}, "name": "test1", "email": "roberto+test1@leibman.net", "active": true, "created": "2020-05-25T11:01:29", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-05-25T11:01:29", "lastLoggedIn": null}, "filas": [], "turno": false, "cuenta": [{"esHoyo": true, "puntos": 0}, {"esHoyo": false, "puntos": 6}, {"esHoyo": false, "puntos": 4}], "fichas": [{"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 1}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 6}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null}, {"mano": true, "user": {"id": {"value": 47}, "name": "aoeuaoeu", "email": "roberto+aoeuaoeu@leibman.net", "active": true, "created": "2020-06-04T09:15:58", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-06-04T09:15:58", "lastLoggedIn": null}, "filas": [{"index": 0, "fichas": [{"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 3}}]}], "turno": false, "cuenta": [{"esHoyo": false, "puntos": 2}, {"esHoyo": false, "puntos": 1}, {"esHoyo": true, "puntos": -4}], "fichas": [{"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 6}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null}, {"mano": false, "user": {"id": {"value": 46}, "name": "aoeu", "email": "roberto+aoeu@leibman.net", "active": true, "created": "2020-06-04T09:12:10", "deleted": false, "isAdmin": false, "userStatus": {"Playing": {}}, "lastUpdated": "2020-06-04T09:12:10", "lastLoggedIn": null}, "filas": [], "turno": false, "cuenta": [{"esHoyo": false, "puntos": 5}, {"esHoyo": true, "puntos": 0}], "fichas": [{"type": "conocida", "abajo": {"value": 6}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 1}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 2}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null}], "gameStatus": {"jugando": {}}, "statusString": "aoeuaoeu se llevo la ultima fila", "estrictaDerecha": false, "satoshiPerPoint": 100, "currentEventIndex": 166}'
where id = 62;

#CSS
2 filas a la derecha o izquierda look weird.

#Partido terminado:
DONE - Poner un boton en el centro: "Regresar al Lobby"
DONE - Quitar "Abandona juego"
- Poner un boton en el lobby: "Empezar nuevo partido con los mismos jugadores"

#Agregar al lobby 
Cuentas personales

