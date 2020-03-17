package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{MediaType, Request, Response, Uri}
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils.newResponse
import com.twitter.server.util.{JsonConverter, MetricSchemaSource}
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
 *   "@version" : 1.0,
 *   "counters_latched" : false,
 *   "metrics" : [
 *     {
 *       "name" : "my/cool/counter",
 *       "kind" : "counter",
 *       "source" : {
 *         "class": "finagle.stats.cool",
 *         "category": "Server",
 *         "process_path": "dc/role/zone/service"
 *       },
 *       "description" : "Counts how many cools are seen",
 *       "unit" : "Requests",
 *       "verbosity": "Verbosity(default)",
 *       "key_indicator" : true
 *     },
 *     {
 *       "name" : "your/fine/gauge",
 *       "kind" : "gauge",
 *       "source" : {
 *         "class": "finagle.stats.your",
 *         "category": "Client",
 *         "process_path": "dc/your_role/zone/your_service"
 *       },
 *       "description" : "Measures how fine the downstream system is",
 *       "unit" : "Percentage",
 *       "verbosity": "Verbosity(debug)",
 *       "key_indicator" : false
 *     },
 *     {
 *       "name" : "my/only/histo",
 *       "kind" : "histogram",
 *       "source" : {
 *         "class": "Unspecified",
 *         "category": "NoRoleSpecified",
 *         "process_path": "Unspecified"
 *       },
 *       "description" : "No description provided",
 *       "unit" : "Unspecified",
 *       "verbosity": "Verbosity(default)",
 *       "key_indicator" : false,
 *       "buckets" : [
 *         0.5,
 *         0.9,
 *         0.99,
 *         0.999,
 *         0.9999
 *       ]
 *     }
 *   ]}
 */
class MetricMetadataQueryHandler(source: MetricSchemaSource = new MetricSchemaSource)
    extends Service[Request, Response] {

  private[this] def query(keys: Iterable[String]) =
    for (k <- keys; e <- source.getSchema(k)) yield e

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
        JsonConverter.writeToString(
          Map(
            "@version" -> 1.0,
            "counters_latched" -> latched,
            "metrics" -> metrics
          )))
    )
  }
}
