import org.apache.commons.io.FileUtils
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.Files

import sbtcrossproject.CrossPlugin.autoImport.crossProject
import sbtcrossproject.CrossProject

lazy val start = TaskKey[Unit]("start")
lazy val dist = TaskKey[File]("dist")
lazy val debugDist = TaskKey[File]("debugDist")

Global / onChangedBuildSource := ReloadOnSourceChanges

enablePlugins(
  LinuxPlugin,
  JDebPackaging,
  DebianPlugin,
  DebianDeployPlugin,
  JavaServerAppPackaging,
  SystemloaderPlugin,
  SystemdPlugin,
  CodegenPlugin
)

lazy val commonJVM = common.jvm
lazy val commonJS = common.js

lazy val root = (project in file("."))
  .aggregate(commonJVM, commonJS, server, web, login)
  .settings(
    name               := "chuti",
    crossScalaVersions := Nil,
    publish / skip     := true,
    headerLicense      := None
  )

lazy val akkaVersion = "2.6.4"
lazy val circeVersion = "0.13.0"
lazy val monocleVersion = "2.0.4" // depends on cats 2.x
lazy val calibanVersion = "0.7.8"
lazy val scalaCacheVersion = "0.28.2-SNAPSHOT"

lazy val commonSettings = Seq(
  organization     := "leibman.net",
  version          := "0.1",
  scalaVersion     := "2.13.1",
  startYear        := Some(2020),
  organizationName := "Roberto Leibman",
  headerLicense    := Some(HeaderLicense.ALv2("2020", "Roberto Leibman", HeaderLicenseStyle.Detailed))
)

lazy val commonVmSettings = commonSettings ++ Seq(
  libraryDependencies ++= Seq(
    "com.github.ghostdogpr"      %% "caliban"       % calibanVersion withSources (),
    "com.github.julien-truffaut" %% "monocle-core"  % monocleVersion withSources (),
    "com.github.julien-truffaut" %% "monocle-macro" % monocleVersion withSources (),
    "com.github.julien-truffaut" %% "monocle-law"   % monocleVersion % "test" withSources (),
    "org.scalatest"              %% "scalatest"     % "3.1.1" % "test" withSources ()
  ),
  libraryDependencies ++= Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser",
    "io.circe" %% "circe-literal"
  ).map(_ % circeVersion)
)

lazy val common: CrossProject = crossProject(JSPlatform, JVMPlatform)
  .in(file("common"))
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
        "com.github.julien-truffaut" %%% "monocle-core"  % monocleVersion withSources (),
        "com.github.julien-truffaut" %%% "monocle-macro" % monocleVersion withSources (),
        "com.github.julien-truffaut" %%% "monocle-law"   % monocleVersion % "test" withSources (),
        "org.scalatest" %%% "scalatest"                  % "3.1.1" % "test" withSources ()
      ),
      libraryDependencies ++= Seq(
        "io.circe" %%% "circe-core",
        "io.circe" %%% "circe-generic",
        "io.circe" %%% "circe-parser",
        "io.circe" %%% "circe-literal"
      ).map(_ % circeVersion)
    )
  )

lazy val server: Project = project
  .in(file("server"))
  .dependsOn(commonJVM)
  .settings(commonVmSettings)
  .enablePlugins(
    AutomateHeaderPlugin,
    GitVersioning
  )
  .settings(
    name := "chuti-server",
    libraryDependencies ++= Seq(
      //Akka
      "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion withSources (),
      "com.typesafe.akka" %% "akka-stream"      % akkaVersion withSources (),
      "com.typesafe.akka" %% "akka-http"        % "10.1.11" withSources (),
      "de.heikoseeberger" %% "akka-http-circe"  % "1.32.0" withSources (),
      //DB
      "com.typesafe.slick" %% "slick"               % "3.3.2",
      "com.typesafe.slick" %% "slick-hikaricp"      % "3.3.2",
      "com.typesafe.slick" %% "slick-codegen"       % "3.3.2",
      "mysql"              % "mysql-connector-java" % "8.0.19",
      // Scala Cache
      "com.github.cb372" %% "scalacache-core"     % scalaCacheVersion withSources (),
      "com.github.cb372" %% "scalacache-caffeine" % scalaCacheVersion withSources (),
      "com.github.cb372" %% "scalacache-zio"      % scalaCacheVersion withSources (),
      //ZIO
      "dev.zio"               %% "zio"               % "1.0.0-RC18-2" withSources (),
      "com.github.ghostdogpr" %% "caliban"           % calibanVersion withSources (),
      "com.github.ghostdogpr" %% "caliban-akka-http" % calibanVersion withSources (),
      //Util
      "com.github.pathikrit"               %% "better-files"    % "3.8.0" withSources (),
      "de.heikoseeberger"                  %% "akka-http-circe" % "1.31.0" withSources (),
      "com.softwaremill.akka-http-session" %% "core"            % "0.5.11" withSources (),
      "com.github.daddykotex"              %% "courier"         % "2.0.0" withSources (),
      "ch.qos.logback"                     % "logback-classic"  % "1.2.3" withSources (),
      "org.slf4j"                          % "slf4j-nop"        % "1.7.30" withSources ()
    )
  )

lazy val login: Project = project
  .in(file("login"))
  .dependsOn(commonJS)
  .settings(commonSettings)
  .configure(bundlerSettings)
  .configure(commonWeb)
  .enablePlugins(
    AutomateHeaderPlugin,
    GitVersioning,
    ScalaJSPlugin,
    CodegenPlugin
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

lazy val web: Project = project
  .in(file("web"))
  .dependsOn(commonJS)
  .settings(commonSettings)
  .configure(bundlerSettings)
  .configure(commonWeb)
  .enablePlugins(
    AutomateHeaderPlugin,
    GitVersioning,
    ScalaJSPlugin,
    CodegenPlugin
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
  _.enablePlugins(ScalablyTypedConverterPlugin)
    .settings(
      resolvers += Resolver.bintrayRepo("oyvindberg", "converter"),
      stFlavour := Flavour.Japgolly,
      stIgnore ++= List("react-dom"),
      libraryDependencies ++= Seq(
        "io.circe" %%% "circe-core",
        "io.circe" %%% "circe-generic",
        "io.circe" %%% "circe-parser",
        "io.circe" %%% "circe-literal"
      ).map(_ % circeVersion),
      libraryDependencies ++= Seq(
        "commons-io"                                    % "commons-io" % "2.6" withSources (),
        "com.github.ghostdogpr" %%% "caliban-client"    % calibanVersion withSources (),
        "dev.zio" %%% "zio"                             % "1.0.0-RC18-2" withSources (),
        "com.softwaremill.sttp.client" %%% "core"       % "2.0.9" withSources (),
        "com.softwaremill.sttp.client"                  %% "async-http-client-backend-zio" % "2.0.9",
        "ru.pavkin" %%% "scala-js-momentjs"             % "0.10.3" withSources (),
        "io.github.cquiroz" %%% "scala-java-time"       % "2.0.0-RC3" withSources (),
        "io.github.cquiroz" %%% "scala-java-time-tzdb"  % "2.0.0-RC3_2019a" withSources (),
        "org.scala-js" %%% "scalajs-dom"                % "0.9.8" withSources (),
        "com.olvind" %%% "scalablytyped-runtime"        % "2.1.0",
        "com.github.japgolly.scalajs-react" %%% "core"  % "1.6.0" withSources (),
        "com.github.japgolly.scalajs-react" %%% "extra" % "1.6.0" withSources (),
        "com.lihaoyi" %%% "scalatags"                   % "0.9.0" withSources (),
        "com.github.japgolly.scalacss" %%% "core"       % "0.6.0" withSources (),
        "com.github.japgolly.scalacss" %%% "ext-react"  % "0.6.0" withSources (),
        "com.github.pathikrit"                          %% "better-files" % "3.8.0",
        "org.scalatest" %%% "scalatest"                 % "3.1.1" % "test" withSources ()
      ),
      organization     := "net.leibman",
      organizationName := "Roberto Leibman",
      startYear        := Some(2020),
      licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")),
      scalacOptions ++= Seq(
        "-P:scalajs:sjsDefinedByDefault",
        "-deprecation", // Emit warning and location for usages of deprecated APIs.
        "-explaintypes", // Explain type errors in more detail.
        "-feature", // Emit warning and location for usages of features that should be imported explicitly.
        "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
        "-language:experimental.macros", // Allow macro definition (besides implementation and application)
        "-language:higherKinds", // Allow higher-kinded types
        "-language:implicitConversions", // Allow definition of implicit functions called views
        "-unchecked", // Enable additional warnings where generated code depends on assumptions.
        "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
        //"-Xfatal-warnings", // Fail the compilation if there are any warnings.
        "-Xlint:adapted-args", // Warn if an argument list is modified to match the receiver.
        "-Xlint:constant", // Evaluation of a constant arithmetic expression results in an error.
        "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
        "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
        "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
        "-Xlint:infer-any", // Warn when a type argument is inferred to be `Any`.
        "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
        "-Xlint:nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
        "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
        "-Xlint:option-implicit", // Option.apply used implicit view.
        "-Xlint:package-object-classes", // Class or object defined in package object.
        "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
        "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
        "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
        "-Xlint:type-parameter-shadow", // A local type parameter shadows a type already in scope.
        "-Ywarn-dead-code", // Warn when dead code is identified.
        "-Ywarn-extra-implicit", // Warn when more than one implicit parameter section is defined.
        "-Ywarn-numeric-widen", // Warn when numerics are widened.
        "-Ywarn-unused:implicits", // Warn if an implicit parameter is unused.
        "-Ywarn-unused:imports", // Warn if an import selector is not referenced.
        "-Ywarn-unused:locals", // Warn if a local definition is unused.
        "-Ywarn-unused:params", // Warn if a value parameter is unused.
        "-Ywarn-unused:patvars", // Warn if a variable bound in a pattern is unused.
        "-Ywarn-unused:privates", // Warn if a private member is unused.
        "-Ywarn-value-discard", // Warn when non-Unit expression results are unused.
        "-Ybackend-parallelism",
        "8", // Enable paralellisation — change to desired number!
        "-Ycache-plugin-class-loader:last-modified", // Enables caching of classloaders for compiler plugins
        "-Ycache-macro-class-loader:last-modified" // and macro definitions. This can lead to performance improvements.
      ),
      Compile / unmanagedSourceDirectories := Seq((Compile / scalaSource).value),
      Test / unmanagedSourceDirectories    := Seq((Test / scalaSource).value),
      webpackDevServerPort                 := 8009
    )

lazy val bundlerSettings: Project => Project =
  _.enablePlugins(ScalaJSBundlerPlugin)
    .settings(
      scalaJSUseMainModuleInitializer := true,
      /* disabled because it somehow triggers many warnings */
      emitSourceMaps    := false,
      scalaJSModuleKind := ModuleKind.CommonJSModule,
      /* Specify current versions and modes */
      startWebpackDevServer / version := "3.1.10",
      webpack / version               := "4.28.3",
      Compile / fastOptJS / webpackExtraArgs += "--mode=development",
      Compile / fullOptJS / webpackExtraArgs += "--mode=production",
      Compile / fastOptJS / webpackDevServerExtraArgs += "--mode=development",
      Compile / fullOptJS / webpackDevServerExtraArgs += "--mode=production",
      useYarn := true,
      //      jsEnv := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv,
      fork in run                                := true,
      scalaJSStage in Global                     := FastOptStage,
      scalaJSUseMainModuleInitializer in Compile := true,
      scalaJSUseMainModuleInitializer in Test    := false,
      skip in packageJSDependencies              := false,
      artifactPath
        .in(Compile, fastOptJS) := ((crossTarget in (Compile, fastOptJS)).value /
        ((moduleName in fastOptJS).value + "-opt.js")),
      artifactPath
        .in(Compile, fullOptJS) := ((crossTarget in (Compile, fullOptJS)).value /
        ((moduleName in fullOptJS).value + "-opt.js")),
      webpackEmitSourceMaps := true,
      Compile / npmDependencies ++= Seq(
        "react-dom"         -> "16.13.1",
        "@types/react-dom"  -> "16.9.6",
        "react"             -> "16.13.1",
        "@types/react"      -> "16.9.32",
        "semantic-ui-react" -> "0.88.2"
      ),
      npmDevDependencies.in(Compile) := Seq(
        "style-loader"               -> "0.23.1",
        "css-loader"                 -> "2.1.0",
        "sass-loader"                -> "7.1.0",
        "compression-webpack-plugin" -> "2.0.0",
        "file-loader"                -> "3.0.1",
        "gulp-decompress"            -> "2.0.2",
        "image-webpack-loader"       -> "4.6.0",
        "imagemin"                   -> "6.1.0",
        "less"                       -> "3.9.0",
        "less-loader"                -> "4.1.0",
        "lodash"                     -> "4.17.11",
        "node-libs-browser"          -> "2.1.0",
        "react-hot-loader"           -> "4.6.3",
        "url-loader"                 -> "1.1.2",
        "expose-loader"              -> "0.7.5",
        "webpack"                    -> "4.28.3",
        "webpack-merge"              -> "4.2.2"
      )
    )
