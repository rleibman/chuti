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
Juego - Una partida, desde que se canta hasta los regalos.
Partido - Una coleccion de juegos con los mismos jugadores que se juega a 21 
Turno - El jugador que le tocaba cantar este juego
Cantador - El jugador que le tocaba cantar, o el guey que lo salvo
Mano - El jugador que tiene la batuta en un momento dado

## Heuristicas
### Chutis de caida:
 - todas de un numero
 - todas las mulas
 - Las cuatro mas altas de una y 3 mulas
 
### Hoyos tecnicos
1. Si eres el cantador: Cuando sigues corriende el juego a pesar de que ya estas hecho.
Dado un triunfo, cual es el minimo numero de filas que puedes hacer, Nota: siempre se puede correr la primera.
Es decir Si llevas la mano, y el minimo es igual o mayor a lo que cantaste, y vuelves a pedir... es hoyo tecnico!
2. Si no eres el cantador, pero llevas la mano si sigues corriendo el juego a pesar de que el hoyo ya esta hecho
3. Si piden triunfos, tienes y das otra cosa (el juego podria facilmente evitar esto) 

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
