import sbt.Keys._
import sbt._
// shadow sbt-scalajs' crossProject and CrossType from Scala.js 0.6.x
import sbtcrossproject.CrossPlugin.autoImport.{crossProject, CrossType}

ThisBuild / scalafmtOnCompile := true

Global / onChangedBuildSource := ReloadOnSourceChanges

val commonSettings = Seq(
  organization := "io.suzaku",
  version := Version.library,
  crossScalaVersions := Seq("2.12.11", "2.13.2"),
  scalaVersion in ThisBuild := "2.13.2",
  scalacOptions := Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-language:experimental.macros",
    "-language:existentials",
    "-Xfatal-warnings",
    "-Xlint",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard"
  ) ++ (CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 12)) => Seq("-Xlint:-unused", "-language:higherKinds")
    case _             => Nil
  }),
  Compile / scalacOptions -= "-Ywarn-value-discard",
  Compile / doc / scalacOptions -= "-Xfatal-warnings",
  testFrameworks += new TestFramework("utest.runner.Framework"),
  libraryDependencies ++= Seq(
    "com.lihaoyi"            %%% "utest"                  % "0.7.4" % "test",
    "org.scala-lang.modules" %% "scala-collection-compat" % "2.1.6"
  )
)

inThisBuild(
  List(
    homepage := Some(url("https://github.com/suzaku-io/diode")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer("ochrons",
                "Otto Chrons",
                "",
                url("https://github.com/ochrons"))
    ),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/suzaku-io/diode"),
        "scm:git:git@github.com:suzaku-io/diode.git",
        Some("scm:git:git@github.com:suzaku-io/diode.git")
      )
    ),
    Test / publishArtifact := false
  )
)

val sourceMapSetting =
  Def.setting(
    if (isSnapshot.value) Seq.empty
    else
      Seq({
        val a = baseDirectory.value.toURI.toString.replaceFirst("[^/]+/?$", "")
        val g = "https://raw.githubusercontent.com/suzaku-io/diode"
        s"-P:scalajs:mapSourceURI:$a->$g/v${version.value}/${name.value}/"
      })
  )

def preventPublication(p: Project) =
  p.settings(
    publish := (()),
    publishLocal := (()),
    publishArtifact := false,
    publishTo := Some(
      Resolver.file("Unused transient repository", target.value / "fakepublish")
    ),
    packagedArtifacts := Map.empty
  )

lazy val diodeCore = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("diode-core"))
  .settings(commonSettings: _*)
  .settings(
    name := "diode-core",
    libraryDependencies += "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided"
  )
  .jsSettings(scalacOptions ++= sourceMapSetting.value)
  .jvmSettings()

lazy val diodeCoreJS = diodeCore.js

lazy val diodeCoreJVM = diodeCore.jvm

lazy val diodeData = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("diode-data"))
  .settings(commonSettings: _*)
  .settings(name := "diode-data")
  .jsSettings(scalacOptions ++= sourceMapSetting.value)
  .jvmSettings()
  .dependsOn(diodeCore)

lazy val diodeDataJS = diodeData.js

lazy val diodeDataJVM = diodeData.jvm

lazy val diode = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("diode"))
  .settings(commonSettings: _*)
  .settings(
    name := "diode",
    test := {}
  )
  .dependsOn(diodeCore, diodeData)

lazy val diodeJS = diode.js

lazy val diodeJVM = diode.jvm

lazy val diodeDevtools = crossProject(JSPlatform, JVMPlatform)
  .crossType(CrossType.Full)
  .in(file("diode-devtools"))
  .settings(commonSettings: _*)
  .settings(
    name := "diode-devtools"
  )
  .jsSettings(
    libraryDependencies ++= Seq("org.scala-js" %%% "scalajs-dom" % "1.0.0"),
    scalacOptions ++= sourceMapSetting.value
  )
  .jvmSettings()
  .dependsOn(diodeCore)

lazy val diodeDevToolsJS = diodeDevtools.js

lazy val diodeDevToolsJVM = diodeDevtools.jvm

lazy val diodeReact: Project = project
  .in(file("diode-react"))
  .settings(commonSettings: _*)
  .settings(
    name := "diode-react",
    version := s"${Version.library}.${Version.sjsReact.filterNot(_ == '.')}",
    libraryDependencies ++= Seq(
      "com.github.japgolly.scalajs-react" %%% "core" % Version.sjsReact
    ),
    scalacOptions ++= sourceMapSetting.value
  )
  .dependsOn(diodeJS)
  .enablePlugins(ScalaJSPlugin)

lazy val coreProjects = Seq[ProjectReference](
  diodeJS,
  diodeJVM,
  diodeCoreJS,
  diodeCoreJVM,
  diodeDataJS,
  diodeDataJVM,
  diodeDevToolsJS,
  diodeDevToolsJVM
)

lazy val allProjects = coreProjects :+ diodeReact.project

lazy val projects: Seq[ProjectReference] =
  if (scalaJSVersion.startsWith("0.6")) allProjects else coreProjects

lazy val root = preventPublication(project.in(file(".")))
  .settings(
    commonSettings
  )
  .aggregate(projects: _*)
