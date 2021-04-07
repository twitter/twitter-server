package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{MediaType, Request, Response, Uri}
import com.twitter.finagle.stats.exp.Expression._
import com.twitter.finagle.stats.exp.{
  ConstantExpression,
  Expression,
  FunctionExpression,
  HistogramExpression,
  MetricExpression
}
import com.twitter.finagle.stats.{
  CounterSchema,
  HistogramSchema,
  MetricSchema,
  StatsFormatter,
  metadataScopeSeparator
}
import com.twitter.io.Buf
import com.twitter.server.handler.MetricExpressionHandler.{Version, translateToQuery}
import com.twitter.server.util.HttpUtils.newResponse
import com.twitter.server.util.{AdminJsonConverter, MetricSchemaSource}
import com.twitter.util.Future

object MetricExpressionHandler {
  private val Version = 0.6
  private val statsFormatter = StatsFormatter.default

  /**
   * Translate the [[Expression]] object to a single line string which represents generic
   * query language.
   * @param latched For unlatched counter, we wrap the metric within `rate`
   */
  // exposed for testing
  private[server] def translateToQuery(expr: Expression, latched: Boolean = false): String =
    expr match {
      case HistogramExpression(schema, component) => getHisto(schema, component)
      case MetricExpression(schema) => getMetric(schema, latched)
      case ConstantExpression(repr) => repr
      case FunctionExpression(funcName, exprs) =>
        s"$funcName(${exprs.map { expr => translateToQuery(expr, latched) }.mkString(",")})"
    }

  // Form a fully formatted name of the histogram with components
  // the returned metric is styled the same way as admin/metrics.json
  // e.g.request_latency.p9999 or request_latency.min
  private def getHisto(
    histoSchema: HistogramSchema,
    histoComponent: Either[HistogramComponent, Double]
  ): String = {
    val name = histoSchema.metricBuilder.name.mkString(metadataScopeSeparator())
    val component = histoComponent match {
      case Right(percentile) => statsFormatter.labelPercentile(percentile)
      case Left(Min) => statsFormatter.labelMin
      case Left(Max) => statsFormatter.labelMax
      case Left(Avg) => statsFormatter.labelAverage
      case Left(Sum) => statsFormatter.labelSum
      case Left(Count) => statsFormatter.labelCount
    }
    statsFormatter.histoName(name, component)
  }

  // Form metrics other than histograms, rate() for unlatched counters
  private def getMetric(
    metricSchema: MetricSchema,
    latched: Boolean,
  ): String = {
    metricSchema match {
      case CounterSchema(metricBuilder) if !latched =>
        s"rate(${metricBuilder.name.mkString(metadataScopeSeparator())})"
      case other => other.metricBuilder.name.mkString(metadataScopeSeparator())
    }
  }
}

/**
 * A handler for metric expression queries at admin/metric/expressions.json.
 * @queryParam ?latching_style=boolean Set true to let expression respect the latchedness of counters,
 *             which means it does not wrap the latched counters in `rate()`.
 *
 * @example http://$HOST:$PORT/admin/metric/expressions.json?latching_style=true
 *          http://$HOST:$PORT/admin/metric/expressions.json  (by default latching_style is false)
 */
class MetricExpressionHandler(source: MetricSchemaSource = new MetricSchemaSource)
    extends Service[Request, Response] {

  private[this] lazy val sourceLatched = source.hasLatchedCounters

  def apply(request: Request): Future[Response] = {
    val keyParam = Uri.fromRequest(request).params.getAll("latching_style")

    val latched = keyParam.exists { value => value == "true" || value == "1" } && sourceLatched

    val expressions = source.expressionList.map { expressionSchema =>
      expressionSchema.copy(exprQuery = translateToQuery(expressionSchema.expr, latched))
    }

    newResponse(
      contentType = MediaType.JsonUtf8,
      content = Buf.Utf8(
        AdminJsonConverter.writeToString(
          Map(
            "@version" -> Version,
            "counters_latched" -> source.hasLatchedCounters,
            "separator_char" -> metadataScopeSeparator(),
            "expressions" -> expressions
          ))
      )
    )
  }

}
