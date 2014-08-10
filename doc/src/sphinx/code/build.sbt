name := "myserver"

version := "1.0"

scalaVersion := "2.10.4"

resolvers += "twtter" at "http://maven.twttr.com/"

libraryDependencies += "com.twitter" % "twitter-server" % "1.7.3"
