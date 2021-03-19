package com.twitter.server.handler.exp

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.http.Request
import com.twitter.finagle.stats.exp.{Expression, ExpressionSchema, GreaterThan, MonotoneThresholds}
import com.twitter.finagle.stats.{
  CounterSchema,
  HistogramSchema,
  InMemoryStatsReceiver,
  MetricBuilder,
  MetricSchema,
  SchemaRegistry
}
import com.twitter.server.handler.MetricExpressionHandler
import com.twitter.server.util.{JsonUtils, MetricSchemaSource}
import com.twitter.util.Await
import org.scalatest.FunSuite

class MetricExpressionHandlerTest extends FunSuite {

  val sr = new InMemoryStatsReceiver

  val successMb = CounterSchema(new MetricBuilder(name = Seq("success"), statsReceiver = sr))
  val failuresMb =
    CounterSchema(new MetricBuilder(name = Seq("failures"), statsReceiver = sr))
  val latencyMb = HistogramSchema(new MetricBuilder(name = Seq("latency"), statsReceiver = sr))

  val successRateExpression =
    ExpressionSchema(
      "success_rate",
      Expression(successMb).divide(Expression(successMb).plus(Expression(failuresMb))))
      .withBounds(MonotoneThresholds(GreaterThan, 99.5, 99.97))

  val throughputExpression =
    ExpressionSchema("throughput", Expression(successMb).plus(Expression(failuresMb)))

  val latencyExpression = ExpressionSchema("latency", Expression(latencyMb))

  val expressionSchemaMap: Map[String, ExpressionSchema] = Map(
    "success_rate" -> successRateExpression,
    "throughput" -> throughputExpression,
    "latency" -> latencyExpression
  )

  val expressionRegistry = new SchemaRegistry {
    def hasLatchedCounters: Boolean = false
    def schemas(): Map[String, MetricSchema] = Map.empty
    val expressions: Map[String, ExpressionSchema] = expressionSchemaMap
  }
  val expressionSource = new MetricSchemaSource(Seq(expressionRegistry))

  val expressionHandler = new MetricExpressionHandler(expressionSource)

  val testRequest = Request("http://$HOST:$PORT/admin/metric/expressions.json")

  test("Get the all expressions") {
    val responseString =
      """
        |{
        |  "@version" : 0.2,
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
        |      "expression" : {
        |        "operator" : "divide",
        |        "metrics" : {
        |          "metric-0" : "success",
        |          "operator" : "plus",
        |          "metrics" : {
        |            "metric-1-0" : "success",
        |            "metric-1-1" : "failures"
        |          }
        |        }
        |      },
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
        |      "expression" : {
        |        "operator" : "plus",
        |        "metrics" : {
        |          "metric-0" : "success",
        |          "metric-1" : "failures"
        |        }
        |      },
        |      "bounds" : {
        |        "kind" : "unbounded"
        |      },
        |      "description" : "Unspecified",
        |      "unit" : "Unspecified"
        |    },
        |    {
        |      "name" : "latency",
        |      "labels" : {
        |        "process_path" : "Unspecified",
        |        "service_name" : "Unspecified",
        |        "role" : "NoRoleSpecified"
        |      },
        |      "expression" : {
        |        "metric" : "latency"
        |      },
        |      "bounds" : {
        |        "kind" : "unbounded"
        |      },
        |      "description" : "Unspecified",
        |      "unit" : "Unspecified"
        |    }
        |  ]
        |}""".stripMargin

    JsonUtils.assertJsonResponse(
      Await.result(expressionHandler(testRequest), 5.seconds).contentString,
      responseString)
  }
}
