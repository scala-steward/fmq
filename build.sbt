lazy val fmq = project
  .in(file("."))
  .settings(commonSettings)
  .settings(commandSettings)
  .settings(noPublishSettings)
  .aggregate(core, extras)

lazy val core = project
  .in(file("core"))
  .settings(commonSettings)
  .settings(commandSettings)
  .settings(
    name                 := "fmq-core",
    libraryDependencies ++= Dependencies.core(scalaVersion.value)
  )

lazy val extras = project
  .in(file("extras"))
  .settings(commonSettings)
  .settings(commandSettings)
  .settings(
    name                 := "fmq-extras",
    libraryDependencies ++= Dependencies.extras
  )
  .dependsOn(core)

lazy val bench = project
  .in(file("bench"))
  .enablePlugins(JmhPlugin)
  .settings(commonSettings)
  .settings(commandSettings)
  .settings(noPublishSettings)
  .settings(
    name                := "fmq-bench",
    libraryDependencies += Dependencies.fs2
  )
  .dependsOn(core)

lazy val examples = project
  .in(file("examples"))
  .settings(commonSettings)
  .settings(commandSettings)
  .settings(noPublishSettings)
  .settings(
    name                := "fmq-examples",
    libraryDependencies += Dependencies.fs2
  )
  .dependsOn(core, extras)

lazy val docs = project
  .in(file("fmq-docs"))
  .enablePlugins(MdocPlugin, DocusaurusPlugin)
  .settings(commonSettings)
  .settings(noPublishSettings)
  .settings(
    name                := "fmq-docs",
    libraryDependencies += Dependencies.fs2,
    mdocVariables := Map(
      "VERSION" -> version.value.replaceFirst("\\+.*", "")
    )
  )
  .dependsOn(core, extras)

lazy val commonSettings = Seq(
  scalaVersion                           := Versions.scala_213,
  crossScalaVersions                     := Seq(scalaVersion.value, Versions.scala_212),
  Test / fork                            := true,
  Test / parallelExecution               := false,
  Compile / compile / wartremoverErrors ++= Warts.allBut(Wart.Any, Wart.Nothing), // false positive
  addCompilerPlugin("org.typelevel" %% "kind-projector"     % "0.11.2" cross CrossVersion.full),
  addCompilerPlugin("com.olegpy"    %% "better-monadic-for" % "0.3.1")
)

lazy val commandSettings = {
  val ci = Command.command("ci") { state =>
    "clean" ::
      "coverage" ::
      "scalafmtSbtCheck" ::
      "scalafmtCheckAll" ::
      "test" ::
      "coverageReport" ::
      "coverageAggregate" ::
      state
  }

  val testAll = Command.command("testAll") { state =>
    "clean" :: "coverage" :: "test" :: "coverageReport" :: "coverageAggregate" :: state
  }

  commands ++= List(ci, testAll)
}

lazy val noPublishSettings = Seq(
  publish         := {},
  publishLocal    := {},
  publishArtifact := false,
  publish / skip  := true
)

inThisBuild(
  Seq(
    organization := "io.github.irevive",
    homepage     := Some(url("https://github.com/iRevive/fmq")),
    licenses     := List("MIT" -> url("http://opensource.org/licenses/MIT")),
    developers   := List(Developer("iRevive", "Maksim Ochenashko", "", url("https://github.com/iRevive")))
  )
)
