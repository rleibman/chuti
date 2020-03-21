organization := "leibman.net"

name := "chuti"

version := "0.1"

scalaVersion := "2.13.1"

enablePlugins(
  AutomateHeaderPlugin,
  GitVersioning,
  BuildInfoPlugin,
  LinuxPlugin,
  JDebPackaging,
  DebianPlugin,
  DebianDeployPlugin,
  JavaServerAppPackaging,
  SystemloaderPlugin,
  SystemdPlugin)

startYear := Some(2020)

organizationName := "Roberto Leibman"

licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))

Global / onChangedBuildSource := ReloadOnSourceChanges