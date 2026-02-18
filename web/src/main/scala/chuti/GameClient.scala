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
import caliban.client.scalajs.GameClient.{
  User as CalibanUser,
  UserEvent as CalibanUserEvent,
  UserEventType as CalibanUserEventType,
  *
}
import caliban.client.scalajs.given
import caliban.{ScalaJSClientAdapter, WebSocketHandler}
import chat.*
import db.{GameOperations, UserOperations}
import game.GameEngine
import japgolly.scalajs.react.Callback
import japgolly.scalajs.react.callback.AsyncCallback
import org.scalajs.dom.window
import sttp.client4.UriContext
import zio.json.*
import zio.json.ast.Json

import java.time

trait ChatOperations[F[_]] {

  def getRecentMessages(channelId: ChannelId): F[Seq[ChatMessage]]

  def say(request: SayRequest): F[ChatMessage]

  def makeWebSocket(
    channelId: ChannelId,
    onData:    ChatMessage => Callback = (_ => Callback.empty)
  ): WebSocketHandler

}

object GameClient {

  val gameClient: ScalaJSClientAdapter = ScalaJSClientAdapter(uri"api/game")
  val chatClient: ScalaJSClientAdapter = ScalaJSClientAdapter(uri"api/chat")

  val connectionId: ConnectionId = ConnectionId.random

  private val userSB = CalibanUser.view.map { u =>
    chuti.User(
      id = UserId(u.id),
      name = u.name,
      email = u.email,
      created = Option(java.time.Instant.parse(u.created)).getOrElse(java.time.Instant.now()),
      lastUpdated = Option(java.time.Instant.parse(u.lastUpdated)).getOrElse(java.time.Instant.now()),
      active = u.active
    )
  }

  /** Helper to decode JSON to Game */
  def decodeGame(json: Json): Game =
    json.toJson.fromJson[Game] match {
      case Right(game) => game
      case Left(error) => throw RuntimeException(s"Failed to decode Game: $error")
    }

  /** Helper to decode JSON to Seq[Game] */
  def decodeGames(json: Json): Seq[Game] =
    json.toJson.fromJson[Seq[Game]] match {
      case Right(games) => games
      case Left(error)  => throw RuntimeException(s"Failed to decode Games: $error")
    }

  /** Gets the JWT token from localStorage, or empty string if not present */
  private def getToken: String = Option(window.localStorage.getItem("jwtToken")).getOrElse("")

  trait ExtendedUserOperations extends UserOperations[AsyncCallback] {}

  val game: GameEngine[AsyncCallback] = new GameEngine[AsyncCallback] {
    override def newGame(satoshiPerPoint: Long): AsyncCallback[Game] =
      gameClient
        .asyncCalibanCallWithAuth(Mutations.newGame(satoshiPerPoint))
        .map(_.map(decodeGame).getOrElse(throw RuntimeException("Failed to create new game")))

    override def newGameSameUsers(gameId: GameId): AsyncCallback[Game] =
      gameClient
        .asyncCalibanCallWithAuth(Mutations.newGameSameUsers(gameId.value))
        .map(_.map(decodeGame).getOrElse(throw RuntimeException("Failed to create new game")))

    override def joinRandomGame(): AsyncCallback[Game] =
      gameClient
        .asyncCalibanCallWithAuth(Mutations.joinRandomGame)
        .map(_.map(decodeGame).getOrElse(throw RuntimeException("Failed to join game")))

    override def abandonGame(gameId: GameId): AsyncCallback[Boolean] =
      gameClient
        .asyncCalibanCallWithAuth(Mutations.abandonGame(gameId.value))
        .map(_.getOrElse(false))

    override def inviteByEmail(
      name:   String,
      email:  String,
      gameId: GameId
    ): AsyncCallback[Boolean] =
      gameClient
        .asyncCalibanCallWithAuth(Mutations.inviteByEmail(name, email, gameId.value))
        .map(_.getOrElse(false))

    override def startGame(gameId: GameId): AsyncCallback[Boolean] =
      gameClient
        .asyncCalibanCallWithAuth(Mutations.startGame(gameId.value))
        .map(_.getOrElse(false))

    override def inviteToGame(
      userId: UserId,
      gameId: GameId
    ): AsyncCallback[Boolean] =
      gameClient
        .asyncCalibanCallWithAuth(Mutations.inviteToGame(userId.value, gameId.value))
        .map(_.getOrElse(false))

    override def acceptGameInvitation(gameId: GameId): AsyncCallback[Game] =
      gameClient
        .asyncCalibanCallWithAuth(Mutations.acceptGameInvitation(gameId.value))
        .map(_.map(decodeGame).getOrElse(throw RuntimeException("Failed to create new game")))

    override def declineGameInvitation(gameId: GameId): AsyncCallback[Boolean] =
      gameClient
        .asyncCalibanCallWithAuth(Mutations.declineGameInvitation(gameId.value))
        .map(_.getOrElse(false))

    override def cancelUnacceptedInvitations(gameId: GameId): AsyncCallback[Boolean] =
      gameClient
        .asyncCalibanCallWithAuth(Mutations.cancelUnacceptedInvitations(gameId.value))
        .map(_.getOrElse(false))

    override def play(
      gameId:    GameId,
      gameEvent: PlayEvent
    ): AsyncCallback[Game] = ??? // We really don't want to get the game every time we play a turn

    override def playSilently(
      gameId:    GameId,
      gameEvent: PlayEvent
    ): AsyncCallback[Boolean] = {
      val eventJson = gameEvent.toJsonAST match {
        case Right(json) => json
        case Left(error) => throw RuntimeException(s"Failed to encode GameEvent: $error")
      }
      gameClient
        .asyncCalibanCallWithAuth(Mutations.play(gameId.value, eventJson))
        .map(_.getOrElse(false))
    }

    override def getLoggedInUsers: AsyncCallback[Seq[chuti.User]] =
      gameClient
        .asyncCalibanCallWithAuth(Queries.getLoggedInUsers(userSB))
        .map(_.getOrElse(Nil))
  }

  val user: ExtendedUserOperations = new ExtendedUserOperations {

    override def firstLogin: AsyncCallback[Option[time.Instant]] = ???

    override def unfriend(enemy: chuti.UserId): AsyncCallback[Boolean] =
      gameClient.asyncCalibanCallWithAuth(Mutations.unfriend(enemy.value)).map(_.getOrElse(false))

    override def friend(friend: chuti.UserId): AsyncCallback[Boolean] =
      gameClient.asyncCalibanCallWithAuth(Mutations.friend(friend.value)).map(_.getOrElse(false))

    override def friends: AsyncCallback[Seq[chuti.User]] =
      gameClient
        .asyncCalibanCallWithAuth(Queries.getFriends(userSB))
        .map(_.toSeq.flatten)

    override def changePassword(password: String): AsyncCallback[Boolean] =
      gameClient.asyncCalibanCallWithAuth(Mutations.changePassword(password).map(_.getOrElse(false)))

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
      gameClient.asyncCalibanCallWithAuth(Queries.isFirstLoginToday).map(_.getOrElse(false))

    private val walletSB: SelectionBuilder[UserWallet, chuti.UserWallet] = UserWallet.view.map { w =>
      chuti.UserWallet(
        userId = UserId(w.userId),
        amount = w.amount
      )
    }

    override def getWallet: AsyncCallback[Option[chuti.UserWallet]] =
      gameClient.asyncCalibanCallWithAuth(Queries.getWallet(walletSB))

    override def getWallet(userId: UserId): AsyncCallback[Option[chuti.UserWallet]] = ??? // Not in Client

    override def updateWallet(userWallet: chuti.UserWallet): AsyncCallback[chuti.UserWallet] = ??? // Not in client

    override def userByOAuthProvider(
      provider:   String,
      providerId: String
    ): AsyncCallback[Option[User]] = ??? // Not in client
  }

  val chat: ChatOperations[AsyncCallback] = new ChatOperations[AsyncCallback] {

    import caliban.client.scalajs.ChatClient.{
      ChatMessage as CalibanChatMessage,
      Instant as ChatInstant,
      User as ChatUser
    }

    private val chatUserSB: SelectionBuilder[ChatUser, chuti.User] = ChatUser.view.map { u =>
      chuti.User(
        id = UserId(u.id),
        name = u.name,
        email = u.email,
        created = Option(java.time.Instant.parse(u.created)).getOrElse(java.time.Instant.now()),
        lastUpdated = Option(java.time.Instant.parse(u.lastUpdated)).getOrElse(java.time.Instant.now()),
        active = u.active
      )
    }

    private val chatMessageSB: SelectionBuilder[CalibanChatMessage, ChatMessage] =
      (CalibanChatMessage.fromUser(chatUserSB) ~
        CalibanChatMessage.msg ~
        CalibanChatMessage.channelId ~
        CalibanChatMessage.date ~
        CalibanChatMessage.toUser(chatUserSB)).map {
        (
          fromUser:  chuti.User,
          msg:       String,
          channelId: Long,
          date:      ChatInstant,
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
      chatClient
        .asyncCalibanCallWithAuth(
          caliban.client.scalajs.ChatClient.Queries.getRecentMessages(channelId.value)(chatMessageSB)
        )
        .map(_.getOrElse(Nil))

    override def say(request: SayRequest): AsyncCallback[ChatMessage] =
      chatClient
        .asyncCalibanCallWithAuth(
          caliban.client.scalajs.ChatClient.Mutations.say(request.msg, request.channelId.value, None)
        )
        .map { _ =>
          // The say mutation returns Boolean, so we construct a minimal ChatMessage as acknowledgment
          // The actual message will be received through the WebSocket subscription
          ChatMessage(
            fromUser = chuti.User(UserId.empty, "", "", java.time.Instant.now(), java.time.Instant.now()),
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
      chatClient.makeWebSocketClient[Option[ChatMessage]](
        path = "api/chat/ws",
        webSocket = None,
        query =
          caliban.client.scalajs.ChatClient.Subscriptions.chatStream(channelId.value, connectionId.value, getToken)(
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

  val gameRepo: ExtendedGameOperations = new ExtendedGameOperations {
    val gameClient: ScalaJSClientAdapter = ScalaJSClientAdapter(uri"api/game")

    override def getHistoricalUserGames: AsyncCallback[Seq[Game]] =
      gameClient
        .asyncCalibanCallWithAuth(Queries.getHistoricalUserGames)
        .map {
          case Some(json) => decodeGames(json)
          case None       => Seq.empty
        }

    override def userInGame(id: GameId): AsyncCallback[Boolean] =
      gameClient
        .asyncCalibanCallWithAuth(Queries.getGame(id.value))
        .map(_.isDefined)

    override def updatePlayers(game: Game): AsyncCallback[Game] =
      // There's no direct updatePlayers mutation, use play with appropriate event
      AsyncCallback.throwException(NotImplementedError("updatePlayers not available via GraphQL"))

    override def gameInvites: AsyncCallback[Seq[Game]] =
      gameClient
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
      gameClient
        .asyncCalibanCallWithAuth(Queries.getGameForUser)
        .map(_.map(decodeGame))

    override def upsert(game: Game): AsyncCallback[Game] =
      // For new games, use newGame mutation
      // For existing games, the state is typically updated through play events
      game.id match {
        case GameId.empty =>
          // New game - use satoshiPerPoint from the game
          gameClient
            .asyncCalibanCallWithAuth(Mutations.newGame(game.satoshiPerPoint))
            .map {
              case Some(json) => decodeGame(json)
              case None       => throw RuntimeException("Failed to create new game")
            }
        case _ =>
          // Existing games are updated through play events, not direct upsert
          AsyncCallback.throwException(NotImplementedError("Game updates happen through play events"))
      }

    override def get(pk: GameId): AsyncCallback[Option[Game]] =
      gameClient
        .asyncCalibanCallWithAuth(Queries.getGame(pk.value))
        .map(_.map(decodeGame))

    override def delete(
      pk:         GameId,
      softDelete: Boolean
    ): AsyncCallback[Boolean] =
      gameClient
        .asyncCalibanCallWithAuth(Mutations.abandonGame(pk.value))
        .map(_.getOrElse(false))

    override def search(search: Option[EmptySearch]): AsyncCallback[Seq[Game]] =
      // Use getHistoricalUserGames as a search substitute
      getHistoricalUserGames

    override def count(search: Option[EmptySearch]): AsyncCallback[Long] =
      // Count by searching and getting length
      this.search(search).map(_.length.toLong)

    override def makeGameWebSocket(
      gameId: GameId,
      onData: GameEvent => Callback
    ): WebSocketHandler = {
      gameClient.makeWebSocketClient[Option[Json]](
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

      val userEventSB: SelectionBuilder[CalibanUserEvent, UserEvent] = {
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

      gameClient.makeWebSocketClient[Option[UserEvent]](
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
