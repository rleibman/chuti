package util

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
