import scoverage.ScoverageKeys

// All Twitter library releases are date versioned as YY.MM.patch
val releaseVersion = "17.12.0-SNAPSHOT"

val jacksonVersion = "2.8.4"
val jacksonLibs = Seq(
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion exclude("com.google.guava", "guava"),
  "com.google.guava" % "guava" % "19.0"
)
val slf4jVersion = "1.7.21"

def util(which: String) = "com.twitter" %% ("util-"+which) % releaseVersion
def finagle(which: String) = "com.twitter" %% ("finagle-"+which) % releaseVersion

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false,
  // sbt-pgp's publishSigned task needs this defined even though it is not publishing.
  publishTo := Some(Resolver.file("Unused transient repository", file("target/unusedrepo")))
)

lazy val sharedSettings = Seq(
  version := releaseVersion,
  organization := "com.twitter",
  scalaVersion := "2.12.4",
  crossScalaVersions := Seq("2.11.11", "2.12.4"),
  libraryDependencies ++= Seq(
    "org.scalacheck" %% "scalacheck" % "1.13.4" % "test",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test",
    // See http://www.scala-sbt.org/0.13/docs/Testing.html#JUnit
    "com.novocode" % "junit-interface" % "0.11" % "test",
    "org.mockito" % "mockito-all" % "1.9.5" % "test"
  ),

  ScoverageKeys.coverageHighlighting := true,

  ivyXML :=
    <dependencies>
      <exclude org="com.sun.jmx" module="jmxri" />
      <exclude org="com.sun.jdmk" module="jmxtools" />
      <exclude org="javax.jms" module="jms" />
    </dependencies>,

  scalacOptions ++= Seq(
    "-target:jvm-1.8",
    "-deprecation",
    "-unchecked",
    "-feature", "-Xlint",
    "-encoding", "utf8"
  ),
  javacOptions ++= Seq("-Xlint:unchecked", "-source", "1.8", "-target", "1.8"),
  javacOptions in doc := Seq("-source", "1.8"),

  // This is bad news for things like com.twitter.util.Time
  parallelExecution in Test := false,

  // -a: print stack traces for failing asserts
  testOptions += Tests.Argument(TestFrameworks.JUnit, "-a"),

  // Sonatype publishing
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  publishMavenStyle := true,
  autoAPIMappings := true,
  apiURL := Some(url("https://twitter.github.io/twitter-server/docs/")),
  pomExtra :=
    <url>https://github.com/twitter/twitter-server</url>
    <licenses>
      <license>
        <name>Apache License, Version 2.0</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0</url>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:twitter/twitter-server.git</url>
      <connection>scm:git:git@github.com:twitter/twitter-server.git</connection>
    </scm>
    <developers>
      <developer>
        <id>twitter</id>
        <name>Twitter Inc.</name>
        <url>https://www.twitter.com/</url>
      </developer>
    </developers>,
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (version.value.trim.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  }
)

lazy val root = (project in file("."))
  .enablePlugins(
    ScalaUnidocPlugin
  )
  .settings(sharedSettings)
  .settings(noPublishSettings)
  .aggregate(
    twitterServer,
    twitterServerSlf4jJdk14,
    twitterServerSlf4jLog4j12,
    twitterServerSlf4jLogbackClassic)

lazy val twitterServer = (project in file("server"))
  .enablePlugins(
    ScalaUnidocPlugin
  )
  .settings(
    name :=  "twitter-server",
    moduleName :=  "twitter-server",
    sharedSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      finagle("core"),
      finagle("http"),
      finagle("toggle"),
      finagle("tunable"),
      finagle("zipkin-core"),
      util("app"),
      util("core"),
      util("jvm"),
      util("lint"),
      util("registry"),
      util("slf4j-api"),
      util("slf4j-jul-bridge"),
      util("tunable")
    ),
    libraryDependencies ++= jacksonLibs)

lazy val twitterServerSlf4jJdk14 = (project in file("slf4j-jdk14"))
  .settings(
    name :=  "twitter-server-slf4j-jdk14",
    moduleName :=  "twitter-server-slf4j-jdk14",
    sharedSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      "org.slf4j" % "slf4j-jdk14" % slf4jVersion,
      "org.slf4j" % "jcl-over-slf4j" % slf4jVersion,
      "org.slf4j" % "log4j-over-slf4j" % slf4jVersion))
  .dependsOn(
    twitterServer)

lazy val twitterServerSlf4jLog4j12 = (project in file("slf4j-log4j12"))
  .settings(
    name :=  "twitter-server-slf4j-log4j12",
    moduleName :=  "twitter-server-slf4j-log4j12",
    sharedSettings)
  .settings(
    libraryDependencies ++= Seq(
      "log4j" % "log4j" % "1.2.17" % "provided",
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      "org.slf4j" % "slf4j-log4j12" % slf4jVersion,
      "org.slf4j" % "jcl-over-slf4j" % slf4jVersion,
      "org.slf4j" % "jul-to-slf4j" % slf4jVersion))
  .dependsOn(
    twitterServer)

lazy val twitterServerSlf4jLogbackClassic = (project in file("logback-classic"))
  .settings(
    name :=  "twitter-server-logback-classic",
    moduleName :=  "twitter-server-logback-classic",
    sharedSettings)
  .settings(
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.1.7" % "provided",
      "ch.qos.logback" % "logback-core" % "1.1.7" % "provided",
      "org.slf4j" % "slf4j-api" % slf4jVersion,
      "org.slf4j" % "jcl-over-slf4j" % slf4jVersion,
      "org.slf4j" % "jul-to-slf4j" % slf4jVersion,
      "org.slf4j" % "log4j-over-slf4j" % slf4jVersion))
  .dependsOn(
    twitterServer)

lazy val twitterServerDoc = (project in file("doc"))
  .enablePlugins(
    ScalaUnidocPlugin,
    SphinxPlugin
  )
  .settings(
    name := "twitter-server-doc",
    sharedSettings,
    Seq(
      scalacOptions in doc ++= Seq("-doc-title", "TwitterServer", "-doc-version", version.value),
      includeFilter in Sphinx := ("*.html" | "*.png" | "*.js" | "*.css" | "*.gif" | "*.txt")))
  .configs(DocTest).settings(
    inConfig(DocTest)(Defaults.testSettings): _*)
  .settings(
    unmanagedSourceDirectories in DocTest += baseDirectory.value / "src/sphinx/code",

    // Make the "test" command run both, test and doctest:test
    test := Seq(test in Test, test in DocTest).dependOn.value)
  .dependsOn(twitterServer)

/* Test Configuration for running tests on doc sources */
lazy val DocTest = config("testdoc") extend Test