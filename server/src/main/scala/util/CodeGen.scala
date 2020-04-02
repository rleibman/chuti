/*
 * Copyright 2020 Roberto Leibman
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package util
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import slick.jdbc.H2Profile.api._
import slick.jdbc.H2Profile
object CodeGen extends App {
  slick.codegen.SourceCodeGenerator.main(
    Array(
      "slick.jdbc.MySQLProfile",
      "com.mysql.cj.jdbc.Driver",
      "jdbc:mysql://192.168.1.5:3306/chuti?serverTimezone=UTC",
      "server/src/main/scala",
      "dao.gen",
      "chuti",
      "chuti"
    )
  )
}
