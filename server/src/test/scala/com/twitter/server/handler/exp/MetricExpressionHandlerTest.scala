package com.twitter.server.handler.exp

import com.twitter.finagle.http.Request
import com.twitter.finagle.stats.exp.{Expression, ExpressionSchema}
import com.twitter.finagle.stats.{
  CounterSchema,
  HistogramSchema,
  InMemoryStatsReceiver,
  MetricBuilder,
  MetricSchema,
  SchemaRegistry
}
import com.twitter.server.handler.MetricExpressionHandler
import com.twitter.server.util.MetricSchemaSource
import com.twitter.util.Await
import org.scalatest.FunSuite

class MetricExpressionHandlerTest extends FunSuite {

  val sr = new InMemoryStatsReceiver

  val successMb = CounterSchema(new MetricBuilder(name = Seq("success"), statsReceiver = sr))
  val failuresMb =
    CounterSchema(new MetricBuilder(name = Seq("failures"), statsReceiver = sr))
  val latencyMb = HistogramSchema(new MetricBuilder(name = Seq("latency"), statsReceiver = sr))

  val successRateExpression = ExpressionSchema(
    "success_rate",
    Expression(successMb).divide(Expression(successMb).plus(Expression(failuresMb))))
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

  private[this] def assertJsonResponse(actual: String, expected: String) = {
    assert(stripWhitespace(actual) == stripWhitespace(expected))
  }

  private[this] def stripWhitespace(string: String): String =
    string.filter { case c => !c.isWhitespace }

  test("Get the all expressions") {
    val responseString =
      """
        |{
        |  "@version" : 0.1,
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
        |      "bounds" : "Unbounded",
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
        |      "bounds" : "Unbounded",
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
        |      "bounds" : "Unbounded",
        |      "description" : "Unspecified",
        |      "unit" : "Unspecified"
        |    }
        |  ]
        |}""".stripMargin

    assertJsonResponse(responseString, Await.result(expressionHandler(testRequest)).contentString)
  }
}
