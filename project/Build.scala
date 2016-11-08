


import java.io._
import org.stormenroute.mecha._
import sbt._
import sbt.Keys._
import sbt.Process._
import org.scalajs.sbtplugin.ScalaJSPlugin
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import org.scalajs.sbtplugin.cross.CrossProject



object ReactorsBuild extends MechaRepoBuild {
  def repoName = "reactors"

  val reactorsScalaVersion = "2.11.8"

  def projectSettings(suffix: String) = {
    Seq(
      name := s"reactors$suffix",
      organization := "io.reactors",
      scalaVersion := reactorsScalaVersion,
      testFrameworks += new TestFramework("org.scalameter.ScalaMeterFramework"),
      parallelExecution in Test := false,
      parallelExecution in ThisBuild := false,
      concurrentRestrictions in Global += Tags.limit(Tags.Test, 1),
      cancelable in Global := true,
      fork in Test := true,
      fork in run := true,
      javaOptions in Test ++= Seq(
        "-Xmx2G",
        "-XX:MaxPermSize=384m",
        "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
      ),
      scalacOptions ++= Seq(
        "-deprecation"
      ),
      scalacOptions in (Compile, doc) ++= Seq(
        "-implicits"
      ),
      testOptions in Test += Tests.Argument(
        TestFrameworks.ScalaCheck,
        "-minSuccessfulTests", "200",
        "-workers", "1",
        "-verbosity", "2"
      ),
      resolvers ++= Seq(
        "Sonatype OSS Snapshots" at
          "https://oss.sonatype.org/content/repositories/snapshots",
        "Sonatype OSS Releases" at
          "https://oss.sonatype.org/content/repositories/releases",
        "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"
      ),
      libraryDependencies ++= superRepoDependencies(s"reactors$suffix"),
      ivyLoggingLevel in ThisBuild := UpdateLogging.Quiet,
      publishMavenStyle := true,
      publishTo <<= version { (v: String) =>
        val nexus = "https://oss.sonatype.org/"
        if (v.trim.endsWith("SNAPSHOT"))
          Some("snapshots" at nexus + "content/repositories/snapshots")
        else
          Some("releases"  at nexus + "service/local/staging/deploy/maven2")
      },
      publishArtifact in Test := false,
      pomIncludeRepository := { _ => false },
      pomExtra :=
        <url>http://reactors.io/</url>
        <licenses>
          <license>
            <name>BSD-style</name>
            <url>http://opensource.org/licenses/BSD-3-Clause</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:reactors-io/reactors.git</url>
          <connection>
            scm:git:git@github.com:reactors-io/reactors.git
          </connection>
        </scm>
        <developers>
          <developer>
            <id>axel22</id>
            <name>Aleksandar Prokopec</name>
            <url>http://axel22.github.com/</url>
          </developer>
        </developers>,
      mechaPublishKey := { publish.value },
      mechaDocsRepoKey := "git@github.com:storm-enroute/apidocs.git",
      mechaDocsBranchKey := "gh-pages",
      mechaDocsPathKey := "reactors"
    )
  }

  def gitPropsContents(dir: File, baseDir: File): Seq[File] = {
    def run(cmd: String*): String = Process(cmd, Some(baseDir)).!!
    val branch = run("git", "rev-parse", "--abbrev-ref", "HEAD").trim
    val commitTs = run("git", "--no-pager", "show", "-s", "--format=%ct", "HEAD")
    val sha = run("git", "rev-parse", "HEAD").trim
    val contents = s"""
    {
      "branch": "$branch",
      "commit-timestamp": $commitTs,
      "sha": "$sha"
    }
    """
    val file = dir / "reactors-io" / ".gitprops"
    IO.write(file, contents)
    Seq(file)
  }

  lazy val Benchmark = config("bench") extend (Test)

  lazy val reactorsCommon: CrossProject = crossProject.crossType(CrossType.Full)
    .in(file("reactors-common"))
    .settings(
      projectSettings("-common") ++ Seq(
        libraryDependencies ++= Seq(
          "org.scalatest" %%% "scalatest" % "3.0.0" % "test",
          "org.scalacheck" %%% "scalacheck" % "1.13.2" % "test"
        ),
        unmanagedSourceDirectories in Compile +=
          baseDirectory.value.getParentFile / "shared" / "src" / "main" / "scala",
        unmanagedSourceDirectories in Test +=
          baseDirectory.value.getParentFile / "shared" / "src" / "test" / "scala"
      ): _*
    )
    .configs(Benchmark)
    .settings(inConfig(Benchmark)(Defaults.testSettings): _*)
    .jvmSettings(
      libraryDependencies ++= Seq(
        "com.typesafe.akka" %% "akka-actor" % "2.3.15" % "test;bench"
      ),
      libraryDependencies ++= superRepoDependencies(s"reactors-common-jvm")
    )
    .jvmConfigure(_.copy(id = "reactors-common-jvm").dependsOnSuperRepo)
    .jsSettings(
      fork in Test := false,
      fork in run := false,
      scalaJSUseRhino in Global := false
    )
    .jsConfigure(_.copy(id = "reactors-common-js").dependsOnSuperRepo)

  lazy val reactorsCommonJvm = reactorsCommon.jvm

  lazy val reactorsCommonJs = reactorsCommon.js

  lazy val reactorsCore = crossProject
    .in(file("reactors-core"))
    .settings(
      projectSettings("-core") ++ Seq(
        resourceGenerators in Compile <+=
          (resourceManaged in Compile, baseDirectory) map {
            (dir, baseDir) => gitPropsContents(dir, baseDir)
          },
        libraryDependencies ++= Seq(
          "org.scalatest" %%% "scalatest" % "3.0.0" % "test",
          "org.scalacheck" %%% "scalacheck" % "1.13.2" % "test"
        ),
        unmanagedSourceDirectories in Compile +=
          baseDirectory.value.getParentFile / "shared" / "src" / "main" / "scala",
        unmanagedSourceDirectories in Test +=
          baseDirectory.value.getParentFile / "shared" / "src" / "test" / "scala"
      ): _*
    )
    .configs(Benchmark)
    .settings(inConfig(Benchmark)(Defaults.testSettings): _*)
    .jvmSettings(
      (test in Test) <<= (test in Test).dependsOn(test in (reactorsCommon.jvm, Test)),
      publish <<= publish.dependsOn(publish in reactorsCommon.jvm),
      libraryDependencies ++= Seq(
        "com.typesafe" % "config" % "1.2.1",
        "com.typesafe.akka" %% "akka-actor" % "2.3.15" % "test;bench"
      )
    )
    .jvmConfigure(_.copy(id = "reactors-core-jvm").dependsOnSuperRepo)
    .jsSettings(
      (test in Test) <<= (test in Test).dependsOn(test in (reactorsCommon.js, Test)),
      publish <<= publish.dependsOn(publish in reactorsCommon.js),
      fork in Test := false,
      fork in run := false,
      scalaJSUseRhino in Global := false,
      libraryDependencies ++= Seq(
        "org.scala-js" %%% "scala-parser-combinators" % "1.0.2"
      )
    )
    .jsConfigure(_.copy(id = "reactors-core-js").dependsOnSuperRepo)
    .dependsOn(
      reactorsCommon % "compile->compile;test->test"
    )

  lazy val reactorsCoreJvm = reactorsCore.jvm

  lazy val reactorsCoreJs = reactorsCore.js

  lazy val reactorsContainer = crossProject
    .in(file("reactors-container"))
    .settings(
      projectSettings("-container") ++ Seq(
        libraryDependencies ++= Seq(
          "org.scalatest" %%% "scalatest" % "3.0.0" % "test",
          "org.scalacheck" %%% "scalacheck" % "1.13.2" % "test"
        ),
        unmanagedSourceDirectories in Compile +=
          baseDirectory.value.getParentFile / "shared" / "src" / "main" / "scala",
        unmanagedSourceDirectories in Test +=
          baseDirectory.value.getParentFile / "shared" / "src" / "test" / "scala"
      ): _*
    )
    .configs(Benchmark)
    .settings(inConfig(Benchmark)(Defaults.testSettings): _*)
    .jvmSettings(
      (test in Test) <<= (test in Test).dependsOn(test in (reactorsCore.jvm, Test)),
      publish <<= publish.dependsOn(publish in reactorsCore.jvm)
    )
    .jvmConfigure(_.copy(id = "reactors-container-jvm").dependsOnSuperRepo)
    .jsSettings(
      (test in Test) <<= (test in Test).dependsOn(test in (reactorsCore.js, Test)),
      publish <<= publish.dependsOn(publish in reactorsCore.js),
      fork in Test := false,
      fork in run := false,
      scalaJSUseRhino in Global := false
    )
    .jsConfigure(_.copy(id = "reactors-container-js").dependsOnSuperRepo)
    .dependsOn(
      reactorsCore % "compile->compile;test->test"
    )

  lazy val reactorsContainerJvm = reactorsContainer.jvm

  lazy val reactorsContainerJs = reactorsContainer.js

  lazy val reactorsProtocols = crossProject
    .in(file("reactors-protocols"))
    .settings(
      projectSettings("-protocols") ++ Seq(
        libraryDependencies ++= Seq(
          "org.scalatest" %%% "scalatest" % "3.0.0" % "test",
          "org.scalacheck" %%% "scalacheck" % "1.13.2" % "test"
        ),
        unmanagedSourceDirectories in Compile +=
          baseDirectory.value.getParentFile / "shared" / "src" / "main" / "scala",
        unmanagedSourceDirectories in Test +=
          baseDirectory.value.getParentFile / "shared" / "src" / "test" / "scala"
      ): _*
    )
    .configs(Benchmark)
    .settings(inConfig(Benchmark)(Defaults.testSettings): _*)
    .jvmSettings(
      (test in Test) <<= (test in Test).dependsOn(test in (reactorsCore.jvm, Test)),
      publish <<= publish.dependsOn(publish in reactorsCore.jvm)
    )
    .jvmConfigure(_.copy(id = "reactors-protocols-jvm").dependsOnSuperRepo)
    .jsSettings(
      (test in Test) <<= (test in Test).dependsOn(test in (reactorsCore.js, Test)),
      publish <<= publish.dependsOn(publish in reactorsCore.js),
      fork in Test := false,
      fork in run := false,
      scalaJSUseRhino in Global := false
    )
    .jsConfigure(_.copy(id = "reactors-protocols-js").dependsOnSuperRepo)
    .dependsOn(
      reactorsCommon % "compile->compile;test->test",
      reactorsCore % "compile->compile;test->test",
      reactorsContainer % "compile->compile;test->test"
    )

  lazy val reactorsProtocolsJvm = reactorsProtocols.jvm

  lazy val reactorsProtocolsJs = reactorsProtocols.js

  lazy val reactorsRemote = crossProject
    .in(file("reactors-remote"))
    .settings(
      projectSettings("-remote") ++ Seq(
        libraryDependencies ++= Seq(
          "org.scalatest" %%% "scalatest" % "3.0.0" % "test",
          "org.scalacheck" %%% "scalacheck" % "1.13.2" % "test"
        ),
        unmanagedSourceDirectories in Compile +=
          baseDirectory.value.getParentFile / "shared" / "src" / "main" / "scala",
        unmanagedSourceDirectories in Test +=
          baseDirectory.value.getParentFile / "shared" / "src" / "test" / "scala"
      ): _*
    )
    .configs(Benchmark)
    .settings(inConfig(Benchmark)(Defaults.testSettings): _*)
    .jvmSettings(
      (test in Test) <<= (test in Test).dependsOn(test in (reactorsCore.jvm, Test)),
      publish <<= publish.dependsOn(publish in reactorsCore.jvm)
    )
    .jvmConfigure(_.copy(id = "reactors-remote-jvm").dependsOnSuperRepo)
    .jsSettings(
      (test in Test) <<= (test in Test).dependsOn(test in (reactorsCore.js, Test)),
      publish <<= publish.dependsOn(publish in reactorsCore.js),
      fork in Test := false,
      fork in run := false,
      scalaJSUseRhino in Global := false
    )
    .jsConfigure(_.copy(id = "reactors-remote-js").dependsOnSuperRepo)
    .dependsOn(
      reactorsCore % "compile->compile;test->test"
    )

  lazy val reactorsRemoteJvm = reactorsRemote.jvm

  lazy val reactorsRemoteJs = reactorsRemote.js

  lazy val reactorsExtra = project
    .copy(id = "reactors-extra")
    .in(file("reactors-extra"))
    .settings(
      projectSettings("-extra") ++ Seq(
        libraryDependencies ++= Seq(
          "org.scala-lang" % "scala-reflect" % "2.11.4",
          "org.scalatest" %%% "scalatest" % "3.0.0" % "test",
          "org.scalacheck" %%% "scalacheck" % "1.13.2" % "test",
          "com.typesafe.akka" %% "akka-actor" % "2.3.15" % "test;bench"
        )
      ): _*
    )
    .configs(Benchmark)
    .settings(inConfig(Benchmark)(Defaults.testSettings): _*)
    .settings(
      (test in Test) <<= (test in Test).dependsOn(test in (reactorsCore.jvm, Test)),
      publish <<= publish.dependsOn(publish in reactorsCore.jvm)
    )
    .dependsOn(
      reactorsCore.jvm % "compile->compile;test->test",
      reactorsProtocols.jvm % "compile->compile;test->test"
    )
    .dependsOnSuperRepo

  lazy val reactorsDebugger = project
    .copy(id = "reactors-debugger")
    .in(file("reactors-debugger"))
    .settings(
      projectSettings("-debugger") ++ Seq(
        libraryDependencies ++= Seq(
          "org.scala-lang" % "scala-compiler" % "2.11.8",
          "org.rapidoid" % "rapidoid-http-server" % "5.1.9",
          "org.rapidoid" % "rapidoid-gui" % "5.1.9",
          "com.github.spullara.mustache.java" % "compiler" % "0.9.2",
          "commons-io" % "commons-io" % "2.4",
          "org.json4s" %% "json4s-jackson" % "3.4.0",
          "org.scalatest" %%% "scalatest" % "3.0.0" % "test",
          "org.scalacheck" %%% "scalacheck" % "1.13.2" % "test",
          "org.seleniumhq.selenium" % "selenium-java" % "2.53.1" % "test",
          "org.seleniumhq.selenium" % "selenium-chrome-driver" % "2.53.1" % "test"
        )
      ): _*
    )
    .configs(Benchmark)
    .settings(inConfig(Benchmark)(Defaults.testSettings): _*)
    .settings(
      (test in Test) <<= (test in Test).dependsOn(test in (reactorsCore.jvm, Test)),
      publish <<= publish.dependsOn(publish in reactorsCore.jvm)
    )
    .dependsOn(
      reactorsCore.jvm % "compile->compile;test->test",
      reactorsProtocols.jvm % "compile->compile;test->test"
    )
    .dependsOnSuperRepo

  lazy val reactors: CrossProject = crossProject
    .in(file("reactors"))
    .settings(
      projectSettings("") ++ Seq(
        libraryDependencies ++= Seq(
          "org.scalatest" %%% "scalatest" % "3.0.0" % "test",
          "org.scalacheck" %%% "scalacheck" % "1.13.2" % "test"
        ),
        unmanagedSourceDirectories in Compile +=
          baseDirectory.value.getParentFile / "shared" / "src" / "main" / "scala",
        unmanagedSourceDirectories in Test +=
          baseDirectory.value.getParentFile / "shared" / "src" / "test" / "scala"
      ): _*
    )
    .jvmSettings(
      (test in Test) <<= (test in Test)
        .dependsOn(test in (reactorsCommon.jvm, Test))
        .dependsOn(test in (reactorsCore.jvm, Test))
        .dependsOn(test in (reactorsContainer.jvm, Test))
        .dependsOn(test in (reactorsRemote.jvm, Test))
        .dependsOn(test in (reactorsProtocols.jvm, Test))
        .dependsOn(test in (reactorsDebugger, Test))
        .dependsOn(test in (reactorsExtra, Test)),
      publish <<= publish
        .dependsOn(publish in reactorsCommon.jvm)
        .dependsOn(publish in reactorsCore.jvm)
        .dependsOn(publish in reactorsContainer.jvm)
        .dependsOn(publish in reactorsRemote.jvm)
        .dependsOn(publish in reactorsProtocols.jvm)
        .dependsOn(publish in reactorsExtra),
      libraryDependencies ++= Seq(
        "com.novocode" % "junit-interface" % "0.11" % "test",
        "junit" % "junit" % "4.12" % "test"
      )
    )
    .jvmConfigure(
      _.copy(id = "reactors-jvm").dependsOnSuperRepo
        .aggregate(
          reactorsDebugger,
          reactorsExtra
        )
        .dependsOn(
          reactorsDebugger % "compile->compile;test->test",
          reactorsExtra % "compile->compile;test->test"
        )
    )
    .jsSettings(
      fork in Test := false,
      fork in run := false,
      (test in Test) <<= (test in Test)
        .dependsOn(test in (reactorsCommon.js, Test))
        .dependsOn(test in (reactorsCore.js, Test))
        .dependsOn(test in (reactorsContainer.js, Test))
        .dependsOn(test in (reactorsRemote.js, Test))
        .dependsOn(test in (reactorsProtocols.js, Test)),
      publish <<= publish
        .dependsOn(publish in reactorsCommon.js)
        .dependsOn(publish in reactorsCore.js)
        .dependsOn(publish in reactorsContainer.js)
        .dependsOn(publish in reactorsRemote.js)
        .dependsOn(publish in reactorsProtocols.js)
    )
    .jsConfigure(
      _.copy(id = "reactors-js").dependsOnSuperRepo
    )
    .aggregate(
      reactorsCommon,
      reactorsCore,
      reactorsContainer,
      reactorsRemote,
      reactorsProtocols
    )
    .dependsOn(
      reactorsCommon % "compile->compile;test->test",
      reactorsCore % "compile->compile;test->test",
      reactorsContainer % "compile->compile;test->test",
      reactorsRemote % "compile->compile;test->test",
      reactorsProtocols % "compile->compile;test->test"
    )

  lazy val reactorsJvm = reactors.jvm

  lazy val reactorsJs = reactors.js
}
