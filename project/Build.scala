import sbt._
import Keys._
import Tests._
import com.typesafe.sbt.SbtSite.site
import com.typesafe.sbt.site.SphinxSupport.Sphinx

object TwitterServer extends Build {
  val libVersion = "1.8.1"
  val utilVersion = "6.22.2"
  val finagleVersion = "6.22.2"
  val mustacheVersion = "0.8.12.1"

  // The following won't be necessary once we've upgraded internally to 2.4.
  def jacksonVersion(scalaVersion: String) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 11)) => "2.4.4"
      case _ => "2.3.1"
    }
  def jacksonLibs(scalaVersion: String) = Seq(
    "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion(scalaVersion),
    "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion(scalaVersion),
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion(scalaVersion) exclude("com.google.guava", "guava"),
    "com.google.guava" % "guava" % "16.0.1"
  )

  def util(which: String) = "com.twitter" %% ("util-"+which) % utilVersion
  def finagle(which: String) = "com.twitter" %% ("finagle-"+which) % finagleVersion

  val sharedSettings = Seq(
    version := libVersion,
    organization := "com.twitter",
    scalaVersion := "2.10.4",
    crossScalaVersions := Seq("2.10.4", "2.11.4"),
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % "2.2.2" % "test",
      "junit" % "junit" % "4.10" % "test",
      "org.mockito" % "mockito-all" % "1.9.5" % "test"
    ),
    resolvers += "twitter-repo" at "http://maven.twttr.com",

    ivyXML :=
      <dependencies>
        <exclude org="com.sun.jmx" module="jmxri" />
        <exclude org="com.sun.jdmk" module="jmxtools" />
        <exclude org="javax.jms" module="jms" />
      </dependencies>,

    scalacOptions ++= Seq("-encoding", "utf8"),
    scalacOptions += "-deprecation",
    javacOptions ++= Seq("-source", "1.6", "-target", "1.6"),
    javacOptions in doc := Seq("-source", "1.6"),

    // This is bad news for things like com.twitter.util.Time
    parallelExecution in Test := false,

    // Sonatype publishing
    publishArtifact in Test := false,
    pomIncludeRepository := { _ => false },
    publishMavenStyle := true,
    pomExtra := (
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
      </developers>),
    publishTo <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    }
  )

  lazy val twitterServer = Project(
    id = "twitter-server",
    base = file("."),
    settings = Project.defaultSettings ++
      sharedSettings ++
      Unidoc.settings
  ).settings(
    name := "twitter-server",
    libraryDependencies ++= Seq(
      finagle("core"),
      finagle("http"),
      util("logging"),
      finagle("zipkin"),
      util("app"),
      util("core"),
      util("jvm"),
      "com.github.spullara.mustache.java" % "compiler" % mustacheVersion
    ),
    libraryDependencies <++= scalaVersion(jacksonLibs(_)),
    ivyXML :=
      <dependencies>
        <dependency org="com.github.spullara.mustache.java" name="compiler" rev={mustacheVersion}>
          <exclude org="com.google.guava" name="guava"/>
        </dependency>
      </dependencies>
  )

  lazy val twitterServerDoc = Project(
    id = "twitter-server-doc",
    base = file("doc"),
    settings =
      Project.defaultSettings ++
      sharedSettings ++
      site.settings ++
      site.sphinxSupport() ++
      Seq(
        scalacOptions in doc <++= (version).map(v => Seq("-doc-title", "TwitterServer", "-doc-version", v)),
        includeFilter in Sphinx := ("*.html" | "*.png" | "*.js" | "*.css" | "*.gif" | "*.txt")
      )
    ).configs(DocTest).settings(
      inConfig(DocTest)(Defaults.testSettings): _*
    ).settings(
      unmanagedSourceDirectories in DocTest <+= baseDirectory { _ / "src/sphinx/code" },

      // Make the "test" command run both, test and doctest:test
      test <<= Seq(test in Test, test in DocTest).dependOn
    ).dependsOn(twitterServer)

  /* Test Configuration for running tests on doc sources */
  lazy val DocTest = config("testdoc") extend(Test)
}
