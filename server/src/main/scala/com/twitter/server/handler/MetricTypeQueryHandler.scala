package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{MediaType, Request, Response}
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils.{newResponse, parse}
import com.twitter.server.util.{JsonConverter, MetricSource}
import com.twitter.util.Future

/**
 * A handler which accepts metrics metadata queries via http query strings and returns
 * json encoded metrics with their types and an indicator of whether or not counters are latched.
 *
 * This is a temporary endpoint which will be replaced by a more fleshed out metadata endpoint.
 * @note Histograms are not yet supported.
 *
 * Example Request:
 * http://$HOST:$PORT/admin/exp/metric_metadata?m=srv/http/requests&m=srv/http/pending
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
 *     }
 *   ]
 * }
 */
class MetricTypeQueryHandler(source: MetricSource = new MetricSource)
    extends Service[Request, Response] {

  private[this] def query(keys: Seq[String]) =
    for (k <- keys; e <- source.getType(k)) yield e

  def apply(req: Request): Future[Response] = {
    val (_, params) = parse(req.uri)

    val latched = source.hasLatchedCounters
    params.getOrElse("m", Nil) match {
      case Nil =>
        newResponse(
          contentType = MediaType.JsonUtf8,
          content = Buf.Utf8(
            JsonConverter.writeToString(
              Map(
                "latched" -> latched,
                "metrics" -> source.typeMap
              )))
        )

      case someKeys =>
        newResponse(
          contentType = MediaType.JsonUtf8,
          content = Buf.Utf8(
            JsonConverter.writeToString(
              Map(
                "latched" -> latched,
                "metrics" -> query(someKeys)
              )))
        )
    }
  }
}