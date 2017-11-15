resolvers ++= Seq(
  Classpaths.sbtPluginReleases,
  Resolver.sonatypeRepo("snapshots")
)

addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.3.0")
// removed until https://github.com/sbt/sbt/issues/3496 is fixed
// addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC12")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.1")
addSbtPlugin("com.eed3si9n" % "sbt-unidoc" % "0.4.1")
