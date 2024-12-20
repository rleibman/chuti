////////////////////////////////////////////////////////////////////////////////////
// Common stuff
addSbtPlugin("com.dwijnand"      % "sbt-travisci"        % "1.2.0")
addSbtPlugin("com.typesafe.sbt"  % "sbt-git"             % "1.0.2")
addSbtPlugin("de.heikoseeberger" % "sbt-header"          % "5.10.0")
addSbtPlugin("com.github.sbt"    % "sbt-native-packager" % "1.10.4")
addSbtPlugin("com.eed3si9n"      % "sbt-buildinfo"       % "0.13.1")
addSbtPlugin("org.scalameta"     % "sbt-scalafmt"        % "2.5.2")
addSbtPlugin("ch.epfl.scala"     % "sbt-scalafix"        % "0.13.0")

////////////////////////////////////////////////////////////////////////////////////
// Server
addSbtPlugin("io.spray" % "sbt-revolver" % "0.10.0")

////////////////////////////////////////////////////////////////////////////////////
// Web client
addSbtPlugin("org.scala-js"          % "sbt-scalajs"              % "1.17.0")
addSbtPlugin("com.github.ghostdogpr" % "caliban-codegen-sbt"      % "2.9.0")
addSbtPlugin("org.portable-scala"    % "sbt-scalajs-crossproject" % "1.3.2")
addSbtPlugin("ch.epfl.scala"         % "sbt-scalajs-bundler"      % "0.21.1")
//libraryDependencies += "org.slf4j"   % "slf4j-nop"                % "1.7.36" // Needed by sbt-git
//libraryDependencies += "commons-io"  % "commons-io"               % "2.18.0"

addSbtPlugin("org.scalablytyped.converter" % "sbt-converter" % "1.0.0-beta44")

////////////////////////////////////////////////////////////////////////////////////
// Testing
addSbtPlugin("io.stryker-mutator" % "sbt-stryker4s" % "0.16.1")
addSbtPlugin("org.scoverage"      % "sbt-scoverage" % "2.2.2")
