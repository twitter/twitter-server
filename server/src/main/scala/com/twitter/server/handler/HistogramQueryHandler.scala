package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Uri}
import com.twitter.finagle.stats.{BucketAndCount, HistogramDetail, WithHistogramDetails}
import com.twitter.io.Buf
import com.twitter.server.util.HtmlUtils.escapeHtml
import com.twitter.server.util.HttpUtils.newResponse
import com.twitter.server.util.JsonConverter
import com.twitter.util.Future

object HistogramQueryHandler {

  private val ContentTypeJson = "application/json;charset=UTF-8"
  private val ContentTypeHtml = "text/html;charset=UTF-8"

  private case class Summary(
    name: String,
    count: Long,
    sum: Long,
    average: Option[Long],
    min: Option[Long],
    max: Option[Long],
    percentiles: Map[String, Long])

  /** the name and percentile thresholds used for summaries */
  private val SummaryThresholds = Seq(
    "p50" -> 0.5,
    "p90" -> 0.9,
    "p95" -> 0.95,
    "p99" -> 0.99,
    "p999" -> 0.999,
    "p9999" -> 0.9999
  )

  /**
   * Stores histogram bucket and a percentage.
   * The percentage is either the density or a
   * cumulative distribution for the bucket
   */
  case class BucketAndPercentage(lowerLimit: Long, upperLimit: Long, percentage: Float)

  private[HistogramQueryHandler] def countPoints(counts: Seq[BucketAndCount]): Int =
    counts.foldLeft(0) { case (acc, v) => acc + v.count }

  // For each key return a percentage
  private[server] def pdf(counts: Seq[BucketAndCount]): Seq[BucketAndPercentage] = {
    val count = countPoints(counts)
    counts.map { v =>
      BucketAndPercentage(v.lowerLimit, v.upperLimit, v.count.toFloat / count)
    }
  }

  // For each key return a cumulative percentage
  private[server] def cdf(counts: Seq[BucketAndCount]): Seq[BucketAndPercentage] = {
    val count = countPoints(counts)
    var c = 0
    counts.map { v: BucketAndCount =>
      c += v.count
      BucketAndPercentage(v.lowerLimit, v.upperLimit, c.toFloat / count)
    }
  }

  private[HistogramQueryHandler] def deliverData(
    counts: Map[String, Seq[BucketAndCount]],
    transform: Seq[BucketAndCount] => Any
  ): String =
    // ".toMap" is important here for scala 2.13 as otherwise it will be a MapView which
    // doesn't serialize correctly with Jackson
    JsonConverter.writeToString(counts.mapValues(transform).toMap)

  // Generates html for visualizing histograms
  private[HistogramQueryHandler] val render: String = {
    val css =
      """<link type="text/css" href="/admin/files/css/histogram-query.css" rel="stylesheet"/>"""

    val chart =
      """<div class="chart">
             <div id="curve_chart" style="width: 900px; height: 500px"></div>
           </div>"""

    /** Generates an html table to display key statistics of a histogram */
    val statsTable = {
      def entry(id: String, display: String): String = {
        s"""<tr>
                <td style="text-align:left">${escapeHtml(display)}</td>
                <td style="text-align:left" id="$id"></td>
              </tr>"""
      }
      s"""
          <div id="stats">
            <table>
              <thead>
                <th style="text-align:left" colspan="2">Details</th>
              </thead>
              <tbody>
                ${entry("detail_count", "Count")}
                ${entry("detail_sum", "Sum")}
                ${entry("detail_average", "Average")}
                ${entry("detail_min", "Min")}
                ${entry("detail_max", "Max")}
                ${entry("detail_p50", "p50")}
                ${entry("detail_p90", "p90")}
                ${entry("detail_p95", "p95")}
                ${entry("detail_p99", "p99")}
                ${entry("detail_p999", "p999")}
                ${entry("detail_p9999", "p9999")}
              </tbody>
            </table>
          </div>"""
    }

    val buttonPanel =
      """<div id="option-panel">
          <form action="post">
            <span class="option-description">Type:
              <a id="PDF" class="button-switch button-light-green left-rounded" title="Probability density function">PDF</a><a id="CDF" class="button-switch button-green right-rounded" title="Cumulative distribution function">CDF</a>
            </span>

            <span class="option-description">Scale:
              <a id="reg" class="button-switch button-red left-rounded" title="Linear scale">Reg</a><a id="log" class="button-switch button-light-red right-rounded" title="Log scale">Log</a>
            </span>

            <span class="option-description">Refresh:
              <a id="refreshOn" class="button-switch button-gray left-rounded" title="Refresh the plot every minute">On</a><a id="refreshOff" class="button-switch button-black right-rounded">Off</a>
            </span>

            <span class="option-description-last"><a id="download-link" class="button-download button-blue" title="Download bucket counts in json">Download</a></span>
          </form>
        </div>"""

    val scripts =
      """
        <script type="text/javascript" src="https://www.gstatic.com/charts/loader.js"></script>
        <script type="text/javascript" src="/admin/files/js/histogram-utils.js"></script>
        <script type="text/javascript" src="/admin/files/js/histogram-dom.js"></script>
        <script type="text/javascript" src="/admin/files/js/histogram-main.js"></script>"""
    css + chart + statsTable + buttonPanel + scripts
  }

  // Generates html for the histogram selection page (/admin/histograms)
  private[HistogramQueryHandler] def renderFront(keys: Seq[String]): String = {
    val css =
      """
      <link type="text/css" href="/admin/files/css/metric-query.css" rel="stylesheet"/>
      <link type="text/css" href="/admin/files/css/histogram-homepage.css" rel="stylesheet"/>
      """
    val histogramListing = s"""
      <div id="metrics-grid" class="row">
        <div class="col-md-4 snuggle-right">
          <ul id="metrics" class="list-unstyled">
            ${(for (key <- keys.sorted) yield {
      s"""<li id="${key.replace("/", "-")}"><a id="special-$key">${escapeHtml(key)}</a></li>"""
    }).mkString("\n")}
          </ul>
        </div>
        <div class="col-md-8 snuggle-left">
          <div style="width: 95%; margin: 0 auto;">
            <div id="metrics-header">Histograms</div>
            <ul>
              <li class="metrics-point">Visualize metric distributions</li>
              <li class="metrics-point">Download histogram contents</li>
              <li class="metrics-point">For more, read the
                <a id="doc-link" href="https://twitter.github.io/twitter-server/Features.html#histograms">docs</a>
              </li>
            </ul>
          </div>
        </div>
      </div>
      """

    val scripts = s"""
      <script>
        ${(for (key <- keys.sorted) yield {
      s"""document.getElementById("special-$key").setAttribute("href", window.location.href + "?h=$key&fmt=plot_cdf");"""
    }).mkString("\n")}
      </script>
      """
    css + histogramListing + scripts
  }
}

/**
 * A handler which accepts queries via http strings and returns
 * json encoded histogram details
 */
private[server] class HistogramQueryHandler(details: WithHistogramDetails)
    extends Service[Request, Response] {
  import HistogramQueryHandler._

  // If possible, access histograms inside statsReceiversLoaded
  private[this] def histograms: Map[String, HistogramDetail] = details.histogramDetails

  private[this] def jsonResponse(
    query: String,
    transform: Seq[BucketAndCount] => String
  ): Future[Response] =
    newResponse(
      contentType = ContentTypeJson,
      content = {
        val text = histograms.get(query) match {
          case Some(h) => transform(h.counts)
          case None => s"Key: $query is not a valid histogram."
        }
        Buf.Utf8(text)
      }
    )

  private[this] def renderHistogramsJson: String =
    JsonConverter.writeToString(histograms.map {
      case (key, value) =>
        (key, value.counts)
    })

  // needs a special case for the upper bound sentinel.
  private[this] def midPoint(bc: BucketAndCount): Double =
    if (bc.upperLimit >= Int.MaxValue) bc.lowerLimit
    else (bc.upperLimit + bc.lowerLimit) / 2.0

  private[this] def generateSummary(histoName: String): Option[Summary] = {
    histograms.get(histoName).map { detail =>
      val bcs = detail.counts.sortBy(_.lowerLimit)

      // first, the basic computations: sum, count, min, max, average
      val min = bcs.headOption.map(_.lowerLimit)
      val max = bcs.lastOption.map(_.upperLimit)
      var sum = 0.0
      var count = 0L
      bcs.foreach { bc =>
        count += bc.count
        sum += bc.count.toDouble * midPoint(bc)
      }
      val average =
        if (count == 0L) None
        else Some(sum.toLong / count)

      // note: this is modeled after `c.t.f.stats.BucketedHistogram.percentile`
      def percentile(total: Long, p: Double): Long = {
        if (p < 0.0 || p > 1.0)
          throw new AssertionError(s"percentile must be within 0.0 to 1.0 inclusive: $p")

        val target = Math.round(p * total)

        val iter = bcs.iterator
        var sum = 0L
        var bc: BucketAndCount = null
        while (iter.hasNext && sum < target) {
          bc = iter.next()
          sum += bc.count
        }
        bc match {
          case null => 0
          case _ if !iter.hasNext => max.getOrElse(0)
          case _ => midPoint(bc).toLong
        }
      }

      val percentiles: Map[String, Long] = SummaryThresholds.map {
        case (name, p) =>
          name -> percentile(count, p)
      }.toMap

      Summary(
        name = histoName,
        count = count,
        sum = sum.toLong,
        average = average,
        min = min,
        max = max,
        percentiles = percentiles
      )
    }
  }

  private[this] def renderSummary(summary: Summary): String =
    JsonConverter.writeToString(summary)

  private[this] def htmlResponse(query: String): Future[Response] =
    newResponse(
      contentType = ContentTypeHtml,
      content = Buf.Utf8 {
        if (histograms.contains(query))
          render
        else
          s"Key: $query is not a valid histogram."
      }
    )

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
   *
   * For `histograms.json`, if there is a `summary` parameter,
   * it will return JSON summarizing the histogram for the given `h` parameter.
   * {{{
   * {
   *   "name": "finagle/timer/deviation_ms",
   *   "summary": {
   *     "count": 65059,
   *     "sum": 651088,
   *     "average": 10,
   *     "min": 1,
   *     "max": 94,
   *     "percentiles": {
   *       "p50": 10,
   *       "p90": 12,
   *       "p95": 12,
   *       "p99": 13,
   *       "p999": 20,
   *       "p9999": 52,
   *     }
   *   }
   * }
   * }}}
   *
   * If `h` is not found, an empty JSON hash will be returned `{}`.
   */
  def apply(req: Request): Future[Response] = {
    val uri = Uri.fromRequest(req)
    val path = uri.path
    val params = uri.params

    path match {
      case "/admin/histograms.json" =>
        if (!params.contains("summary")) {
          newResponse(contentType = ContentTypeJson, content = Buf.Utf8(renderHistogramsJson))
        } else {
          val summary: Option[Summary] = params.get("h") match {
            case Some(histoName) => generateSummary(histoName)
            case _ => None
          }
          val text: String = summary match {
            case Some(s) => renderSummary(s)
            case None => "{}"
          }
          newResponse(contentType = ContentTypeJson, content = Buf.Utf8(text))
        }
      case "/admin/histograms" =>
        params.get("h") match {
          case Some(query) =>
            params.get("fmt") match {
              case Some("plot_pdf") | Some("plot_cdf") =>
                htmlResponse(query)

              case Some("raw") =>
                jsonResponse(query, { counts: Seq[BucketAndCount] =>
                  deliverData(Map(query -> counts), identity)
                })

              case Some("pdf") =>
                jsonResponse(query, { counts: Seq[BucketAndCount] =>
                  deliverData(Map(query -> counts), x => pdf(x))
                })

              case Some("cdf") =>
                jsonResponse(query, { counts: Seq[BucketAndCount] =>
                  deliverData(Map(query -> counts), x => cdf(x))
                })

              case _ =>
                newResponse(
                  contentType = ContentTypeHtml,
                  content = Buf.Utf8("Please provide a format: fmt = raw | pdf | cdf")
                )
            }
          case _ =>
            newResponse(
              contentType = ContentTypeHtml,
              content = Buf.Utf8(renderFront(histograms.keySet.toSeq))
            )
        }
      case _ =>
        newResponse(
          contentType = ContentTypeHtml,
          content = Buf.Utf8("Invalid endpoint. Did you mean /admin/histograms.json?")
        )
    }
  }
}
