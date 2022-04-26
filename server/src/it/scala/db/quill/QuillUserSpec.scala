package db.quill

import api.ChutiSession
import api.config.Config
import chuti.User
import dao.quill.QuillRepository
import dao.{Repository, SessionProvider}
import zio.*
import zio.clock.Clock
import zio.logging.Logging
import zio.logging.slf4j.Slf4jLogger
import zio.test.*
import zio.test.environment.TestEnvironment

import java.time.{ZoneId, ZoneOffset}

object QuillUserSpec extends DefaultRunnableSpec {

  private val baseConfigLayer: ULayer[Config] = ZLayer.succeed(api.config.live)
  private val configLayer = baseConfigLayer >>> ChutiTestContainer.containerConfig.toLayer
  private val quillLayer:   ULayer[Repository] = configLayer >>> QuillRepository.live
  private val loggingLayer: ULayer[Logging] = Slf4jLogger.make((_, b) => b)
  private val godSession:   ULayer[SessionProvider] = SessionProvider.layer(ChutiSession(chuti.god))
  private val clockLayer: ULayer[Clock] =
    ZLayer.succeed(java.time.Clock.fixed(java.time.Instant.parse("2022-03-11T00:00:00.00Z"), ZoneId.from(ZoneOffset.UTC))) >>> Clock.javaClock

  val fullLayer: ULayer[Config & Repository & Logging & SessionProvider & Clock] =
    configLayer ++ quillLayer ++ loggingLayer ++ godSession ++ clockLayer

  private val testUser = User(None, "test@example.com", "Frank Lloyd Wright")

  override def spec: Spec[TestEnvironment, TestFailure[Any], TestSuccess] =
    suite("MySuite")(
      testM("CRUD") {
        for {
          repo                 <- ZIO.service[Repository.Service].map(_.userOperations)
          allUsersBeforeInsert <- repo.search()
          inserted             <- repo.upsert(testUser)
          allUsersAfterInsert  <- repo.search()
          deleted              <- repo.delete(inserted.id.get)
          allUsersAfterDelete  <- repo.search()
        } yield assertTrue(allUsersBeforeInsert.isEmpty) &&
          assertTrue(inserted.id.nonEmpty) &&
          assertTrue(allUsersAfterInsert.nonEmpty) &&
          assertTrue(allUsersAfterInsert.head.id == inserted.id) &&
          assertTrue(allUsersAfterInsert.head.email == inserted.email) &&
          assertTrue(deleted) &&
          assertTrue(allUsersAfterDelete.isEmpty)
      }
    ).provideLayer(fullLayer)

}
