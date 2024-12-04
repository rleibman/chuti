[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![release-badge][]][release]

[![Build Status](https://travis-ci.com/rleibman/chuti.svg?branch=master)](https://travis-ci.com/rleibman/chuti)
[![BCH compliance](https://bettercodehub.com/edge/badge/rleibman/chuti?branch=master)](https://bettercodehub.com/)
[![Coverage Status](https://coveralls.io/repos/github/rleibman/chuti/badge.svg?branch=master)](https://coveralls.io/github/rleibman/chuti?branch=master)
[![Mutation testing badge](https://badge.stryker-mutator.io/github.com/rleibman/chuti/master)](https://stryker-mutator.github.io)

[release]:              https://github.com/rleibman/chuti/releases
[release-badge]:        https://img.shields.io/github/tag/rleibman/chuti.svg?label=version&color=blue
[rleibman-github-badge]:     https://img.shields.io/badge/-Github-yellowgreen.svg?style=social&logo=GitHub&logoColor=black
[rleibman-github-link]:      https://github.com/rleibman
[rleibman-linkedin-badge]:     https://img.shields.io/badge/-Linkedin-yellowgreen.svg?style=social&logo=LinkedIn&logoColor=black
[rleibman-linkedin-link]:      https://linkedin.com/in/rleibman
[rleibman-personal-badge]:     https://img.shields.io/badge/-Website-yellowgreen.svg?style=social&logo=data:image/svg+xml;base64,PHN2ZyBoZWlnaHQ9JzMwMHB4JyB3aWR0aD0nMzAwcHgnICBmaWxsPSIjMDAwMDAwIiB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHhtbG5zOnhsaW5rPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5L3hsaW5rIiB2ZXJzaW9uPSIxLjEiIHg9IjBweCIgeT0iMHB4IiB2aWV3Qm94PSIwIDAgNjQgNjQiIGVuYWJsZS1iYWNrZ3JvdW5kPSJuZXcgMCAwIDY0IDY0IiB4bWw6c3BhY2U9InByZXNlcnZlIj48Zz48Zz48cGF0aCBkPSJNNDEuNiwyNy4yYy04LjMsMC0xNSw2LjctMTUsMTVzNi43LDE1LDE1LDE1YzguMywwLDE1LTYuNywxNS0xNVM0OS45LDI3LjIsNDEuNiwyNy4yeiBNNTEuNSwzNmgtMy4zICAgIGMtMC42LTEuNy0xLjQtMy4zLTIuNC00LjZDNDguMiwzMi4yLDUwLjIsMzMuOSw1MS41LDM2eiBNNDEuNiwzMS41YzEuMywxLjIsMi4zLDIuNywzLDQuNGgtNkMzOS4zLDM0LjIsNDAuNCwzMi43LDQxLjYsMzEuNXogICAgIE0zNy40LDMxLjNjLTEsMS40LTEuOCwyLjktMi40LDQuNmgtMy4zQzMzLjEsMzMuOSwzNS4xLDMyLjIsMzcuNCwzMS4zeiBNMzAuMyw0NWMtMC4yLTAuOS0wLjQtMS44LTAuNC0yLjhjMC0xLDAuMS0yLDAuNC0yLjkgICAgaDMuOWMtMC4xLDEtMC4yLDEuOS0wLjIsMi45YzAsMC45LDAuMSwxLjksMC4yLDIuOEgzMC4zeiBNMzEuNyw0OC4zSDM1YzAuNiwxLjcsMS40LDMuNCwyLjQsNC44QzM1LDUyLjIsMzMsNTAuNSwzMS43LDQ4LjN6ICAgICBNNDEuNiw1Mi45Yy0xLjMtMS4yLTIuMy0yLjgtMy4xLTQuNWg2LjFDNDQsNTAuMSw0Mi45LDUxLjcsNDEuNiw1Mi45eiBNMzcuNiw0NWMtMC4yLTAuOS0wLjItMS44LTAuMi0yLjhjMC0xLDAuMS0yLDAuMy0yLjloOCAgICBjMC4yLDAuOSwwLjMsMS45LDAuMywyLjljMCwxLTAuMSwxLjktMC4yLDIuOEgzNy42eiBNNDUuOCw1My4xYzEtMS40LDEuOC0zLDIuNC00LjhoMy4zQzUwLjIsNTAuNSw0OC4yLDUyLjIsNDUuOCw1My4xeiBNNDksNDUgICAgYzAuMS0wLjksMC4yLTEuOCwwLjItMi44YzAtMS0wLjEtMi0wLjItMi45aDMuOWMwLjIsMC45LDAuNCwxLjksMC40LDIuOWMwLDEtMC4xLDEuOS0wLjQsMi44SDQ5eiI+PC9wYXRoPjxwYXRoIGQ9Ik0zNCwyNS45Yy0wLjktMC43LTEuOC0xLjMtMi45LTEuOGMyLTIuMSwzLjItNC45LDMuMi03LjljMC02LjMtNS4xLTExLjQtMTEuNC0xMS40UzExLjYsOS45LDExLjYsMTYuMiAgICBjMCwzLjEsMS4yLDUuOSwzLjIsNy45Yy00LjEsMi02LjgsNS40LTcuMSw5LjRsLTAuMywzLjhjMCwyLDcsMy42LDE1LjYsMy42YzAuMiwwLDAuNSwwLDAuNywwQzI0LjIsMzQuMywyOC4yLDI4LjYsMzQsMjUuOXogICAgIE0yMyw4LjhjNC4xLDAsNy40LDMuMyw3LjQsNy40cy0zLjMsNy40LTcuNCw3LjRzLTcuNC0zLjMtNy40LTcuNFMxOC45LDguOCwyMyw4Ljh6Ij48L3BhdGg+PC9nPjwvZz48L3N2Zz4=&logoColor=black
[rleibman-personal-link]:      http://www.chuti.fun
[rleibman-patreon-link]:       https://www.patreon.com/rleibman
[rleibman-patreon-badge]: https://img.shields.io/badge/-Patreon-yellowgreen.svg?style=social&logo=Patreon&logoColor=black

Chuti!
```
  _______
 /______/|
|     o ||
|   o   ||
| o     ||
|-------||
| o   o ||
|   o   ||
| o   o ||
|_______|/
```

En cuanto todo funcione, te invito a jugar en:

http://www.chuti.fun

## Diagrama de estado del juego
```
Comienzo
   |
   |
   V
Esperando
   |
   +-------------------
   |                  |
   V                  |
Cantando              |
   |                  V
   +-------------------
   |                  |
   V                  |
Jugando               |
   |                  V
   +-------------------
   |                  |
   V                  V
Terminado         Abandonado
```

## Definiciones:
- Juego - Una partida, desde que se canta hasta los regalos.
- Partido - Una coleccion de juegos con los mismos jugadores que se juega a 21 
- Turno - El jugador que le tocaba cantar este juego
- Cantante - El jugador que le tocaba cantar, o el guey que lo salvo
- Mano - El jugador que tiene la batuta en un momento dado

## Heuristicas
### Chutis de caida:
 - todas de un numero
 - todas las mulas
 - Las cuatro mas altas de una y 3 mulas
 
### Hoyos tecnicos
1. Si eres el cantante: Cuando sigues corriendo el juego a pesar de que ya estas hecho.
Dado un triunfo, cual es el mínimo numero de filas que puedes hacer, Nota: siempre se puede correr la primera.
Es decir Si llevas la mano, y el mínimo es igual o mayor a lo que cantaste, y vuelves a pedir... es hoyo tecnico!
2. Si no eres el cantante, pero llevas la mano si sigues corriendo el juego a pesar de que el hoyo ya esta hecho
3. Si piden cualquier numero (triunfo o no), tienes y das otra cosa (el juego podría fácilmente evitar esto) 
4. Si piden cualquier numero que no sea triunfo, no tienes de esas, pero tienes triunfo y no lo sueltas

### El juego se acaba
- Cuando el que canto ya hizo el numero que canto
- Cuando el que canto no tiene ya posibilidades de hacer lo que canto (sin importar quien lleva la mano)  

## Paginas 
### Log in
User
Password

### Self Registration and Invited Registration
User
Email
Password
Repeat Password

### User Admin
User
Email
Password
Repeat Password

### Lost password

### Lobby
#### Chat
#### Private messages
#### Users
#### Invites
#### Current game
#### Start New game
#### Join random game

### Mesa de Juego
- Cuatro secciones, una para cada jugador
- Fila de controles, con todos los posibles comandos, segun el juego
- Seccion central donde ocurre el juego
- Chat lateral (en versiones futuras video de los jugadores) 
- Ver cuentas
- Regreso al Lobby

#Technology
- ZIO
- akka-http
- Caliban GraphQL library
- Circe
- Scala.js
- Semantic UI

### Roberto Leibman
* [![rleibman-github-badge][]][rleibman-github-link]
* [![rleibman-linkedin-badge][]][rleibman-linkedin-link]
* [![rleibman-personal-badge][]][rleibman-personal-link]
* [![rleibman-patreon-badge][]][rleibman-patreon-link]

#Porque no marca a Roberto como ganador????
{"id": {"value": 146}, "created": "2020-07-27T20:51:27.472449", "enJuego": [], "triunfo": {"TriunfoNumero": {"num": {"value": 6}}}, "jugadores": [{"mano": true, "user": {"id": {"value": 1}, "name": "Roberto Leibman", "email": "roberto@leibman.net", "active": true, "created": "2020-04-09T00:28:28", "deleted": false, "isAdmin": false}, "filas": [{"index": 0, "fichas": [{"type": "conocida", "abajo": {"value": 6}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 6}}]}, {"index": 1, "fichas": [{"type": "conocida", "abajo": {"value": 5}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 5}}]}, {"index": 2, "fichas": [{"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 1}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 4}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 4}}]}, {"index": 4, "fichas": [{"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 2}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 0}}]}, {"index": 0, "fichas": [{"type": "conocida", "abajo": {"value": 3}, "arriba": {"value": 6}}]}], "turno": true, "cuenta": [{"esHoyo": false, "puntos": 1}, {"esHoyo": false, "puntos": 5}, {"esHoyo": false, "puntos": 5}, {"esHoyo": false, "puntos": 2}, {"esHoyo": false, "puntos": 3}, {"esHoyo": false, "puntos": 5}], "fichas": [], "invited": false, "cantante": true, "statusString": "", "cuantasCantas": {"Casa": {}}, "ganadorDePartido": false}, {"mano": false, "user": {"id": {"value": 46}, "name": "aoeu", "email": "roberto+aoeu@leibman.net", "active": true, "created": "2020-06-04T09:12:10", "deleted": false, "isAdmin": false}, "filas": [], "turno": false, "cuenta": [{"esHoyo": false, "puntos": 1}, {"esHoyo": false, "puntos": 4}, {"esHoyo": false, "puntos": 1}, {"esHoyo": false, "puntos": 1}, {"esHoyo": true, "puntos": -4}, {"esHoyo": false, "puntos": 1}], "fichas": [], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null, "ganadorDePartido": false}, {"mano": false, "user": {"id": {"value": 39}, "name": "test1", "email": "roberto+test1@leibman.net", "active": true, "created": "2020-05-25T11:01:29", "deleted": false, "isAdmin": false}, "filas": [{"index": 3, "fichas": [{"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 0}, "arriba": {"value": 3}}, {"type": "conocida", "abajo": {"value": 4}, "arriba": {"value": 6}}, {"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 3}}]}, {"index": -1, "fichas": [{"type": "conocida", "abajo": {"value": 2}, "arriba": {"value": 5}}, {"type": "conocida", "abajo": {"value": 1}, "arriba": {"value": 2}}]}], "turno": false, "cuenta": [{"esHoyo": false, "puntos": 5}, {"esHoyo": false, "puntos": 2}, {"esHoyo": false, "puntos": 4}, {"esHoyo": false, "puntos": 2}, {"esHoyo": true, "puntos": -4}, {"esHoyo": false, "puntos": 2}, {"esHoyo": false, "puntos": 2}], "fichas": [], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null, "ganadorDePartido": false}, {"mano": false, "user": {"id": {"value": 47}, "name": "aoeuaoeu", "email": "roberto+aoeuaoeu@leibman.net", "active": true, "created": "2020-06-04T09:15:58", "deleted": false, "isAdmin": false}, "filas": [], "turno": false, "cuenta": [{"esHoyo": false, "puntos": 1}, {"esHoyo": false, "puntos": 2}, {"esHoyo": false, "puntos": 1}, {"esHoyo": false, "puntos": 1}, {"esHoyo": true, "puntos": -4}], "fichas": [], "invited": false, "cantante": false, "statusString": "", "cuantasCantas": null, "ganadorDePartido": false}], "gameStatus": {"requiereSopa": {}}, "statusString": "Roberto Leibman se hizo con 5 filas. Regalos para test1. Se termino el juego, esperando a que Roberto Leibman haga la sopa", "estrictaDerecha": false, "satoshiPerPoint": 150, "currentEventIndex": 171}

# Client code generation
calibanGenClient /home/rleibman/projects/chuti/server/src/main/graphql/game.schema /home/rleibman/projects/chuti/web/src/main/scala/caliban/client/scalajs/GameClient.scala --genView true --scalarMappings Json:io.circe.Json,LocalDateTime:java.time.LocalDateTime --packageName caliban.client.scalajs
calibanGenClient /home/rleibman/projects/chuti/server/src/main/graphql/chat.schema /home/rleibman/projects/chuti/web/src/main/scala/caliban/client/scalajs/ChatClient.scala --genView true --scalarMappings Json:io.circe.Json,LocalDateTime:java.time.LocalDateTime --packageName caliban.client.scalajs
