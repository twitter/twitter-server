Features
========

We’ll walk through the features provided by TwitterServer by
examining a slightly more advanced version of the example shown in the
introduction.

.. includecode:: code/AdvancedServer.scala
   :language: scala

Flags
-----

The flags implementation, `found in Twitter's util library
<https://github.com/twitter/util/blob/master/util-app/src/main/scala/com/twitter/app/Flag.scala>`_,
focuses on simplicity and type safety, parsing flags into Scala values.

You define your flag like this, in that case the flag type is `String`:

.. includecode:: code/AdvancedServer.scala#flag
   :language: scala

But you can also define flags of composite type:

.. includecode:: code/AdvancedServer.scala#complex_flag
   :language: scala

We also provide an automatic help entry that displays information about
all the flags defined.

::

  $ java -jar target/myserver-1.0.0-SNAPSHOT.jar -help
  AdvancedServer
    -alarm_durations='1.seconds,5.seconds': 2 alarm durations
    -help='false': Show this help
    -admin.port=':9990': Admin http server port
    -bind=':0': Network interface to use
    -log.level='INFO': Log level
    -log.output='/dev/stderr': Output file
    -what='hello': String to return

Note that you cannot read flags until after the arguments have been
parsed, which happens before premains have been executed, but after
the constructor and the inits.  Similarly, you should only declare a
flag in the constructor, before the arguments have been parsed.

As a precaution, we recommend turning on the `failfastOnFlagsNotParsed`
option within your server. Having this option turned on means that if
a `Flag` is attempted to be accessed before the flag has been parsed,
then an `IllegalStateException` will be thrown.

.. includecode:: code/AdvancedServer.scala#fail_fast
   :language: scala

Logging
-------

The `TwitterServer` trait provides a logger named `log`. It is
configured via default command line flags: `-log.level` and
`-log.output`. As you can see from the above `-help` output, it logs
to `stderr` by default with a log level of `INFO`.

.. includecode:: code/AdvancedServer.scala#log_usage
   :language: scala

To change the format of the log output, a custom `Formatter` is needed.
This is best done by overriding the `defaultFormatter` provided by the
`Logging` trait.

.. includecode:: code/AdvancedServer.scala#formatter
   :language: scala

For more complicated logging schemes, you can extend the Logging trait
and mix it back into a `TwitterServer`.

Per-logger log levels can be changed on-the-fly via the logging
handler on the admin interface.

.. _metrics_label:

Metrics
-------

Note: In order to enable usage of the Metrics library, you must have
the finagle-stats jar on your classpath.  `finagle-stats` depends on
libraries which can be found in the
`https://maven.twttr.com <https://maven.twttr.com>`_ repository.  There
are instructions on the :doc:`quickstart <index>` for adding it in
maven or sbt.

The `statsReceiver` field of `TwitterServer` defines a sink for
metrics. With it you can update counters and stats (histograms) or
define gauges (instantaneous values).

For instance, you define your stats:

.. includecode:: code/AdvancedServer.scala#stats
   :language: scala

And update the value:

.. includecode:: code/AdvancedServer.scala#stats_usage
   :language: scala

The value of this counter will be exported by the HTTP server and
accessible at /admin/metrics.json. To see an example of the counter
incrementing run the following:

::

  $ curl -s localhost:9990/admin/metrics.json | jq '.requests_counter'
  0

  $ curl -s localhost:9990/echo
  hello

  $ curl -s localhost:9990/admin/metrics.json | jq '.requests_counter'
  1

Filtering stats out
*******************

Metrics can be too expensive to store. By passing a comma-separated
list of regexes to exclude from stats using
`-com.twitter.finagle.stats.statsFilter` flag, one can single out the
stats that will not be shown when queried with `filtered=true`. In
other words, you can still access all of the stats normally, but this
adds the option to fetch the filtered list.

For example, to filter out all stats starting with jvm and also any
p90 stats, one can pass the following to TwitterServer:

::

-com.twitter.finagle.stats.statsFilter="jvm.*,.*\.p90"

To query the reduced list:

::

/admin/metrics.json?filtered=true

Note that this only works with `finagle-stats` and doesn't work with
`finagle-ostrich4`.

Pretty output
*************

If you would like a pretty version of the json output, add the
parameter pretty=true or pretty=1, eg /admin/metrics.json?pretty=true

::

  {
    "requests_counter": 234,
    "finagle/closechans": 592,
    "finagle/connection_duration.avg": 561,
    "finagle/connection_duration.count": 592,
    "finagle/connection_duration.max": 299986,
    "finagle/connection_duration.min": 3,
    "finagle/connection_duration.p50": 31,
    "finagle/connection_duration.p90": 111,
    "finagle/connection_duration.p95": 120,
    "finagle/connection_duration.p99": 197,
    "finagle/connection_duration.p9990": 2038,
    "finagle/connection_duration.p9999": 2038,
    "finagle/connection_duration.sum": 332690,
    "finagle/success": 0
    ...
  }


JVM Metrics
+++++++++++

A wide variety of metrics are exported by TwitterServer which give
you insight into the JVM's garbage collection. These are computed
in `com.twitter.server.util.JvmStats` and exported as metrics.
These include metrics related to generation size, threads, and garbage collection
and are exported as metrics at `jvm/mem`, `jvm/thread`, and `jvm/gc`
respectively.

If you are using a Hotspot VM, you get a few additional metrics that
may be useful. This includes safe point time (`jvm/safepoint`),
metaspace usage (`jvm/mem/metaspace`) and allocation rates (`jvm/mem/allocations`).
The eden allocation gauge (`jvm/mem/allocations/eden/bytes`) is a particularly
relevant metric for service developers. The vast majority of allocations are
done into the eden space, so this metric can be used to calculate the allocations
per request which in turn can be used to validate code changes
don't increase garbage collection pressure on the hot path.

HTTP Admin interface
--------------------

TwitterServer starts an HTTP server (it binds to the port defined by
the flag `-admin.port`; port 9990 by default). Visit `/admin` on your admin port
in a web browser to see what is available.
TwitterServer defines a series of default endpoints:

**/admin/announcer**
  Returns a set of announcement chains that have run through the
  Announcer. This allows one to see how a particular target is being
  announced.

**/admin/pprof/contention**
  Returns a CPU contention profile which identifies blocked threads
  (`Thread.State.BLOCKED`).
  The output is in `pprof <https://github.com/gperftools/gperftools>`_ format.
  The process will be profiled for 10 seconds at a frequency of 100 hz. These
  values can be controlled via HTTP request parameters `seconds` and `hz`
  respectively.

**/admin/pprof/profile**
  Returns a CPU usage profile. The output is in `pprof
  <https://github.com/gperftools/gperftools>`_ format.
  The process will be profiled for 10 seconds at a frequency of 100 hz. These
  values can be controlled via HTTP request parameters `seconds` and `hz`
  respectively.

::

  $ curl -s localhost:9990/admin/pprof/profile > /tmp/cpu_profiling
  $ pprof --text /tmp/cpu_profiling
  Using local file /tmp/cpu_profiling.
  Using local file /tmp/cpu_profiling.
  Total: 83 samples
        17  20.5%  20.5%       24  28.9% com.twitter.finagle.ProxyServiceFactory$class.status
         8   9.6%  30.1%       10  12.0% scala.collection.immutable.HashMap$HashTrieMap.updated0
         5   6.0%  36.1%       70  84.3% scala.collection.Iterator$class.foreach
         5   6.0%  42.2%        5   6.0% scala.runtime.ScalaRunTime$.hash
         4   4.8%  47.0%        4   4.8% com.twitter.finagle.transport.Transport$$anon$2.status
         4   4.8%  51.8%        4   4.8% sun.management.OperatingSystemImpl.getOpenFileDescriptorCount
         3   3.6%  55.4%        9  10.8% com.twitter.finagle.Filter$$anon$2.status
         ...

**/admin/pprof/heap**
  Returns a heap profile computed by the `heapster agent
  <https://github.com/mariusae/heapster>`_.  The output is in
  `pprof <https://github.com/gperftools/gperftools>`_ format.

::

  $ java -agentlib:heapster -jar target/myserver-1.0.0-SNAPSHOT.jar
  $ pprof /tmp/heapster_profile
  Welcome to pprof!  For help, type 'help'.
  (pprof) top
  Total: 2001520 samples
   2000024  99.9%  99.9%  2000048  99.9% LTest;main
      1056   0.1% 100.0%     1056   0.1% Ljava/lang/Object;
       296   0.0% 100.0%      296   0.0% Ljava/lang/String;toCharArray
       104   0.0% 100.0%      136   0.0% Ljava/lang/Shutdown;

**/admin/metrics.json**
  Export a snapshot of the current statistics of the program. You can
  use the StatsReceiver in your application for add new
  counters/gauges/histograms, simply use the `statsReceiver` variable
  provided by TwitterServer.

  This endpoint is available when you are using the `finagle-stats` library.
  See the :ref:`metrics <metrics_label>` section for more information.

**/admin/metrics**
  Watch specific stats and extract them via http queries.

::

  $ curl "localhost:9990/admin/metrics?m=requests_counter"
  [
    {
      "name" : "requests_counter",
      "delta" : 3.0,
      "value" : 10.0
    }
  ]

**/admin/server_info**
  Return build information about this server.
  See `/admin/registry.json` for this in addition to other details.

::

  {
    "name" : "myserver",
    "version" : "1.0.0-SNAPSHOT",
    "build" : "20130221-105425",
    "build_revision" : "694299d640d337c58fadf668e44322b17fd0562e",
    "build_branch_name" : "refs/heads/twitter-server!doc",
    "build_last_few_commits" : [
      "694299d (HEAD, origin/twitter-server!doc, twitter-server!doc) Merge branch 'master' into twitter-server!doc",
      "ba1c062 Fix test for sbt + Jeff's comments",
    ],
    "start_time" : "Thu Feb 21 13:43:32 PST 2013",
    "uptime" : 22458
  }

**/admin/contention**
  Show call stack of blocked and waiting threads.

::

  $ curl localhost:9990/admin/contention
  Blocked:
  "util-jvm-timer-1" Id=11 TIMED_WAITING on java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject@33aac3c
    at sun.misc.Unsafe.park(Native Method)
    -  waiting on java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject@33aac3c
    at java.util.concurrent.locks.LockSupport.parkNanos(LockSupport.java:226)
    at java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.awaitNanos(AbstractQueuedSynchronizer.java:2082)
    at java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue.take(ScheduledThreadPoolExecutor.java:1090)
    at java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue.take(ScheduledThreadPoolExecutor.java:807)
    at java.util.concurrent.ThreadPoolExecutor.getTask(ThreadPoolExecutor.java:1043)
    at java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1103)
    at java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:603)
    ...

**/admin/clients**
  Surface client information exposed by Finagle. Per-client configuration parameters and
  values for each module are available at /admin/clients/<client name>.

**/admin/servers**
  Surface server information exposed by Finagle. Per-server configuration parameters and
  values for each module are available at /admin/clients/<client name>.

**/admin/registry.json**
  Displays how the service is currently configured across a variety of dimensions
  including the client stack, server stack, flags, service loader values,
  system properties, environment variables, build properties and more.

**/admin/events**
  A user interface for collecting and viewing runtime events to make it easier to
  diagnose production issues. This includes logging by default, while metrics are included
  if you are using ``finagle-stats`` and tracing events are included if you are
  using ``finagle-zipkin``. JSON output is also available
  through tools like curl via the inspection of the HTTP Accept header.

**/admin/lint**
  Runs and displays the results for all registered linters to check for various service issues.

**/admin/shutdown**
  Stop the process gracefully.

**/admin/tracing**
  Enable (/admin/tracing?enable=true) or disable tracing (/admin/tracing?disable=true)

  See `zipkin <https://github.com/openzipkin/zipkin>`_ documentation for more info.

**/admin/threads**
  A user interface for capturing the current stacktraces. Includes filtering
  of inactive threads as well as deadlock detection. JSON output is also available
  through tools like curl via the inspection of the HTTP Accept header.

**/admin/ping**
  Return pong (used for monitoring)

**/admin/logging**
  Display the set of loggers and their current log level. The level of
  each logger can also be modified on-the-fly.

::

  root                              ALL CRITICAL DEBUG ERROR FATAL INFO OFF TRACE WARNING
  com.twitter.ostrich.stats.Metric  ALL CRITICAL DEBUG ERROR FATAL INFO OFF TRACE WARNING
  com.twitter.ostrich.stats.Stats$  ALL CRITICAL DEBUG ERROR FATAL INFO OFF TRACE WARNING

Lifecycle Management
--------------------

TwitterServer exposes endpoints to manage server lifecycle that are compatible with
`Mesos's <http://mesos.apache.org/>`_ job manager:

**/abortabortabort**
  Abort the process.

**/health**
  By default, respond with content-body "OK". This endpoint can be managed manually by mixing in
  the Lifecycle.Warmup trait with your server.

**/quitquitquit**
  Quit the process.


These entries are the default, but if you need you can add your own handler to this HTTP server:

.. includecode:: code/AdvancedServer.scala#registering_http_service
   :language: scala

Extension
---------

TwitterServer can be extended modularly by mixing in more traits. If
you want to alter the behavior of a trait that is already mixed into
`TwitterServer`, you can override methods that you want to have
different behavior and then mix it in again. For example, in the
`Logging
<https://github.com/twitter/util/blob/master/util-logging/src/main/scala/com/twitter/logging/App.scala>`_
trait, you can override loggers to change where you send logs.

If you want finer grained control over your server, you can remix
traits however you like in the same way that the `TwitterServer
<https://github.com/twitter/twitter-server/blob/master/src/main/scala/com/twitter/server/TwitterServer.scala>`_
trait is built.
