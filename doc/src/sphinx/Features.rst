Features
========


We’ll walk through the features provided by twitter-server by examining a slightly more advanced version of the example shown in the introduction.

.. includecode:: code/AdvancedServer.scala

Flags
-----

The flags implementation, `found in Twitter's util library <https://github.com/twitter/util/blob/master/util-app/src/main/scala/com/twitter/app/Flag.scala>`_, focuses on simplicity and type safety, parsing flags into Scala values.

You define your flag like this, in that case the flag type is `String`:

.. includecode:: code/AdvancedServer.scala#flag

But you can also define flags of composite type:

.. includecode:: code/AdvancedServer.scala#complex_flag

We also provide automatic help entry that display information about all the flags defined.

Note that you cannot read flags until after the arguments have been parsed, which happens before
premains have been executed, but after the constructor and the inits.  Similarly, you should only
declare a flag in the constructor, before the arguments have been parsed.

If you access a flag before the parsing phase (e.g. inside a constructor), you will see a SEVERE
log message.

::

  $ java -jar target/myserver-1.0.0-SNAPSHOT.jar -help
  AdvancedServer
    -alarm_durations='1.seconds,5.seconds': 2 alarm durations
    -help='false': Show this help
    -admin.port=':8080': Admin http server port
    -bind=':0': Network interface to use
    -log.level='INFO': Log level
    -log.output='/dev/stderr': Output file
    -what='hello': String to return

Logging
-------

Trait `TwitterServer` provides a logger named `log`. It is configured via default command line flags: `-log.level` and `-log.output`. As you can see from the above `-help` output, it logs to `stderr` by default with a log level of `INFO`.

.. includecode:: code/AdvancedServer.scala#log_usage

For more complicated logging schemes, you can extend the Logging trait and mix it back into twitter-server.

Per-logger log levels can be changed on-the-fly via the logging handler on the admin interface.

.. _metrics_label:

Metrics
-------

Note: in order to turn on metrics, you must have the finagle-stats jar on your classpath.  finagle-stats depends on libraries which can be found in the maven.twttr.com repository.  There are instructions on the :doc:`quickstart <index>` for adding it in maven or sbt.

`statsReceiver`, defined by `TwitterServer`, defines a sink for metrics. With it you can update counters and stats (histograms) or define gauges (instantaneous values).

For instance, you define your stats:

.. includecode:: code/AdvancedServer.scala#stats

And update the value:

.. includecode:: code/AdvancedServer.scala#stats_usage

The value of this counter will be exported by the HTTP server and accessible at /admin/metrics.json

Filtering stats out
*******************

Metrics can be too expensive to store. By passing a comma-separated list of regexes to exclude from stats using `-com.twitter.finagle.stats.statsFilter`  flag, one can single out the stats that will not be shown when queried with `filtered=true`. In other words, you can still access all of the stats normally, but this adds the option to fetch the filtered list.

For example, to filter out all stats starting with jvm and also any p90 stats, one can pass the following to Twitter-Server:

::

-com.twitter.finagle.stats.statsFilter="jvm.*,.*\.p90"

To query the reduced list:

::

/admin/metrics.json?filtered=true

Note that this only works with `finagle-stats` and doesn't work with `finagle-ostrich4`.

Pretty output
*************

If you would like a pretty version of the json output, add the parameter pretty=true or pretty=1, eg /admin/metrics.json?pretty=true

::

  {
    "requests_counter": 234,
    "finagle/closechans": 592,
    "finagle/closed": 592,
    "finagle/closes": 575,
    "finagle/connection_duration.avg": 561,
    "finagle/connection_duration.count": 592,
    "finagle/connection_duration.max": 299986,
    "finagle/connection_duration.min": 3,
    "finagle/connection_duration.p25": 29,
    "finagle/connection_duration.p50": 31,
    "finagle/connection_duration.p75": 58,
    "finagle/connection_duration.p90": 111,
    "finagle/connection_duration.p95": 120,
    "finagle/connection_duration.p99": 197,
    "finagle/connection_duration.p9990": 2038,
    "finagle/connection_duration.p9999": 2038,
    "finagle/connection_duration.sum": 332690,
    "finagle/connections": 2,
    "finagle/http/failfast/unhealthy_for_ms": 0,
    "finagle/http/failfast/unhealthy_num_tries": 0,
    "finagle/success": 0
    ...
  }


HTTP Admin interface
--------------------

Twitter-server starts an HTTP server (it binds to the port defined by the flag `-admin.port`; port 8080 by default). It exports an HttpMuxer object in which endpoints are registered. The library defines a series of default endpoints:

::

  $ curl localhost:8080/admin
  /admin/pprof/contention
  /admin/pprof/profile
  /admin/metrics.json
  /admin/server_info
  /admin/resolutions
  /admin/pprof/heap
  /admin/contention
  /admin/clients
  /admin/announcer
  /admin/shutdown
  /admin/logging
  /admin/resolver
  /admin/tracing
  /admin/threads
  /admin/ping

**/admin/resolutions**
  Returns a set of resolution chains that have run through Resolver. This allows one to see how a particular target is being resolved.

**/admin/announcer**
  Returns a set of announcement chains that have run through the Announcer. This allows one to see how a particular target is being announced.

**/admin/pprof/contention**
  Returns a CPU contention profile. The output is in `pprof <http://code.google.com/p/gperftools/>`_ format.

**/admin/pprof/profile**
  Returns a CPU usage profile. The output is in `pprof <http://code.google.com/p/gperftools/>`_ format.

::

  $ curl -s localhost:8080/admin/pprof/profile > /tmp/cpu_profiling
  $ pprof --text /tmp/cpu_profiling
  Using local file /tmp/cpu_profiling.
  Using local file /tmp/cpu_profiling.
  Total: 48 samples
        47  97.9%  97.9%       47  97.9% sun.nio.ch.KQueueArrayWrapper.kevent0
         1   2.1% 100.0%        1   2.1% java.lang.System.arraycopy
         0   0.0% 100.0%        1   2.1% com.twitter.concurrent.AsyncQueue.offer
         0   0.0% 100.0%        1   2.1% com.twitter.concurrent.Scheduler$.submit
         0   0.0% 100.0%        1   2.1% com.twitter.concurrent.Scheduler$LocalScheduler.run
         0   0.0% 100.0%        1   2.1% com.twitter.concurrent.Scheduler$LocalScheduler.submit
         0   0.0% 100.0%        1   2.1% com.twitter.finagle.Filter$$anon$2.apply
         ...

**/admin/pprof/heap**
  Returns a heap profile computed by the `heapster agent <https://github.com/mariusaeriksen/heapster>`_. The output is in `pprof <http://code.google.com/p/gperftools/>`_ format.

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
  Export a snapshot of the current statistics of the program. You can use the StatsReceiver in your application for add new counters/gauges/histograms, simply use the `statsReceiver` variable provided by TwitterServer.

See the :ref:`metrics <metrics_label>` section for more information.

**/admin/server_info**
  Return build informations about this server

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

  $ curl localhost:8080/admin/contention
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
  Display a list of clients. Per-client configuration parameters and values for each module are available at /admin/clients?name=<client name>.

**/admin/shutdown**
  Stop the process gracefully.

**/admin/tracing**
  Enable (/admin/tracing?enable=true) or disable tracing (/admin/tracing?disable=true)

See `zipkin <https://github.com/twitter/zipkin>`_ documentation for more info.

**/admin/threads**
  Dumps the call stacks of all the threads (JSON output).

::

  {
    "threads" : {
      "12" : {
        "priority" : 5,
        "state" : "TIMED_WAITING",
        "daemon" : true,
        "thread" : "util-jvm-timer-1",
        "stack" : [
          "sun.misc.Unsafe.park(Native Method)",
          "java.util.concurrent.locks.LockSupport.parkNanos(LockSupport.java:226)",
          "java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.awaitNanos(AbstractQueuedSynchronizer.java:2082)",
          "java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue.take(ScheduledThreadPoolExecutor.java:1090)",
          "java.util.concurrent.ScheduledThreadPoolExecutor$DelayedWorkQueue.take(ScheduledThreadPoolExecutor.java:807)",
          "java.util.concurrent.ThreadPoolExecutor.getTask(ThreadPoolExecutor.java:1043)",
          "java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1103)",
          "java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:603)",
          "java.lang.Thread.run(Thread.java:722)"
        ]
      },
      ...
    }
  }

**/admin/ping**
  Return pong (used for monitoring)

**/admin/logging**
  Display the set of loggers and their current log level. The level of each logger can also be modified on-the-fly.

::

  root                              ALL CRITICAL DEBUG ERROR FATAL INFO OFF TRACE WARNING
  com.twitter.ostrich.stats.Metric  ALL CRITICAL DEBUG ERROR FATAL INFO OFF TRACE WARNING
  com.twitter.ostrich.stats.Stats$  ALL CRITICAL DEBUG ERROR FATAL INFO OFF TRACE WARNING

Lifecycle Management
--------------------

Twitter-server exposes endpoints to manage server lifecycle that are compatible with `Mesos's <http://mesos.apache.org/>`_ job manager:

**/abortabortabort**
  Abort the process.

**/health**
  By default, respond with content-body "OK". This endpoint can be managed manually by mixing in the Lifecycle.Warmup trait with your server.

**/quitquitquit**
  Quit the process.


These entries are the default, but if you need you can add your own handler to this HTTP server:

.. includecode:: code/AdvancedServer.scala#registering_http_service

Extension
---------

Twitter-server can be extended modularly by mixing in more traits.  If you want to alter the behavior of a trait that is already mixed into twitter-server, you can override methods that you want to have different behavior and then mix it in again.  For example, in the `Logging <https://github.com/twitter/util/blob/master/util-logging/src/main/scala/com/twitter/logging/App.scala>`_ trait, you can override loggers to change where you send logs.

If you want finer grained control over your server, you can remix traits however you like in the same way that the `TwitterServer <https://github.com/twitter/twitter-server/blob/master/src/main/scala/com/twitter/server/TwitterServer.scala>`_ trait is built.
