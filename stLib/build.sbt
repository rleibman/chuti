//////////////////////////////////////////////////////////////////////////////////////////////////
// Global stuff
lazy val SCALA = "3.5.2"

val scalajsReactVersion = "2.1.1"
val reactVersion = "^18.3.0"

version := "0.2.0-SNAPSHOT"

enablePlugins(ScalablyTypedConverterGenSourcePlugin)

Global / onChangedBuildSource := ReloadOnSourceChanges
scalaVersion                  := SCALA
Global / scalaVersion         := SCALA

organization     := "net.leibman"
startYear        := Some(2020)
organizationName := "Roberto Leibman"
headerLicense    := Some(HeaderLicense.MIT("2024", "Roberto Leibman", HeaderLicenseStyle.Detailed))
name             := "chuti-stlib"
useYarn          := true
stOutputPackage  := "net.leibman.chuti"
stFlavour        := Flavour.ScalajsReact

/* javascript / typescript deps */
Compile / npmDependencies ++= Seq(
  "react-dom"         -> reactVersion,
  "@types/react-dom"  -> reactVersion,
  "react"             -> reactVersion,
  "@types/react"      -> reactVersion,
  "csstype"           -> "^3.1.0",
  "@types/prop-types" -> "^15.7.0",
  "semantic-ui-react" -> "^2.1.5"
)

Test / npmDependencies ++= Seq(
  "react"     -> reactVersion,
  "react-dom" -> reactVersion
)

/* disabled because it somehow triggers many warnings */
scalaJSLinkerConfig ~= (_.withSourceMap(false))

// focus only on these libraries
stMinimize := Selection.AllExcept("semantic-ui-react", "react-quill")

stIgnore ++= List(
)

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

doc / sources := Nil
