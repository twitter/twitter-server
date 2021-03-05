package com.twitter.server.handler

import com.twitter.finagle.http.Request
import com.twitter.finagle.stats._
import com.twitter.server.util.MetricSchemaSource
import com.twitter.util.Await
import org.scalatest.FunSuite

class MetricMetadataQueryHandlerTest extends FunSuite {

  val schemaMap: Map[String, MetricSchema] = Map(
    "my/cool/counter" -> CounterSchema(
      new MetricBuilder(
        keyIndicator = true,
        description = "Counts how many cools are seen",
        units = Requests,
        role = Server,
        verbosity = Verbosity.Default,
        sourceClass = Some("finagle.stats.cool"),
        name = Seq("my", "cool", "counter"),
        processPath = Some("dc/role/zone/service"),
        percentiles = IndexedSeq(0.5, 0.9, 0.95, 0.99, 0.999, 0.9999),
        statsReceiver = null
      )),
    "your/fine/gauge" -> GaugeSchema(
      new MetricBuilder(
        keyIndicator = false,
        description = "Measures how fine the downstream system is",
        units = Percentage,
        role = Client,
        verbosity = Verbosity.Debug,
        sourceClass = Some("finagle.stats.your"),
        name = Seq("your", "fine", "gauge"),
        processPath = Some("dc/your_role/zone/your_service"),
        percentiles = IndexedSeq(0.5, 0.9, 0.95, 0.99, 0.999, 0.9999),
        statsReceiver = null
      )),
    "my/only/histo" -> HistogramSchema(
      new MetricBuilder(
        name = Seq("my", "only", "histo"),
        percentiles = IndexedSeq(0.5, 0.9, 0.95, 0.99, 0.999, 0.9999),
        statsReceiver = null
      )),
    "my/bad/null/counter" -> CounterSchema(
      new MetricBuilder(
        keyIndicator = true,
        description = "A counter scoped by null get deserialized correctly",
        units = Requests,
        role = Server,
        verbosity = Verbosity.Default,
        sourceClass = Some("finagle.stats.bad"),
        name = Seq("my", "bad", null, "counter"),
        processPath = Some("dc/role/zone/service"),
        percentiles = IndexedSeq(0.5, 0.9, 0.95, 0.99, 0.999, 0.9999),
        statsReceiver = null
      ))
  )

  trait UnlatchedRegistry extends SchemaRegistry {
    val hasLatchedCounters = false
    override def schemas(): Map[String, MetricSchema] = schemaMap
  }

  trait LatchedRegistry extends SchemaRegistry {
    val hasLatchedCounters = true
    override def schemas(): Map[String, MetricSchema] = schemaMap
  }

  val latchedSchemaRegistry = new LatchedRegistry() {}

  val unlatchedSchemaRegistry = new UnlatchedRegistry() {}

  val latchedMetricSchemaSource = new MetricSchemaSource(
    Seq(latchedSchemaRegistry)
  )

  val unlatchedMetricSchemaSource = new MetricSchemaSource(
    Seq(unlatchedSchemaRegistry)
  )

  private[this] val latchedHandler =
    new MetricMetadataQueryHandler(latchedMetricSchemaSource)
  private[this] val unlatchedHandler =
    new MetricMetadataQueryHandler(unlatchedMetricSchemaSource)

  val typeRequestNoArg = Request("http://$HOST:$PORT/admin/metric_metadata.json")

  val typeRequestWithAnArg = Request(
    "http://$HOST:$PORT/admin/metric_metadata.json?name=your/fine/gauge")

  val typeRequestWithManyArgs = Request(
    "http://$HOST:$PORT/admin/metric_metadata.json?name=my/cool/counter&name=your/fine/gauge")

  val typeRequestWithHisto = Request(
    "http://$HOST:$PORT/admin/metric_metadata.json?name=my/only/histo")

  val typeRequestWithHistoAndNon = Request(
    "http://$HOST:$PORT/admin/metric_metadata.json?name=my/cool/counter&name=my/only/histo")

  val responseToNoArg =
    """
      |   "metrics" : [
      |     {
      |      "name" : "my/cool/counter",
      |      "relative_name" : ["my","cool","counter"],
      |      "kind" : "counter",
      |      "source" : {
      |        "class": "finagle.stats.cool",
      |        "category": "Server",
      |        "process_path": "dc/role/zone/service"
      |      },
      |      "description" : "Counts how many cools are seen",
      |      "unit" : "Requests",
      |      "verbosity": "Verbosity(default)",
      |      "key_indicator" : true
      |     },
      |     {
      |      "name" : "your/fine/gauge",
      |      "relative_name" : ["your","fine","gauge"],
      |      "kind" : "gauge",
      |      "source" : {
      |        "class": "finagle.stats.your",
      |        "category": "Client",
      |        "process_path": "dc/your_role/zone/your_service"
      |      },
      |      "description" : "Measures how fine the downstream system is",
      |      "unit" : "Percentage",
      |      "verbosity": "Verbosity(debug)",
      |      "key_indicator" : false
      |     },
      |     {
      |      "name" : "my/only/histo",
      |      "relative_name" : ["my","only","histo"],
      |      "kind" : "histogram",
      |      "source" : {
      |        "class": "Unspecified",
      |        "category": "NoRoleSpecified",
      |        "process_path": "Unspecified"
      |      },
      |      "description" : "No description provided",
      |      "unit" : "Unspecified",
      |      "verbosity": "Verbosity(default)",
      |      "key_indicator" : false,
      |      "buckets" : {
      |        "count" : ".count",
      |        "sum" : ".sum",
      |        "average" : ".avg",
      |        "minimum" : ".min",
      |        "maximum" : ".max",
      |        "0.5" : ".p50",
      |        "0.9" : ".p90",
      |        "0.95" : ".p95",
      |        "0.99" : ".p99",
      |        "0.999" : ".p9990",
      |        "0.9999" : ".p9999"
      |       }
      |     },
      |     {
      |      "name" : "my/bad/null/counter",
      |      "relative_name" : ["my","bad", "null", "counter"],
      |      "kind" : "counter",
      |      "source" : {
      |        "class": "finagle.stats.bad",
      |        "category": "Server",
      |        "process_path": "dc/role/zone/service"
      |      },
      |      "description" : "A counter scoped by null get deserialized correctly",
      |      "unit" : "Requests",
      |      "verbosity": "Verbosity(default)",
      |      "key_indicator" : true
      |     }
      |   ]
      | }
    """.stripMargin

  val responseToAnArg =
    """
      |   "metrics" : [
      |     {
      |      "name" : "your/fine/gauge",
      |      "relative_name" : ["your","fine","gauge"],
      |      "kind" : "gauge",
      |      "source" : {
      |        "class": "finagle.stats.your",
      |        "category": "Client",
      |        "process_path": "dc/your_role/zone/your_service"
      |      },
      |      "description" : "Measures how fine the downstream system is",
      |      "unit" : "Percentage",
      |      "verbosity": "Verbosity(debug)",
      |      "key_indicator" : false
      |     }
      |   ]
      | }    """.stripMargin

  val responseToManyArgs =
    """
      |   "metrics" : [
      |     {
      |      "name" : "my/cool/counter",
      |      "relative_name" : ["my","cool","counter"],
      |      "kind" : "counter",
      |      "source" : {
      |        "class": "finagle.stats.cool",
      |        "category": "Server",
      |        "process_path": "dc/role/zone/service"
      |      },
      |      "description" : "Counts how many cools are seen",
      |      "unit" : "Requests",
      |      "verbosity": "Verbosity(default)",
      |      "key_indicator" : true
      |     },
      |     {
      |      "name" : "your/fine/gauge",
      |      "relative_name" : ["your","fine","gauge"],
      |      "kind" : "gauge",
      |      "source" : {
      |        "class": "finagle.stats.your",
      |        "category": "Client",
      |        "process_path": "dc/your_role/zone/your_service"
      |      },
      |      "description" : "Measures how fine the downstream system is",
      |      "unit" : "Percentage",
      |      "verbosity": "Verbosity(debug)",
      |      "key_indicator" : false
      |     }
      |   ]
      | }
    """.stripMargin

  val responseToHisto =
    """
      |   "metrics" : [
      |     {
      |      "name" : "my/only/histo",
      |      "relative_name" : ["my","only","histo"],
      |      "kind" : "histogram",
      |      "source" : {
      |        "class": "Unspecified",
      |        "category": "NoRoleSpecified",
      |        "process_path": "Unspecified"
      |      },
      |      "description" : "No description provided",
      |      "unit" : "Unspecified",
      |      "verbosity": "Verbosity(default)",
      |      "key_indicator" : false,
      |      "buckets" : {
      |        "count" : ".count",
      |        "sum" : ".sum",
      |        "average" : ".avg",
      |        "minimum" : ".min",
      |        "maximum" : ".max",
      |        "0.5" : ".p50",
      |        "0.9" : ".p90",
      |        "0.95" : ".p95",
      |        "0.99" : ".p99",
      |        "0.999" : ".p9990",
      |        "0.9999" : ".p9999"
      |       }
      |     }
      |   ]
      | }    """.stripMargin

  val responseToHistoAndNon =
    """
      |   "metrics" : [
      |     {
      |      "name" : "my/cool/counter",
      |      "relative_name" : ["my","cool","counter"],
      |      "kind" : "counter",
      |      "source" : {
      |        "class": "finagle.stats.cool",
      |        "category": "Server",
      |        "process_path": "dc/role/zone/service"
      |      },
      |      "description" : "Counts how many cools are seen",
      |      "unit" : "Requests",
      |      "verbosity": "Verbosity(default)",
      |      "key_indicator" : true
      |     },
      |     {
      |      "name" : "my/only/histo",
      |      "relative_name" : ["my","only","histo"],
      |      "kind" : "histogram",
      |      "source" : {
      |        "class": "Unspecified",
      |        "category": "NoRoleSpecified",
      |        "process_path": "Unspecified"
      |      },
      |      "description" : "No description provided",
      |      "unit" : "Unspecified",
      |      "verbosity": "Verbosity(default)",
      |      "key_indicator" : false,
      |      "buckets" : {
      |        "count" : ".count",
      |        "sum" : ".sum",
      |        "average" : ".avg",
      |        "minimum" : ".min",
      |        "maximum" : ".max",
      |        "0.5" : ".p50",
      |        "0.9" : ".p90",
      |        "0.95" : ".p95",
      |        "0.99" : ".p99",
      |        "0.999" : ".p9990",
      |        "0.9999" : ".p9999"
      |       }
      |     }
      |   ]
      | }
      """.stripMargin

  val testNameStart = "MetricTypeQueryHandler generates reasonable json for "

  // NOTE: these tests assume a specific iteration order over the registries
  // and HashMaps which IS NOT a guarantee. should these tests begin to fail
  // due to that, we will need a more robust approach to validation.
  private[this] def assertJsonResponse(actual: String, expected: String) = {
    assert(stripWhitespace(actual) == stripWhitespace(expected))
  }

  private[this] def stripWhitespace(string: String): String =
    string.filter { case c => !c.isWhitespace }

  def testCase(latched: Boolean, request: Request): Unit = {
    if (request == typeRequestNoArg) {
      testCase(latched, request, testNameStart + "full set of metrics", responseToNoArg)
    } else if (request == typeRequestWithAnArg) {
      testCase(latched, request, testNameStart + "a single requested metric", responseToAnArg)
    } else if (request == typeRequestWithManyArgs) {
      testCase(latched, request, testNameStart + "requested subset of metrics", responseToManyArgs)
    } else if (request == typeRequestWithHisto) {
      testCase(latched, request, testNameStart + "a single requested histogram", responseToHisto)
    } else if (request == typeRequestWithHistoAndNon) {
      testCase(
        latched,
        request,
        testNameStart + "requested subset of metrics with a histogram",
        responseToHistoAndNon)
    }
  }

  def testCase(
    latched: Boolean,
    request: Request,
    testName: String,
    responseMetrics: String
  ): Unit = {
    if (latched) {
      val responseStart =
        """
          | {
          |   "@version" : 3.0,
          |   "counters_latched" : true,
          |   "separator_char" : "/",
        """.stripMargin
      test(testName + " when using latched counters") {
        assertJsonResponse(
          responseStart + responseMetrics,
          Await.result(latchedHandler(request)).contentString)
      }

    } else {
      val responseStart =
        """
          | {
          |   "@version" : 3.0,
          |   "counters_latched" : false,
          |   "separator_char" : "/",
        """.stripMargin
      test(testName + " when using unlatched counters") {
        assertJsonResponse(
          responseStart + responseMetrics,
          Await.result(unlatchedHandler(request)).contentString)
      }

    }
  }

  Seq(
    testCase(true, typeRequestNoArg),
    testCase(true, typeRequestWithManyArgs),
    testCase(true, typeRequestWithAnArg),
    testCase(true, typeRequestWithHisto),
    testCase(true, typeRequestWithHistoAndNon),
    testCase(false, typeRequestNoArg),
    testCase(false, typeRequestWithManyArgs),
    testCase(false, typeRequestWithAnArg),
    testCase(false, typeRequestWithHisto),
    testCase(false, typeRequestWithHistoAndNon)
  )
}
