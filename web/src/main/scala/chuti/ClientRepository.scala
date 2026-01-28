/*
 * Copyright 2020 Roberto Leibman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package chuti

import caliban.client.SelectionBuilder
import caliban.client.scalajs.GameClient.*
import caliban.client.scalajs.given
import caliban.{ScalaJSClientAdapter, WebSocketHandler}
import chat.*
import dao.{GameOperations, UserOperations}
import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.callback.AsyncCallback
import org.scalajs.dom.window
import sttp.client4.UriContext
import zio.json.*
import zio.json.ast.Json

import java.time

object ClientRepository {

  val connectionId: ConnectionId = ConnectionId.random

  /** Gets the JWT token from localStorage, or empty string if not present */
  private def getToken: String =
    Option(window.localStorage.getItem("jwtToken")).getOrElse("")

  trait ChatOperations[F[_]] {

    def getRecentMessages(channelId: ChannelId): F[Seq[ChatMessage]]

    def say(request: SayRequest): F[ChatMessage]

    def makeWebSocket(
      channelId: ChannelId,
      onData:    ChatMessage => Callback = (_ => Callback.empty)
    ): WebSocketHandler

  }

  trait ExtendedUserOperations extends UserOperations[AsyncCallback] {}

  val user: ExtendedUserOperations = new ExtendedUserOperations {
    private val userSB = User.view.map { u =>
      chuti.User(
        id = u.id.map(UserId.apply),
        name = u.name,
        email = u.email,
        created = Option(java.time.Instant.parse(u.created)).getOrElse(java.time.Instant.now()),
        lastUpdated = Option(java.time.Instant.parse(u.lastUpdated)).getOrElse(java.time.Instant.now()),
        active = u.active
      )
    }

    val client: ScalaJSClientAdapter = ScalaJSClientAdapter(uri"api/game")

    override def firstLogin: AsyncCallback[Option[time.Instant]] = ???

    override def unfriend(enemy: chuti.User): AsyncCallback[Boolean] = {
      enemy.id.fold(AsyncCallback.pure(false))(id =>
        client.asyncCalibanCallWithAuth(Mutations.unfriend(id.value)).map(_.getOrElse(false))
      )
    }

    override def friend(friend: chuti.User): AsyncCallback[Boolean] = {
      friend.id.fold(AsyncCallback.pure(false))(id =>
        client.asyncCalibanCallWithAuth(Mutations.friend(id.value)).map(_.getOrElse(false))
      )
    }

    override def friends: AsyncCallback[Seq[chuti.User]] =
      client
        .asyncCalibanCallWithAuth(Queries.getFriends(userSB))
        .map(_.toSeq.flatten)

    override def changePassword(password: String): AsyncCallback[Boolean] =
      client.asyncCalibanCallWithAuth(Mutations.changePassword(password).map(_.getOrElse(false)))

    override def upsert(e: chuti.User): AsyncCallback[chuti.User] = ???

    override def changePassword(
      user:     chuti.User,
      password: String
    ): AsyncCallback[Boolean] = ??? // Not in client

    override def get(pk: UserId): AsyncCallback[Option[chuti.User]] = ??? // Not in client

    override def delete(
      pk:         UserId,
      softDelete: Boolean
    ): AsyncCallback[Boolean] = ??? // Not in client

    override def search(search: Option[PagedStringSearch]): AsyncCallback[Seq[chuti.User]] = ??? // Not in client

    override def count(search: Option[PagedStringSearch]): AsyncCallback[Long] = ??? // Not in client

    override def login(
      email:    String,
      password: String
    ): AsyncCallback[Option[chuti.User]] = ??? // Not directly in clietn

    override def userByEmail(email: String): AsyncCallback[Option[chuti.User]] = ??? // Not in client

    override def isFirstLoginToday: AsyncCallback[Boolean] =
      client.asyncCalibanCallWithAuth(Queries.isFirstLoginToday).map(_.getOrElse(false))

    private val walletSB: SelectionBuilder[UserWallet, chuti.UserWallet] = UserWallet.view.map { w =>
      chuti.UserWallet(
        userId = UserId(w.userId),
        amount = w.amount
      )
    }

    override def getWallet: AsyncCallback[Option[chuti.UserWallet]] =
      client.asyncCalibanCallWithAuth(Queries.getWallet(walletSB))

    override def getWallet(userId: UserId): AsyncCallback[Option[chuti.UserWallet]] = ??? // Not in Client

    override def updateWallet(userWallet: chuti.UserWallet): AsyncCallback[chuti.UserWallet] = ??? // Not in client
  }

  val chat: ChatOperations[AsyncCallback] = new ChatOperations[AsyncCallback] {
    val client: ScalaJSClientAdapter = ScalaJSClientAdapter(uri"api/chat")

    import caliban.client.scalajs.ChatClient.{ChatMessage as CalibanChatMessage2, Instant as CalibanInstant, User as CalibanUser}

    private val userSB: SelectionBuilder[CalibanUser, chuti.User] = CalibanUser.view.map { u =>
      chuti.User(
        id = u.id.map(UserId.apply),
        name = u.name,
        email = u.email,
        created = Option(java.time.Instant.parse(u.created)).getOrElse(java.time.Instant.now()),
        lastUpdated = Option(java.time.Instant.parse(u.lastUpdated)).getOrElse(java.time.Instant.now()),
        active = u.active
      )
    }

    private val chatMessageSB: SelectionBuilder[CalibanChatMessage2, ChatMessage] =
      (CalibanChatMessage2.fromUser(userSB) ~
        CalibanChatMessage2.msg ~
        CalibanChatMessage2.channelId ~
        CalibanChatMessage2.date ~
        CalibanChatMessage2.toUser(userSB)).map {
        (
          fromUser:  chuti.User,
          msg:       String,
          channelId: Long,
          date:      CalibanInstant,
          toUser:    Option[chuti.User]
        ) =>
          ChatMessage(
            fromUser = fromUser,
            msg = msg,
            channelId = ChannelId(channelId),
            date = Option(java.time.Instant.parse(date)).getOrElse(java.time.Instant.now()),
            toUser = toUser
          )
      }

    override def getRecentMessages(channelId: ChannelId): AsyncCallback[Seq[ChatMessage]] =
      client
        .asyncCalibanCallWithAuth(
          caliban.client.scalajs.ChatClient.Queries.getRecentMessages(channelId.value)(chatMessageSB)
        )
        .map(_.getOrElse(Nil))

    override def say(request: SayRequest): AsyncCallback[ChatMessage] =
      client
        .asyncCalibanCallWithAuth(
          caliban.client.scalajs.ChatClient.Mutations.say(request.msg, request.channelId.value, None)
        )
        .map { _ =>
          // The say mutation returns Boolean, so we construct a minimal ChatMessage as acknowledgment
          // The actual message will be received through the WebSocket subscription
          ChatMessage(
            fromUser = chuti.User(None, "", "", java.time.Instant.now(), java.time.Instant.now()),
            msg = request.msg,
            channelId = request.channelId,
            date = java.time.Instant.now(),
            toUser = None
          )
        }

    override def makeWebSocket(
      channelId: ChannelId,
      onData:    ChatMessage => Callback = _ => Callback.empty
    ): WebSocketHandler =
      client.makeWebSocketClient[Option[ChatMessage]](
        path = "api/chat/ws",
        webSocket = None,
        query = caliban.client.scalajs.ChatClient.Subscriptions.chatStream(channelId.value, connectionId.value, getToken)(
          chatMessageSB
        ),
        onData = {
          (
            _,
            data
          ) => data.flatten.fold(Callback.empty)(onData)
        },
        operationId = "-",
        socketConnectionId = s"${connectionId.value}-${channelId.value}"
      )

  }

  // User event types matching the server
  enum UserEventType {

    case Connected, Disconnected, JoinedGame, AbandonedGame, Modified

  }
  object UserEventType {

    given JsonDecoder[UserEventType] =
      JsonDecoder.string.mapOrFail {
        case "Connected"     => Right(Connected)
        case "Disconnected"  => Right(Disconnected)
        case "JoinedGame"    => Right(JoinedGame)
        case "AbandonedGame" => Right(AbandonedGame)
        case "Modified"      => Right(Modified)
        case other           => Left(s"Unknown UserEventType: $other")
      }

  }

  case class UserEvent(
    user:          chuti.User,
    userEventType: UserEventType,
    gameId:        Option[GameId]
  )
  object UserEvent {

    given JsonDecoder[UserEvent] = DeriveJsonDecoder.gen[UserEvent]

  }

  trait ExtendedGameOperations extends GameOperations[AsyncCallback] {

    def newGame(satoshiPerPoint: Long):   AsyncCallback[Option[Game]]
    def newGameSameUsers(gameId: GameId): AsyncCallback[Option[Game]]
    def joinRandomGame:                   AsyncCallback[Option[Game]]
    def abandonGame(gameId:      GameId): AsyncCallback[Boolean]
    def inviteByEmail(
      name:   String,
      email:  String,
      gameId: GameId
    ):                             AsyncCallback[Boolean]
    def startGame(gameId: GameId): AsyncCallback[Boolean]
    def inviteToGame(
      userId: UserId,
      gameId: GameId
    ):                                               AsyncCallback[Boolean]
    def acceptGameInvitation(gameId:        GameId): AsyncCallback[Option[Game]]
    def declineGameInvitation(gameId:       GameId): AsyncCallback[Boolean]
    def cancelUnacceptedInvitations(gameId: GameId): AsyncCallback[Boolean]
    def play(
      gameId:    GameId,
      gameEvent: GameEvent
    ):                    AsyncCallback[Boolean]
    def getLoggedInUsers: AsyncCallback[Seq[chuti.User]]

    /** Creates a WebSocket for receiving game events */
    def makeGameWebSocket(
      gameId: GameId,
      onData: GameEvent => Callback
    ): WebSocketHandler

    /** Creates a WebSocket for receiving user events (connected, disconnected, etc.) */
    def makeUserWebSocket(
      onData: UserEvent => Callback
    ): WebSocketHandler

  }

  val game: ExtendedGameOperations = new ExtendedGameOperations {
    val client: ScalaJSClientAdapter = ScalaJSClientAdapter(uri"api/game")

    /** Helper to decode JSON to Game */
    private def decodeGame(json: Json): Game =
      json.toJson.fromJson[Game] match {
        case Right(game) => game
        case Left(error) => throw RuntimeException(s"Failed to decode Game: $error")
      }

    /** Helper to decode JSON to Seq[Game] */
    private def decodeGames(json: Json): Seq[Game] =
      json.toJson.fromJson[Seq[Game]] match {
        case Right(games) => games
        case Left(error)  => throw RuntimeException(s"Failed to decode Games: $error")
      }

    override def getHistoricalUserGames: AsyncCallback[Seq[Game]] =
      client
        .asyncCalibanCallWithAuth(Queries.getHistoricalUserGames)
        .map {
          case Some(json) => decodeGames(json)
          case None       => Seq.empty
        }

    override def userInGame(id: GameId): AsyncCallback[Boolean] =
      client
        .asyncCalibanCallWithAuth(Queries.getGame(id.value))
        .map(_.isDefined)

    override def updatePlayers(game: Game): AsyncCallback[Game] =
      // There's no direct updatePlayers mutation, use play with appropriate event
      AsyncCallback.throwException(NotImplementedError("updatePlayers not available via GraphQL"))

    override def gameInvites: AsyncCallback[Seq[Game]] =
      client
        .asyncCalibanCallWithAuth(Queries.getGameInvites)
        .map {
          case Some(json) => decodeGames(json)
          case None       => Seq.empty
        }

    override def gamesWaitingForPlayers(): AsyncCallback[Seq[Game]] =
      // There's no direct gamesWaitingForPlayers query
      // This would typically be implemented by joining a random game
      AsyncCallback.throwException(NotImplementedError("gamesWaitingForPlayers not available via GraphQL"))

    override def getGameForUser: AsyncCallback[Option[Game]] =
      client
        .asyncCalibanCallWithAuth(Queries.getGameForUser)
        .map(_.map(decodeGame))

    override def upsert(e: Game): AsyncCallback[Game] =
      // For new games, use newGame mutation
      // For existing games, the state is typically updated through play events
      e.id match {
        case None =>
          // New game - use satoshiPerPoint from the game
          client
            .asyncCalibanCallWithAuth(Mutations.newGame(e.satoshiPerPoint))
            .map {
              case Some(json) => decodeGame(json)
              case None       => throw RuntimeException("Failed to create new game")
            }
        case Some(_) =>
          // Existing games are updated through play events, not direct upsert
          AsyncCallback.throwException(NotImplementedError("Game updates happen through play events"))
      }

    override def get(pk: GameId): AsyncCallback[Option[Game]] =
      client
        .asyncCalibanCallWithAuth(Queries.getGame(pk.value))
        .map(_.map(decodeGame))

    override def delete(
      pk:         GameId,
      softDelete: Boolean
    ): AsyncCallback[Boolean] =
      client
        .asyncCalibanCallWithAuth(Mutations.abandonGame(pk.value))
        .map(_.getOrElse(false))

    override def search(search: Option[EmptySearch]): AsyncCallback[Seq[Game]] =
      // Use getHistoricalUserGames as a search substitute
      getHistoricalUserGames

    override def count(search: Option[EmptySearch]): AsyncCallback[Long] =
      // Count by searching and getting length
      this.search(search).map(_.length.toLong)

    override def newGame(satoshiPerPoint: Long): AsyncCallback[Option[Game]] =
      client
        .asyncCalibanCallWithAuth(Mutations.newGame(satoshiPerPoint))
        .map(_.map(decodeGame))

    override def newGameSameUsers(gameId: GameId): AsyncCallback[Option[Game]] =
      client
        .asyncCalibanCallWithAuth(Mutations.newGameSameUsers(gameId.value))
        .map(_.map(decodeGame))

    override def joinRandomGame: AsyncCallback[Option[Game]] =
      client
        .asyncCalibanCallWithAuth(Mutations.joinRandomGame)
        .map(_.map(decodeGame))

    override def abandonGame(gameId: GameId): AsyncCallback[Boolean] =
      client
        .asyncCalibanCallWithAuth(Mutations.abandonGame(gameId.value))
        .map(_.getOrElse(false))

    override def inviteByEmail(
      name:   String,
      email:  String,
      gameId: GameId
    ): AsyncCallback[Boolean] =
      client
        .asyncCalibanCallWithAuth(Mutations.inviteByEmail(name, email, gameId.value))
        .map(_.getOrElse(false))

    override def startGame(gameId: GameId): AsyncCallback[Boolean] =
      client
        .asyncCalibanCallWithAuth(Mutations.startGame(gameId.value))
        .map(_.getOrElse(false))

    override def inviteToGame(
      userId: UserId,
      gameId: GameId
    ): AsyncCallback[Boolean] =
      client
        .asyncCalibanCallWithAuth(Mutations.inviteToGame(userId.value, gameId.value))
        .map(_.getOrElse(false))

    override def acceptGameInvitation(gameId: GameId): AsyncCallback[Option[Game]] =
      client
        .asyncCalibanCallWithAuth(Mutations.acceptGameInvitation(gameId.value))
        .map(_.map(decodeGame))

    override def declineGameInvitation(gameId: GameId): AsyncCallback[Boolean] =
      client
        .asyncCalibanCallWithAuth(Mutations.declineGameInvitation(gameId.value))
        .map(_.getOrElse(false))

    override def cancelUnacceptedInvitations(gameId: GameId): AsyncCallback[Boolean] =
      client
        .asyncCalibanCallWithAuth(Mutations.cancelUnacceptedInvitations(gameId.value))
        .map(_.getOrElse(false))

    override def play(
      gameId:    GameId,
      gameEvent: GameEvent
    ): AsyncCallback[Boolean] = {
      val eventJson = gameEvent.toJsonAST match {
        case Right(json) => json
        case Left(error) => throw RuntimeException(s"Failed to encode GameEvent: $error")
      }
      client
        .asyncCalibanCallWithAuth(Mutations.play(gameId.value, eventJson))
        .map(_.getOrElse(false))
    }

    private val userSB = User.view.map { u =>
      chuti.User(
        id = u.id.map(UserId.apply),
        name = u.name,
        email = u.email,
        created = Option(java.time.Instant.parse(u.created)).getOrElse(java.time.Instant.now()),
        lastUpdated = Option(java.time.Instant.parse(u.lastUpdated)).getOrElse(java.time.Instant.now()),
        active = u.active
      )
    }

    override def getLoggedInUsers: AsyncCallback[Seq[chuti.User]] =
      client
        .asyncCalibanCallWithAuth(Queries.getLoggedInUsers(userSB))
        .map(_.getOrElse(Nil))

    override def makeGameWebSocket(
      gameId: GameId,
      onData: GameEvent => Callback
    ): WebSocketHandler = {
      client.makeWebSocketClient[Option[Json]](
        path = "api/game/ws",
        webSocket = None,
        query = Subscriptions.gameStream(gameId.value, connectionId.value, getToken),
        onData = {
          (
            _,
            data
          ) =>
            data.flatten.fold(Callback.empty) { json =>
              json.as[GameEvent] match {
                case Right(event) => onData(event)
                case Left(error) =>
                  Callback.log(s"Failed to decode GameEvent: $error")
              }
            }
        },
        operationId = "-",
        socketConnectionId = s"${connectionId.value}-${gameId.value}"
      )
    }

    override def makeUserWebSocket(
      onData: UserEvent => Callback
    ): WebSocketHandler = {
      import caliban.client.scalajs.GameClient.{
        User as CalibanUser,
        UserEvent as CalibanUserEvent,
        UserEventType as CalibanUserEventType
      }

      val userEventSB: SelectionBuilder[CalibanUserEvent, UserEvent] = {
        val userSB: SelectionBuilder[CalibanUser, chuti.User] = (
          CalibanUser.id ~
            CalibanUser.name ~
            CalibanUser.email ~
            CalibanUser.created ~
            CalibanUser.lastUpdated
        ).map {
          (
            id:          Option[Long],
            name:        String,
            email:       String,
            created:     String,
            lastUpdated: String
          ) =>
            chuti.User(
              id = id.map(UserId.apply),
              name = name,
              email = email,
              created = Option(java.time.Instant.parse(created)).getOrElse(java.time.Instant.now()),
              lastUpdated = Option(java.time.Instant.parse(lastUpdated)).getOrElse(java.time.Instant.now())
            )
        }

        (CalibanUserEvent.user(userSB) ~
          CalibanUserEvent.userEventType ~
          CalibanUserEvent.gameId).map {
          (
            user:      chuti.User,
            eventType: CalibanUserEventType,
            gameIdOpt: Option[Long]
          ) =>
            val userEventType = eventType match {
              case CalibanUserEventType.Connected     => UserEventType.Connected
              case CalibanUserEventType.Disconnected  => UserEventType.Disconnected
              case CalibanUserEventType.JoinedGame    => UserEventType.JoinedGame
              case CalibanUserEventType.AbandonedGame => UserEventType.AbandonedGame
              case CalibanUserEventType.Modified      => UserEventType.Modified
            }
            UserEvent(user, userEventType, gameIdOpt.map(GameId.apply))
        }
      }

      client.makeWebSocketClient[Option[UserEvent]](
        path = "api/game/ws",
        webSocket = None,
        query = Subscriptions.userStream(connectionId.value, getToken)(userEventSB),
        onData = {
          (
            _,
            data
          ) => data.flatten.fold(Callback.empty)(onData)
        },
        operationId = "-",
        socketConnectionId = s"${connectionId.value}-user"
      )
    }

  }

}
