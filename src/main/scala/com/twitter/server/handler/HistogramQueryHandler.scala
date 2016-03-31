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

object HistogramQueryHandler {

  val ContentTypeJson = "application/json;charset=UTF-8"
  val ContentTypeHtml = "text/html;charset=UTF-8"

  /** 
   * Stores histogram bucket and a percentage.
   * The percentage is either the density or a 
   * cumulative density for the bucket
   */
  case class BucketAndPercentage(lowerLimit: Long, upperLimit: Long, percentage: Float)

  // For each key return a percentage
  private[server] def pdf(counts: Seq[BucketAndCount]): Seq[BucketAndPercentage] = {
    val count = countPoints(counts)
    counts.map { v => BucketAndPercentage(v.lowerLimit, v.upperLimit, v.count.toFloat/count) }
  }

  // For each key return a cumulative percentage
  private[server] def cdf(counts: Seq[BucketAndCount]): Seq[BucketAndPercentage] = {
    val count = countPoints(counts)
    var c = 0
    counts.map { v: BucketAndCount => 
      c += v.count
      BucketAndPercentage(v.lowerLimit, v.upperLimit, c.toFloat/ count) }
  }

  private[HistogramQueryHandler] def deliverData(counts: Seq[BucketAndCount], 
    transform: Seq[BucketAndCount] => Any): String = 
      JsonConverter.writeToString(transform(counts))

  private[HistogramQueryHandler] def countPoints(counts: Seq[BucketAndCount]): Int = 
    counts.foldLeft(0) { case (acc, v) => acc + v.count }

  /** Generates an html table to display key statistics of a histogram */ 
  private[HistogramQueryHandler] def statsTableHtml: String = {
    def entry(name: String): String = {
      s"""
        <tr>
          <td>$name:</td>
          <td id=$name></td>
        </tr>""" 
    }
    s"""
      <table>
        <thead>
          <th colspan="2">Statistics</th>
        </thead>
        <tbody>
          ${entry("Count")}
          ${entry("Sum")}
          ${entry("Avg")}
          ${entry("Max")}
          ${entry("Min")}
          ${entry("P-50")}
          ${entry("P-90")}
          ${entry("P-95")}
          ${entry("P-99")}
          ${entry("P-999")}
          ${entry("P-9999")}
        </tbody>
      </table>"""
  }
}

/** 
 * A handler which accepts queries via http strings and returns
 * json encoded histogram details
 */
private[server] class HistogramQueryHandler(details: WithHistogramDetails) extends Service[Request, Response] {
  import HistogramQueryHandler._

  // If possible, access histograms inside statsReceiversLoaded
  private[this] def histograms: Map[String, HistogramDetail] = details.histogramDetails

  private[this] def jsonResponse(query: String, 
    transform: Seq[BucketAndCount] => String) = 
      newResponse(
        contentType = ContentTypeJson,
        content = {
          histograms.get(query) match {
            case Some(h) => Buf.Utf8(transform(h.counts))
            case None => Buf.Utf8(s"Key: $query is not a valid histogram.")
          }
        }
      )

  private[this] def renderHistogramsJson(): String = {
    JsonConverter.writeToString(histograms.map {case (key, value) => (key, value.counts)})
  }

  /**
   * Handles requests for all histograms (/admin/histogram.json)
   * or for a specific histogram (/admin/histogram?h=...)
   *
   * For specific histograms the following options are available:
   *
   * "h": the name of the histogram
   *    Ex: finagle/timer/deviation_ms
   *
   * "fmt": the type of format used to display results.
   *    The formats we support are raw, pdf, and cdf
   *    raw: histogram bucket counts 
   *      (use to do a custom computation with histogram counts)
   *    pdf: percentage of total for each bucket 
   *      (use to identify modes of a distribution)
   *    cdf: cumulative percentage of total for each bucket
   *      (use to view more quantiles)
   *
   * "log_scale": whether or not the x-axis increases linearly or exponentially.
   *    This parameter can be omitted if not querying for plots
   */

  def apply(req: Request): Future[Response] = {
    val (path, params) = parse(req.uri)

    path match {
      case "/admin/histograms.json" =>
       newResponse(contentType = ContentTypeJson, content = Buf.Utf8(renderHistogramsJson))
      case "/admin/histograms" =>
        params.get("h") match {
          case Some(Seq(query)) =>
            params.get("fmt") match {
              case Some(Seq("raw")) =>
                jsonResponse(query, { counts: Seq[BucketAndCount] => 
                  deliverData(counts, x => x) })

              case Some(Seq("pdf")) => 
                jsonResponse(query, { counts: Seq[BucketAndCount] => 
                  deliverData(counts, x => pdf(x)) })

              case Some(Seq("cdf")) => 
                jsonResponse(query, { counts: Seq[BucketAndCount] => 
                  deliverData(counts, x => cdf(x)) })

              case _ => 
                newResponse(
                  contentType = ContentTypeHtml,
                  content = Buf.Utf8("Please provide a format: fmt = raw | pdf | cdf"))
            }
          case _ => 
            newResponse(
              contentType = ContentTypeHtml,
              content = Buf.Utf8("Please provide a histogram as the h parameter"))
        }
      case _ => newResponse(contentType = ContentTypeHtml, content = Buf.Utf8("Invalid endpoint. Did you mean /admin/histograms.json?"))
    }
  }
}