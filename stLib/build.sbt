//////////////////////////////////////////////////////////////////////////////////////////////////
// Global stuff
lazy val SCALA = "3.8.2"

val scalajsReactVersion = "3.0.0"
val reactVersion = "^18.3.0"

version := "1.0.0"

enablePlugins(ScalablyTypedConverterGenSourcePlugin)

Global / onChangedBuildSource := ReloadOnSourceChanges
scalaVersion                  := SCALA
Global / scalaVersion         := SCALA

organization     := "net.leibman"
startYear        := Some(2024)
organizationName := "Roberto Leibman"
headerLicense    := Some(HeaderLicense.MIT("2024", "Roberto Leibman", HeaderLicenseStyle.Detailed))
name             := "chuti-stlib"
stUseYarn        := true
stOutputPackage  := "net.leibman.chuti"
stFlavour        := Flavour.ScalajsReact

// sbt 2 has cross-platform support built in: `%%` resolves the Scala.js (_sjs1_3) artifacts here.
libraryDependencies ++= Seq(
  "com.github.japgolly.scalajs-react" %% "core"  % scalajsReactVersion,
  "com.github.japgolly.scalajs-react" %% "extra" % scalajsReactVersion
)

dependencyOverrides += "com.github.japgolly.scalajs-react" %% "core" % scalajsReactVersion

// The converter pins scalajs-react 2.1.3 in every generated facade, while we compile against a newer one. sbt 2 turns
// that major-version eviction into an error (sbt 1 only warned).
libraryDependencySchemes ++= Seq(
  "com.github.japgolly.scalajs-react" % "core_sjs1_3"  % VersionScheme.Always,
  "com.github.japgolly.scalajs-react" % "extra_sjs1_3" % VersionScheme.Always
)

/* javascript / typescript deps */
Compile / stNpmDependencies ++= Seq(
  "@types/react"      -> reactVersion,
  "@types/react-dom"  -> reactVersion,
  "react"             -> reactVersion,
  "react-dom"         -> reactVersion,
  "@types/prop-types" -> "^15.7.0",
  "csstype"           -> "^3.1.0",
  "semantic-ui-react" -> "^2.1.5"
)

Test / stNpmDependencies ++= Seq(
  "react"     -> reactVersion,
  "react-dom" -> reactVersion
)

/* disabled because it somehow triggers many warnings */
scalaJSLinkerConfig ~= (_.withSourceMap(false))

// focus only on these libraries
stMinimize := Selection.AllExcept("semantic-ui-react")

stIgnore ++= List(
)

licenses += ("MIT", uri("http://opensource.org/licenses/MIT"))

doc / sources := Nil
