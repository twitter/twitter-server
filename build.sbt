import scoverage.ScoverageKeys

val branch = Process("git" :: "rev-parse" :: "--abbrev-ref" :: "HEAD" :: Nil).!!.trim
val suffix = if (branch == "master") "" else "-SNAPSHOT"

val libVersion = "1.31.0" + suffix
val utilVersion = "7.0.0" + suffix
val finagleVersion = "7.0.0" + suffix

val jacksonVersion = "2.8.4"
val jacksonLibs = Seq(
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion exclude("com.google.guava", "guava"),
  "com.google.guava" % "guava" % "19.0"
)

def util(which: String) = "com.twitter" %% ("util-"+which) % utilVersion
def finagle(which: String) = "com.twitter" %% ("finagle-"+which) % finagleVersion

val sharedSettings = Seq(
  version := libVersion,
  organization := "com.twitter",
  scalaVersion := "2.12.1",
  crossScalaVersions := Seq("2.11.11", "2.12.1"),
  libraryDependencies ++= Seq(
    "org.scalacheck" %% "scalacheck" % "1.13.4" % "test",
    "org.scalatest" %% "scalatest" % "3.0.0" % "test",
    "junit" % "junit" % "4.10" % "test",
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

lazy val twitterServer = Project(
  id = "twitter-server",
  base = file(".")
).settings(
  sharedSettings,
  unidocSettings
).settings(
  name := "twitter-server",
  libraryDependencies ++= Seq(
    finagle("core"),
    finagle("http"),
    finagle("toggle"),
    finagle("tunable"),
    finagle("zipkin-core"),
    util("app"),
    util("core"),
    util("jvm"),
    util("lint"),
    util("logging"),
    util("registry"),
    util("tunable")
  ),
  libraryDependencies ++= jacksonLibs
)

lazy val twitterServerDoc = Project(
  id = "twitter-server-doc",
  base = file("doc")
).enablePlugins(
  SphinxPlugin
).settings(
  sharedSettings,
  Seq(
    scalacOptions in doc ++= Seq("-doc-title", "TwitterServer", "-doc-version", version.value),
    includeFilter in Sphinx := ("*.html" | "*.png" | "*.js" | "*.css" | "*.gif" | "*.txt")
  )
).configs(DocTest).settings(
  inConfig(DocTest)(Defaults.testSettings): _*
).settings(
  unmanagedSourceDirectories in DocTest += baseDirectory.value / "src/sphinx/code",

  // Make the "test" command run both, test and doctest:test
  test := Seq(test in Test, test in DocTest).dependOn.value
).dependsOn(twitterServer)

/* Test Configuration for running tests on doc sources */
lazy val DocTest = config("testdoc") extend Test
