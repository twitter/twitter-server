package com.twitter.server.handler.exp

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.http.Request
import com.twitter.finagle.stats.InMemoryStatsReceiver
import com.twitter.finagle.stats.MetricBuilder
import com.twitter.finagle.stats.MetricBuilder.CounterType
import com.twitter.finagle.stats.MetricBuilder.GaugeType
import com.twitter.finagle.stats.MetricBuilder.HistogramType
import com.twitter.finagle.stats.SchemaRegistry
import com.twitter.finagle.stats.exp.Expression
import com.twitter.finagle.stats.exp.ExpressionSchema
import com.twitter.finagle.stats.exp.ExpressionSchemaKey
import com.twitter.finagle.stats.exp.GreaterThan
import com.twitter.finagle.stats.exp.HistogramComponent
import com.twitter.finagle.stats.exp.MonotoneThresholds
import com.twitter.server.handler.MetricExpressionHandler
import com.twitter.server.util.AdminJsonConverter
import com.twitter.server.util.JsonUtils
import com.twitter.server.util.MetricSchemaSource
import com.twitter.util.Await
import com.twitter.util.Awaitable
import com.twitter.util.Duration
import org.scalatest.funsuite.AnyFunSuite

class MetricExpressionHandlerTest extends AnyFunSuite {

  private[this] def await[T](awaitable: Awaitable[T], timeout: Duration = 5.second): T =
    Await.result(awaitable, timeout)

  val sr = new InMemoryStatsReceiver

  val successMb = MetricBuilder(name = Seq("success"), metricType = CounterType)
  val failuresMb =
    MetricBuilder(name = Seq("failures"), metricType = CounterType)
  val latencyMb =
    MetricBuilder(name = Seq("latency"), metricType = HistogramType)

  val successRateExpression =
    ExpressionSchema(
      "success_rate",
      Expression(100).multiply(
        Expression(successMb).divide(Expression(successMb).plus(Expression(failuresMb))))
    ).withBounds(MonotoneThresholds(GreaterThan, 99.5, 99.97))

  val throughputExpression =
    ExpressionSchema("throughput", Expression(successMb).plus(Expression(failuresMb)))
      .withNamespace("path", "to", "tenantName")

  val latencyP99 =
    ExpressionSchema("latency_p99", Expression(latencyMb, HistogramComponent.Percentile(0.99)))
      .withNamespace("tenantName")

  val failureExpression =
    ExpressionSchema("failures", Expression(failuresMb, true))

  val uptimeExpression =
    ExpressionSchema("uptime", Expression(Seq("jvm", "uptime"), isCounter = true))
  val pendingExpression =
    ExpressionSchema("pending", Expression(Seq("srv", "pending"), isCounter = false))

  val expressionSchemaMap: Map[ExpressionSchemaKey, ExpressionSchema] = Map(
    ExpressionSchemaKey("success_rate", Map(), Seq()) -> successRateExpression,
    ExpressionSchemaKey("throughput", Map(), Seq()) -> throughputExpression,
    ExpressionSchemaKey("latency", Map(), Seq("path", "to", "tenantName")) -> latencyP99,
    ExpressionSchemaKey("failures", Map(), Seq()) -> failureExpression,
    ExpressionSchemaKey("uptime", Map(), Seq()) -> uptimeExpression,
    ExpressionSchemaKey("pending", Map(), Seq()) -> pendingExpression
  )

  val expressionRegistry = new SchemaRegistry {
    def hasLatchedCounters: Boolean = false
    def schemas(): Map[String, MetricBuilder] = Map.empty

    val expressions: _root_.scala.Predef.Map[
      _root_.com.twitter.finagle.stats.exp.ExpressionSchemaKey,
      _root_.com.twitter.finagle.stats.exp.ExpressionSchema
    ] = expressionSchemaMap
  }
  val expressionSource = new MetricSchemaSource(Seq(expressionRegistry), sort = true)
  val expressionHandler = new MetricExpressionHandler(expressionSource)

  val latchedRegistry = new SchemaRegistry {
    def hasLatchedCounters: Boolean = true
    def schemas(): Map[String, MetricBuilder] = Map.empty
    def expressions(): Map[ExpressionSchemaKey, ExpressionSchema] =
      expressionSchemaMap
  }
  val latchedSource = new MetricSchemaSource(Seq(latchedRegistry))
  val latchedHandler = new MetricExpressionHandler(latchedSource)

  val testRequest = Request("http://$HOST:$PORT/admin/metric/expressions.json")
  val latchedStyleRequest = Request(
    "http://$HOST:$PORT/admin/metric/expressions.json?latching_style=true")
  val testRequestWithName = Request(
    "http://$HOST:$PORT/admin/metric/expressions.json?name=success_rate")
  val testRequestWithNamespace = Request(
    "http://$HOST:$PORT/admin/metric/expressions.json?namespace=tenantName")
  val testRequestWithNamespacePath = Request(
    "http://$HOST:$PORT/admin/metric/expressions.json?namespace=path:to:tenantName")
  val testRequestWithNamespaces = Request(
    "http://$HOST:$PORT/admin/metric/expressions.json?namespace=path:to:tenantName&namespace=tenantName")

  private def getSucessRateExpression(json: String): String = {
    val expressions =
      AdminJsonConverter
        .parse[Map[String, Any]](json).get("expressions").get.asInstanceOf[List[
          Map[String, String]
        ]]

    expressions
      .filter(m => m.getOrElse("name", "") == "success_rate").head.getOrElse("expression", "")
  }

  test("Get all expressions") {
    val expectedResponse =
      """
        |{
        |  "@version": 1.1,
        |  "counters_latched": false,
        |  "expressions": [
        |    {
        |      "bounds": {
        |        "kind": "unbounded"
        |      },
        |      "description": "Unspecified",
        |      "expression": "failures/*",
        |      "labels": {
        |        "process_path": "Unspecified",
        |        "role": "NoRoleSpecified",
        |        "service_name": "Unspecified"
        |      },
        |      "name": "failures",
        |      "unit": "Unspecified"
        |    },
        |    {
        |      "bounds": {
        |        "kind": "unbounded"
        |      },
        |      "description": "Unspecified",
        |      "expression": "latency.p99",
        |      "labels": {
        |        "bucket": "p99",
        |        "process_path": "Unspecified",
        |        "role": "NoRoleSpecified",
        |        "service_name": "Unspecified"
        |      },
        |      "name": "latency_p99",
        |      "namespaces": [
        |        "tenantName"
        |      ],
        |      "unit": "Unspecified"
        |    },
        |    {
        |      "bounds": {
        |        "kind": "unbounded"
        |      },
        |      "description": "Unspecified",
        |      "expression": "srv/pending",
        |      "labels": {
        |        "process_path": "Unspecified",
        |        "role": "NoRoleSpecified",
        |        "service_name": "Unspecified"
        |      },
        |      "name": "pending",
        |      "unit": "Unspecified"
        |    },
        |    {
        |      "bounds": {
        |        "bad_threshold": 99.5,
        |        "good_threshold": 99.97,
        |        "kind": "monotone",
        |        "lower_bound_inclusive": null,
        |        "operator": ">",
        |        "upper_bound_exclusive": null
        |      },
        |      "description": "Unspecified",
        |      "expression": "multiply(100.0,divide(success,plus(success,failures)))",
        |      "labels": {
        |        "process_path": "Unspecified",
        |        "role": "NoRoleSpecified",
        |        "service_name": "Unspecified"
        |      },
        |      "name": "success_rate",
        |      "unit": "Unspecified"
        |    },
        |    {
        |      "bounds": {
        |        "kind": "unbounded"
        |      },
        |      "description": "Unspecified",
        |      "expression": "plus(success,failures)",
        |      "labels": {
        |        "process_path": "Unspecified",
        |        "role": "NoRoleSpecified",
        |        "service_name": "Unspecified"
        |      },
        |      "name": "throughput",
        |      "namespaces": [
        |        "path",
        |        "to",
        |        "tenantName"
        |      ],
        |      "unit": "Unspecified"
        |    },
        |    {
        |      "bounds": {
        |        "kind": "unbounded"
        |      },
        |      "description": "Unspecified",
        |      "expression": "jvm/uptime",
        |      "labels": {
        |        "process_path": "Unspecified",
        |        "role": "NoRoleSpecified",
        |        "service_name": "Unspecified"
        |      },
        |      "name": "uptime",
        |      "unit": "Unspecified"
        |    }
        |  ],
        |  "separator_char": "/"
        |}""".stripMargin

    JsonUtils.assertJsonResponse(
      await(expressionHandler(testRequest)).contentString,
      expectedResponse)
  }

  test("Get the latched expression with ?latched_style=true") {
    val responseString = await(latchedHandler(latchedStyleRequest)).contentString

    assert(
      getSucessRateExpression(
        responseString) == "multiply(100.0,divide(success,plus(success,failures)))")
  }

  test("Get the latched expression without latched_style") {
    val responseString = await(latchedHandler(testRequest)).contentString

    assert(
      getSucessRateExpression(
        responseString) == "multiply(100.0,divide(success,plus(success,failures)))")
  }

  test("translate expressions - counters") {
    val unratedStyle =
      MetricExpressionHandler.translateToQuery(
        successRateExpression.expr,
        shouldRate = false,
        sourceLatched = false,
        successRateExpression.labels)
    assert(unratedStyle == "multiply(100.0,divide(success,plus(success,failures)))")

    val ratedStyle =
      MetricExpressionHandler.translateToQuery(
        successRateExpression.expr,
        shouldRate = true,
        sourceLatched = false,
        successRateExpression.labels)
    assert(ratedStyle == "multiply(100.0,divide(rate(success),plus(rate(success),rate(failures))))")
  }

  test("translate histogram expressions - latched does not affect result") {
    val unratedStyle =
      MetricExpressionHandler.translateToQuery(
        latencyP99.expr,
        shouldRate = false,
        sourceLatched = false,
        latencyP99.labels)
    val ratedStyle =
      MetricExpressionHandler.translateToQuery(
        latencyP99.expr,
        shouldRate = true,
        sourceLatched = false,
        latencyP99.labels)
    assert(ratedStyle == ratedStyle)
  }

  test("translate histogram expressions - components") {

    val latencyMin = ExpressionSchema("min", Expression(latencyMb, HistogramComponent.Min))
    val min = MetricExpressionHandler.translateToQuery(
      latencyMin.expr,
      shouldRate = false,
      sourceLatched = false,
      latencyMin.labels)
    val p99 = MetricExpressionHandler.translateToQuery(
      latencyP99.expr,
      shouldRate = false,
      sourceLatched = false,
      latencyP99.labels)
    assert(min == "latency.min")
    assert(p99 == "latency.p99")
  }

  test("translate expressions - gauges") {
    val connMb =
      MetricBuilder(name = Seq("client", "connections"), metricType = GaugeType)
    val result = MetricExpressionHandler.translateToQuery(
      Expression(connMb),
      shouldRate = false,
      sourceLatched = false,
      labels = Map())
    assert(result == "client/connections")
  }

  test("translate expressions - strings") {
    val unratedStyle =
      MetricExpressionHandler.translateToQuery(
        pendingExpression.expr,
        shouldRate = false,
        sourceLatched = false,
        pendingExpression.labels)
    val ratedStyle =
      MetricExpressionHandler.translateToQuery(
        pendingExpression.expr,
        shouldRate = true,
        sourceLatched = false,
        pendingExpression.labels)
    assert(ratedStyle == unratedStyle)
  }

  test("translate expressions - counter-style strings") {
    val unratedStyle =
      MetricExpressionHandler.translateToQuery(
        uptimeExpression.expr,
        shouldRate = false,
        sourceLatched = false,
        uptimeExpression.labels)
    assert(unratedStyle == "jvm/uptime")

    val ratedStyle =
      MetricExpressionHandler.translateToQuery(
        uptimeExpression.expr,
        shouldRate = true,
        sourceLatched = false,
        uptimeExpression.labels)
    assert(ratedStyle == "rate(jvm/uptime)")
  }

  test("Get expression with name param filters to expressions with that name") {
    val expectedResponse =
      """
        |{
        |  "@version" : 1.1,
        |  "counters_latched" : false,
        |  "separator_char" : "/",
        |  "expressions" : [
        |    {
        |      "name" : "success_rate",
        |      "labels" : {
        |        "process_path" : "Unspecified",
        |        "service_name" : "Unspecified",
        |        "role" : "NoRoleSpecified"
        |      },
        |      "expression" : "multiply(100.0,divide(success,plus(success,failures)))",
        |      "bounds" : {
        |        "kind" : "monotone",
        |        "operator" : ">",
        |        "bad_threshold" : 99.5,
        |        "good_threshold" : 99.97,
        |        "lower_bound_inclusive" : null,
        |        "upper_bound_exclusive" : null
        |      },
        |      "description" : "Unspecified",
        |      "unit" : "Unspecified"
        |    }
        |  ]
        |}""".stripMargin

    JsonUtils.assertJsonResponse(
      await(expressionHandler(testRequestWithName)).contentString,
      expectedResponse)
  }

  test("Get expression with namespace param filters to expressions with that namespace") {
    val expectedResponse =
      """
        |{
        |  "@version" : 1.1,
        |  "counters_latched" : false,
        |  "separator_char" : "/",
        |  "expressions" : [
        |    {
        |      "name" : "latency_p99",
        |      "labels" : {
        |        "process_path" : "Unspecified",
        |        "service_name" : "Unspecified",
        |        "role" : "NoRoleSpecified",
        |        "bucket": "p99"
        |      },
        |      "namespaces" : ["tenantName"],
        |      "expression" :  "latency.p99",
        |      "bounds" : {
        |        "kind" : "unbounded"
        |      },
        |      "description" : "Unspecified",
        |      "unit" : "Unspecified"
        |    },
        |    {
        |      "name" : "throughput",
        |      "labels" : {
        |        "process_path" : "Unspecified",
        |        "service_name" : "Unspecified",
        |        "role" : "NoRoleSpecified"
        |      },
        |      "namespaces" : ["path","to","tenantName"],
        |      "expression" : "plus(success,failures)",
        |      "bounds" : {
        |        "kind" : "unbounded"
        |      },
        |      "description" : "Unspecified",
        |      "unit" : "Unspecified"
        |    }
        |  ]
        |}""".stripMargin

    JsonUtils.assertJsonResponse(
      await(expressionHandler(testRequestWithNamespaces)).contentString,
      expectedResponse)
  }

  test("Get expression with namespace params filters to expression with that namespace") {
    val expectedResponse =
      """
        |{
        |  "@version" : 1.1,
        |  "counters_latched" : false,
        |  "separator_char" : "/",
        |  "expressions" : [
        |    {
        |      "name" : "latency_p99",
        |      "labels" : {
        |        "process_path" : "Unspecified",
        |        "service_name" : "Unspecified",
        |        "role" : "NoRoleSpecified",
        |        "bucket": "p99"
        |      },
        |      "namespaces" : ["tenantName"],
        |      "expression" :  "latency.p99",
        |      "bounds" : {
        |        "kind" : "unbounded"
        |      },
        |      "description" : "Unspecified",
        |      "unit" : "Unspecified"
        |    }        
        |  ]
        |}""".stripMargin

    JsonUtils.assertJsonResponse(
      await(expressionHandler(testRequestWithNamespace)).contentString,
      expectedResponse)
  }

  test("Get expression with namespace path param filters to expression with that namespace") {
    val expectedResponse =
      """
        |{
        |  "@version" : 1.1,
        |  "counters_latched" : false,
        |  "separator_char" : "/",
        |  "expressions" : [
        |    {
        |      "name" : "throughput",
        |      "labels" : {
        |        "process_path" : "Unspecified",
        |        "service_name" : "Unspecified",
        |        "role" : "NoRoleSpecified"
        |      },
        |      "namespaces" : ["path","to","tenantName"],
        |      "expression" : "plus(success,failures)",
        |      "bounds" : {
        |        "kind" : "unbounded"
        |      },
        |      "description" : "Unspecified",
        |      "unit" : "Unspecified"
        |    }        
        |  ]
        |}""".stripMargin

    JsonUtils.assertJsonResponse(
      await(expressionHandler(testRequestWithNamespacePath)).contentString,
      expectedResponse)
  }
}
