# TODO 

- DONE. Paint all dominos
- DOSE. Finalize wireframe
    DONE - Where does the "table" go?
- DONE. Database
    - mysql with json column 
- DONE. Server
    - decide server technology.
        - Zio, akka-http, caliban, circe
    - 
- DONE. Client
    - decide client technology
        - Scalajs, scalablytyped, websockets, caliban, circe
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
- Chose back logo

##Random
- Consistent language
- Translate to English
- Versioning and upgrade in game

##Server
- Rehydrate the system after restart
    - Tokens
    - Games
    - Logged in User repo

## Web client
### Pregame
- Create new game
- Join random game (first user)
- Join random game (second and third users)
- Join random game (fourth user)
- Abandon unstarted game
- Abandon a started game
- Invite one existing user to game
- Invite non-existing user to game
- Accept game invite

### Game
- Animations of Borlotes
    - Sopa
    - Poner ficha
    - Hoyo
    - Chuti
    - El hoyo que camina
    - Tecnico

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
