////////////////////////////////////////////////////////////////////////////////////
// Common Stuff
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import org.apache.commons.io.FileUtils
import sbt.Keys.testFrameworks
import sbtcrossproject.CrossPlugin.autoImport.crossProject

//////////////////////////////////////////////////////////////////////////////////////////////////
// Global stuff
resolvers += Resolver.mavenLocal
resolvers += Resolver.sonatypeRepo("snapshots")
resolvers += "Typesafe repository" at "https://repo.typesafe.com/typesafe/releases/"
Global / onChangedBuildSource := ReloadOnSourceChanges

//////////////////////////////////////////////////////////////////////////////////////////////////
// Shared settings

lazy val start = TaskKey[Unit]("start")
lazy val dist = TaskKey[File]("dist")
lazy val debugDist = TaskKey[File]("debugDist")

enablePlugins(
  GitVersioning,
  CalibanPlugin
)

val akkaVersion = "2.6.19"
val akkaHttpVersion = "10.2.9"
val slickVersion = "3.3.3"
val circeVersion = "0.14.1"
val calibanVersion = "1.4.0"
val scalaCacheVersion = "0.28.0"
val zioVersion = "1.0.14"
val monocleVersion = "2.1.0"
val tapirVersion = "0.20.1"
val quillVersion = "3.16.3"

lazy val commonSettings = Seq(
  organization     := "net.leibman",
  scalaVersion     := "2.13.8",
  startYear        := Some(2020),
  organizationName := "Roberto Leibman",
  headerLicense    := Some(HeaderLicense.ALv2("2020", "Roberto Leibman", HeaderLicenseStyle.Detailed))
)

//lazy val scala3CompatOpts = Seq(
//  // Scala 2.13 in scala 3 compatibility mode
//  "-Xsource:3",
//  "-explaintypes", // Explain type errors in more detail.
//  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
//  "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
//  "-language:experimental.macros", // Allow macro definition (besides implementation and application)
//  "-language:higherKinds", // Allow higher-kinded types
//  "-language:implicitConversions", // Allow definition of implicit functions called views
//  "-Xlint:nonlocal-return", // A return statement used an exception for flow control.
//  "-Xlint:implicit-not-found", // Check @implicitNotFound and @implicitAmbiguous messages.
//  "-Xlint:serial", // @SerialVersionUID on traits and non-serializable classes.
//  "-Xlint:valpattern", // Enable pattern checks in val definitions.
//  "-Xlint:eta-zero", // Warn on eta-expansion (rather than auto-application) of zero-ary method.
//  "-Xlint:eta-sam", // Warn on eta-expansion to meet a Java-defined functional interface that is not explicitly annotated with @FunctionalInterface.
//  "-Xlint:implicit-recursion", // Warn when an implicit resolves to an enclosing self-definition.
//  "-Wextra-implicit", // Warn when more than one implicit parameter section is defined.
//  "-Wmacros:both", // Lints code before and after applying a macro
//  "-Wnumeric-widen", // Warn when numerics are widened.
//  "-Woctal-literal", // Warn on obsolete octal syntax.
//  // weird interaction with Zio... "-Wdead-code",                       // Warn when dead code is identified.
//  "-Wunused:imports", // Warn if an import selector is not referenced.
//  "-Wunused:patvars", // Warn if a variable bound in a pattern is unused.
//  "-Wunused:privates", // Warn if a private member is unused.
//  "-Wunused:locals", // Warn if a local definition is unused.
//  "-Wunused:explicits", // Warn if an explicit parameter is unused.
//  "-Wunused:implicits", // Warn if an implicit parameter is unused.
//  "-Wunused:params", // Enable -Wunused:explicits,implicits.
//  "-Wunused:linted",
//  "-Wvalue-discard", // Warn when non-Unit expression results are unused.
//  "-Ymacro-annotations", // required to enable @ConfiguredJsonCodec annotation from circe
//  "-Ywarn-macros:after" // this is to avoid `unused local variable warning` for implicits; https://github.com/scala/bug/issues/10599,
//)

lazy val commonVmSettings = commonSettings ++ Seq(
  scalacOptions ++= Seq(
    "-Xsource:3",
    "-deprecation" // Emit warning and location for usages of deprecated APIs.
  ),
  libraryDependencies ++= Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser",
    "io.circe" %% "circe-literal"
  ).map(_ % circeVersion)
)

////////////////////////////////////////////////////////////////////////////////////
// common (i.e. model)
lazy val commonJVM = common.jvm
lazy val commonJS = common.js

lazy val common = crossProject(JSPlatform, JVMPlatform)
  .settings(commonVmSettings)
  .enablePlugins(
    AutomateHeaderPlugin,
    GitVersioning,
    BuildInfoPlugin
  )
  .settings(
    name             := "chuti-common",
    buildInfoPackage := "chuti"
  )
  .jvmSettings(
    commonVmSettings
  )
  .jsSettings(
    commonSettings ++ Seq(
      libraryDependencies ++= Seq(
        "io.circe" %%% "circe-core",
        "io.circe" %%% "circe-generic",
        "io.circe" %%% "circe-parser",
        "io.circe" %%% "circe-literal"
      ).map(_ % circeVersion),
      libraryDependencies ++= Seq(
        "com.github.julien-truffaut" %%% "monocle-core"    % monocleVersion,
        "com.github.julien-truffaut" %%% "monocle-generic" % monocleVersion,
        "com.github.julien-truffaut" %%% "monocle-macro"   % monocleVersion,
        "com.github.julien-truffaut" %%% "monocle-state"   % monocleVersion,
        "com.github.julien-truffaut" %%% "monocle-refined" % monocleVersion,
        "com.github.julien-truffaut" %%% "monocle-unsafe"  % monocleVersion
      )
    )
  )

resolvers += Resolver.sonatypeRepo("releases")

lazy val server = project
  .enablePlugins(
    AutomateHeaderPlugin,
    GitVersioning,
    LinuxPlugin,
    JDebPackaging,
    DebianPlugin,
    DebianDeployPlugin,
    JavaServerAppPackaging,
    SystemloaderPlugin,
    SystemdPlugin
  )
  .settings(Defaults.itSettings, debianSettings)
  .settings(commonVmSettings)
  .configs(IntegrationTest)
  .dependsOn(commonJVM)
  .settings(
    name                                                 := "chuti-server",
    libraryDependencySchemes += "org.scala-lang.modules" %% "scala-java8-compat" % "always",
    libraryDependencies ++= Seq(
      // Akka
      "com.typesafe.akka"                  %% "akka-stream"     % akkaVersion withSources (),
      "com.typesafe.akka"                  %% "akka-http"       % akkaHttpVersion withSources (),
      "de.heikoseeberger"                  %% "akka-http-circe" % "1.39.2" withSources (),
      "com.softwaremill.akka-http-session" %% "core"            % "0.7.0" withSources (),
      // DB
      "com.typesafe.slick"        %% "slick"                  % slickVersion withSources (),
      "com.typesafe.slick"        %% "slick-codegen"          % slickVersion withSources (),
      "com.typesafe.slick"        %% "slick-hikaricp"         % slickVersion withSources (),
      "mysql"                      % "mysql-connector-java"   % "8.0.29" withSources (),
      "com.foerster-technologies" %% "slick-mysql_circe-json" % "1.1.0" withSources (),
      "io.getquill"               %% "quill-jdbc-zio"         % quillVersion withSources (),
      "io.getquill"               %% "quill-jasync-mysql"     % quillVersion withSources (),
      // Scala Cache
      "com.github.cb372" %% "scalacache-core"     % scalaCacheVersion withSources (),
      "com.github.cb372" %% "scalacache-caffeine" % scalaCacheVersion withSources (),
      // ZIO
      "dev.zio"                     %% "zio"               % zioVersion withSources (),
      "dev.zio"                     %% "zio-logging-slf4j" % "0.5.14" withSources (),
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe"  % tapirVersion withSources (),
      "com.github.ghostdogpr"       %% "caliban"           % calibanVersion withSources (),
      "com.github.ghostdogpr"       %% "caliban-tapir"     % calibanVersion withSources (),
      "com.github.ghostdogpr"       %% "caliban-akka-http" % calibanVersion withSources (),
      // Other random utilities
      "com.github.pathikrit"  %% "better-files"    % "3.9.1" withSources (),
      "com.github.daddykotex" %% "courier"         % "3.1.0" withSources (),
      "ch.qos.logback"         % "logback-classic" % "1.2.11" withSources (),
      "org.slf4j"              % "slf4j-nop"       % "1.7.36" withSources (),
      "commons-codec"          % "commons-codec"   % "1.15",
      // Testing
      "dev.zio"       %% "zio-test"                       % zioVersion % "it, test" withSources (),
      "dev.zio"       %% "zio-test-sbt"                   % zioVersion % "it, test" withSources (),
      "org.scalatest" %% "scalatest"                      % "3.2.12"   % "it, test" withSources (),
      "org.mockito"   %% "mockito-scala-scalatest"        % "1.17.5"   % "it, test" withSources (),
      "com.dimafeng"  %% "testcontainers-scala-scalatest" % "0.40.5" withSources (),
      "com.dimafeng"  %% "testcontainers-scala-mysql"     % "0.40.5" withSources ()
    ),
    testFrameworks ++= Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
    IntegrationTest / testFrameworks ++= Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )

lazy val login: Project = project
  .dependsOn(commonJS, stLib)
  .settings(commonSettings)
  .configure(bundlerSettings)
  .configure(withCssLoading)
  .configure(commonWeb)
  .enablePlugins(
    AutomateHeaderPlugin,
    GitVersioning,
    ScalaJSPlugin,
    CalibanPlugin
  )
  .settings(
    name := "chuti-login",
    debugDist := {

      val assets = (ThisBuild / baseDirectory).value / "login" / "src" / "main" / "web"

      val artifacts = (Compile / fastOptJS / webpack).value
      val artifactFolder = (Compile / fastOptJS / crossTarget).value
      val debugFolder = (ThisBuild / baseDirectory).value / "debugDist"

      debugFolder.mkdirs()
      FileUtils.copyDirectory(assets, debugFolder, true)
      artifacts.foreach { artifact =>
        val target = artifact.data.relativeTo(artifactFolder) match {
          case None          => debugFolder / artifact.data.name
          case Some(relFile) => debugFolder / relFile.toString
        }

        println(s"Trying to copy ${artifact.data.toPath} to ${target.toPath}")
        Files.copy(artifact.data.toPath, target.toPath, REPLACE_EXISTING)
      }

      debugFolder
    },
    dist := {
      val assets = (ThisBuild / baseDirectory).value / "login" / "src" / "main" / "web"

      val artifacts = (Compile / fullOptJS / webpack).value
      val artifactFolder = (Compile / fullOptJS / crossTarget).value
      val distFolder = (ThisBuild / baseDirectory).value / "dist"

      distFolder.mkdirs()
      FileUtils.copyDirectory(assets, distFolder, true)
      artifacts.foreach { artifact =>
        val target = artifact.data.relativeTo(artifactFolder) match {
          case None          => distFolder / artifact.data.name
          case Some(relFile) => distFolder / relFile.toString
        }

        println(s"Trying to copy ${artifact.data.toPath} to ${target.toPath}")
        Files.copy(artifact.data.toPath, target.toPath, REPLACE_EXISTING)
      }

      distFolder
    }
  )

lazy val debianSettings =
  Seq(
    Debian / name               := "chuti",
    Debian / normalizedName     := "chuti",
    Debian / packageDescription := "The game of chuti",
    Debian / packageSummary     := "The game of chuti",
    Linux / maintainer          := "Roberto Leibman <roberto@leibman.net>",
    Linux / daemonUser          := "chuti",
    Linux / daemonGroup         := "chuti",
    Debian / serverLoading      := Some(ServerLoader.Systemd),
    Universal / mappings += {
      val src = sourceDirectory.value
      val conf = src / "templates" / "application.conf"
      conf -> "conf/application.conf"
    }
  )

////////////////////////////////////////////////////////////////////////////////////
// Web
val scalajsReactVersion = "2.1.0"
val reactVersion = "17.0.0"

lazy val reactNpmDeps: Project => Project =
  _.settings(
    Compile / npmDependencies ++= Seq(
      "react-dom"         -> reactVersion,
      "@types/react-dom"  -> reactVersion,
      "react"             -> reactVersion,
      "@types/react"      -> reactVersion,
      "csstype"           -> "2.6.11",
      "@types/prop-types" -> "15.7.3"
    )
  )

lazy val bundlerSettings: Project => Project =
  _.enablePlugins(ScalaJSBundlerPlugin)
    .settings(
      startWebpackDevServer / version := "3.1.10",
      webpack / version               := "4.28.3",
//      Compile / fastOptJS / webpackExtraArgs += "--mode=development",
      Compile / fastOptJS / webpackDevServerExtraArgs += "--mode=development",
      Compile / fastOptJS / artifactPath := ((Compile / fastOptJS / crossTarget).value /
        ((fastOptJS / moduleName).value + "-opt.js")),
//      Compile / fullOptJS / webpackExtraArgs += "--mode=production",
      Compile / fullOptJS / webpackDevServerExtraArgs += "--mode=production",
      Compile / fullOptJS / artifactPath := ((Compile / fullOptJS / crossTarget).value /
        ((fullOptJS / moduleName).value + "-opt.js")),
      useYarn                                   := true,
      run / fork                                := true,
      Global / scalaJSStage                     := FastOptStage,
      Compile / scalaJSUseMainModuleInitializer := true,
      Test / scalaJSUseMainModuleInitializer    := false,
      webpackEmitSourceMaps                     := true
    )

lazy val stLib = project
  .in(file("chuti-stLib"))
  .enablePlugins(ScalablyTypedConverterGenSourcePlugin)
  .configure(reactNpmDeps)
  .settings(
    name                     := "chuti-stLib",
    scalaVersion             := "2.13.8",
    useYarn                  := true,
    stOutputPackage          := "net.leibman.chuti",
    stFlavour                := Flavour.ScalajsReact,
    stReactEnableTreeShaking := Selection.All,
    Compile / npmDependencies ++= Seq(
      "semantic-ui-react" -> "2.0.3"
    ),
    scalaJSUseMainModuleInitializer := true,
    /* disabled because it somehow triggers many warnings */
    scalaJSLinkerConfig ~= (_.withSourceMap(false)),
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "core"  % scalajsReactVersion withSources (),
      "com.github.japgolly.scalajs-react" %%% "extra" % scalajsReactVersion withSources ()
    )
  )

lazy val web: Project = project
  .dependsOn(commonJS, stLib)
  .settings(commonSettings)
  .configure(bundlerSettings)
  .configure(withCssLoading)
  .configure(commonWeb)
  .enablePlugins(
    AutomateHeaderPlugin,
    GitVersioning,
    ScalaJSPlugin,
    CalibanPlugin
  )
  .settings(
    name := "chuti-web",
    debugDist := {

      val assets = (ThisBuild / baseDirectory).value / "web" / "src" / "main" / "web"

      val artifacts = (Compile / fastOptJS / webpack).value
      val artifactFolder = (Compile / fastOptJS / crossTarget).value
      val debugFolder = (ThisBuild / baseDirectory).value / "debugDist"

      debugFolder.mkdirs()
      FileUtils.copyDirectory(assets, debugFolder, true)
      artifacts.foreach { artifact =>
        val target = artifact.data.relativeTo(artifactFolder) match {
          case None          => debugFolder / artifact.data.name
          case Some(relFile) => debugFolder / relFile.toString
        }

        println(s"Trying to copy ${artifact.data.toPath} to ${target.toPath}")
        Files.copy(artifact.data.toPath, target.toPath, REPLACE_EXISTING)
      }

      debugFolder
    },
    dist := {
      val assets = (ThisBuild / baseDirectory).value / "web" / "src" / "main" / "web"

      val artifacts = (Compile / fullOptJS / webpack).value
      val artifactFolder = (Compile / fullOptJS / crossTarget).value
      val distFolder = (ThisBuild / baseDirectory).value / "dist"

      distFolder.mkdirs()
      FileUtils.copyDirectory(assets, distFolder, true)
      artifacts.foreach { artifact =>
        val target = artifact.data.relativeTo(artifactFolder) match {
          case None          => distFolder / artifact.data.name
          case Some(relFile) => distFolder / relFile.toString
        }

        println(s"Trying to copy ${artifact.data.toPath} to ${target.toPath}")
        Files.copy(artifact.data.toPath, target.toPath, REPLACE_EXISTING)
      }

      distFolder
    }
  )

lazy val commonWeb: Project => Project =
  _.settings(
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core",
      "io.circe" %%% "circe-generic",
      "io.circe" %%% "circe-parser",
      "io.circe" %%% "circe-literal"
    ).map(_ % circeVersion),
    libraryDependencies ++= Seq(
      "commons-io"                                    % "commons-io"                    % "2.11.0" withSources (),
      "com.github.ghostdogpr" %%% "caliban-client"    % calibanVersion withSources (),
      "dev.zio" %%% "zio"                             % zioVersion withSources (),
      "com.softwaremill.sttp.client" %%% "core"       % "2.3.0" withSources (),
      "com.softwaremill.sttp.client"                 %% "async-http-client-backend-zio" % "2.3.0",
      "ru.pavkin" %%% "scala-js-momentjs"             % "0.10.5" withSources (),
      "io.github.cquiroz" %%% "scala-java-time"       % "2.3.0" withSources (),
      "io.github.cquiroz" %%% "scala-java-time-tzdb"  % "2.3.0" withSources (),
      "org.scala-js" %%% "scalajs-dom"                % "2.1.0" withSources (),
      "com.olvind" %%% "scalablytyped-runtime"        % "2.4.2",
      "com.github.japgolly.scalajs-react" %%% "core"  % "2.1.0" withSources (),
      "com.github.japgolly.scalajs-react" %%% "extra" % "2.1.0" withSources (),
      "com.lihaoyi" %%% "scalatags"                   % "0.11.1" withSources (),
      "com.github.japgolly.scalacss" %%% "core"       % "1.0.0" withSources (),
      "com.github.japgolly.scalacss" %%% "ext-react"  % "1.0.0" withSources (),
      ("org.scala-js" %%% "scalajs-java-securerandom" % "1.0.0").cross(CrossVersion.for3Use2_13)
    ),
    organizationName := "Roberto Leibman",
    startYear        := Some(2020),
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    scalacOptions ++= Seq(
      "-Xsource:3",
      // Feature options
      "-explaintypes", // Explain type errors in more detail.
      "-feature", // Emit warning and location for usages of features that should be imported explicitly.
      "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
      "-language:experimental.macros", // Allow macro definition (besides implementation and application)
      "-language:higherKinds", // Allow higher-kinded types
      "-language:implicitConversions", // Allow definition of implicit functions called views
      "-Ymacro-annotations",
      "-encoding",
      "utf-8",
      // Warnings as errors!
//        "-Xfatal-warnings",

      // Linting options
      "-unchecked", // Enable additional warnings where generated code depends on assumptions.
      "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
      "-Xlint:adapted-args", // Warn if an argument list is modified to match the receiver.
      "-Xlint:constant", // Evaluation of a constant arithmetic expression results in an error.
      "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
      "-Xlint:deprecation", // Emit warning and location for usages of deprecated APIs.
      "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
      "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
      "-Xlint:infer-any", // Warn when a type argument is inferred to be `Any`.
      "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
      "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
      "-Xlint:option-implicit", // Option.apply used implicit view.
      "-Xlint:package-object-classes", // Class or object defined in package object.
      "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
      "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
      "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
      "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
      "-Wdead-code", // Warn when dead code is identified.
      "-Wextra-implicit", // Warn when more than one implicit parameter section is defined.
      "-Wnumeric-widen", // Warn when numerics are widened.
      "-Wunused:implicits", // Warn if an implicit parameter is unused.
      "-Wunused:imports", // Warn if an import selector is not referenced.
      "-Wunused:locals", // Warn if a local definition is unused.
      "-Wunused:params", // Warn if a value parameter is unused.
      "-Wunused:patvars", // Warn if a variable bound in a pattern is unused.
      "-Wunused:privates", // Warn if a private member is unused.
      "-Wvalue-discard", // Warn when non-Unit expression results are unused.
      "-Ybackend-parallelism",
      "8", // Enable paralellisation — change to desired number!
      "-Ycache-plugin-class-loader:last-modified", // Enables caching of classloaders for compiler plugins
      "-Ycache-macro-class-loader:last-modified" // and macro definitions. This can lead to performance improvements.
    ),
    Compile / unmanagedSourceDirectories := Seq((Compile / scalaSource).value),
    Test / unmanagedSourceDirectories    := Seq((Test / scalaSource).value),
    webpackDevServerPort                 := 8009
  )

lazy val withCssLoading: Project => Project =
  _.settings(
    /* custom webpack file to include css */
    webpackConfigFile := Some((ThisBuild / baseDirectory).value / "custom.webpack.config.js"),
    Compile / npmDevDependencies ++= Seq(
      "webpack-merge" -> "4.2.2",
      "css-loader"    -> "3.4.2",
      "style-loader"  -> "1.1.3",
      "file-loader"   -> "5.1.0",
      "url-loader"    -> "4.1.0"
    )
  )

//////////////////////////////////////////////////////////////////////////////////////////////////
// Root project
lazy val root = project
  .in(file("."))
  .aggregate(commonJVM, commonJS, server, web, login, stLib)
  .settings(
    name           := "chuti",
    publish / skip := true,
    version        := "0.1.0",
    headerLicense  := None
  )
