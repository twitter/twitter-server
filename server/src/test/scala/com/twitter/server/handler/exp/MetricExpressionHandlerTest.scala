package com.twitter.server.handler.exp

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.http.Request
import com.twitter.finagle.stats.exp.{
  Expression,
  ExpressionSchema,
  ExpressionSchemaKey,
  GreaterThan,
  MonotoneThresholds
}
import com.twitter.finagle.stats.{
  CounterSchema,
  GaugeSchema,
  HistogramSchema,
  InMemoryStatsReceiver,
  MetricBuilder,
  MetricSchema,
  SchemaRegistry
}
import com.twitter.server.handler.MetricExpressionHandler
import com.twitter.server.util.{AdminJsonConverter, JsonUtils, MetricSchemaSource}
import com.twitter.util.{Await, Awaitable, Duration}
import org.scalatest.funsuite.AnyFunSuite

class MetricExpressionHandlerTest extends AnyFunSuite {

  private[this] def await[T](awaitable: Awaitable[T], timeout: Duration = 5.second): T =
    Await.result(awaitable, timeout)

  val sr = new InMemoryStatsReceiver

  val successMb = CounterSchema(MetricBuilder(name = Seq("success"), statsReceiver = sr))
  val failuresMb =
    CounterSchema(MetricBuilder(name = Seq("failures"), statsReceiver = sr))
  val latencyMb = HistogramSchema(MetricBuilder(name = Seq("latency"), statsReceiver = sr))

  val successRateExpression =
    ExpressionSchema(
      "success_rate",
      Expression(100).multiply(
        Expression(successMb).divide(Expression(successMb).plus(Expression(failuresMb))))
    ).withBounds(MonotoneThresholds(GreaterThan, 99.5, 99.97))

  val throughputExpression =
    ExpressionSchema("throughput", Expression(successMb).plus(Expression(failuresMb)))
      .withNamespaces("path", "to", "tenantName")

  val latencyP99 =
    ExpressionSchema("latency_p99", Expression(latencyMb, Right(0.99))).withNamespaces("tenantName")

  val expressionSchemaMap: Map[ExpressionSchemaKey, ExpressionSchema] = Map(
    ExpressionSchemaKey("success_rate", None, Seq()) -> successRateExpression,
    ExpressionSchemaKey("throughput", None, Seq()) -> throughputExpression,
    ExpressionSchemaKey("latency", None, Seq("path", "to", "tenantName")) -> latencyP99
  )

  val expressionRegistry = new SchemaRegistry {
    def hasLatchedCounters: Boolean = false
    def schemas(): Map[String, MetricSchema] = Map.empty

    val expressions: _root_.scala.Predef.Map[
      _root_.com.twitter.finagle.stats.exp.ExpressionSchemaKey,
      _root_.com.twitter.finagle.stats.exp.ExpressionSchema
    ] = expressionSchemaMap
  }
  val expressionSource = new MetricSchemaSource(Seq(expressionRegistry))
  val expressionHandler = new MetricExpressionHandler(expressionSource)

  val latchedRegistry = new SchemaRegistry {
    def hasLatchedCounters: Boolean = true
    def schemas(): Map[String, MetricSchema] = Map.empty
    def expressions(): Map[ExpressionSchemaKey, ExpressionSchema] =
      expressionSchemaMap
  }
  val latchedSource = new MetricSchemaSource(Seq(latchedRegistry))
  val latchedHandler = new MetricExpressionHandler(latchedSource)

  val testRequest = Request("http://$HOST:$PORT/admin/metric/expressions.json")
  val latchedStyleRequest = Request(
    "http://$HOST:$PORT/admin/metric/expressions.json?latching_style=true")

  private def getSucessRateExpression(json: String): String = {
    val expressions =
      AdminJsonConverter
        .parse[Map[String, Any]](json).get("expressions").get.asInstanceOf[List[
          Map[String, String]
        ]]

    expressions
      .filter(m => m.getOrElse("name", "") == "success_rate").head.getOrElse("expression", "")
  }

  test("Get the all expressions") {
    val expectedResponse =
      """
        |{
        |  "@version" : 1.0,
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
        |      "expression" : "multiply(100.0,divide(rate(success),plus(rate(success),rate(failures))))",
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
        |    },
        |    {
        |      "name" : "throughput",
        |      "labels" : {
        |        "process_path" : "Unspecified",
        |        "service_name" : "Unspecified",
        |        "role" : "NoRoleSpecified"
        |      },
        |      "namespaces" : ["path","to","tenantName"],
        |      "expression" : "plus(rate(success),rate(failures))",
        |      "bounds" : {
        |        "kind" : "unbounded"
        |      },
        |      "description" : "Unspecified",
        |      "unit" : "Unspecified"
        |    },
        |    {
        |      "name" : "latency_p99",
        |      "labels" : {
        |        "process_path" : "Unspecified",
        |        "service_name" : "Unspecified",
        |        "role" : "NoRoleSpecified"
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

    assert(getSucessRateExpression(
      responseString) == "multiply(100.0,divide(rate(success),plus(rate(success),rate(failures))))")
  }

  test("translate expressions - counters") {
    val latchedResult =
      MetricExpressionHandler.translateToQuery(successRateExpression.expr, latched = true)
    assert(latchedResult == "multiply(100.0,divide(success,plus(success,failures)))")

    val unlatchedResult =
      MetricExpressionHandler.translateToQuery(successRateExpression.expr)
    assert(
      unlatchedResult == "multiply(100.0,divide(rate(success),plus(rate(success),rate(failures))))")
  }

  test("translate histogram expressions - latched does not affect result") {
    val latchedResult = MetricExpressionHandler.translateToQuery(latencyP99.expr, latched = true)
    val unLatchedResult = MetricExpressionHandler.translateToQuery(latencyP99.expr)
    assert(latchedResult == unLatchedResult)
  }

  test("translate histogram expressions - components") {
    val latencyMinExpr = Expression(latencyMb, Left(Expression.Min))
    val min = MetricExpressionHandler.translateToQuery(latencyMinExpr)
    val p99 = MetricExpressionHandler.translateToQuery(latencyP99.expr)
    assert(min == "latency.min")
    assert(p99 == "latency.p99")
  }

  test("translate expressions - gauges") {
    val connMb =
      GaugeSchema(MetricBuilder(name = Seq("client", "connections"), statsReceiver = sr))
    val result = MetricExpressionHandler.translateToQuery(Expression(connMb))
    assert(result == "client/connections")
  }
}
