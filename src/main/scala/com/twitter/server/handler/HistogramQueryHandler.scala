package com.twitter.server.handler

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.Service
import com.twitter.finagle.stats.{BucketAndCount, HistogramDetail, WithHistogramDetails}
import com.twitter.io.Buf
import com.twitter.server.util.HtmlUtils.escapeHtml
import com.twitter.server.util.HttpUtils.{newResponse, parse}
import com.twitter.server.util.JsonConverter
import com.twitter.util.Future

object HistogramQueryHandler {

  val ContentTypeJson = "application/json;charset=UTF-8"
  val ContentTypeHtml = "text/html;charset=UTF-8"

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

  // Generates html for visualizing histograms
  private[HistogramQueryHandler] val render: String = {
      val css = """<link type="text/css" href="/admin/files/css/histogram-query.css" rel="stylesheet"/>"""

      val chart =
        """<div class="chart">
             <div id="curve_chart" style="width: 900px; height: 500px"></div>
           </div>"""

      /** Generates an html table to display key statistics of a histogram */
      val statsTable = {
        def entry(name: String): String = {
          s"""<tr>
                <td>${escapeHtml(name)}:</td>
                <td id=$name></td>
              </tr>"""
        }
        s"""
          <div id="stats">
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

      val scripts = """
        <script type="text/javascript" src="https://www.gstatic.com/charts/loader.js"></script>
        <script type="text/javascript" src="/admin/files/js/histogram-utils.js"></script>
        <script type="text/javascript" src="/admin/files/js/histogram-dom.js"></script>
        <script type="text/javascript" src="/admin/files/js/histogram-main.js"></script>"""
      css + chart + statsTable + buttonPanel + scripts
  }

  // Generates html for the histogram selection page (/admin/histograms)
  private[HistogramQueryHandler] def renderFront(keys: Seq[String]): String = {
    val css = """
      <link type="text/css" href="/admin/files/css/metric-query.css" rel="stylesheet"/>
      <link type="text/css" href="/admin/files/css/histogram-homepage.css" rel="stylesheet"/>
      """
    val histogramListing = s"""
      <div id="metrics-grid" class="row">
        <div class="col-md-4 snuggle-right">
          <ul id="metrics" class="list-unstyled">
            ${ (for (key <- keys.sorted) yield {
                  s"""<li id="${key.replace("/", "-")}"><a id="special-$key">${escapeHtml(key)}</a></li>"""
                }).mkString("\n") }
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
        ${ (for (key <- keys.sorted) yield {
          s"""document.getElementById("special-$key").setAttribute("href", window.location.href + "?h=$key&fmt=plot_cdf");"""
        }).mkString("\n") }
      </script>
      """
    css + histogramListing + scripts
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

  private[this] def htmlResponse(query: String) =
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
              case Some(Seq("plot_pdf")) | Some(Seq("plot_cdf")) =>
                htmlResponse(query)

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
              content = Buf.Utf8(renderFront(histograms.keySet.toSeq))
            )
        }
      case _ => newResponse(contentType = ContentTypeHtml, content = Buf.Utf8("Invalid endpoint. Did you mean /admin/histograms.json?"))
    }
  }
}
