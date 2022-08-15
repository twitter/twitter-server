package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.MediaType
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Uri
import com.twitter.finagle.stats.MetricBuilder
import com.twitter.finagle.stats.StatsFormatter
import com.twitter.finagle.stats.metadataScopeSeparator
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils.newResponse
import com.twitter.server.util.AdminJsonConverter
import com.twitter.server.util.MetricSchemaSource
import com.twitter.util.Future

/**
 * A handler which accepts metrics metadata queries via http query strings and returns
 * json encoded metrics with the metadata contained in their MetricSchemas, as well as an indicator
 * of whether or not counters are latched.
 *
 * @note   When passing an explicit histogram metric via ?name=, users must provide the raw histogram
 *         name, no percentile (eg, .p99) appended.
 *
 * Example Request:
 *   http://$HOST:$PORT/admin/metric_metadata?name=my/cool/counter&name=your/fine/gauge&name=my/only/histo
 *
 * Response:
 *   {
 *   "@version" : 3.0,
 *   "counters_latched" : false,
 *   "metrics" : [
 *     {
 *       "name" : "my/cool/counter",
 *       "relative_name" : ["cool","counter"],
 *       "kind" : "counter",
 *       "source" : {
 *         "class": "finagle.stats.cool",
 *         "category": "Server",
 *         "process_path": "dc/role/zone/service"
 *       },
 *       "description" : "Counts how many cools are seen",
 *       "unit" : "Requests",
 *       "verbosity": "default",
 *       "key_indicator" : true
 *     },
 *     {
 *       "name" : "your/fine/gauge",
 *       "relative_name" : ["fine","gauge"],
 *       "kind" : "gauge",
 *       "source" : {
 *         "class": "finagle.stats.your",
 *         "category": "Client",
 *         "process_path": "dc/your_role/zone/your_service"
 *       },
 *       "description" : "Measures how fine the downstream system is",
 *       "unit" : "Percentage",
 *       "verbosity": "debug",
 *       "key_indicator" : false
 *     },
 *     {
 *       "name" : "my/only/histo",
 *       "relative_name" : ["histo"],
 *       "kind" : "histogram",
 *       "source" : {
 *         "class": "Unspecified",
 *         "category": "NoRoleSpecified",
 *         "process_path": "Unspecified"
 *       },
 *       "description" : "No description provided",
 *       "unit" : "Unspecified",
 *       "verbosity": "default",
 *       "key_indicator" : false,
 *       "buckets" : {
 *         "count" : ".count",
 *         "sum" : ".sum",
 *         "average" : ".avg",
 *         "minimum" : ".min",
 *         "maximum" : ".max",
 *         "0.5" : ".p50",
 *         "0.9" : ".p90",
 *         "0.95" : ".p95",
 *         "0.99" : ".p99",
 *         "0.999" : ".p9990",
 *         "0.9999" : ".p9999"
 *       }
 *     }
 *   ]}
 */
class MetricMetadataQueryHandler(source: MetricSchemaSource = new MetricSchemaSource)
    extends Service[Request, Response] {

  private[this] val statsFormatter = StatsFormatter.default

  private[this] def query(keys: Iterable[String]): Iterable[MetricBuilder] = {
    keys.flatMap { k =>
      // histogram metric.
      if (k.lastIndexOf(statsFormatter.histogramSeparator) > k.lastIndexOf(
          metadataScopeSeparator())) {
        val trimmedMetricName =
          if (k.endsWith("percentile")) { // see StatsFormatter.CommonsStats
            val intermediate = k.substring(0, k.lastIndexOf(statsFormatter.histogramSeparator))
            intermediate.substring(0, intermediate.lastIndexOf(statsFormatter.histogramSeparator))
          } else k.substring(0, k.lastIndexOf(statsFormatter.histogramSeparator))
        val metric = source.getSchema(trimmedMetricName)
        if (metric.isEmpty) source.getSchema(k) else metric
      } else {
        source.getSchema(k)
      }
    }
  }

  def apply(req: Request): Future[Response] = {
    val uri = Uri.fromRequest(req)

    val latched = source.hasLatchedCounters
    val keysParam = uri.params.getAll("name")

    val metrics =
      if (keysParam.isEmpty) source.schemaList
      else query(keysParam)

    newResponse(
      contentType = MediaType.JsonUtf8,
      content = Buf.Utf8(
        AdminJsonConverter.writeToString(
          Map(
            "@version" -> 3.2,
            "counters_latched" -> latched,
            "separator_char" -> metadataScopeSeparator(),
            "metrics" -> metrics
          )))
    )
  }
}
