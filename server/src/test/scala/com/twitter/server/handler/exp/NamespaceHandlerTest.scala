package com.twitter.server.handler.exp

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.http.Request
import com.twitter.finagle.stats.SchemaRegistry
import com.twitter.server.util.JsonUtils
import com.twitter.server.util.MetricSchemaSource
import com.twitter.server.handler.NamespaceHandler
import com.twitter.finagle.stats.MetricBuilder
import com.twitter.finagle.stats.exp.ExpressionSchema
import com.twitter.finagle.stats.exp.ExpressionSchemaKey
import com.twitter.util.Await
import com.twitter.util.Awaitable
import com.twitter.util.Duration
import org.scalatest.funsuite.AnyFunSuite
import com.twitter.finagle.stats.MetricBuilder.CounterType
import com.twitter.finagle.stats.MetricBuilder.HistogramType
import com.twitter.finagle.stats.SchemaRegistry
import com.twitter.finagle.stats.exp.Expression
import com.twitter.finagle.stats.exp.ExpressionSchema
import com.twitter.finagle.stats.exp.ExpressionSchemaKey
import com.twitter.finagle.stats.exp.GreaterThan
import com.twitter.finagle.stats.exp.HistogramComponent
import com.twitter.finagle.stats.exp.MonotoneThresholds
import NamespaceHandlerTest._

object NamespaceHandlerTest {
  private val successMb = MetricBuilder(name = Seq("success"), metricType = CounterType)
  private val failuresMb =
    MetricBuilder(name = Seq("failures"), metricType = CounterType)
  private val latencyMb =
    MetricBuilder(name = Seq("latency"), metricType = HistogramType)

  private val successRateExpression =
    ExpressionSchema(
      "success_rate",
      Expression(100).multiply(
        Expression(successMb).divide(Expression(successMb).plus(Expression(failuresMb))))
    ).withBounds(MonotoneThresholds(GreaterThan, 99.5, 99.97))

  private val throughputExpression =
    ExpressionSchema("throughput", Expression(successMb).plus(Expression(failuresMb)))
      .withNamespace("my/namespace/0", "my/namespace/1", "my/namespace/2")

  private val latencyP99 =
    ExpressionSchema("latency_p99", Expression(latencyMb, HistogramComponent.Percentile(0.99)))
      .withNamespace("my/namespace/0", "my/namespace/3")

  private val expressionSchemaMap: Map[ExpressionSchemaKey, ExpressionSchema] = Map(
    ExpressionSchemaKey("success_rate", Map(), Seq()) -> successRateExpression,
    ExpressionSchemaKey(
      "throughput",
      Map(),
      Seq("my/namespace/0", "my/namespace/1", "my/namespace/2")) -> throughputExpression,
    ExpressionSchemaKey("latency", Map(), Seq("my/namespace/0", "my/namespace/3")) -> latencyP99
  )
  val NamespaceRegistry = new SchemaRegistry {
    val hasLatchedCounters = false
    def schemas(): Map[String, MetricBuilder] = Map.empty
    def expressions(): Map[ExpressionSchemaKey, ExpressionSchema] = expressionSchemaMap
  }
}

class NamespaceHandlerTest extends AnyFunSuite {

  val namespaceSource = new MetricSchemaSource(Seq(NamespaceRegistry), sort = true)
  val namespaceHandler = new NamespaceHandler(namespaceSource)
  val testRequest = Request("http://$HOST:$PORT/admin/namespaces")

  test("Get all namespaces") {
    val expectedResponse =
      """
        |{ "@version": 1.0,
        | "namespaces": [
        |  { "name": "my/namespace/0" },
        |  { "name": "my/namespace/1" },
        |  { "name": "my/namespace/2" },
        |  { "name": "my/namespace/3" }
        |]}""".stripMargin

    JsonUtils.assertJsonResponse(
      await(namespaceHandler(testRequest)).contentString,
      expectedResponse)
  }

  private[this] def await[T](awaitable: Awaitable[T], timeout: Duration = 5.second): T =
    Await.result(awaitable, timeout)
}
