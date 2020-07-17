package util

object ServerSecret extends App {
  println(com.softwaremill.session.SessionUtil.randomServerSecret())
}
