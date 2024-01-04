package db.quill

import api.config.Config
import chuti.*
import dao.{Repository, RepositoryError, RepositoryPermissionError, SessionContext}
// import db.quill.QuillGameSpec.fixedClock
import zio.*
import zio.logging.*
import zio.test.*

import java.math.BigInteger
import java.time.{ZoneId, ZoneOffset}

object QuillUserSpec extends QuillSpec {

  import Assertion.*

  override def spec: Spec[Any, RepositoryError] =
    suite("Quill User Suite")(
      test("random") {
        for {
          tok <- Random.nextBytes(16).map(r => new BigInteger(r.toArray).toString(32))
          _   <- ZIO.logInfo(tok)
        } yield assert(tok.length)(isGreaterThan(0))
      },
      test("Happy CRUD") {
        (for {
          testUser             <- testUserZIO
          repo                 <- ZIO.service[Repository].map(_.userOperations)
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
          assertTrue(!allUsersAfterDelete.exists(_.email == inserted.email)))
        // .withClock(fixedClock)
      },
      test("inserting the same user (by email should fail)") {
        (for {
          repo     <- ZIO.service[Repository].map(_.userOperations)
          testUser <- testUserZIO
          _        <- repo.upsert(testUser)
          _        <- repo.upsert(testUser)
        } yield assertTrue(false))
          // .withClock(fixedClock)
          .tapError(e => ZIO.logInfo(e.getMessage.nn))
          .catchSome { case _: RepositoryError => ZIO.succeed(assertTrue(true)) }
      },
      test("changing a user's email should only succeed if that user doesn't exist already") {
        (for {
          repo       <- ZIO.service[Repository].map(_.userOperations)
          testUser1  <- testUserZIO
          testUser2  <- testUserZIO
          firstUser  <- repo.upsert(testUser1)
          secondUser <- repo.upsert(testUser2)
          _          <- repo.upsert(firstUser.copy(email = secondUser.email))
        } yield assertTrue(false))
          // .withClock(fixedClock)
          .tapError(e => ZIO.logInfo(e.getMessage.nn))
          .catchSome { case _: RepositoryError => ZIO.succeed(assertTrue(true)) }
      },
      test("Deleting a non-existent user") {
        (for {
          repo     <- ZIO.service[Repository].map(_.userOperations)
          deleted  <- repo.delete(UserId(123), softDelete = false)
          deleted2 <- repo.delete(UserId(123), softDelete = true)
        } yield assertTrue(!deleted) && assertTrue(!deleted2))
        // .withClock(fixedClock)
      },
      test("Updating a non-existent user") {
        (for {
          repo     <- ZIO.service[Repository].map(_.userOperations)
          testUser <- testUserZIO
          _        <- repo.upsert(testUser.copy(id = Some(UserId(123)), name = "ChangedName"))
        } yield assertTrue(false))
          // .withClock(fixedClock)
          .tapError(e => ZIO.logInfo(e.getMessage.nn))
          .catchSome { case _: RepositoryError => ZIO.succeed(assertTrue(true)) }
      },
      test("Deleting a user with no permissions") {
        (for {
          repo <- ZIO.service[Repository].map(_.userOperations)
          _    <- repo.delete(UserId(123)).provideSomeLayer[Config & Repository](satanSession)
        } yield assertTrue(false))
          // .withClock(fixedClock)
          .tapError(e => ZIO.logInfo(e.getMessage.nn))
          .catchSome { case _: RepositoryPermissionError =>
            ZIO.succeed(assertTrue(true))
          }
      },
      test("Updating a user with no permissions") {
        (for {
          repo     <- ZIO.service[Repository].map(_.userOperations)
          testUser <- testUserZIO
          inserted <- repo.upsert(testUser)
          _        <- repo.upsert(inserted.copy(name = "changedName")).provideSomeLayer[Config & Repository](satanSession)
        } yield assertTrue(false))
          // .withClock(fixedClock)
          .tapError(e => ZIO.logInfo(e.getMessage.nn))
          .catchSome { case _: RepositoryPermissionError =>
            ZIO.succeed(assertTrue(true))
          }
      },
      test("login") {
        (for {
          now             <- Clock.instant
          repo            <- ZIO.service[Repository].map(_.userOperations)
          testUser        <- testUserZIO
          inserted        <- repo.upsert(testUser)
          active          <- repo.upsert(inserted.copy(active = true))
          passwordChanged <- repo.changePassword(active, password)
          loggedIn        <- repo.login(active.email, password)
          firstLogin      <- repo.firstLogin.provideSomeLayer[Config & Repository](userSession(active))
        } yield assertTrue(passwordChanged) &&
          assertTrue(loggedIn.contains(active)) &&
          assertTrue(firstLogin.contains(now)))
        // .withClock(fixedClock)
      },
      test("user by email") {
        (for {
          repo        <- ZIO.service[Repository].map(_.userOperations)
          testUser    <- testUserZIO
          inserted    <- repo.upsert(testUser)
          active      <- repo.upsert(inserted.copy(active = true))
          userByEmail <- repo.userByEmail(testUser.email)
        } yield assertTrue(userByEmail.contains(active)))
        // .withClock(fixedClock)

      },
      test("friend stuff") {
        (for {
          repo       <- ZIO.service[Repository].map(_.userOperations)
          testUser1  <- testUserZIO
          inserted1  <- repo.upsert(testUser1.copy(active = true))
          testUser2  <- testUserZIO
          inserted2  <- repo.upsert(testUser2.copy(active = true))
          noFriends1 <- repo.friends.provideSomeLayer[Config & Repository](userSession(inserted1))
          noFriends2 <- repo.friends.provideSomeLayer[Config & Repository](userSession(inserted2))
          friended   <- repo.friend(inserted2).provideSomeLayer[Config & Repository](userSession(inserted1))
          aFriend1   <- repo.friends.provideSomeLayer[Config & Repository](userSession(inserted1))
          aFriend2   <- repo.friends.provideSomeLayer[Config & Repository](userSession(inserted2))
          unfriended <- repo.unfriend(inserted1).provideSomeLayer[Config & Repository](userSession(inserted2))
          noFriends3 <- repo.friends.provideSomeLayer[Config & Repository](userSession(inserted1))
          noFriends4 <- repo.friends.provideSomeLayer[Config & Repository](userSession(inserted2))
        } yield {
          assert(noFriends1)(isEmpty) &&
          assert(noFriends2)(isEmpty) &&
          assertTrue(friended) &&
          assertTrue(aFriend1.headOption.map(_.id).contains(inserted2.id)) &&
          assertTrue(aFriend2.headOption.map(_.id).contains(inserted1.id)) &&
          assertTrue(unfriended) &&
          assert(noFriends3)(isEmpty) &&
          assert(noFriends4)(isEmpty)
        })
        // .withClock(fixedClock)
      },
      test("wallet") {
        (for {
          repo          <- ZIO.service[Repository].map(_.userOperations)
          testUser1     <- testUserZIO
          inserted1     <- repo.upsert(testUser1.copy(active = true))
          wallet        <- repo.getWallet(inserted1.id.get)
          updated       <- repo.updateWallet(wallet.get.copy(amount = 12345))
          walletUpdated <- repo.getWallet(inserted1.id.get)
        } yield assertTrue(wallet.nonEmpty) &&
          assert(wallet.get.amount)(equalTo(BigDecimal(10000))) &&
          assertTrue(updated == walletUpdated.get) &&
          assert(walletUpdated.get.amount)(equalTo(BigDecimal(12345))))
        // .withClock(fixedClock)
      }

      // Crud tests
      //
      // unfriend
      // friend
      // friends
      // getWallet
      // updateWallet
    ).provideShared(containerLayer, configLayer, quillLayer, loggingLayer, godSession)

}
