# TODO 

- DONE. Paint all dominos
- DONE. Finalize wireframe
    - DONE Where does the "table" go?
- DONE. Database
    - DONE mysql with json column 
- DONE. Server
    DONE - decide server technology.
      - DONE Zio, akka-http, caliban, circe
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

##Random
DONE - Consistent language

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
DONE - Mark users who are invited or playing in our game already
DONE - On last accept the screen ends up in "No es el momento de esperandoJugadoresInvitados", como parte e RedoEvent.
    This happens because joining is not an event, so the system doesn't know we're ready to play
DONE - Cuentas personales

### Game
DONE - Main screen setup
DONE - Decide what can be done depending on where you are (look at bot)

## Bugs
- "Juega con quien sea" is not sending out a game event when joining 
DONE - Last person joining is causing huge issues.
DONE - onComponentUnmount... close down ws sessions 
DONE - Aplicando #C, ya esta listo para caerse pero el juego no lo detecta correctamente.
DONE - Aplicando #C, me encuentro en una posicion en la que dos personas pueden pedir... porque?
DONE - 2 filas a la derecha o izquierda look weird.
- Double pressing of buttons is messing things up!
- Si haces hoyo técnico y cantaste se te cuentan todos tus puntos negativos 

## To test
- Invite by email, unhappy paths
- Transfer of ownership when original user abandons game
- Add unique constraint to friends

## Other
DONE - Remove all "Repository with DatabaseProvider", Repository should stand on it's own 

#Partido terminado:
DONE - Poner un boton en el centro: "Regresar al Lobby"
DONE - Quitar "Abandona juego"
DONE - Poner un boton en el lobby: "Empezar nuevo partido con los mismos jugadores"

# Post Release (v 2.0?)
##Random
DONE - Consistent language
- Translate to English
- Versioning and upgrade in Game object
- FUTURE. Chose domino back logo

##Server
- Rehydrate the system after restart
    - Tokens
    - Games
    - Logged in User repo
    - (in client) load last 10 minutes of messages
- Don't send email invitations? if the user is online and in lobby?    

## Web client
- Add WebRTC video 
- Clean up GameException errors
- Clean up presentation of really bad errors
- Animations of Borlotes
    - Sopa
    - Poner ficha
    - Hoyo
    - Chuti
    - El hoyo que camina
    - Tecnico
- Cuando seleccionas una ficha, automaticamente selecciona triunfo?  
- Allow user to rearrange the dominos (or do it automatically?)
- Show triunfos flipped on top
  
### Chat en juego
Mensajes predispuestos
- Con tu hoyo me voy
- Con mi casa me voy
- Mandar todos los borlotes por chat.
    
### Admin screen
Games playing, game index, event list
Glimpse into queues
### User management screen
Accounts, add money, request money transfer
### About screen


------------------------------------------------------

#Interesting games
#A punto de ganar con chuti
update game set current_index = 10, status='jugando', lastSnapshot = '{"id": {"value": 106}, "created": "2020-07-16T14:45:17.435008", "enJuego": [], "triunfo": null, "jugadores": [{"mano": true, "user": {"id": {"value": 1}, "name": "Roberto", "email": "roberto@leibman.net", "active": true, "created": "2020-04-09T00:28:28", "deleted": false, "isAdmin": false}, "filas": [], "turno": false, "cuenta": [], "fichas": [{"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 0}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 1}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 0}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 1}}], "invited": false, "cantante": true, "statusString": "", "cuantasCantas": {"CantoTodas": {}}, "ganadorDePartido": false}, {"mano": false, "user": {"id": {"value": 39}, "name": "test1", "email": "roberto+test1@leibman.net", "active": true, "created": "2020-05-25T11:01:29", "deleted": false, "isAdmin": false}, "filas": [], "turno": false, "cuenta": [], "fichas": [{"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 1}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 6}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null, "ganadorDePartido": false}, {"mano": false, "user": {"id": {"value": 46}, "name": "aoeu", "email": "roberto+aoeu@leibman.net", "active": true, "created": "2020-06-04T09:12:10", "deleted": false, "isAdmin": false}, "filas": [], "turno": true, "cuenta": [], "fichas": [{"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 6}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 5}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null, "ganadorDePartido": false}, {"mano": false, "user": {"id": {"value": 47}, "name": "aoeuaoeu", "email": "roberto+aoeuaoeu@leibman.net", "active": true, "created": "2020-06-04T09:15:58", "deleted": false, "isAdmin": false}, "filas": [], "turno": false, "cuenta": [], "fichas": [{"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 6}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null, "ganadorDePartido": false}], "gameStatus": {"cantando": {}}, "statusString": "", "estrictaDerecha": false, "satoshiPerPoint": 150, "currentEventIndex": 10}' where id = 106;
update game set current_index = 14, status='jugando', lastSnapshot = '{"id": {"value": 106}, "created": "2020-07-16T14:45:17.435008", "enJuego": [], "triunfo": {"TriunfoNumero": {"num": {"value": 3}}}, "jugadores": [{"mano": true, "user": {"id": {"value": 1}, "name": "Roberto", "email": "roberto@leibman.net", "active": true, "created": "2020-04-09T00:28:28", "deleted": false, "isAdmin": false}, "filas": [{"index": 0, "fichas": [{"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 5}}]}], "turno": false, "cuenta": [], "fichas": [{"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 0}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 1}}], "invited": false, "cantante": true, "statusString": "", "cuantasCantas": {"CantoTodas": {}}, "ganadorDePartido": false}, {"mano": false, "user": {"id": {"value": 39}, "name": "test1", "email": "roberto+test1@leibman.net", "active": true, "created": "2020-05-25T11:01:29", "deleted": false, "isAdmin": false}, "filas": [], "turno": false, "cuenta": [], "fichas": [{"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 1}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 6}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null, "ganadorDePartido": false}, {"mano": false, "user": {"id": {"value": 46}, "name": "aoeu", "email": "roberto+aoeu@leibman.net", "active": true, "created": "2020-06-04T09:12:10", "deleted": false, "isAdmin": false}, "filas": [], "turno": true, "cuenta": [], "fichas": [{"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 6}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 5}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null, "ganadorDePartido": false}, {"mano": false, "user": {"id": {"value": 47}, "name": "aoeuaoeu", "email": "roberto+aoeuaoeu@leibman.net", "active": true, "created": "2020-06-04T09:15:58", "deleted": false, "isAdmin": false}, "filas": [], "turno": false, "cuenta": [], "fichas": [{"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 6}}], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null, "ganadorDePartido": false}], "gameStatus": {"jugando": {}}, "statusString": "Roberto gano la ultima mano", "estrictaDerecha": false, "satoshiPerPoint": 150, "currentEventIndex": 14}' where id = 106;

#Bugs
- Double pressing of buttons is messing things up!
- Si haces hoyo técnico y cantaste se te cuentan todos tus puntos negativos
- Si haces hoyo tecnico,  
- Hoyo tecnico cuando: el cantador no tiene la mano, tiene nada mas una ficha y esta a punto de perder. No hay boton de caete.

#Still pending v1
- Abandon a started game

## To test
- Invite by email, unhappy paths
- Transfer of ownership when original user abandons game
- Add unique constraint to friends


update game_players set invited = 1 where game_id = 89 and user_id > 1; update game set current_index = 93, status='esperandoJugadoresInvitados', lastSnapshot = '{"id": {"value": 89}, "created": "2020-07-13T15:53:33.438268", "enJuego": [], "triunfo": null, "jugadores": [{"mano": false, "user": {"id": {"value": 1}, "name": "Roberto", "email": "roberto@leibman.net", "active": true, "created": "2020-04-09T00:28:28", "deleted": false, "isAdmin": false}, "filas": [], "turno": false, "cuenta": [], "fichas": [], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null}, {"mano": false, "user": {"id": {"value": 47}, "name": "aoeuaoeu", "email": "roberto+aoeuaoeu@leibman.net", "active": true, "created": "2020-06-04T09:15:58", "deleted": false, "isAdmin": false}, "filas": [], "turno": false, "cuenta": [], "fichas": [], "invited": true, "cantante": false, "statusString": "", "cuantasCantas": null}, {"mano": false, "user": {"id": {"value": 46}, "name": "aoeu", "email": "roberto+aoeu@leibman.net", "active": true, "created": "2020-06-04T09:12:10", "deleted": false, "isAdmin": false}, "filas": [], "turno": false, "cuenta": [], "fichas": [], "invited": true, "cantante": false, "statusString": "", "cuantasCantas": null}, {"mano": false, "user": {"id": {"value": 39}, "name": "test1", "email": "roberto+test1@leibman.net", "active": true, "created": "2020-05-25T11:01:29", "deleted": false, "isAdmin": false}, "filas": [], "turno": false, "cuenta": [], "fichas": [], "invited": true, "cantante": false, "statusString": "", "cuantasCantas": null}], "gameStatus": {"esperandoJugadoresInvitados": {}}, "statusString": "", "estrictaDerecha": false, "satoshiPerPoint": 100, "currentEventIndex": 4}' where id = 89;

Odd winning: 3:2, 3:3, 1:1, 3:4 triunfan 0, gano 3:2?