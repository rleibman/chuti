/*
 * Copyright (c) 2024 Roberto Leibman
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

import chuti.Numero

import java.time.{Instant, ZoneOffset}
import scala.util.Random
import zio.json.*

package object chuti {

  opaque type UserId = Int

  object UserId {

    def apply(userId: Int): UserId = userId
    given JsonDecoder[UserId] = JsonDecoder.int

    given JsonEncoder[UserId] = JsonEncoder.int
//    given zio.Tag[UserId] = zio.Tag.materialize[chuti.UserId]

  }

  extension (id: UserId) {

    def userId: Int = id

  }

  val godlessUserId: UserId = -999

  val godUserId: UserId = -666

  opaque type ConnectionId = Int

  extension (id: ConnectionId) {

    def connectionId: Int = id

  }

  object ConnectionId {

    def apply(connectionId: Int): ConnectionId = connectionId

    given JsonDecoder[ConnectionId] = JsonDecoder.int
    given JsonEncoder[ConnectionId] = JsonEncoder.int

  }

  opaque type GameId = Int

  extension (id: GameId) {

    def gameId: Int = id

  }

  object GameId {

    def apply(gameId: Int): GameId = gameId

    given JsonDecoder[GameId] = JsonDecoder.int

    given JsonEncoder[GameId] = JsonEncoder.int

  }

  opaque type ChannelId = Int

  extension (id: ChannelId) {

    def channelId: Int = id

  }

  object ChannelId {

    def apply(channelId: Int): ChannelId = channelId

    given JsonDecoder[ChannelId] = JsonDecoder.int

    given JsonEncoder[ChannelId] = JsonEncoder.int

    // some special channels
    val lobbyChannel:  ChannelId = -1
    val directChannel: ChannelId = -2

  }

  // A user who can do anything
  val god: User = User(
    id = Some(godUserId),
    email = "god@chuti.fun",
    name = "Un-namable",
    created = Instant.ofEpochMilli(0).nn,
    lastUpdated = Instant.ofEpochMilli(0).nn,
    isAdmin = true
  )

  // A user who can do some stuff, but not much
  val godless: User = User(
    id = Some(godlessUserId),
    email = "godless@chuti.fun",
    name = "Nothing",
    created = Instant.ofEpochMilli(0).nn,
    lastUpdated = Instant.ofEpochMilli(0).nn,
    isAdmin = true
  )

  private val names = Seq(
    "Aarón",
    "Abigail",
    "Abril",
    "Adrián",
    "Adriana",
    "Agustín",
    "Agustina",
    "Aitana",
    "Alan",
    "Alberto",
    "Alejandra",
    "Alejandro",
    "Alessandra",
    "Álex",
    "Alexa",
    "Alexander",
    "Alfredo",
    "Allison",
    "Alma",
    "Alonso",
    "Amanda",
    "Ana",
    "Andrea",
    "Andrés",
    "Ángel",
    "Angelina",
    "Anthony",
    "Antonella",
    "Antonia",
    "Antonio",
    "Ariana",
    "Arturo",
    "Ashley",
    "Axel",
    "Bautista",
    "Benjamín",
    "Bianca",
    "Brianna",
    "Bruno",
    "Camila",
    "Camilo",
    "Candela",
    "Carla",
    "Carlos",
    "Carolina",
    "Catalina",
    "Christian",
    "Christopher",
    "Constanza",
    "Daniel",
    "Daniella",
    "Danna",
    "Dante",
    "David",
    "Delfina",
    "Diana",
    "Diego Alejandro",
    "Diego",
    "Dylan",
    "Eduardo",
    "Emiliano",
    "Emilio",
    "Emily",
    "Emma",
    "Emmanuel",
    "Enrique",
    "Esteban",
    "Esteban",
    "Fabiana",
    "Facundo",
    "Fátima",
    "Felipe",
    "Fernanda",
    "Fernando",
    "Fiorella",
    "Florencia",
    "Francisco",
    "Franco",
    "Gabriel",
    "Gabriela",
    "Gael",
    "Génesis",
    "Guadalupe",
    "Héctor",
    "Ian",
    "Ignacio",
    "Isaac",
    "Isabel",
    "Isabella",
    "Isidora",
    "Iván",
    "Ivana",
    "Javier",
    "Jazmín",
    "Jerónimo",
    "Jerry",
    "Jesus",
    "Jesús",
    "Joaquín",
    "Jonathan",
    "Jorge",
    "José	Emilia",
    "José",
    "Josefa",
    "Josefina",
    "Joshua",
    "Josué",
    "Juan Carlos",
    "Juan David",
    "Juan Diego",
    "Juan Pablo",
    "Juan Sebastián",
    "Juan",
    "Juan",
    "Juana",
    "Julián",
    "Juliana",
    "Julieta",
    "Julio",
    "Kevin",
    "Kimberly",
    "Laura",
    "Lautaro",
    "Leonardo",
    "Lola",
    "Luana",
    "Lucas",
    "Lucía",
    "Luciana",
    "Luciano",
    "Luis",
    "Luna",
    "Maite",
    "Malena",
    "Manuel",
    "Manuel",
    "Manuela",
    "Marcos",
    "María Camila",
    "María Fernanda",
    "María José",
    "María Paula",
    "María",
    "Mariana",
    "Mariángel",
    "Mario",
    "Martín",
    "Martina",
    "Mateo",
    "Matías",
    "Matthew",
    "Mauricio",
    "Maximiliano",
    "Máximo",
    "Melanie",
    "Mía",
    "Micaela",
    "Michelle",
    "Miguel Ángel",
    "Miguel",
    "Milagros",
    "Miranda",
    "Morena",
    "Natalia",
    "Nicolás",
    "Nicole",
    "Olivia",
    "Óscar",
    "Pablo",
    "Paloma",
    "Paola",
    "Patricio",
    "Paula",
    "Paulina",
    "Pedro",
    "Pilar",
    "Rafael",
    "Ramiro",
    "Raul",
    "Regina",
    "Renata",
    "Ricardo",
    "Roberto",
    "Rocío",
    "Rodrigo",
    "Romina",
    "Salomé",
    "Samantha",
    "Samuel",
    "Santiago",
    "Santino",
    "Sara",
    "Sebastián",
    "Sergio",
    "Simón",
    "Sofía",
    "Thiago",
    "Tomás",
    "Valentina",
    "Valentino",
    "Valeria",
    "Valery",
    "Vanessa",
    "Vicente",
    "Victoria",
    "Violeta",
    "Ximena",
    "Zoe"
  )

  // A bot user
  def hal9000: User = {
    val id = -(Random.nextInt(9998 - 1000) + 1001)
    User(
      id = Some(UserId(id)),
      email = s"hal${-id}@chuti.fun",
      name = s"${names(Random.nextInt(names.length))} HAL$id (bot)",
      created = Instant.ofEpochMilli(0).nn,
      lastUpdated = Instant.ofEpochMilli(0).nn
    )
  }

}
