package db.quill

import api.ChutiSession
import api.config.Config
import chuti.*
import dao.quill.QuillRepository
import dao.{Repository, RepositoryError, RepositoryPermissionError, SessionProvider}
import zio.*
import zio.clock.Clock
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger
import zio.magic.*
import zio.random.Random
import zio.test.*
import zio.test.environment.TestEnvironment

import java.math.BigInteger
import java.time.{Instant, ZoneId, ZoneOffset}

object QuillUserSpec extends DefaultRunnableSpec {

  import Assertion.*
  private val password = "testPassword123"
  private val satan = // A user with no permissions
    User(id = Some(UserId(999)), email = "Satan@hell.com", name = "Lucifer Morningstar")

  private val loggingLayer:    ULayer[Logging] = Slf4jLogger.make((_, b) => b)
  private val baseConfigLayer: ULayer[Config] = ZLayer.succeed(api.config.live)
  private val containerLayer = ChutiContainer.containerLayer.orDie
  private val configLayer = (containerLayer ++ baseConfigLayer) >>> ChutiContainer.configLayer
  private val quillLayer = QuillRepository.live
  private val godSession:              ULayer[SessionProvider] = SessionProvider.layer(ChutiSession(chuti.god))
  private val satanSession:            ULayer[SessionProvider] = SessionProvider.layer(ChutiSession(satan))
  private def userSession(user: User): ULayer[SessionProvider] = SessionProvider.layer(ChutiSession(user))
  val now:                             Instant = java.time.Instant.parse("2022-03-11T00:00:00.00Z")
  private val clockLayer: ULayer[Clock] =
    ZLayer.succeed(java.time.Clock.fixed(now, ZoneId.from(ZoneOffset.UTC))) >>> Clock.javaClock

//  val fullGodLayer: ULayer[Config & Repository & Logging & SessionProvider & Clock & Random] =
//    configLayer ++ quillLayer ++ loggingLayer ++ godSession ++ clockLayer ++ Random.live

  private val testUserZIO: URIO[Random, User] = {
    for {
      random <- ZIO.service[Random.Service]
      str    <- random.nextString(5)
    } yield User(None, email = s"$str@example.com", name = "Frank Lloyd Wright")
  }

  override def spec: Spec[TestEnvironment, TestFailure[Any], TestSuccess] =
    suite("MySuite")(
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
          assertTrue(count > 1) && // Because tests are run in parallel, we don't really know how many there are, but there should at least be one
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
      }

      // Crud tests
      //
      // unfriend
      // friend
      // friends
      // getWallet
      // updateWallet
    ).injectShared(containerLayer, configLayer, quillLayer, loggingLayer, godSession, clockLayer, Random.live)

}
