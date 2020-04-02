/*
 * Copyright 2020 Roberto Leibman
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package api

import better.files.File
import com.typesafe.config.ConfigFactory

/**
  * A trait to keep app configuration
  */
trait Config {
  val configKey = "chuti"
  val config: com.typesafe.config.Config = {
    val confFileName =
      System.getProperty("application.conf", "./src/main/resources/application.conf")
    val confFile = File(confFileName)
    val config = ConfigFactory
      .parseFile(confFile.toJava)
      .withFallback(ConfigFactory.load())
    config
  }
}
