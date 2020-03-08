TwitterServer
==============

TwitterServer defines a template from which servers at Twitter are
built. It provides common application components such as an
administrative HTTP server, tracing, stats, etc. These features are
wired in correctly for use in production at Twitter.

Getting Started
---------------

TwitterServer is published to Maven central:

.. parsed-literal::

  <dependency>
    <groupId>com.twitter</groupId>
    <artifactId>twitter-server_2.12</artifactId>
    <version>\ |release|\ </version>
  </dependency>

or, with sbt:

.. parsed-literal::

  libraryDependencies += "com.twitter" %% "twitter-server" % "|release|"

First we’ll need to import a few things into our namespace.

.. includecode:: code/BasicServer.scala#imports
   :language: scala

TwitterServer defines its own version of the standard ``main``. To use
it, create an object extended with ``com.twitter.server.TwitterServer``,
and define the ``main()`` method (no arguments).

In this example, we use Finagle to start an HTTP server on
port 8888. The service bound to this port is a simple hello service.

.. includecode:: code/BasicServer.scala#server
   :language: scala

``onExit`` is used to register code to be run when the process shutdown
is requested.

After compiling, we can start the server like any other java or scala
process.

::

  $ java -jar target/myserver-1.0.0-SNAPSHOT.jar &
  [1] 74159
  I 1210 21:32:39.326 THREAD1: /admin => com.twitter.server.handler.SummaryHandler
  ...
  I 1210 21:32:39.332 THREAD1: /favicon.ico => com.twitter.server.handler.ResourceHandler
  I 1210 21:32:39.340 THREAD1: Serving admin http on 0.0.0.0/0.0.0.0:9990
  I 1210 21:32:39.361 THREAD1: Finagle version 6.31.0 (rev=50d3bb0eea5ad3ed332111d707184c80fed6a506) built at 20151203-164135
  I 1210 21:32:39.910 THREAD1: Tracer: com.twitter.finagle.zipkin.thrift.SamplingTracer

  $ curl localhost:8888
  hello

This server is fully configured to run in Twitter’s production
environment. See :doc:`features <Features>` for more details.


User’s guide
------------

.. toctree::
   :maxdepth: 4

   Features
   Admin
   Java
   Migration
   FAQ
   Changelog
