resolvers += Resolver.url("artifactory-sbt-plugin-releases",
  new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

resolvers += Classpaths.sbtPluginReleases

addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "0.8.1")
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-RC5")
addSbtPlugin("org.scoverage" % "sbt-scoverage" % "1.5.0")
