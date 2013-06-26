Twitter-server
==============

Twitter-server defines a template from which servers at Twitter are built. Twitter-server ensures that common components like an administrative HTTP server, tracing, stats, etc. are wired in correctly for production use at Twitter.

Quick-start
-----------

Twitter-server is published with Maven:

::

  <dependency>
    <groupId>com.twitter</groupId>
    <artifactId>twitter-server_2.9.2</artifactId>
    <version>1.0.2</version>
  </dependency>

or, with sbt:

::

  libraryDependencies += "com.twitter" %% "twitter-server" % "1.0.2"


First we’ll need to import a few things into our namespace.

.. includecode:: code/BasicServer.scala#imports

Twitter-server defines its own version of the standard `main`. To use it, create an object extended with `com.twitter.server.TwitterServer`, and define the `main()` method (no arguments).

In this example, we use the finagle HTTP server to start a server on port 8888, the service bound to this port is a simple hello service.

.. includecode:: code/BasicServer.scala#server

`onExit` is used to register code to be run when the process shutdown is requested.

After compiling, we can start the server like any other java or scala process.

::

  $ java -jar target/myserver-1.0.0-SNAPSHOT.jar &
  [1] 66569
  Feb 21, 2013 10:55:57 AM com.twitter.finagle.http.HttpMuxer$$anonfun$5 apply
  INFO: HttpMuxer[/admin/metrics.json] = com.twitter.finagle.stats.MetricsExporter(<function1>)
  Feb 21, 2013 10:55:57 AM com.twitter.finagle.http.HttpMuxer$$anonfun$5 apply
  INFO: HttpMuxer[/stats] = com.twitter.finagle.stats.OstrichExporter(<function1>)

  $ curl localhost:8888
  hello

This server is fully configured to run in Twitter's production environment; see :doc:`features <Features>` for more details.

User's guide
------------

.. toctree::
   :maxdepth: 4

   Features
   Migration
