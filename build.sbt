////////////////////////////////////////////////////////////////////////////////////
// Common Stuff

import org.apache.commons.io.FileUtils

import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

lazy val buildTime: SettingKey[String] = SettingKey[String]("buildTime", "time of build").withRank(KeyRanks.Invisible)

//////////////////////////////////////////////////////////////////////////////////////////////////
// Global stuff
ThisBuild / resolvers += Resolver.sonatypeCentralSnapshots

lazy val SCALA = "3.8.1"
Global / onChangedBuildSource := ReloadOnSourceChanges
scalaVersion                  := SCALA
Global / scalaVersion         := SCALA

import scala.concurrent.duration.*
Global / watchAntiEntropy := 1.second

ThisBuild / libraryDependencySchemes += "dev.zio" %% "zio-json" % VersionScheme.Always


//////////////////////////////////////////////////////////////////////////////////////////////////
// Shared settings

lazy val start = TaskKey[Unit]("start")
lazy val dist = TaskKey[File]("dist")
lazy val debugDist = TaskKey[File]("debugDist")

lazy val scala3Opts = Seq(
  "-Wconf:msg=Implicit parameters should be provided with a `using` clause:s",
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-no-indent", // scala3
  "-old-syntax", // I hate space sensitive languages!
  "-encoding",
  "utf-8", // Specify character encoding used by source files.
  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
  "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
  "-language:implicitConversions",
  "-language:higherKinds", // Allow higher-kinded types
  //  "-language:strictEquality", //This is cool, but super noisy
  "-unchecked", // Enable additional warnings where generated code depends on assumptions.
//  "-Wsafe-init", //Great idea, breaks compile though.
  "-Xmax-inlines",
  "128",
  //  "-explain-types", // Explain type errors in more detail.
  //  "-explain",
  "-Yexplicit-nulls", // Make reference types non-nullable. Nullable types can be expressed with unions: e.g. String|Null.
  "-Yretain-trees" // Retain trees for debugging.,
)

enablePlugins(
  com.github.sbt.git.GitVersioning
)

val betterFilesVersion = "3.9.2"
val calibanClientVersion = "3.0.0"
val calibanVersion = "3.0.0"
val commonsCodecVersion = "1.21.0"
val courierVersion = "4.0.0-RC1"
val flywayVersion = "12.0.1"
val izumiReflectVersion = "3.0.9"
val jsoniterVersion = "2.38.8"
val justSemverCoreVersion = "1.1.1"
val jwtCirceVersion = "11.0.3"
val jwtZioJsonVersion = "11.0.3"
val langchain4jOllamaVersion = "1.11.0"
val langchainCoreVersion = "1.11.0"
val langchainLibrariesVersion = "1.11.0-beta19"
val logbackVersion = "1.5.32"
val mariadbVersion = "3.5.7"
val openPdfVersion = "3.0.0"
val qdrantVersion = "1.21.4"
val quillVersion = "4.8.6"
val scalablytypedRuntimeVersion = "2.4.2"
val scalacssVersion = "1.0.0"
val scalaJavaLocaleVersion = "1.5.4"
val scalaJavaTimeVersion = "2.6.0"
val scalajsDomVersion = "2.8.1"
val scalajsReactVersion = "3.0.0"
val scalatagsVersion = "0.13.1"
val scalaXmlVersion = "2.4.0"
val stlibVersion = "1.0.0"
val sttpClient4Version = "4.0.18"
val testContainerVersion = "0.44.1"
val zioAuth = "3.1.2"
val zioCacheVersion = "0.2.7"
val zioConfigVersion = "4.0.6"
val zioHttpVersion = "3.8.1"
val zioJsonVersion = "0.9.0"
val zioLoggingSlf4j2Version = "2.5.3"
val zioNioVersion = "2.0.2"
val zioPreludeVersion = "1.0.0-RC46"
val zioSchemaVersion = "1.8.0"
val zioVersion = "2.1.24"

lazy val commonSettings = Seq(
  organization       := "net.leibman",
  startYear          := Some(2024),
  organizationName   := "Roberto Leibman",
  git.useGitDescribe := true,
  headerLicense      := Some(HeaderLicense.ALv2("2020", "Roberto Leibman", HeaderLicenseStyle.Detailed)),
  scalacOptions ++= scala3Opts,
  resolvers += Resolver.mavenLocal
)

////////////////////////////////////////////////////////////////////////////////////
// Model
lazy val modelJVM = model.jvm
lazy val modelJS = model.js

lazy val model = crossProject(JSPlatform, JVMPlatform)
  .enablePlugins(
    AutomateHeaderPlugin,
    com.github.sbt.git.GitVersioning,
    BuildInfoPlugin
  )
  .jvmSettings(scalacOptions ++= scala3Opts :+ "-Werror")
  .jsSettings(scalacOptions ++= scala3Opts)
  .settings(
    name             := "chuti-model",
    buildInfoPackage := "chuti",
    commonSettings,
    libraryDependencies ++= Seq(
      "net.leibman" % "zio-auth_3" % zioAuth withSources () // I don't know why %% isn't working.
    )
  )
  .jvmEnablePlugins(com.github.sbt.git.GitVersioning, BuildInfoPlugin)
  .jvmSettings(
    libraryDependencies ++= Seq(
      "dev.zio"     %% "zio"                 % zioVersion withSources (),
      "dev.zio"     %% "zio-nio"             % zioNioVersion withSources (),
      "dev.zio"     %% "zio-config-magnolia" % zioConfigVersion withSources (),
      "dev.zio"     %% "zio-config-typesafe" % zioConfigVersion withSources (),
      "dev.zio"     %% "zio-json"            % zioJsonVersion withSources (),
      "dev.zio"     %% "zio-prelude"         % zioPreludeVersion withSources (),
      "dev.zio"     %% "zio-http"            % zioHttpVersion withSources (),
      "io.getquill" %% "quill-jdbc-zio"      % quillVersion withSources (),
      "io.kevinlee" %% "just-semver-core"    % justSemverCoreVersion withSources ()
    )
  )
  .jsEnablePlugins(com.github.sbt.git.GitVersioning, BuildInfoPlugin)
  .jsSettings(
    libraryDependencies ++= Seq(
      "net.leibman"               % "zio-auth_sjs1_3" % zioAuth withSources (), // I don't know why %% isn't working.
      "dev.zio" %%% "zio"         % zioVersion withSources (),
      "dev.zio" %%% "zio-json"    % zioJsonVersion withSources (),
      "dev.zio" %%% "zio-prelude" % zioPreludeVersion withSources (),
      "io.kevinlee" %%% "just-semver-core"                                % justSemverCoreVersion withSources (),
      "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-core"   % jsoniterVersion,
      "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-macros" % jsoniterVersion
    )
  )

////////////////////////////////////////////////////////////////////////////////////
// Analytics
lazy val analyticsJVM = analytics.jvm
lazy val analyticsJS = analytics.js

lazy val analytics = crossProject(JSPlatform, JVMPlatform)
  .enablePlugins(
    // AutomateHeaderPlugin,
    com.github.sbt.git.GitVersioning
  )
  .jvmSettings(scalacOptions ++= scala3Opts :+ "-Werror")
  .jsSettings(scalacOptions ++= scala3Opts)
  .settings(
    name             := "chuti-analytics",
    commonSettings,
    libraryDependencies ++= Seq(
      "net.leibman" % "zio-auth_3" % zioAuth withSources () // I don't know why %% isn't working.
    )
  )
  .jvmEnablePlugins(com.github.sbt.git.GitVersioning)
  .jvmSettings(
    libraryDependencies ++= Seq(
      "dev.zio"     %% "zio"                 % zioVersion withSources (),
      "dev.zio"     %% "zio-nio"             % zioNioVersion withSources (),
      "dev.zio"     %% "zio-config-magnolia" % zioConfigVersion withSources (),
      "dev.zio"     %% "zio-config-typesafe" % zioConfigVersion withSources (),
      "dev.zio"     %% "zio-json"            % zioJsonVersion withSources (),
      "dev.zio"     %% "zio-prelude"         % zioPreludeVersion withSources (),
      "dev.zio"     %% "zio-http"            % zioHttpVersion withSources (),
      "io.getquill" %% "quill-jdbc-zio"      % quillVersion withSources (),
      "io.kevinlee" %% "just-semver-core"    % justSemverCoreVersion withSources ()
    )
  )
  .jvmConfigure(_.dependsOn(modelJVM))
  .jsEnablePlugins(com.github.sbt.git.GitVersioning)
  .jsSettings(
    libraryDependencies ++= Seq(
      "net.leibman"               % "zio-auth_sjs1_3" % zioAuth withSources (), // I don't know why %% isn't working.
      "dev.zio" %%% "zio"         % zioVersion withSources (),
      "dev.zio" %%% "zio-json"    % zioJsonVersion withSources (),
      "dev.zio" %%% "zio-prelude" % zioPreludeVersion withSources (),
      "io.kevinlee" %%% "just-semver-core"                                % justSemverCoreVersion withSources (),
      "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-core"   % jsoniterVersion,
      "com.github.plokhotnyuk.jsoniter-scala" %%% "jsoniter-scala-macros" % jsoniterVersion
    )
  )

//////////////////////////////////////////////////////////////////////////////////////////////////
// Server
lazy val server = project
  .enablePlugins(
    AutomateHeaderPlugin,
    com.github.sbt.git.GitVersioning,
    LinuxPlugin,
    DebianPlugin,
    DebianDeployPlugin,
    JavaServerAppPackaging,
    SystemloaderPlugin,
    SystemdPlugin,
    CalibanPlugin
  )
  .settings(debianSettings, commonSettings)
  .dependsOn(modelJVM, ai, analyticsJVM)
  .settings(
    scalacOptions ++= scala3Opts :+ "-Werror",
    name             := "chuti-server",
    libraryDependencies ++= Seq(
      // DB
      "org.mariadb.jdbc" % "mariadb-java-client" % mariadbVersion withSources (),
      "io.getquill"     %% "quill-jdbc-zio"      % quillVersion withSources (),
      "org.flywaydb"     % "flyway-core"         % flywayVersion withSources (),
      "org.flywaydb"     % "flyway-mysql"        % flywayVersion withSources (),
      // Log
      "ch.qos.logback" % "logback-classic" % logbackVersion withSources (),
      // ZIO
      "dev.zio"                       %% "zio"                   % zioVersion withSources (),
      "dev.zio"                       %% "zio-nio"               % zioNioVersion withSources (),
      "dev.zio"                       %% "zio-cache"             % zioCacheVersion withSources (),
      "dev.zio"                       %% "zio-config"            % zioConfigVersion withSources (),
      "dev.zio"                       %% "zio-config-derivation" % zioConfigVersion withSources (),
      "dev.zio"                       %% "zio-config-magnolia"   % zioConfigVersion withSources (),
      "dev.zio"                       %% "zio-config-typesafe"   % zioConfigVersion withSources (),
      "dev.zio"                       %% "zio-logging-slf4j2"    % zioLoggingSlf4j2Version withSources (),
      "dev.zio"                       %% "izumi-reflect"         % izumiReflectVersion withSources (),
      "com.github.ghostdogpr"         %% "caliban"               % calibanVersion withSources (),
      "com.github.ghostdogpr"         %% "caliban-quick"         % calibanVersion withSources (),
      "dev.zio"                       %% "zio-http"              % zioHttpVersion withSources (),
      "com.github.jwt-scala"          %% "jwt-circe"             % jwtCirceVersion withSources (),
      "com.github.jwt-scala"          %% "jwt-zio-json"          % jwtZioJsonVersion withSources (),
      "dev.zio"                       %% "zio-json"              % zioJsonVersion withSources (),
      "com.softwaremill.sttp.client4" %% "core"                  % sttpClient4Version withSources (),
      "com.softwaremill.sttp.client4" %% "zio"                   % sttpClient4Version withSources (),
      "com.softwaremill.sttp.client4" %% "zio-json"              % sttpClient4Version withSources (),
      "org.scala-lang.modules"        %% "scala-xml"             % scalaXmlVersion withSources (),
      // Other random utilities
      "com.github.pathikrit"  %% "better-files"                   % betterFilesVersion withSources (),
      "com.github.daddykotex" %% "courier"                        % courierVersion withSources (),
      "commons-codec"          % "commons-codec"                  % commonsCodecVersion,
      "com.dimafeng"          %% "testcontainers-scala-scalatest" % testContainerVersion withSources (),
      "com.dimafeng"          %% "testcontainers-scala-mariadb"   % testContainerVersion withSources (),
      // Testing
      "dev.zio"       %% "zio-test"     % zioVersion % "test" withSources (),
      "dev.zio"       %% "zio-test-sbt" % zioVersion % "test" withSources (),
      "org.scalatest" %% "scalatest"    % "3.2.19"   % "test" withSources ()
    ),
    Test / fork := true,
    Test / testGrouping := {
      val tests = (Test / definedTests).value
      val quillTests = tests.filter(_.name.startsWith("db.quill"))
      val mailTests = tests.filter(_.name.startsWith("mail."))
      val otherTests = tests.filterNot(t => t.name.startsWith("db.quill") || t.name.startsWith("mail."))
      Seq(
        Tests.Group("quill", quillTests, Tests.SubProcess(ForkOptions())),
        Tests.Group("mail", mailTests, Tests.SubProcess(ForkOptions())),
        Tests.Group("other", otherTests, Tests.SubProcess(ForkOptions()))
      )
    }
  )

lazy val debianSettings =
  Seq(
    Compile / mainClass         := Some("chuti.api.Chuti"),
    Debian / name               := "chuti-server",
    Debian / packageDescription := "El Juego de Chuti",
    Debian / packageSummary     := "El Juego de Chuti",
    Debian / debianChangelog    := Some(file("debian/changelog")),
    Linux / maintainer          := "Roberto Leibman <roberto@leibman.net>",
    Linux / daemonUser          := "chuti",
    Linux / daemonGroup         := "chuti",
    Debian / serverLoading      := Some(ServerLoader.Systemd),
    // Configure JVM to use logback.xml from /etc/chuti-server
    Universal / javaOptions ++= Seq(
      "-Dlogback.configurationFile=/etc/chuti-server/logback.xml"
    ),
    // Map application.conf template
    Universal / mappings += {
      val src = sourceDirectory.value
      val conf = src / "templates" / "application.conf"
      conf -> "conf/application.conf"
    },
    // Map logback.xml template
    Universal / mappings += {
      val src = sourceDirectory.value
      val logback = src / "templates" / "logback.xml"
      logback -> "conf/logback.xml"
    },
    // Map the entire dist directory to /data/www/www.chuti.fun/html
    Universal / mappings ++= {
      val distDir = (ThisBuild / baseDirectory).value / "dist"
      if (distDir.exists()) {
        (distDir.allPaths --- distDir) pair Path.rebase(distDir, "www/")
      } else {
        Seq.empty
      }
    },
    // Install www content to the web directory
    Linux / defaultLinuxInstallLocation := "/opt",
    // Additional package mapping for www content
    Debian / linuxPackageMappings += {
      val distDir = (ThisBuild / baseDirectory).value / "dist"
      packageMapping(
        (distDir.allPaths --- distDir).get.map { f =>
          f -> s"/data/www/www.chuti.fun/html/${Path.relativeTo(distDir)(f).get}"
        }: _*
      ).withUser("chuti").withGroup("chuti")
    },
    // Install configuration files to /etc/chuti-server/ for easy editing
    Debian / linuxPackageMappings += {
      val src = sourceDirectory.value
      val confFile = src / "templates" / "application.conf"
      val logbackFile = src / "templates" / "logback.xml"
      packageMapping(
        confFile    -> "/etc/chuti-server/application.conf",
        logbackFile -> "/etc/chuti-server/logback.xml"
      ).withUser("chuti").withGroup("chuti").withPerms("0644").withConfig()
    },
    // Add custom maintainer scripts to create log directory
    Debian / maintainerScripts := {
      val scripts:  Map[String, Seq[String]] = (Debian / maintainerScripts).value
      val postinst: Seq[String] = scripts.getOrElse("postinst", Seq.empty)

      // Add log directory creation before chown commands
      val updatedPostinst: Seq[String] = postinst.map { line =>
        if (line.contains("chown chuti:chuti '/var/log/chuti-server'")) {
          Seq(
            "mkdir -p '/var/log/chuti-server'",
            "chown -R chuti:chuti '/var/log/chuti-server'",
            "chmod 755 '/var/log/chuti-server'"
          ).mkString("\n")
        } else {
          line
        }
      }

      scripts + ("postinst" -> updatedPostinst)
    }
  )

////////////////////////////////////////////////////////////////////////////////////
// AI
lazy val ai = project
  .enablePlugins(
    // AutomateHeaderPlugin,
    com.github.sbt.git.GitVersioning
  )
  .settings(commonSettings)
  .dependsOn(modelJVM)
  .settings(
    scalacOptions ++= scala3Opts :+ "-Werror",
    name := "chuti-ai",
    libraryDependencies ++= Seq(
      // ZIO
      "dev.zio" %% "zio"     % zioVersion withSources (),
      "dev.zio" %% "zio-nio" % "2.0.2" withSources (),
      // AI stuff
      "com.dimafeng"      %% "testcontainers-scala-core" % testContainerVersion withSources (),
      "org.testcontainers" % "qdrant"                    % qdrantVersion withSources (),
      "dev.langchain4j"    % "langchain4j-core"          % langchainCoreVersion withSources (),
      "dev.langchain4j"    % "langchain4j"               % langchainCoreVersion withSources (),
      "dev.langchain4j"    % "langchain4j-ollama"        % langchain4jOllamaVersion withSources (),
      "dev.langchain4j"    % "langchain4j-easy-rag"      % langchainLibrariesVersion withSources (),
      "dev.langchain4j"    % "langchain4j-qdrant"        % langchainLibrariesVersion withSources (),
      // Other random utilities
      "com.github.pathikrit" %% "better-files"    % betterFilesVersion withSources (),
      "ch.qos.logback"        % "logback-classic" % logbackVersion withSources (),
      // Testing
      "dev.zio" %% "zio-test"     % zioVersion % "test" withSources (),
      "dev.zio" %% "zio-test-sbt" % zioVersion % "test" withSources ()
    )
  )

////////////////////////////////////////////////////////////////////////////////////
// Web
lazy val bundlerSettings: Project => Project =
  _.enablePlugins(ScalaJSBundlerPlugin)
    .settings(
      webpack / version := "5.96.1",
      Compile / fastOptJS / artifactPath := ((Compile / fastOptJS / crossTarget).value /
        ((fastOptJS / moduleName).value + "-opt.js")),
      Compile / fullOptJS / artifactPath := ((Compile / fullOptJS / crossTarget).value /
        ((fullOptJS / moduleName).value + "-opt.js")),
      useYarn                                   := true,
      run / fork                                := true,
      Global / scalaJSStage                     := FastOptStage,
      Compile / scalaJSUseMainModuleInitializer := true,
      Test / scalaJSUseMainModuleInitializer    := false,
      Compile / npmDependencies ++= Seq(
      )
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

lazy val commonWeb: Project => Project =
  _.settings(
    libraryDependencies ++= Seq(
      "net.leibman" %%% "chuti-stlib"                 % stlibVersion withSources (),
      "com.github.ghostdogpr" %%% "caliban-client"    % calibanClientVersion withSources (),
      "dev.zio" %%% "zio"                             % zioVersion withSources (),
      "com.softwaremill.sttp.client4" %%% "core"      % sttpClient4Version withSources (),
      "com.softwaremill.sttp.client4" %%% "zio-json"  % sttpClient4Version withSources (),
      "io.github.cquiroz" %%% "scala-java-time"       % scalaJavaTimeVersion withSources (),
      "io.github.cquiroz" %%% "scala-java-time-tzdb"  % scalaJavaTimeVersion withSources (),
      "io.github.cquiroz" %%% "scala-java-locales"    % scalaJavaLocaleVersion withSources (),
      "org.scala-js" %%% "scalajs-dom"                % scalajsDomVersion withSources (),
      "com.olvind" %%% "scalablytyped-runtime"        % scalablytypedRuntimeVersion,
      "com.github.japgolly.scalajs-react" %%% "core"  % scalajsReactVersion withSources (),
      "com.github.japgolly.scalajs-react" %%% "extra" % scalajsReactVersion withSources (),
      "com.lihaoyi" %%% "scalatags"                   % scalatagsVersion withSources (),
      "com.github.japgolly.scalacss" %%% "core"       % scalacssVersion withSources (),
      "com.github.japgolly.scalacss" %%% "ext-react"  % scalacssVersion withSources (),
      // Testing
      "dev.zio" %%% "zio-test"                       % zioVersion          % "test" withSources (),
      "dev.zio" %%% "zio-test-sbt"                   % zioVersion          % "test" withSources (),
      "com.github.japgolly.scalajs-react" %%% "test" % scalajsReactVersion % "test" withSources ()
    ),
    dependencyOverrides ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "core"  % scalajsReactVersion,
      "com.github.japgolly.scalajs-react" %%% "extra" % scalajsReactVersion
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    organizationName                     := "Roberto Leibman",
    startYear                            := Some(2024),
    Compile / unmanagedSourceDirectories := Seq((Compile / scalaSource).value),
    Test / unmanagedSourceDirectories    := Seq((Test / scalaSource).value)
    //    webpackDevServerPort                 := 8009
  )

lazy val web: Project = project
  .dependsOn(modelJS)
  .configure(bundlerSettings)
  .configure(withCssLoading)
  .configure(commonWeb)
  .settings(commonSettings)
  .enablePlugins(
    AutomateHeaderPlugin,
    com.github.sbt.git.GitVersioning,
    ScalaJSPlugin
  )
  .settings(
    scalacOptions ++= scala3Opts,
    name := "chuti-web",
    libraryDependencies ++= Seq(
      "dev.zio" %%% "zio"      % zioVersion withSources (),
      "dev.zio" %%% "zio-json" % zioJsonVersion withSources ()
    ),
    debugDist := {

      val assets = (ThisBuild / baseDirectory).value / "web" / "src" / "main" / "web"

      val artifacts = (Compile / fastOptJS / webpack).value
      val artifactFolder = (Compile / fastOptJS / crossTarget).value
      val debugFolder = (ThisBuild / baseDirectory).value / "debugDist"

      debugFolder.mkdirs()
      FileUtils.copyDirectory(assets, debugFolder, true)

      // Copy all JS and map files from webpack output directory (including chunks)
      val webpackOutputDir = artifactFolder
      if (webpackOutputDir.exists()) {
        println(s"Copying webpack output from: $webpackOutputDir")
        val allBundles = (webpackOutputDir * "*.js").get ++ (webpackOutputDir * "*.js.map").get
        allBundles.foreach { bundleFile =>
          val targetFile = debugFolder / bundleFile.name
          Files.copy(bundleFile.toPath, targetFile.toPath, REPLACE_EXISTING)
        }
      } else {
        println(s"Webpack output directory does not exist: $webpackOutputDir")
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

      // Copy all JS and map files from webpack output directory (including chunks)
      val webpackOutputDir = artifactFolder
      if (webpackOutputDir.exists()) {
        println(s"Copying webpack output from: $webpackOutputDir")
        val allBundles = (webpackOutputDir * "*.js").get ++ (webpackOutputDir * "*.js.map").get
        allBundles.foreach { bundleFile =>
          val targetFile = distFolder / bundleFile.name
          Files.copy(bundleFile.toPath, targetFile.toPath, REPLACE_EXISTING)
        }
      } else {
        println(s"Webpack output directory does not exist: $webpackOutputDir")
      }

      distFolder
    }
  )

//////////////////////////////////////////////////////////////////////////////////////////////////
// Root project
lazy val root = project
  .in(file("."))
  .aggregate(modelJVM, modelJS, server, web)
  .settings(
    name           := "chuti",
    publish / skip := true,
    version        := "0.1.0",
    headerLicense  := Some(HeaderLicense.ALv2("2020", "Roberto Leibman", HeaderLicenseStyle.Detailed))
  )
