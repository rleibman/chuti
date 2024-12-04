////////////////////////////////////////////////////////////////////////////////////
// Common Stuff
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import org.apache.commons.io.FileUtils

//////////////////////////////////////////////////////////////////////////////////////////////////
// Global stuff
Global / onChangedBuildSource := ReloadOnSourceChanges

//////////////////////////////////////////////////////////////////////////////////////////////////
// Shared settings

lazy val start = TaskKey[Unit]("start")
lazy val dist = TaskKey[File]("dist")
lazy val debugDist = TaskKey[File]("debugDist")

lazy val scala3Opts = Seq(
  "-no-indent", // scala3
  "-old-syntax", // scala3
  "-encoding",
  "utf-8", // Specify character encoding used by source files.
  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
  "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
  "-language:implicitConversions",
  "-language:higherKinds", // Allow higher-kinded types
  //  "-language:strictEquality",
  "-unchecked", // Enable additional warnings where generated code depends on assumptions.
  "-Xfatal-warnings", // Fail the compilation if there are any warnings.
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  //  "-explain-types", // Explain type errors in more detail.
  //  "-explain",
  "-Yexplicit-nulls", // Make reference types non-nullable. Nullable types can be expressed with unions: e.g. String|Null.
  "-Xmax-inlines",
  "128",
  "-Yretain-trees" // Retain trees for debugging.,
)

enablePlugins(
  GitVersioning,
  CalibanPlugin
)

val circeVersion = "0.14.10"
val calibanVersion = "2.9.0"
val zioVersion = "2.1.13"
val quillVersion = "4.8.6"
val zioHttpVersion = "3.0.1"
val zioConfigVersion = "4.0.2"
val zioJsonVersion = "0.7.3"
val testContainerVersion = "0.41.4"

lazy val commonSettings = Seq(
  organization                     := "net.leibman",
  scalaVersion                     := "3.5.2",
  startYear                        := Some(2020),
  organizationName                 := "Roberto Leibman",
  headerLicense                    := Some(HeaderLicense.ALv2("2020", "Roberto Leibman", HeaderLicenseStyle.Detailed)),
  libraryDependencies += "dev.zio" %% "zio" % zioVersion withSources (),
  libraryDependencies ++= Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser",
    "io.circe" %% "circe-literal"
  ).map(_ % circeVersion),
  scalacOptions ++= scala3Opts
)

lazy val commonVmSettings = commonSettings

////////////////////////////////////////////////////////////////////////////////////
// common (i.e. model)
lazy val commonJVM = common.jvm
lazy val commonJS = common.js

lazy val common = crossProject(JSPlatform, JVMPlatform)
  .enablePlugins(
    AutomateHeaderPlugin,
    GitVersioning,
    BuildInfoPlugin
  )
  .settings(
    scalaVersion     := "3.1.3",
    name             := "chuti-common",
    buildInfoPackage := "chuti"
  )
  .jvmSettings(
    commonVmSettings
  )
  .jsSettings(
    commonSettings
  )

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
  .settings(debianSettings)
  .settings(commonVmSettings)
  .dependsOn(commonJVM)
  .settings(
    name := "chuti-server",
//    libraryDependencySchemes += "org.scala-lang.modules" %% "scala-java8-compat" % "always",
    libraryDependencies ++= Seq(
      // DB
      "mysql"        % "mysql-connector-java" % "8.0.33" withSources (),
      "io.getquill" %% "quill-jdbc-zio"       % quillVersion withSources (),
      // ZIO
      "dev.zio"               %% "zio"                   % zioVersion withSources (),
      "dev.zio"               %% "zio-nio"               % "2.0.2" withSources (),
      "dev.zio"               %% "zio-cache"             % "0.2.3" withSources (),
      "dev.zio"               %% "zio-config"            % zioConfigVersion withSources (),
      "dev.zio"               %% "zio-config-derivation" % zioConfigVersion withSources (),
      "dev.zio"               %% "zio-config-magnolia"   % zioConfigVersion withSources (),
      "dev.zio"               %% "zio-config-typesafe"   % zioConfigVersion withSources (),
      "dev.zio"               %% "zio-logging-slf4j"     % "2.4.0" withSources (),
      "dev.zio"               %% "izumi-reflect"         % "2.3.10" withSources (),
      "dev.zio"               %% "zio-json"              % zioJsonVersion withSources (),
      "com.github.ghostdogpr" %% "caliban"               % calibanVersion withSources (),
      "com.github.ghostdogpr" %% "caliban-tapir"         % calibanVersion withSources (),
      "com.github.ghostdogpr" %% "caliban-zio-http"      % calibanVersion withSources (),
      "dev.zio"               %% "zio-http"              % zioHttpVersion withSources (),
      "com.github.jwt-scala"  %% "jwt-circe"             % "10.0.1" withSources (),
      // Other random utilities
      ("com.github.pathikrit" %% "better-files"    % "3.9.2" withSources ()).cross(CrossVersion.for3Use2_13),
      "com.github.daddykotex" %% "courier"         % "3.2.0" withSources (),
      "ch.qos.logback"         % "logback-classic" % "1.5.12" withSources (),
      "commons-codec"          % "commons-codec"   % "1.17.1",
      // Testing
      "dev.zio"       %% "zio-test"                       % zioVersion           % "it, test" withSources (),
      "dev.zio"       %% "zio-test-sbt"                   % zioVersion           % "it, test" withSources (),
      "org.scalatest" %% "scalatest"                      % "3.2.19"             % "it, test" withSources (),
      "com.dimafeng"  %% "testcontainers-scala-scalatest" % testContainerVersion % "it, test" withSources (),
      "com.dimafeng"  %% "testcontainers-scala-mysql"     % testContainerVersion % "it, test" withSources ()
//      "io.d11" %% "zhttp-test" % zioHttpVersion % "it, test" withSources()
    ),
    testFrameworks ++= Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
  )

lazy val login: Project = project
  .dependsOn(commonJS)
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
val scalajsReactVersion = "2.1.2"
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
      Compile / npmDependencies ++= Seq(
      ),
      webpack / version               := "5.96.1",
      startWebpackDevServer / version := "3.1.10",
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
      webpackEmitSourceMaps                     := false,
      scalaJSLinkerConfig ~= { a =>
        a.withSourceMap(true) // .withRelativizeSourceMapBase(None)
      }
    )

lazy val web: Project = project
  .dependsOn(commonJS)
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

//        println(s"Trying to copy ${artifact.data.toPath} to ${target.toPath}")
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

//        println(s"Trying to copy ${artifact.data.toPath} to ${target.toPath}")
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
      "net.leibman" %%% "chuti-stlib"              % "0.2.0-SNAPSHOT" withSources (),
      "commons-io"                                 % "commons-io" % "2.11.0" withSources (),
      "com.github.ghostdogpr" %%% "caliban-client" % calibanVersion withSources (),
      "dev.zio" %%% "zio"                          % zioVersion withSources (),
      "com.softwaremill.sttp.client3" %%% "core"   % "3.10.1" withSources (),
      //      ("com.softwaremill.sttp.model" %%% "core" % "1.4.27" withSources()).cross(CrossVersion.for3Use2_13),
      //      ("com.softwaremill.sttp.client" %%% "core" % "2.3.0" withSources()).cross(CrossVersion.for3Use2_13),
      //      ("com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % "2.3.0").cross(CrossVersion.for3Use2_13),
      //      ("ru.pavkin" %%% "scala-js-momentjs" % "0.10.5" withSources()).cross(CrossVersion.for3Use2_13),
      "io.github.cquiroz" %%% "scala-java-time"                           % "2.6.0" withSources (),
      "io.github.cquiroz" %%% "scala-java-time-tzdb"                      % "2.6.0" withSources (),
      "org.scala-js" %%% "scalajs-dom"                                    % "2.8.0" withSources (),
      "com.olvind" %%% "scalablytyped-runtime"                            % "2.4.2",
      "com.github.japgolly.scalajs-react" %%% "core"                      % scalajsReactVersion withSources (),
      "com.github.japgolly.scalajs-react" %%% "extra"                     % scalajsReactVersion withSources (),
      "com.lihaoyi" %%% "scalatags"                                       % "0.13.1" withSources (),
      "com.github.japgolly.scalacss" %%% "core"                           % "1.0.0" withSources (),
      "com.github.japgolly.scalacss" %%% "ext-react"                      % "1.0.0" withSources (),
      "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-core"   % "2.31.3",
      "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-macros" % "2.31.3"

      //      ("org.scala-js" %%% "scalajs-java-securerandom" % "1.0.0").cross(CrossVersion.for3Use2_13)
    ),
    organizationName := "Roberto Leibman",
    startYear        := Some(2020),
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
    Compile / unmanagedSourceDirectories := Seq((Compile / scalaSource).value),
    Test / unmanagedSourceDirectories    := Seq((Test / scalaSource).value)
//    webpackDevServerPort                 := 8009
  )

lazy val withCssLoading: Project => Project =
  _.settings(
    /* custom webpack file to include css */
    webpackConfigFile := Some((ThisBuild / baseDirectory).value / "custom.webpack.config.js"),
    Compile / npmDevDependencies ++= Seq(
      "webpack-merge" -> "6.0.1",
      "css-loader"    -> "7.1.2",
      "style-loader"  -> "4.0.0",
      "file-loader"   -> "6.2.0",
      "url-loader"    -> "4.1.1"
    )
  )

//////////////////////////////////////////////////////////////////////////////////////////////////
// Root project
lazy val root = project
  .in(file("."))
  .aggregate(commonJVM, commonJS, server, web, login)
  .settings(
    name           := "chuti",
    publish / skip := true,
    version        := "0.1.0",
    headerLicense  := None
  )
