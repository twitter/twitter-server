package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.MediaType
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Uri
import com.twitter.finagle.stats.HistogramDetail
import com.twitter.finagle.stats.WithHistogramDetails
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils.newResponse
import com.twitter.server.util.AdminJsonConverter
import com.twitter.server.util.MetricSource
import com.twitter.util.Future

/**
 * A handler which accepts metrics metadata queries via http query strings and returns
 * json encoded metrics with their types and an indicator of whether or not counters are latched.
 *
 * This is a temporary endpoint which will be replaced by a more fleshed out metadata endpoint.
 *
 * @note When passing an explicit histogram metric via ?m=, users must provide the raw histogram
 *       name, no percentile (eg, .p99) appended.
 *
 * Example Request:
 * http://$HOST:$PORT/admin/exp/metric_metadata?m=srv/http/requests&m=srv/http/pending&m=srv/mux/framer/write_stream_bytes
 *
 * Response:
 * {
 *   "latched" : true,
 *   "metrics" : [
 *     {
 *       "name" : "srv/http/requests",
 *       "kind" : "counter"
 *     },
 *     {
 *       "name" : "srv/http/pending",
 *       "kind" : "gauge"
 *     },
 *     {
 *       "name" : "srv/mux/framer/write_stream_bytes",
 *       "kind" : "histogram"
 *     }
 *   ]
 * }
 */
class MetricTypeQueryHandler(
  source: MetricSource = new MetricSource,
  details: Option[WithHistogramDetails] = None)
    extends Service[Request, Response] {

  protected def histograms: Map[String, HistogramDetail] =
    details match {
      case Some(histos) => histos.histogramDetails
      case None => Map.empty
    }

  protected def query(keys: Iterable[String]): Iterable[MetricSource.MetricTypeInfo] =
    for (k <- keys; e <- source.getType(k)) yield e

  protected def getHistos(): Iterable[MetricSource.MetricTypeInfo] = {
    histograms.map {
      case (name, _) => MetricSource.MetricTypeInfo(name, "histogram")
    }
  }

  protected def queryHistos(keys: Set[String]): Iterable[MetricSource.MetricTypeInfo] = {
    histograms.filterKeys(keys.contains).map {
      case (name, _) => MetricSource.MetricTypeInfo(name, "histogram")
    }
  }

  def apply(req: Request): Future[Response] = {
    val uri = Uri.fromRequest(req)

    val latched = source.hasLatchedCounters
    val keysParam = uri.params.getAll("m")

    val metrics =
      if (keysParam.isEmpty) source.typeMap ++ getHistos()
      else query(keysParam) ++ queryHistos(keysParam.toSet)

    newResponse(
      contentType = MediaType.JsonUtf8,
      content = Buf.Utf8(
        AdminJsonConverter.writeToString(
          Map(
            "latched" -> latched,
            "metrics" -> metrics
          )))
    )
  }
}
