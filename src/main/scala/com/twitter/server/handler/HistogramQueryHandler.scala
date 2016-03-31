package com.twitter.server.handler

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.Service
import com.twitter.finagle.stats.{BroadcastStatsReceiver, BucketAndCount, HistogramDetail, 
  LoadedStatsReceiver, StatsReceiver, WithHistogramDetails}
import com.twitter.io.{Buf, Charsets}
import com.twitter.server.util.HttpUtils.{newResponse, parse}
import com.twitter.server.util.{JsonConverter, MetricSource}
import com.twitter.util.{Future, Time}
import java.text.DecimalFormat;
import scala.collection.mutable
import scala.language.reflectiveCalls

/**
 * Handler for all histogram-related queries and visualizations
 */

private[server] object HistogramQueryHandler {
  val ContentTypeJson = "application/json;charset=UTF-8"
  val ContentTypeHtml = "text/html;charset=UTF-8"
}

private[server] class HistogramQueryHandler(details: WithHistogramDetails) extends Service[Request, Response] {
  import HistogramQueryHandler._

  // If possible, access histograms inside statsReceiversLoaded
  private[this] def histograms: Map[String, HistogramDetail] = details.histogramDetails

  private[this] def renderHistogramsJson(): String = {
    JsonConverter.writeToString(histograms.map {case (key, value) => (key, value.counts)})
  }

  /** Handles requests for all histograms (/admin/histogram.json) */
  def apply(req: Request): Future[Response] = {
    val (path, params) = parse(req.uri)

    path match {
      case "/admin/histograms.json" => newResponse(contentType = ContentTypeJson, content = Buf.Utf8(renderHistogramsJson))
      case _ => newResponse(contentType = ContentTypeHtml, content = Buf.Utf8("Invalid endpoint. Did you mean /admin/histograms.json?"))
    }
  }
}