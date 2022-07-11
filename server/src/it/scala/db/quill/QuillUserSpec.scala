package db.quill

import api.config.Config
import chuti.*
import dao.{Repository, RepositoryError, RepositoryPermissionError, SessionContext}
import zio.*
import zio.clock.Clock
import zio.logging.Logging
import zio.random.Random
import zio.test.*
import zio.test.environment.{Live, TestClock, TestConsole, TestEnvironment, TestRandom, TestSystem}

import java.math.BigInteger

object QuillUserSpec extends QuillSpec {

  import Assertion.*

  val fullLayer: ULayer[
    Config & Repository & SessionContext & Logging & Annotations &
      (Live & (Sized & (TestClock & (TestConfig & (TestConsole & (TestRandom & (TestSystem & zio.ZEnv)))))))
  ] = ???

  override def spec: Spec[TestEnvironment, TestFailure[Any], TestSuccess] =
    suite("Quill User Suite")(
      testM("random") {
        for {
          tok <- ZIO.service[Random.Service].flatMap(_.nextBytes(16)).map(r => new BigInteger(r.toArray).toString(32))
          _   <- Logging.info(tok)
        } yield assert(tok.length)(isGreaterThan(0))
      },
      testM("Happy CRUD") {
        for {
          testUser             <- testUserZIO
          repo                 <- ZIO.service[Repository.Service].map(_.userOperations)
          allUsersBeforeInsert <- repo.search()
          inserted             <- repo.upsert(testUser)
          allUsersAfterInsert  <- repo.search()
          count                <- repo.count()
          deleted              <- repo.delete(inserted.id.get)
          allUsersAfterDelete  <- repo.search()
        } yield assertTrue(allUsersBeforeInsert.isEmpty) &&
          assertTrue(inserted.id.nonEmpty) &&
          assertTrue(allUsersAfterInsert.nonEmpty) &&
          assertTrue(count > 1L) && // Because tests are run in parallel, we don't really know how many there are, but there should at least be one
          assertTrue(allUsersAfterInsert.exists(_.id == inserted.id)) &&
          assertTrue(allUsersAfterInsert.exists(_.email == inserted.email)) &&
          assertTrue(deleted) &&
          assertTrue(!allUsersAfterDelete.exists(_.id == inserted.id)) &&
          assertTrue(!allUsersAfterDelete.exists(_.email == inserted.email))
      },
      testM("inserting the same user (by email should fail)") {
        (for {
          repo     <- ZIO.service[Repository.Service].map(_.userOperations)
          testUser <- testUserZIO
          _        <- repo.upsert(testUser)
          _        <- repo.upsert(testUser)
        } yield assertTrue(false))
          .tapError(e => Logging.info(e.getMessage))
          .catchSome { case _: RepositoryError => ZIO.succeed(assertTrue(true)) }
      },
      testM("changing a user's email should only succeed if that user doesn't exist already") {
        (for {
          repo       <- ZIO.service[Repository.Service].map(_.userOperations)
          testUser1  <- testUserZIO
          testUser2  <- testUserZIO
          firstUser  <- repo.upsert(testUser1)
          secondUser <- repo.upsert(testUser2)
          _          <- repo.upsert(firstUser.copy(email = secondUser.email))
        } yield assertTrue(false))
          .tapError(e => Logging.info(e.getMessage))
          .catchSome { case _: RepositoryError => ZIO.succeed(assertTrue(true)) }
      },
      testM("Deleting a non-existent user") {
        for {
          repo     <- ZIO.service[Repository.Service].map(_.userOperations)
          deleted  <- repo.delete(UserId(123), softDelete = false)
          deleted2 <- repo.delete(UserId(123), softDelete = true)
        } yield assertTrue(!deleted) && assertTrue(!deleted2)
      },
      testM("Updating a non-existent user") {
        (for {
          repo     <- ZIO.service[Repository.Service].map(_.userOperations)
          testUser <- testUserZIO
          _        <- repo.upsert(testUser.copy(id = Some(UserId(123)), name = "ChangedName"))
        } yield assertTrue(false))
          .tapError(e => Logging.info(e.getMessage))
          .catchSome { case _: RepositoryError => ZIO.succeed(assertTrue(true)) }
      },
      testM("Deleting a user with no permissions") {
        (for {
          repo <- ZIO.service[Repository.Service].map(_.userOperations)
          _    <- repo.delete(UserId(123)).provideSomeLayer[Config & Repository & Logging & Clock](satanSession)
        } yield assertTrue(false))
          .tapError(e => Logging.info(e.getMessage))
          .catchSome { case _: RepositoryPermissionError =>
            ZIO.succeed(assertTrue(true))
          }
      },
      testM("Updating a user with no permissions") {
        (for {
          repo     <- ZIO.service[Repository.Service].map(_.userOperations)
          testUser <- testUserZIO
          inserted <- repo.upsert(testUser)
          _        <- repo.upsert(inserted.copy(name = "changedName")).provideSomeLayer[Config & Repository & Logging & Clock](satanSession)
        } yield assertTrue(false))
          .tapError(e => Logging.info(e.getMessage))
          .catchSome { case _: RepositoryPermissionError =>
            ZIO.succeed(assertTrue(true))
          }
      },
      testM("login") {
        for {
          now             <- ZIO.service[Clock.Service].flatMap(_.instant)
          repo            <- ZIO.service[Repository.Service].map(_.userOperations)
          testUser        <- testUserZIO
          inserted        <- repo.upsert(testUser)
          active          <- repo.upsert(inserted.copy(active = true))
          passwordChanged <- repo.changePassword(active, password)
          loggedIn        <- repo.login(active.email, password)
          firstLogin      <- repo.firstLogin.provideSomeLayer[Config & Repository & Logging & Clock](userSession(active))
        } yield assertTrue(passwordChanged) &&
          assert(loggedIn)(equalTo(Some(active))) &&
          assert(firstLogin)(equalTo(Some(now)))
      },
      testM("user by email") {
        for {
          repo        <- ZIO.service[Repository.Service].map(_.userOperations)
          testUser    <- testUserZIO
          inserted    <- repo.upsert(testUser)
          active      <- repo.upsert(inserted.copy(active = true))
          userByEmail <- repo.userByEmail(testUser.email)
        } yield assert(userByEmail)(equalTo(Some(active)))

      },
      testM("friend stuff") {
        for {
          repo       <- ZIO.service[Repository.Service].map(_.userOperations)
          testUser1  <- testUserZIO
          inserted1  <- repo.upsert(testUser1.copy(active = true))
          testUser2  <- testUserZIO
          inserted2  <- repo.upsert(testUser2.copy(active = true))
          noFriends1 <- repo.friends.provideSomeLayer[Config & Repository & Logging & Clock](userSession(inserted1))
          noFriends2 <- repo.friends.provideSomeLayer[Config & Repository & Logging & Clock](userSession(inserted2))
          friended   <- repo.friend(inserted2).provideSomeLayer[Config & Repository & Logging & Clock](userSession(inserted1))
          aFriend1   <- repo.friends.provideSomeLayer[Config & Repository & Logging & Clock](userSession(inserted1))
          aFriend2   <- repo.friends.provideSomeLayer[Config & Repository & Logging & Clock](userSession(inserted2))
          unfriended <- repo.unfriend(inserted1).provideSomeLayer[Config & Repository & Logging & Clock](userSession(inserted2))
          noFriends3 <- repo.friends.provideSomeLayer[Config & Repository & Logging & Clock](userSession(inserted1))
          noFriends4 <- repo.friends.provideSomeLayer[Config & Repository & Logging & Clock](userSession(inserted2))
        } yield {
          assert(noFriends1)(isEmpty) &&
          assert(noFriends2)(isEmpty) &&
          assertTrue(friended) &&
          assert(aFriend1.headOption.map(_.id))(equalTo(Some(inserted2.id))) &&
          assert(aFriend2.headOption.map(_.id))(equalTo(Some(inserted1.id))) &&
          assertTrue(unfriended) &&
          assert(noFriends3)(isEmpty) &&
          assert(noFriends4)(isEmpty)
        }
      },
      testM("wallet") {
        for {
          repo          <- ZIO.service[Repository.Service].map(_.userOperations)
          testUser1     <- testUserZIO
          inserted1     <- repo.upsert(testUser1.copy(active = true))
          wallet        <- repo.getWallet(inserted1.id.get)
          updated       <- repo.updateWallet(wallet.get.copy(amount = 12345))
          walletUpdated <- repo.getWallet(inserted1.id.get)
        } yield assertTrue(wallet.nonEmpty) &&
          assert(wallet.get.amount)(equalTo(BigDecimal(10000))) &&
          assertTrue(updated == walletUpdated.get) &&
          assert(walletUpdated.get.amount)(equalTo(BigDecimal(12345)))

      }

      // Crud tests
      //
      // unfriend
      // friend
      // friends
      // getWallet
      // updateWallet
    ).provideLayerShared(fullLayer) // containerLayer, configLayer, quillLayer, loggingLayer, godSession, clockLayer, Random.live)

}
