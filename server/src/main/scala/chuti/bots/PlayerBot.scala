package chuti.bots

import chuti.{Game, GameId, PlayEvent, User}
import dao.{Repository, SessionProvider}
import game.GameService.{GameLayer, GameService}
import zio.{RIO, ZIO}

trait PlayerBot {
  def decideTurn(user: User, game: Game): Option[PlayEvent]

  def takeTurn(gameId: GameId): RIO[GameLayer with GameService, Game] = {
    for {
      gameOperations <- ZIO.access[Repository](_.get.gameOperations)
      gameService    <- ZIO.access[GameService](_.get)
      user           <- ZIO.access[SessionProvider](_.get.session.user)
      game           <- gameOperations.get(gameId).map(_.get)
      played <- {
        ZIO.foreach(decideTurn(user, game))(gameService.play(gameId, _))
      }
    } yield played.getOrElse(game)
  }
}
