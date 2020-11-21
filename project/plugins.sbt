////////////////////////////////////////////////////////////////////////////////////
// Common stuff
addSbtPlugin("com.dwijnand"       % "sbt-travisci"             % "1.2.0")
addSbtPlugin("com.typesafe.sbt"   % "sbt-git"                  % "1.0.0")
addSbtPlugin("de.heikoseeberger"  % "sbt-header"               % "5.6.0")
addSbtPlugin("com.typesafe.sbt"   % "sbt-native-packager"      % "1.7.4")
addSbtPlugin("com.eed3si9n"       % "sbt-buildinfo"            % "0.10.0")
addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.0.0")
addSbtPlugin("ch.epfl.scala"      % "sbt-scalafix"             % "0.9.23")

////////////////////////////////////////////////////////////////////////////////////
// Server
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")

////////////////////////////////////////////////////////////////////////////////////
// Web client
addSbtPlugin("org.scala-js"          % "sbt-scalajs"         % "1.3.1")
addSbtPlugin("ch.epfl.scala"         % "sbt-scalajs-bundler" % "0.20.0")
addSbtPlugin("com.github.ghostdogpr" % "caliban-codegen-sbt" % "0.9.3")
libraryDependencies += "org.slf4j"   % "slf4j-nop"           % "1.7.30" // Needed by sbt-git
libraryDependencies += "commons-io"  % "commons-io"          % "2.8.0"
libraryDependencies += "org.vafer"   % "jdeb"                % "1.8" artifacts Artifact("jdeb", "jar", "jar")

resolvers += Resolver.bintrayRepo("oyvindberg", "converter")

addSbtPlugin("org.scalablytyped.converter" % "sbt-converter" % "1.0.0-beta28")

////////////////////////////////////////////////////////////////////////////////////
// Testing
addSbtPlugin("io.stryker-mutator" % "sbt-stryker4s" % "0.9.2")
addSbtPlugin("org.scoverage"      % "sbt-scoverage" % "1.6.1")
addSbtPlugin("org.scoverage"      % "sbt-coveralls" % "1.2.7")
