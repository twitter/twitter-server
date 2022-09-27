package com.twitter.server.handler

import com.twitter.finagle.http.Request
import com.twitter.finagle.stats.MetricBuilder.CounterType
import com.twitter.finagle.stats.MetricBuilder.GaugeType
import com.twitter.finagle.stats.MetricBuilder.HistogramType
import com.twitter.finagle.stats.MetricBuilder.Identity
import com.twitter.finagle.stats.MetricBuilder.IdentityType
import com.twitter.finagle.stats._
import com.twitter.finagle.stats.exp.ExpressionSchema
import com.twitter.finagle.stats.exp.ExpressionSchemaKey
import com.twitter.server.util.JsonUtils
import com.twitter.server.util.MetricSchemaSource
import com.twitter.util.Await
import com.twitter.util.Duration
import org.scalatest.funsuite.AnyFunSuite

class MetricMetadataQueryHandlerTest extends AnyFunSuite {

  val schemaMap: Map[String, MetricBuilder] = Map(
    "my/cool/counter" ->
      MetricBuilder(
        keyIndicator = true,
        description = "Counts how many cools are seen",
        units = Requests,
        role = SourceRole.Server,
        verbosity = Verbosity.Default,
        sourceClass = Some("finagle.stats.cool"),
        name = Seq("my", "cool", "counter"),
        processPath = Some("dc/role/zone/service"),
        percentiles = IndexedSeq(0.5, 0.9, 0.95, 0.99, 0.999, 0.9999),
        metricType = CounterType
      ).withIdentity(
        Identity(
          Seq("my", "cool", "counter"),
          Seq("my", "cool", "counter"),
          Map("label1" -> "value1", "label2" -> "value2"),
          identityType = IdentityType.Full
        )),
    "your/fine/gauge" ->
      MetricBuilder(
        keyIndicator = false,
        description = "Measures how fine the downstream system is",
        units = Percentage,
        role = SourceRole.Client,
        verbosity = Verbosity.Debug,
        sourceClass = Some("finagle.stats.your"),
        name = Seq("your", "fine", "gauge"),
        processPath = Some("dc/your_role/zone/your_service"),
        percentiles = IndexedSeq(0.5, 0.9, 0.95, 0.99, 0.999, 0.9999),
        metricType = GaugeType
      ),
    "my/only/histo" ->
      MetricBuilder(
        percentiles = IndexedSeq(0.5, 0.9, 0.95, 0.99, 0.999, 0.9999),
        metricType = HistogramType,
        histogramFormat = HistogramFormat.ShortSummary,
      ).withIdentity(
        Identity(
          Seq("my", "only", "histo"),
          Seq("my", "only", "histo"),
          Map("label1" -> "value1"),
          identityType = IdentityType.Full
        )),
    "my/bad/null/counter" ->
      MetricBuilder(
        keyIndicator = true,
        description = "A counter scoped by null get deserialized correctly",
        units = Requests,
        role = SourceRole.Server,
        verbosity = Verbosity.Default,
        sourceClass = Some("finagle.stats.bad"),
        name = Seq("my", "bad", null, "counter"),
        processPath = Some("dc/role/zone/service"),
        percentiles = IndexedSeq(0.5, 0.9, 0.95, 0.99, 0.999, 0.9999),
        metricType = CounterType
      )
  )

  trait UnlatchedRegistry extends SchemaRegistry {
    val hasLatchedCounters = false
    def schemas(): Map[String, MetricBuilder] = schemaMap
    def expressions(): Map[ExpressionSchemaKey, ExpressionSchema] = Map.empty
  }

  trait LatchedRegistry extends SchemaRegistry {
    val hasLatchedCounters = true
    override def schemas(): Map[String, MetricBuilder] = schemaMap
    def expressions(): Map[ExpressionSchemaKey, ExpressionSchema] = Map.empty
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
    "http://$HOST:$PORT/admin/metric_metadata.json?name=my/cool/counter&name=my/only/histo.p99")

  val responseToNoArg =
    """
      |   "metrics" : [
      |     {
      |      "name" : "my/cool/counter",
      |      "dimensional_name" : "my_cool_counter",
      |      "relative_name" : ["my","cool","counter"],
      |      "labels" : {
      |        "label1" : "value1",
      |        "label2" : "value2"
      |      },
      |      "dimensional_support" : true,
      |      "kind" : "counter",
      |      "source" : {
      |        "class": "finagle.stats.cool",
      |        "category": "Server",
      |        "process_path": "dc/role/zone/service"
      |      },
      |      "description" : "Counts how many cools are seen",
      |      "unit" : "Requests",
      |      "verbosity": "default",
      |      "key_indicator" : true
      |     },
      |     {
      |      "name" : "your/fine/gauge",
      |      "dimensional_name" : "your_fine_gauge",
      |      "relative_name" : ["your","fine","gauge"],
      |      "labels" : {},
      |      "dimensional_support" : false,
      |      "kind" : "gauge",
      |      "source" : {
      |        "class": "finagle.stats.your",
      |        "category": "Client",
      |        "process_path": "dc/your_role/zone/your_service"
      |      },
      |      "description" : "Measures how fine the downstream system is",
      |      "unit" : "Percentage",
      |      "verbosity": "debug",
      |      "key_indicator" : false
      |     },
      |     {
      |      "name" : "my/only/histo",
      |      "dimensional_name" : "my_only_histo",
      |      "relative_name" : ["my","only","histo"],
      |      "labels" : {
      |        "label1" : "value1"
      |      },
      |      "dimensional_support" : true,
      |      "kind" : "histogram",
      |      "source" : {
      |        "class": "Unspecified",
      |        "category": "NoRoleSpecified",
      |        "process_path": "Unspecified"
      |      },
      |      "description" : "No description provided",
      |      "unit" : "Unspecified",
      |      "verbosity": "default",
      |      "key_indicator" : false,
      |      "histogram_format": "short_summary",
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
      |      "dimensional_name" : "my_bad_null_counter",
      |      "relative_name" : ["my","bad", "null", "counter"],
      |      "labels" : {},
      |      "dimensional_support" : false,
      |      "kind" : "counter",
      |      "source" : {
      |        "class": "finagle.stats.bad",
      |        "category": "Server",
      |        "process_path": "dc/role/zone/service"
      |      },
      |      "description" : "A counter scoped by null get deserialized correctly",
      |      "unit" : "Requests",
      |      "verbosity": "default",
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
      |      "dimensional_name" : "your_fine_gauge",
      |      "relative_name" : ["your","fine","gauge"],
      |      "labels" : {},
      |      "dimensional_support" : false,
      |      "kind" : "gauge",
      |      "source" : {
      |        "class": "finagle.stats.your",
      |        "category": "Client",
      |        "process_path": "dc/your_role/zone/your_service"
      |      },
      |      "description" : "Measures how fine the downstream system is",
      |      "unit" : "Percentage",
      |      "verbosity": "debug",
      |      "key_indicator" : false
      |     }
      |   ]
      | }    """.stripMargin

  val responseToManyArgs =
    """
      |   "metrics" : [
      |     {
      |      "name" : "my/cool/counter",
      |      "dimensional_name": "my_cool_counter",
      |      "relative_name" : ["my","cool","counter"],
      |      "labels" : {
      |        "label1" : "value1",
      |        "label2" : "value2"
      |      },
      |      "dimensional_support" : true,
      |      "kind" : "counter",
      |      "source" : {
      |        "class": "finagle.stats.cool",
      |        "category": "Server",
      |        "process_path": "dc/role/zone/service"
      |      },
      |      "description" : "Counts how many cools are seen",
      |      "unit" : "Requests",
      |      "verbosity": "default",
      |      "key_indicator" : true
      |     },
      |     {
      |      "name" : "your/fine/gauge",
      |      "dimensional_name" : "your_fine_gauge",
      |      "relative_name" : ["your","fine","gauge"],
      |      "labels" : {},
      |      "dimensional_support" : false,
      |      "kind" : "gauge",
      |      "source" : {
      |        "class": "finagle.stats.your",
      |        "category": "Client",
      |        "process_path": "dc/your_role/zone/your_service"
      |      },
      |      "description" : "Measures how fine the downstream system is",
      |      "unit" : "Percentage",
      |      "verbosity": "debug",
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
      |      "dimensional_name" : "my_only_histo",
      |      "relative_name" : ["my","only","histo"],
      |      "labels" : {
      |        "label1" : "value1"
      |      },
      |      "dimensional_support" : true,
      |      "kind" : "histogram",
      |      "source" : {
      |        "class": "Unspecified",
      |        "category": "NoRoleSpecified",
      |        "process_path": "Unspecified"
      |      },
      |      "description" : "No description provided",
      |      "unit" : "Unspecified",
      |      "verbosity": "default",
      |      "key_indicator" : false,
      |      "histogram_format": "short_summary",
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
      |      "dimensional_name" : "my_cool_counter",
      |      "relative_name" : ["my","cool","counter"],
      |      "labels" : {
      |        "label1" : "value1",
      |        "label2" : "value2"
      |      },
      |      "dimensional_support" : true,
      |      "kind" : "counter",
      |      "source" : {
      |        "class": "finagle.stats.cool",
      |        "category": "Server",
      |        "process_path": "dc/role/zone/service"
      |      },
      |      "description" : "Counts how many cools are seen",
      |      "unit" : "Requests",
      |      "verbosity": "default",
      |      "key_indicator" : true
      |     },
      |     {
      |      "name" : "my/only/histo",
      |      "dimensional_name" : "my_only_histo",
      |      "relative_name" : ["my","only","histo"],
      |      "labels" : {
      |        "label1" : "value1"
      |      },
      |      "dimensional_support" : true,
      |      "kind" : "histogram",
      |      "source" : {
      |        "class": "Unspecified",
      |        "category": "NoRoleSpecified",
      |        "process_path": "Unspecified"
      |      },
      |      "description" : "No description provided",
      |      "unit" : "Unspecified",
      |      "verbosity": "default",
      |      "key_indicator" : false,
      |      "histogram_format": "short_summary",
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
          |   "@version" : 3.2,
          |   "counters_latched" : true,
          |   "separator_char" : "/",
        """.stripMargin
      test(testName + " when using latched counters") {
        JsonUtils.assertJsonResponse(
          responseStart + responseMetrics,
          Await.result(latchedHandler(request)).contentString)
      }

    } else {
      val responseStart =
        """
          | {
          |   "@version" : 3.2,
          |   "counters_latched" : false,
          |   "separator_char" : "/",
        """.stripMargin
      test(testName + " when using unlatched counters") {
        JsonUtils.assertJsonResponse(
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

  /**
   * Histogram Metric Querying cases.
   */
  trait SchemalessRegistry extends SchemaRegistry {
    val hasLatchedCounters = false
    def expressions(): Map[ExpressionSchemaKey, ExpressionSchema] = Map.empty
  }

  def histoSuffixTestCase(
    testName: String,
    metricName: String,
    requestParam: String,
    metricType: String,
    useCommonsStats: Boolean = false
  ): Unit = {
    test(testName) {
      trait registry extends SchemalessRegistry {
        def schemas(): Map[String, MetricBuilder] = if (metricType == "counter") {
          Map(
            metricName ->
              MetricBuilder(
                name = metricName.split("\\/"),
                percentiles = IndexedSeq(0.5, 0.9, 0.95, 0.99, 0.999, 0.9999),
                metricType = CounterType
              ))
        } else {
          Map(
            metricName ->
              MetricBuilder(
                name = metricName.split("\\/"),
                percentiles = IndexedSeq(0.5, 0.9, 0.95, 0.99, 0.999, 0.9999),
                metricType = HistogramType
              ))
        }
      }
      val request = Request("http://$HOST:$PORT/admin/metric_metadata.json?name=" + requestParam)
      if (useCommonsStats) {
        format.let("commonsstats") {
          val handler =
            new MetricMetadataQueryHandler(new MetricSchemaSource(Seq(new registry() {})))
          assert(
            Await
              .result(handler(request), Duration.fromSeconds(10))
              .contentString
              .contains("kind\" : \"%s\"".format(metricType)))
        }
      } else {
        val handler =
          new MetricMetadataQueryHandler(new MetricSchemaSource(Seq(new registry() {})))
        assert(
          Await
            .result(handler(request), Duration.fromSeconds(10)).contentString.contains(
              "kind\" : \"%s\"".format(metricType)))
      }
    }
  }

  histoSuffixTestCase(
    "counter with histo separator in name querability test",
    "foo/bar.baz",
    "foo/bar.baz",
    "counter"
  )

  histoSuffixTestCase(
    "counter with histobucket-like name querability test",
    "foo/bar.p999",
    "foo/bar.p999",
    "counter"
  )

  histoSuffixTestCase(
    "histogram querability via bucket name test",
    "foo",
    "foo.p999",
    "histogram"
  )

  histoSuffixTestCase(
    "histogram querability via raw name test",
    "foo",
    "foo",
    "histogram"
  )

  histoSuffixTestCase(
    "counter with less common histo separator in name querability test",
    "foo/bar_baz",
    "foo/bar_baz",
    "counter",
    true)

  histoSuffixTestCase(
    "histo with less common histo separator in name querability test",
    "foo/bar_baz",
    "foo/bar_baz",
    "histogram",
    true)

  histoSuffixTestCase(
    "histo with less common histo separator in name percentile querability test",
    "foo/bar_baz",
    "foo/bar_baz_99_percentile",
    "histogram",
    true)

}
