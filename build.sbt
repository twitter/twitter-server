import scoverage.ScoverageKeys

// All Twitter library releases are date versioned as YY.MM.patch
val releaseVersion = "20.3.0-SNAPSHOT"

val jacksonVersion = "2.9.9"
val jacksonDatabindVersion = "2.9.10.1"
val jacksonLibs = Seq(
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonDatabindVersion,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion exclude("com.google.guava", "guava")
)
val opencensusVersion = "0.19.1"
val slf4jVersion = "1.7.30"

def util(which: String) = "com.twitter" %% ("util-"+which) % releaseVersion
def finagle(which: String) = "com.twitter" %% ("finagle-"+which) % releaseVersion

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false,
  // sbt-pgp's publishSigned task needs this defined even though it is not publishing.
  publishTo := Some(Resolver.file("Unused transient repository", file("target/unusedrepo")))
)

def gcJavaOptions: Seq[String] = {
  val javaVersion = System.getProperty("java.version")
  if (javaVersion.startsWith("1.8")) {
    jdk8GcJavaOptions
  } else {
    jdk11GcJavaOptions
  }
}

def jdk8GcJavaOptions: Seq[String] = {
  Seq(
    "-XX:+UseParNewGC",
    "-XX:+UseConcMarkSweepGC",
    "-XX:+CMSParallelRemarkEnabled",
    "-XX:+CMSClassUnloadingEnabled",
    "-XX:ReservedCodeCacheSize=128m",
    "-XX:SurvivorRatio=128",
    "-XX:MaxTenuringThreshold=0",
    "-Xss8M",
    "-Xms512M",
    "-Xmx2G"
  )
}

def jdk11GcJavaOptions: Seq[String] = {
  Seq(
    "-XX:+UseConcMarkSweepGC",
    "-XX:+CMSParallelRemarkEnabled",
    "-XX:+CMSClassUnloadingEnabled",
    "-XX:ReservedCodeCacheSize=128m",
    "-XX:SurvivorRatio=128",
    "-XX:MaxTenuringThreshold=0",
    "-Xss8M",
    "-Xms512M",
    "-Xmx2G"
  )
}

def travisTestJavaOptions: Seq[String] = {
  // We have some custom configuration for the Travis environment
  // https://docs.travis-ci.com/user/environment-variables/#default-environment-variables
  val travisBuild = sys.env.getOrElse("TRAVIS", "false").toBoolean
  if (travisBuild) {
    Seq(
      "-DSKIP_FLAKY=true",
      "-DSKIP_FLAKY_TRAVIS=true"
    )
  } else {
    Seq(
      "-DSKIP_FLAKY=true"
    )
  }
}

lazy val sharedSettings = Seq(
  version := releaseVersion,
  organization := "com.twitter",
  scalaVersion := "2.12.8",
  crossScalaVersions := Seq("2.11.12", "2.12.8", "2.13.1"),
  fork in Test := true, // We have to fork to get the JavaOptions
  libraryDependencies ++= Seq(
    "org.scalacheck" %% "scalacheck" % "1.14.0" % "test",
    "org.scalatest" %% "scalatest" % "3.0.8" % "test",
    // See https://www.scala-sbt.org/0.13/docs/Testing.html#JUnit
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
  
  javaOptions ++= Seq(
    "-Djava.net.preferIPv4Stack=true",
    "-XX:+AggressiveOpts",
    "-server"
  ),

  javaOptions ++= gcJavaOptions,

  javaOptions in Test ++= travisTestJavaOptions,

  // This is bad news for things like com.twitter.util.Time
  parallelExecution in Test := false,

  // -a: print stack traces for failing asserts
  testOptions += Tests.Argument(TestFrameworks.JUnit, "-a"),

  // Sonatype publishing
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  publishMavenStyle := true,
  publishConfiguration := publishConfiguration.value.withOverwrite(true),
  autoAPIMappings := true,
  apiURL := Some(url("https://twitter.github.io/twitter-server/docs/")),
  pomExtra :=
    <url>https://github.com/twitter/twitter-server</url>
    <licenses>
      <license>
        <name>Apache License, Version 2.0</name>
        <url>https://www.apache.org/licenses/LICENSE-2.0</url>
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
    twitterServerOpenCensus,
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

lazy val twitterServerOpenCensus = (project in file("opencensus"))
  .enablePlugins(
    ScalaUnidocPlugin
  )
  .settings(
    name :=  "twitter-server-opencensus",
    moduleName :=  "twitter-server-opencensus",
    sharedSettings)
  .settings(
    libraryDependencies ++= Seq(
      finagle("core"),
      finagle("http"),
      "io.opencensus" % "opencensus-api" % opencensusVersion,
      "io.opencensus" % "opencensus-contrib-zpages" % opencensusVersion
    ))
  .dependsOn(
    twitterServer)


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
      "ch.qos.logback" % "logback-classic" % "1.2.3" % "provided",
      "ch.qos.logback" % "logback-core" % "1.2.3" % "provided",
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
