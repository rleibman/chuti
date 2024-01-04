////////////////////////////////////////////////////////////////////////////////////
// Common stuff
addSbtPlugin("com.typesafe.sbt"   % "sbt-git"                  % "1.0.2")
addSbtPlugin("de.heikoseeberger"  % "sbt-header"               % "5.10.0")
addSbtPlugin("com.github.sbt"     % "sbt-native-packager"      % "1.9.16")
addSbtPlugin("com.eed3si9n"       % "sbt-buildinfo"            % "0.11.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.3.2")

////////////////////////////////////////////////////////////////////////////////////
// Server
addSbtPlugin("io.spray" % "sbt-revolver" % "0.10.0")

////////////////////////////////////////////////////////////////////////////////////
// Web client
addSbtPlugin("org.scala-js"           % "sbt-scalajs"         % "1.15.0")
addSbtPlugin("ch.epfl.scala"          % "sbt-scalajs-bundler" % "0.21.1")
addSbtPlugin("com.github.ghostdogpr"  % "caliban-codegen-sbt" % "2.5.0")
libraryDependencies += "org.slf4j"    % "slf4j-nop"           % "2.0.10" // Needed by sbt-git
libraryDependencies += "commons-io"   % "commons-io"          % "2.15.1"
libraryDependencies += "com.lihaoyi" %% "os-lib"              % "0.9.3"

addSbtPlugin("org.scalablytyped.converter" % "sbt-converter" % "1.0.0-beta43")

////////////////////////////////////////////////////////////////////////////////////
// Testing
addSbtPlugin("io.stryker-mutator" % "sbt-stryker4s" % "0.16.0")
addSbtPlugin("org.scoverage"      % "sbt-scoverage" % "2.0.9")
