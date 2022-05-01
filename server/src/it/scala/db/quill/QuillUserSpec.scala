package db.quill

import api.ChutiSession
import api.config.Config
import chuti.*
import dao.quill.QuillRepository
import dao.{Repository, RepositoryError, RepositoryPermissionError, SessionProvider}
import db.quill.ChutiContainer.ChutiContainer
import zio.*
import zio.clock.Clock
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger
import zio.test.*
import zio.test.environment.TestEnvironment

import java.time.{ZoneId, ZoneOffset}

object QuillUserSpec extends DefaultRunnableSpec {

  private val satan = // A user with no permissions
    User(id = Some(UserId(999)), email = "Satan@hell.com", name = "Lucifer Morningstar")

  private val baseConfigLayer: ULayer[Config] = ZLayer.succeed(api.config.live)
  private val containerLayer:  ULayer[ChutiContainer] = ChutiContainer.containerLayer
  private val configLayer:     ULayer[Config] = (baseConfigLayer ++ containerLayer) >>> ChutiContainer.configLayer
  private val quillLayer:      ULayer[Repository] = configLayer >>> QuillRepository.live
  private val loggingLayer:    ULayer[Logging] = Slf4jLogger.make((_, b) => b)
  private val godSession:      ULayer[SessionProvider] = SessionProvider.layer(ChutiSession(chuti.god))
  private val satanSession:    ULayer[SessionProvider] = SessionProvider.layer(ChutiSession(satan))
  private val clockLayer: ULayer[Clock] =
    ZLayer.succeed(java.time.Clock.fixed(java.time.Instant.parse("2022-03-11T00:00:00.00Z"), ZoneId.from(ZoneOffset.UTC))) >>> Clock.javaClock

  val fullGodLayer: ULayer[Config & Repository & Logging & SessionProvider & Clock] =
    configLayer ++ quillLayer ++ loggingLayer ++ godSession ++ clockLayer

  private val testUser = User(None, "test@example.com", "Frank Lloyd Wright")

  override def spec: Spec[TestEnvironment, TestFailure[Any], TestSuccess] =
    suite("MySuite")(
      testM("Happy CRUD") {
        for {
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
          assertTrue(count == 1) &&
          assertTrue(allUsersAfterInsert.head.id == inserted.id) &&
          assertTrue(allUsersAfterInsert.head.email == inserted.email) &&
          assertTrue(deleted) &&
          assertTrue(allUsersAfterDelete.isEmpty)
      },
      testM("inserting the same user (by email should fail)") {
        (for {
          repo <- ZIO.service[Repository.Service].map(_.userOperations)
          _    <- repo.upsert(testUser)
          _    <- repo.upsert(testUser)
        } yield (assertTrue(false))).catchSome { case _: RepositoryError => ZIO.succeed(assertTrue(true)) }
      },
      testM("changing a user's email should only succeed if that user doesn't exist already") {
        (for {
          repo       <- ZIO.service[Repository.Service].map(_.userOperations)
          firstUser  <- repo.upsert(testUser)
          secondUser <- repo.upsert(testUser.copy(email = "anotheremail@example.com"))
          _          <- repo.upsert(firstUser.copy(email = secondUser.email))
        } yield (assertTrue(false))).catchSome { case e: RepositoryError => Logging.info(e.getMessage) *> ZIO.succeed(assertTrue(true)) }
      },
      testM("Deleting a non-existent user") {
        for {
          repo     <- ZIO.service[Repository.Service].map(_.userOperations)
          deleted  <- repo.delete(UserId(123), softDelete = false)
          deleted2 <- repo.delete(UserId(123), softDelete = true)
        } yield (assertTrue(!deleted) && assertTrue(!deleted2))
      },
      testM("Updating a non-existent user") {
        (for {
          repo <- ZIO.service[Repository.Service].map(_.userOperations)
          _    <- repo.upsert(testUser.copy(id = Some(UserId(123)), name = "ChangedName"))
        } yield (assertTrue(false))).catchSome { case e: RepositoryError => Logging.info(e.getMessage) *> ZIO.succeed(assertTrue(true)) }
      },
      testM("Deleting a user with no permissions") {
        (for {
          repo <- ZIO.service[Repository.Service].map(_.userOperations)
          _    <- repo.delete(UserId(123)).provideSomeLayer[Config & Repository & Logging & Clock](satanSession)
        } yield (assertTrue(false)))
          .tapError(e => Logging.info(e.getMessage))
          .catchSome { case e: RepositoryPermissionError =>
            ZIO.succeed(assertTrue(true))
          }
      }
      // Crud tests
      //
      // Deleting a user without permissions
      // Updating a user without permissions
      // first login (of a new user)
      // first login (of a logged in user)
      // login
      // userByEmail
      // changePassword
      // unfriend
      // friend
      // friends
      // getWallet
      // updateWallet

    ).provideLayer(fullGodLayer)

}
