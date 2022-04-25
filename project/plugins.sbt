////////////////////////////////////////////////////////////////////////////////////
// Common stuff
addSbtPlugin("com.dwijnand"       % "sbt-travisci"             % "1.2.0")
addSbtPlugin("com.typesafe.sbt"   % "sbt-git"                  % "1.0.2")
addSbtPlugin("de.heikoseeberger"  % "sbt-header"               % "5.7.0")
addSbtPlugin("com.typesafe.sbt"   % "sbt-native-packager"      % "1.8.1")
addSbtPlugin("com.eed3si9n"       % "sbt-buildinfo"            % "0.11.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.2.0")
addSbtPlugin("ch.epfl.scala"      % "sbt-scalafix"             % "0.10.0")

////////////////////////////////////////////////////////////////////////////////////
// Server
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")

////////////////////////////////////////////////////////////////////////////////////
// Web client
addSbtPlugin("org.scala-js"          % "sbt-scalajs"         % "1.10.0")
addSbtPlugin("ch.epfl.scala"         % "sbt-scalajs-bundler" % "0.20.0")
addSbtPlugin("com.github.ghostdogpr" % "caliban-codegen-sbt" % "1.4.0")
libraryDependencies += "org.slf4j"   % "slf4j-nop"           % "1.7.36" // Needed by sbt-git
libraryDependencies += "commons-io"  % "commons-io"          % "2.11.0"

addSbtPlugin("org.scalablytyped.converter" % "sbt-converter" % "1.0.0-beta37")

////////////////////////////////////////////////////////////////////////////////////
// Testing
addSbtPlugin("io.stryker-mutator" % "sbt-stryker4s" % "0.14.3")
addSbtPlugin("org.scoverage"      % "sbt-scoverage" % "1.9.3")
addSbtPlugin("org.scoverage"      % "sbt-coveralls" % "1.3.2")
