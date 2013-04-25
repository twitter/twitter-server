Migration from Ostrich
======================

`Ostrich <https://github.com/twitter/ostrich>`_ is a library used to maintain and export statistics and track services. It is obsoleted by twitter-server.

`Metrics <https://github.com/twitter/commons/tree/master/src/java/com/twitter/common/metrics>`_ replaces Ostrich’s stats library.

.. note::

	Ostrich stats are still present on /stats if `finagle-ostrich4` is in your runtime classpath.

While ostrich provided several different primitives for process statistics (stats, counters, and gauges), metrics provides just one: a gauge. Gauges are instantaneous measurements — they are an exported variable. Metrics provides counters and histograms as well, but these just export gauges.

The upshot is that the exported metrics are raw: every request to /admin/metrics.json simply reads the current value of the collection of metrics. Ostrich provided in-process rate computation of counters — this is no longer possible with metrics.

Stats format
------------

E.g. (Ostrich):

::

  {
    "counters": {
      "finagle/closechans": 594,
      "finagle/closed": 594,
      "finagle/closes": 576,

    },
    "gauges": {
      "finagle/connections": 2,
      "finagle/http/failfast/unhealthy_for_ms": 0,
      "finagle/http/failfast/unhealthy_num_tries": 0,

    },
    "labels": {},
    "metrics": {
      "finagle/connection_duration": {
        "average": 1076,
        "count": 594,
        "maximum": 315467,
        "minimum": 3,
        "p50": 32,
        "p90": 116,
        "p95": 116,
        "p99": 386,
        "p999": 315467,
        "p9999": 315467,
        "sum": 639346
    },

    }
  }

E.g. (metrics):

::

  {
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

.. note::

	The stats exported by Ostrich will also be computed by the Ostrich library. It's not a format conversion but a real dual collection/export of stats. You can compare both stats, but note than you can have different results in histogram values because metrics is *far* more precise than Ostrich.

For example, here are the difference between the two libraries for 10k random numbers between 1 and 10,000:

::

  scala> "real p50:%d  ostrich:%d  metrics:%d".format(p50, op50, mp50)
  res7: String = real p50:5066  ostrich:5210  metrics:5066

  scala> "real p90:%d  ostrich:%d  metrics:%d".format(p90, op90, mp90)
  res8: String = real p90:9072  ostrich:9498  metrics:9072

  scala> "real p99:%d  ostrich:%d  metrics:%d".format(p99, op99, mp99)
  res9: String = real p99:9911  ostrich:9498  metrics:9910

You can `run this code on your machine <https://gist.github.com/stevegury/261b0a204cd0726f47ea>`_ to see by yourself.

Step by step guide
------------------

* Convert your code to twitter-server.

Your server will run as before and expose stats through ostrich "/stats" endpoint as well as through "/metrics.json" endpoint. The observability team will continue to collect your stats from ostrich output on "/stats".

* Update your dashboard.

Update your collecting system to collect stats from the new URL.

* Disable the ostrich stats

Simply by excluding the finagle-ostrich4 dependency


Problem
-------

* Historical Data

If you want to keep your historical data, you need to rebuild the old delta'd data into absolute data.
