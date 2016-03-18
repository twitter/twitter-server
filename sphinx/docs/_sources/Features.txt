Features
========

We’ll walk through the features provided by TwitterServer by
examining a slightly more advanced version of the example shown in the
introduction.

.. includecode:: code/AdvancedServer.scala
   :language: scala

Flags
-----

.. _flags:

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

Admin HTTP interface
--------------------

TwitterServer provides an HTTP server and includes a variety of tools
for diagnostics, profiling, and more. The details are covered in-depth
:ref:`here <admin_interface>`.

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
