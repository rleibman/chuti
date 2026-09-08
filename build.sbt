////////////////////////////////////////////////////////////////////////////////////
// Common Stuff

import org.apache.commons.io.FileUtils
import org.scalajs.linker.interface.ModuleSplitStyle

import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

lazy val buildTime: SettingKey[String] = SettingKey[String]("buildTime", "time of build").withRank(KeyRanks.Invisible)

//////////////////////////////////////////////////////////////////////////////////////////////////
// Global stuff
// All the keys sbt's lintUnused reports are defined by PLUGINS, not by this build: sbt-git sets
// gitDescribedVersion/useGitDescribe on projects that do not version off git, and sbt-native-packager wires up
// the Rpm/Debian/Universal-docs/Universal-src scopes even though only a .deb is ever built.
// sbt-git sets ThisBuild / version but nothing in this build reads it; that is the plugin's business, not a
// mistake here, and sbt 2's lintUnused check has no other way to be told so.
Global / excludeLintKeys += version

Global / excludeLintKeys ++= Set(
  com.github.sbt.git.SbtGit.GitKeys.gitDescribedVersion,
  com.github.sbt.git.SbtGit.GitKeys.useGitDescribe,
  daemonUser,
  daemonUserUid,
  daemonGroup,
  daemonGroupGid,
  executableScriptName,
  javaOptions,
  name,
)

// NOT benign: chuti-stlib was generated against scalajs-react 2.1.1 while web asks for 3.0.0 -- a major-version
// straddle. Declared rather than failing the build, so sbt 2's stricter evictionErrorLevel still catches anything
// NEW. The real fix is regenerating stlib against 3.0.0. `%%` would only cover the JVM artifact, hence _sjs1_3.
// NOT benign: chuti-stlib was generated against scalajs-react 3.0.0 while zio-auth 3.1.7 and web pull 4.0.0 --
// a major-version straddle across every scalajs-react module. Declared rather than failing the build, so sbt 2's
// stricter evictionErrorLevel still catches anything NEW. The real fix is regenerating stlib against 4.0.0.
// `%%` would only cover the JVM artifacts, hence the explicit _sjs1_3 names.
ThisBuild / libraryDependencySchemes ++= Seq(
  "com.github.japgolly.scalajs-react" % "core_sjs1_3"         % VersionScheme.Always,
  "com.github.japgolly.scalajs-react" % "core-generic_sjs1_3" % VersionScheme.Always,
  "com.github.japgolly.scalajs-react" % "extra_sjs1_3"        % VersionScheme.Always,
  "com.github.japgolly.scalajs-react" % "facade_sjs1_3"       % VersionScheme.Always,
  "com.github.japgolly.scalajs-react" % "facade-test_sjs1_3"  % VersionScheme.Always,
  "com.github.japgolly.scalajs-react" % "test_sjs1_3"         % VersionScheme.Always,
  "com.github.japgolly.scalajs-react" % "test-macros_sjs1_3"  % VersionScheme.Always,
)

ThisBuild / resolvers += Resolver.sonatypeCentralSnapshots

lazy val SCALA = "3.9.0"
Global / onChangedBuildSource := ReloadOnSourceChanges
scalaVersion                  := SCALA
Global / scalaVersion         := SCALA

import scala.concurrent.duration.*
Global / watchAntiEntropy := 1.second

// `%%` expands to the JVM artifact (zio-json_3) only, so the Scala.js side needs naming explicitly -- there is
// no `%%%` under sbt 2. sttp-client4 still asks for an older zio-json than this build uses.
ThisBuild / libraryDependencySchemes ++= Seq(
  "dev.zio" %% "zio-json"        % VersionScheme.Always,
  "dev.zio"  % "zio-json_sjs1_3" % VersionScheme.Always,
)

//////////////////////////////////////////////////////////////////////////////////////////////////
// Shared settings

lazy val start = TaskKey[Unit]("start")
lazy val webDist = TaskKey[File]("webDist")
lazy val webDebugDist = TaskKey[File]("webDebugDist")

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
  "-Yretain-trees", // Retain trees for debugging.,
)

enablePlugins(
  com.github.sbt.git.GitVersioning,
)

val betterFilesVersion = "3.9.2"
val calibanClientVersion = "3.1.5"
val calibanVersion = "3.1.5"
val commonsCodecVersion = "1.22.1"
val courierVersion = "4.0.0-RC1"
val flywayVersion = "13.5.0"
val izumiReflectVersion = "3.0.10"
val jsoniterVersion = "2.40.1"
val justSemverCoreVersion = "1.3.0"
val jwtCirceVersion = "11.0.4"
val jwtZioJsonVersion = "11.0.4"
val langchain4jOllamaVersion = "1.11.0"
val langchainCoreVersion = "1.11.0"
val langchainLibrariesVersion = "1.11.0-beta19"
val logbackVersion = "1.6.3"
val mariadbVersion = "3.5.10"
val openPdfVersion = "3.0.0"
val qdrantVersion = "1.21.4"
val quillVersion = "4.8.6"
val scalablytypedRuntimeVersion = "2.4.2"
val scalacssVersion = "1.0.0"
val scalaJavaLocaleVersion = "1.5.4"
val scalaJavaTimeVersion = "2.7.0"
val scalajsDomVersion = "2.8.1"
val scalajsReactVersion = "3.0.0"
val scalatagsVersion = "0.13.1"
val scalaXmlVersion = "2.4.0"
val stlibVersion = "1.0.0"
val sttpClient4Version = "4.0.26"
val testContainerVersion = "0.44.1"
val zioAuth = "3.1.7"
val zioCacheVersion = "0.2.8"
val zioConfigVersion = "4.0.8"
val zioHttpVersion = "3.11.4"
val zioJsonVersion = "1.0.0"
val zioLoggingSlf4j2Version = "2.5.3"
val zioNioVersion = "2.0.2"
val zioPreludeVersion = "1.0.0-RC48"
val zioSchemaVersion = "1.8.0"
val zioVersion = "2.1.26"

lazy val commonSettings = Seq(
  organization       := "net.leibman",
  startYear          := Some(2024),
  organizationName   := "Roberto Leibman",
  git.useGitDescribe := true,
  headerLicense      := Some(HeaderLicense.ALv2("2020", "Roberto Leibman", HeaderLicenseStyle.Detailed)),
  scalacOptions ++= scala3Opts,
  resolvers += Resolver.mavenLocal,
)

////////////////////////////////////////////////////////////////////////////////////
// Model
lazy val modelJVM = model.jvm
lazy val modelJS = model.js

lazy val model = crossProject(JSPlatform, JVMPlatform)
  .enablePlugins(
    AutomateHeaderPlugin,
    com.github.sbt.git.GitVersioning,
    BuildInfoPlugin,
  )
  .jvmSettings(scalacOptions ++= scala3Opts :+ "-Werror")
  .jsSettings(scalacOptions ++= scala3Opts)
  .settings(
    name             := "chuti-model",
    buildInfoPackage := "chuti",
    commonSettings,
    libraryDependencies ++= Seq(
      ("net.leibman" % "zio-auth_3" % zioAuth).withSources(), // I don't know why %% isn't working.
    ),
  )
  .jvmEnablePlugins(com.github.sbt.git.GitVersioning, BuildInfoPlugin)
  .jvmSettings(
    libraryDependencies ++= Seq(
      ("dev.zio"     %% "zio"                 % zioVersion).withSources(),
      ("dev.zio"     %% "zio-nio"             % zioNioVersion).withSources(),
      ("dev.zio"     %% "zio-config-magnolia" % zioConfigVersion).withSources(),
      ("dev.zio"     %% "zio-config-typesafe" % zioConfigVersion).withSources(),
      ("dev.zio"     %% "zio-json"            % zioJsonVersion).withSources(),
      ("dev.zio"     %% "zio-prelude"         % zioPreludeVersion).withSources(),
      ("dev.zio"     %% "zio-http"            % zioHttpVersion).withSources(),
      ("io.getquill" %% "quill-jdbc-zio"      % quillVersion).withSources(),
      ("io.kevinlee" %% "just-semver-core"    % justSemverCoreVersion).withSources(),
    ),
  )
  .jsEnablePlugins(com.github.sbt.git.GitVersioning, BuildInfoPlugin)
  .jsSettings(
    libraryDependencies ++= Seq(
      ("net.leibman"  % "zio-auth_sjs1_3"  % zioAuth).withSources(), // I don't know why %% isn't working.
      ("dev.zio"     %% "zio"              % zioVersion).withSources(),
      ("dev.zio"     %% "zio-json"         % zioJsonVersion).withSources(),
      ("dev.zio"     %% "zio-prelude"      % zioPreludeVersion).withSources(),
      ("io.kevinlee" %% "just-semver-core" % justSemverCoreVersion).withSources(),
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   % jsoniterVersion,
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % jsoniterVersion,
    ),
  )

////////////////////////////////////////////////////////////////////////////////////
// Analytics
lazy val analyticsJVM = analytics.jvm
lazy val analyticsJS = analytics.js

lazy val analytics = crossProject(JSPlatform, JVMPlatform)
  .enablePlugins(
    // AutomateHeaderPlugin,
    com.github.sbt.git.GitVersioning,
  )
  .jvmSettings(scalacOptions ++= scala3Opts :+ "-Werror")
  .jsSettings(scalacOptions ++= scala3Opts)
  .settings(
    name := "chuti-analytics",
    commonSettings,
    libraryDependencies ++= Seq(
      ("net.leibman" % "zio-auth_3" % zioAuth).withSources(), // I don't know why %% isn't working.
    ),
  )
  .jvmEnablePlugins(com.github.sbt.git.GitVersioning)
  .jvmSettings(
    libraryDependencies ++= Seq(
      ("dev.zio"     %% "zio"                 % zioVersion).withSources(),
      ("dev.zio"     %% "zio-nio"             % zioNioVersion).withSources(),
      ("dev.zio"     %% "zio-config-magnolia" % zioConfigVersion).withSources(),
      ("dev.zio"     %% "zio-config-typesafe" % zioConfigVersion).withSources(),
      ("dev.zio"     %% "zio-json"            % zioJsonVersion).withSources(),
      ("dev.zio"     %% "zio-prelude"         % zioPreludeVersion).withSources(),
      ("dev.zio"     %% "zio-http"            % zioHttpVersion).withSources(),
      ("io.getquill" %% "quill-jdbc-zio"      % quillVersion).withSources(),
      ("io.kevinlee" %% "just-semver-core"    % justSemverCoreVersion).withSources(),
    ),
  )
  .jvmConfigure(_.dependsOn(modelJVM))
  .jsEnablePlugins(com.github.sbt.git.GitVersioning)
  .jsSettings(
    libraryDependencies ++= Seq(
      ("net.leibman"  % "zio-auth_sjs1_3"  % zioAuth).withSources(), // I don't know why %% isn't working.
      ("dev.zio"     %% "zio"              % zioVersion).withSources(),
      ("dev.zio"     %% "zio-json"         % zioJsonVersion).withSources(),
      ("dev.zio"     %% "zio-prelude"      % zioPreludeVersion).withSources(),
      ("io.kevinlee" %% "just-semver-core" % justSemverCoreVersion).withSources(),
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-core"   % jsoniterVersion,
      "com.github.plokhotnyuk.jsoniter-scala" %% "jsoniter-scala-macros" % jsoniterVersion,
    ),
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
    CalibanPlugin,
  )
  .settings(debianSettings, commonSettings)
  .dependsOn(modelJVM, ai, analyticsJVM)
  .settings(
    scalacOptions ++= scala3Opts :+ "-Werror",
    name := "chuti-server",
    libraryDependencies ++= Seq(
      // DB
      ("org.mariadb.jdbc" % "mariadb-java-client" % mariadbVersion).withSources(),
      ("io.getquill"     %% "quill-jdbc-zio"      % quillVersion).withSources(),
      ("org.flywaydb"     % "flyway-core"         % flywayVersion).withSources(),
      ("org.flywaydb"     % "flyway-mysql"        % flywayVersion).withSources(),
      // Log
      ("ch.qos.logback" % "logback-classic" % logbackVersion).withSources(),
      // ZIO
      ("dev.zio"                       %% "zio"                   % zioVersion).withSources(),
      ("dev.zio"                       %% "zio-nio"               % zioNioVersion).withSources(),
      ("dev.zio"                       %% "zio-cache"             % zioCacheVersion).withSources(),
      ("dev.zio"                       %% "zio-config"            % zioConfigVersion).withSources(),
      ("dev.zio"                       %% "zio-config-derivation" % zioConfigVersion).withSources(),
      ("dev.zio"                       %% "zio-config-magnolia"   % zioConfigVersion).withSources(),
      ("dev.zio"                       %% "zio-config-typesafe"   % zioConfigVersion).withSources(),
      ("dev.zio"                       %% "zio-logging-slf4j2"    % zioLoggingSlf4j2Version).withSources(),
      ("dev.zio"                       %% "izumi-reflect"         % izumiReflectVersion).withSources(),
      ("com.github.ghostdogpr"         %% "caliban"               % calibanVersion).withSources(),
      ("com.github.ghostdogpr"         %% "caliban-quick"         % calibanVersion).withSources(),
      ("dev.zio"                       %% "zio-http"              % zioHttpVersion).withSources(),
      ("com.github.jwt-scala"          %% "jwt-circe"             % jwtCirceVersion).withSources(),
      ("com.github.jwt-scala"          %% "jwt-zio-json"          % jwtZioJsonVersion).withSources(),
      ("dev.zio"                       %% "zio-json"              % zioJsonVersion).withSources(),
      ("com.softwaremill.sttp.client4" %% "core"                  % sttpClient4Version).withSources(),
      ("com.softwaremill.sttp.client4" %% "zio"                   % sttpClient4Version).withSources(),
      ("com.softwaremill.sttp.client4" %% "zio-json"              % sttpClient4Version).withSources(),
      ("org.scala-lang.modules"        %% "scala-xml"             % scalaXmlVersion).withSources(),
      // Other random utilities
      ("com.github.pathikrit"  %% "better-files"                   % betterFilesVersion).withSources(),
      ("com.github.daddykotex" %% "courier"                        % courierVersion).withSources(),
      "commons-codec"           % "commons-codec"                  % commonsCodecVersion,
      ("com.dimafeng"          %% "testcontainers-scala-scalatest" % testContainerVersion).withSources(),
      ("com.dimafeng"          %% "testcontainers-scala-mariadb"   % testContainerVersion).withSources(),
      // Testing
      ("dev.zio"       %% "zio-test"     % zioVersion % "test").withSources(),
      ("dev.zio"       %% "zio-test-sbt" % zioVersion % "test").withSources(),
      ("org.scalatest" %% "scalatest"    % "3.2.20"   % "test").withSources(),
    ),
    Test / fork := true,
    // sbt 2 caches task outputs, and Seq[Tests.Group] has no JsonFormat -- there is nothing to serialise here
    // anyway, this just partitions the already-discovered tests.
    Test / testGrouping := Def.uncached {
      val tests = (Test / definedTests).value
      val quillTests = tests.filter(_.name.startsWith("db.quill"))
      val mailTests = tests.filter(_.name.startsWith("mail."))
      val otherTests = tests.filterNot(t => t.name.startsWith("db.quill") || t.name.startsWith("mail."))
      Seq(
        Tests.Group("quill", quillTests, Tests.SubProcess(ForkOptions())),
        Tests.Group("mail", mailTests, Tests.SubProcess(ForkOptions())),
        Tests.Group("other", otherTests, Tests.SubProcess(ForkOptions())),
      )
    },
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
      "-Dlogback.configurationFile=/etc/chuti-server/logback.xml",
    ),
    // Map application.conf template
    Universal / mappings += {
      // sbt 2 keys mappings by HashedVirtualFileRef, not File.
      val conv = fileConverter.value
      val conf = sourceDirectory.value / "templates" / "application.conf"
      conv.toVirtualFile(conf.toPath) -> "conf/application.conf"
    },
    // Map logback.xml template
    Universal / mappings += {
      val conv = fileConverter.value
      val logback = sourceDirectory.value / "templates" / "logback.xml"
      conv.toVirtualFile(logback.toPath) -> "conf/logback.xml"
    },
    // Map the entire dist directory to /data/www/www.chuti.fun/html
    Universal / mappings ++= {
      val conv = fileConverter.value
      val distDir = (ThisBuild / baseDirectory).value / "dist"
      if (distDir.exists()) {
        ((distDir.allPaths --- distDir) pair Path.rebase(distDir, "www/"))
          .map { case (f, path) => conv.toVirtualFile(f.toPath) -> path }
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
        (distDir.allPaths --- distDir).get().map { f =>
          f -> s"/data/www/www.chuti.fun/html/${Path.relativeTo(distDir)(f).get}"
        } *,
      ).withUser("chuti").withGroup("chuti")
    },
    // Install configuration files to /etc/chuti-server/ for easy editing
    Debian / linuxPackageMappings += {
      val src = sourceDirectory.value
      val confFile = src / "templates" / "application.conf"
      val logbackFile = src / "templates" / "logback.xml"
      packageMapping(
        confFile    -> "/etc/chuti-server/application.conf",
        logbackFile -> "/etc/chuti-server/logback.xml",
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
            "chmod 755 '/var/log/chuti-server'",
          ).mkString("\n")
        } else {
          line
        }
      }

      scripts + ("postinst" -> updatedPostinst)
    },
  )

////////////////////////////////////////////////////////////////////////////////////
// AI
lazy val ai = project
  .enablePlugins(
    // AutomateHeaderPlugin,
    com.github.sbt.git.GitVersioning,
  )
  .settings(commonSettings)
  .dependsOn(modelJVM)
  .settings(
    scalacOptions ++= scala3Opts :+ "-Werror",
    name := "chuti-ai",
    libraryDependencies ++= Seq(
      // ZIO
      ("dev.zio" %% "zio"     % zioVersion).withSources(),
      ("dev.zio" %% "zio-nio" % "2.0.2").withSources(),
      // AI stuff
      ("com.dimafeng"      %% "testcontainers-scala-core" % testContainerVersion).withSources(),
      ("org.testcontainers" % "qdrant"                    % qdrantVersion).withSources(),
      ("dev.langchain4j"    % "langchain4j-core"          % langchainCoreVersion).withSources(),
      ("dev.langchain4j"    % "langchain4j"               % langchainCoreVersion).withSources(),
      ("dev.langchain4j"    % "langchain4j-ollama"        % langchain4jOllamaVersion).withSources(),
      ("dev.langchain4j"    % "langchain4j-easy-rag"      % langchainLibrariesVersion).withSources(),
      ("dev.langchain4j"    % "langchain4j-qdrant"        % langchainLibrariesVersion).withSources(),
      // Other random utilities
      ("com.github.pathikrit" %% "better-files"    % betterFilesVersion).withSources(),
      ("ch.qos.logback"        % "logback-classic" % logbackVersion).withSources(),
      // Testing
      ("dev.zio" %% "zio-test"     % zioVersion % "test").withSources(),
      ("dev.zio" %% "zio-test-sbt" % zioVersion % "test").withSources(),
    ),
  )

////////////////////////////////////////////////////////////////////////////////////
// Web
/** Links with Scala.js, bundles with `vite build`, then lays the result out beside the static assets.
  *
  * sbt drives vite, not the reverse: @scala-js/vite-plugin-scalajs resolves the linker output by spawning
  * `sbt print fastLinkJSOutput`, which from inside an sbt task means sbt re-entering itself for a path the caller
  * already holds. The paths go over in the environment instead -- see web/vite.config.js.
  */
def viteDistImpl(
  viteRoot:      File,
  scalaJSOutput: File,
  assets:        File,
  stagingDir:    File,
  outputFolder:  File,
  mode:          String,
  log:           Logger,
): File = {
  import scala.sys.process.*

  if (!(viteRoot / "node_modules").exists()) {
    log.info(s"node_modules missing, running `npm install` in $viteRoot")
    val installed = Process("npm" :: "install" :: Nil, viteRoot).!
    if (installed != 0) sys.error(s"npm install failed in $viteRoot (exit code $installed)")
  }

  val env = Seq(
    "SCALAJS_OUTPUT_DIR" -> scalaJSOutput.getAbsolutePath,
    "VITE_OUT_DIR"       -> stagingDir.getAbsolutePath,
    // Rollup exhausts the default node heap on a bundle this size and dies with
    // "Reached heap limit ... JavaScript heap out of memory" (exit 134), surfacing only as a vite failure.
    "NODE_OPTIONS" -> s"${sys.env.getOrElse("NODE_OPTIONS", "")} --max-old-space-size=8192".trim,
  )
  log.info(s"vite build --mode $mode (scala.js output: $scalaJSOutput)")
  val built = Process("npx" :: "vite" :: "build" :: "--mode" :: mode :: Nil, viteRoot, env *).!
  if (built != 0) sys.error(s"vite build failed in $viteRoot (exit code $built)")

  // Clear ONLY the bundler's output area; the static copy below re-adds anything of ours under assets/.
  val bundleAssets = outputFolder / "assets"
  if (bundleAssets.exists()) FileUtils.deleteDirectory(bundleAssets)
  outputFolder.mkdirs()
  // index.html comes from vite's output (it carries the hashed <script>), so skip the source copy of it.
  if (assets.exists()) {
    assets.listFiles().foreach { f =>
      if (f.getName != "index.html") {
        if (f.isDirectory) FileUtils.copyDirectory(f, outputFolder / f.getName, true)
        else FileUtils.copyFile(f, outputFolder / f.getName)
      }
    }
  }
  FileUtils.copyDirectory(stagingDir, outputFolder, true)
  outputFolder
}

lazy val commonWeb: Project => Project =
  _.settings(
    libraryDependencies ++= Seq(
      // Hand-suffixed: this jar comes from ~/.ivy2/local, where coursier cross-versions the module *directory*
      // to chuti-stlib_sjs1_3 but derives the *jar* name as chuti-stlib_3.jar, which does not exist.
      ("net.leibman"                        % "chuti-stlib_sjs1_3"    % stlibVersion).withSources(),
      ("com.github.ghostdogpr"             %% "caliban-client"        % calibanClientVersion).withSources(),
      ("dev.zio"                           %% "zio"                   % zioVersion).withSources(),
      ("com.softwaremill.sttp.client4"     %% "core"                  % sttpClient4Version).withSources(),
      ("com.softwaremill.sttp.client4"     %% "zio-json"              % sttpClient4Version).withSources(),
      ("io.github.cquiroz"                 %% "scala-java-time"       % scalaJavaTimeVersion).withSources(),
      ("io.github.cquiroz"                 %% "scala-java-time-tzdb"  % scalaJavaTimeVersion).withSources(),
      ("io.github.cquiroz"                 %% "scala-java-locales"    % scalaJavaLocaleVersion).withSources(),
      ("org.scala-js"                      %% "scalajs-dom"           % scalajsDomVersion).withSources(),
      "com.olvind"                         %% "scalablytyped-runtime" % scalablytypedRuntimeVersion,
      ("com.github.japgolly.scalajs-react" %% "core"                  % scalajsReactVersion).withSources(),
      ("com.github.japgolly.scalajs-react" %% "extra"                 % scalajsReactVersion).withSources(),
      ("com.lihaoyi"                       %% "scalatags"             % scalatagsVersion).withSources(),
      ("com.github.japgolly.scalacss"      %% "core"                  % scalacssVersion).withSources(),
      ("com.github.japgolly.scalacss"      %% "ext-react"             % scalacssVersion).withSources(),
      // Testing
      ("dev.zio"                           %% "zio-test"     % zioVersion          % "test").withSources(),
      ("dev.zio"                           %% "zio-test-sbt" % zioVersion          % "test").withSources(),
      ("com.github.japgolly.scalajs-react" %% "test"         % scalajsReactVersion % "test").withSources(),
    ),
    dependencyOverrides ++= Seq(
      "com.github.japgolly.scalajs-react" %% "core"  % scalajsReactVersion,
      "com.github.japgolly.scalajs-react" %% "extra" % scalajsReactVersion,
    ),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    organizationName                     := "Roberto Leibman",
    startYear                            := Some(2024),
    Compile / unmanagedSourceDirectories := Seq((Compile / scalaSource).value),
    Test / unmanagedSourceDirectories    := Seq((Test / scalaSource).value),
    //    webpackDevServerPort                 := 8009
  )

lazy val web: Project = project
  .dependsOn(modelJS)
  .configure(commonWeb)
  .settings(commonSettings)
  .enablePlugins(
    AutomateHeaderPlugin,
    com.github.sbt.git.GitVersioning,
    ScalaJSPlugin,
  )
  .settings(
    scalacOptions ++= scala3Opts,
    // scalajs-react's StBuildingComponent is `inline`, so its body -- including a call to the deprecated
    // scala.scalajs.runtime.linkingInfo -- is reported at every one of our call sites. Nothing here can fix it;
    // it goes away when scalajs-react stops using the deprecated alias. Narrowly matched so any other
    // deprecation still warns.
    scalacOptions += "-Wconf:msg=linkingInfo in package scala.scalajs.runtime is deprecated:s",
    name := "chuti-web",
    libraryDependencies ++= Seq(
      ("dev.zio" %% "zio"      % zioVersion).withSources(),
      ("dev.zio" %% "zio-json" % zioJsonVersion).withSources(),
    ),
    // ES modules, the only module kind vite consumes directly -- this replaces what the bundler plugin set up.
    // SmallModulesFor keeps application code in many small chunks so an incremental fastLinkJS rewrites little.
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(ModuleSplitStyle.SmallModulesFor(List("chuti")))
        .withSourceMap(true)
    },
    run / fork                                := true,
    Global / scalaJSStage                     := FastOptStage,
    Compile / scalaJSUseMainModuleInitializer := true,
    Test / scalaJSUseMainModuleInitializer    := false,
    // webDebugDist: readable stack traces (unminified, mapped back to .scala, React's development build)
    webDebugDist := Def.uncached {
      viteDistImpl(
        viteRoot = baseDirectory.value,
        scalaJSOutput = (Compile / fastLinkJSOutput).value,
        assets = (ThisBuild / baseDirectory).value / "web" / "src" / "main" / "web",
        stagingDir = target.value / "vite" / "debugDist",
        outputFolder = (ThisBuild / baseDirectory).value / "debugDist",
        mode = "development",
        log = streams.value.log,
      )
    },
    // webDist: minified, but keeps the source map so production stack traces stay decipherable
    webDist := Def.uncached {
      viteDistImpl(
        viteRoot = baseDirectory.value,
        scalaJSOutput = (Compile / fullLinkJSOutput).value,
        assets = (ThisBuild / baseDirectory).value / "web" / "src" / "main" / "web",
        stagingDir = target.value / "vite" / "dist",
        outputFolder = (ThisBuild / baseDirectory).value / "dist",
        mode = "production",
        log = streams.value.log,
      )
    },
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
    headerLicense  := Some(HeaderLicense.ALv2("2020", "Roberto Leibman", HeaderLicenseStyle.Detailed)),
  )
