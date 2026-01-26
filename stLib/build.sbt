//////////////////////////////////////////////////////////////////////////////////////////////////
// Global stuff
lazy val SCALA = "3.8.1"

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
useYarn          := true
stOutputPackage  := "net.leibman.chuti"
stFlavour        := Flavour.ScalajsReact

libraryDependencies ++= Seq(
  "com.github.japgolly.scalajs-react" %%% "core"  % scalajsReactVersion,
  "com.github.japgolly.scalajs-react" %%% "extra" % scalajsReactVersion
)

dependencyOverrides += "com.github.japgolly.scalajs-react" %%% "core" % scalajsReactVersion

/* javascript / typescript deps */
Compile / npmDependencies ++= Seq(
  "@types/react"      -> reactVersion,
  "@types/react-dom"  -> reactVersion,
  "react"             -> reactVersion,
  "react-dom"         -> reactVersion,
  "@types/prop-types" -> "^15.7.0",
  "csstype"           -> "^3.1.0",
  "semantic-ui-react" -> "^2.1.5"
)

Test / npmDependencies ++= Seq(
  "react"     -> reactVersion,
  "react-dom" -> reactVersion
)

/* disabled because it somehow triggers many warnings */
scalaJSLinkerConfig ~= (_.withSourceMap(false))

// focus only on these libraries
stMinimize := Selection.AllExcept("semantic-ui-react")

stIgnore ++= List(
)

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

doc / sources := Nil
