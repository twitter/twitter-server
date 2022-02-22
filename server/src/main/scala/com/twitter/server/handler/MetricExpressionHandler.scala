package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.MediaType
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Uri
import com.twitter.finagle.stats.MetricBuilder.CounterType
import com.twitter.finagle.stats.MetricBuilder.CounterishGaugeType
import com.twitter.finagle.stats.exp.ConstantExpression
import com.twitter.finagle.stats.exp.Expression
import com.twitter.finagle.stats.exp.FunctionExpression
import com.twitter.finagle.stats.exp.HistogramExpression
import com.twitter.finagle.stats.exp.MetricExpression
import com.twitter.finagle.stats.exp.NoExpression
import com.twitter.finagle.stats.MetricBuilder
import com.twitter.finagle.stats.StatsFormatter
import com.twitter.finagle.stats.exp.StringExpression
import com.twitter.finagle.stats.metadataScopeSeparator
import com.twitter.io.Buf
import com.twitter.server.handler.MetricExpressionHandler.Version
import com.twitter.server.handler.MetricExpressionHandler.translateToQuery
import com.twitter.server.util.HttpUtils.newResponse
import com.twitter.server.util.AdminJsonConverter
import com.twitter.server.util.MetricSchemaSource
import com.twitter.util.Future

object MetricExpressionHandler {
  private val Version = 1.1
  private val statsFormatter = StatsFormatter.default
  private val Wildcard = "/*"

  /**
   * Translate the [[Expression]] object to a single line string which represents generic
   * query language.
   * @param shouldRate If true, we wrap the metric with `rate`
   */
  // exposed for testing
  private[server] def translateToQuery(
    expr: Expression,
    shouldRate: Boolean,
    sourceLatched: Boolean,
    labels: Map[String, String]
  ): String =
    expr match {
      case HistogramExpression(schema, _) => getHisto(schema, labels)
      case MetricExpression(schema, showRollup) =>
        getMetric(schema, showRollup, shouldRate, sourceLatched)
      case ConstantExpression(repr) => repr
      case FunctionExpression(funcName, exprs) =>
        s"$funcName(${exprs
          .map { expr => translateToQuery(expr, shouldRate, sourceLatched, labels) }.mkString(",")})"
      case StringExpression(expr, isCounter) =>
        val metric = expr.mkString(metadataScopeSeparator())
        if (isCounter && shouldRate && !sourceLatched) s"rate($metric)" else metric
      case NoExpression => "null"
    }

  // Form a fully formatted name of the histogram with components
  // the returned metric is styled the same way as admin/metrics.json
  // e.g.request_latency.p9999 or request_latency.min
  private def getHisto(
    metricBuilder: MetricBuilder,
    labels: Map[String, String]
  ): String = {
    val name = metricBuilder.name.mkString(metadataScopeSeparator())
    statsFormatter.histoName(name, labels("bucket"))
  }

  // Form metrics other than histograms, rate() for unlatched counters
  private def getMetric(
    metricBuilder: MetricBuilder,
    showRollUp: Boolean,
    shouldRate: Boolean,
    sourceLatched: Boolean
  ): String = {
    val metric = metricBuilder.name.mkString(metadataScopeSeparator()) + {
      if (showRollUp) Wildcard
      else ""
    }
    metricBuilder.metricType match {
      case CounterType if shouldRate && !sourceLatched =>
        s"rate($metric)"
      case CounterishGaugeType if shouldRate =>
        s"rate($metric)"
      case _ => metric
    }
  }
}

/**
 * A handler for metric expression queries at admin/metric/expressions.json.
 *
 * @queryParam ?latching_style=boolean Set true to let expression respect the latchedness of counters,
 *             which means it does not wrap the latched counters in `rate()`.
 * @queryParam ?name=string only return expressions whose name matches the value of name
 * @queryParam ?namespace=string only return expressions whose namespace matches the value of namespace.
 *             note that the namespace is a sequence of strings, which will be joined into a string,
 *             separated by :s
 * @example http://$HOST:$PORT/admin/metric/expressions.json?latching_style=true
 *          http://$HOST:$PORT/admin/metric/expressions.json  (by default latching_style is false)
 *          http://$HOST:$PORT/admin/metric/expressions.json?name=success_rate
 *              (grab all expressions named "success_rate")
 *          http://$HOST:$PORT/admin/metric/expressions.json?namespace=path:to:namespace
 *              (grab all expressions of the namespace ["path", "to", "namespace"]
 */
class MetricExpressionHandler(source: MetricSchemaSource = new MetricSchemaSource)
    extends Service[Request, Response] {

  private[this] lazy val sourceLatched = source.hasLatchedCounters

  def apply(request: Request): Future[Response] = {
    val uri = Uri.fromRequest(request)
    val latchParam = uri.params.getAll("latching_style")
    val nameParam = uri.params.getAll("name").toSet
    val namespaceParam = uri.params.getAll("namespace").toSet

    val expressionSchemas = source.expressionList
    val filteredSchemas = expressionSchemas.filter(expressionSchema =>
      // namespace match if namespace param is present
      (namespaceParam.isEmpty || namespaceParam.contains(expressionSchema.namespace.mkString(":")))
      // name match if name param is present
        && (nameParam.isEmpty || nameParam.contains(expressionSchema.name)))

    val shouldRate = latchParam.exists { value =>
      value == "true" || value == "1"
    }
    val expressions = filteredSchemas.map { expressionSchema =>
      expressionSchema.copy(exprQuery =
        translateToQuery(expressionSchema.expr, shouldRate, sourceLatched, expressionSchema.labels))
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
