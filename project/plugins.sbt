resolvers ++= Seq(
  Classpaths.sbtPluginReleases,
  Resolver.sonatypeRepo("snapshots")
)

addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "1.4.1")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.9.1")
addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.5.0")
