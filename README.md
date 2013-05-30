Twitter Server
==============

Twitter-server defines a template from which servers at Twitter are built. Twitter-server ensures that common components like an administrative HTTP server, tracing, stats, etc. are wired in correctly for production use at Twitter.

Quick-start
-----------

Twitter-server is published with Maven:

    <dependency>
      <groupId>com.twitter</groupId>
      <artifactId>twitter-server</artifactId>
      <version>1.0.1</version>
    </dependency>

or, with sbt:

    libraryDependencies += "com.twitter" %% "twitter-server" % "1.0.1"

Full Documentation
------------------

<http://twitter.github.io/twitter-server/>
