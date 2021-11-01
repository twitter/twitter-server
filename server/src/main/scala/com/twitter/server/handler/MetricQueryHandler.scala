package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.MediaType
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Uri
import com.twitter.io.Buf
import com.twitter.server.util.HtmlUtils.escapeHtml
import com.twitter.server.util.HttpUtils.newResponse
import com.twitter.server.util.AdminJsonConverter
import com.twitter.server.util.MetricSource
import com.twitter.util.Future

private object MetricQueryHandler {
  def render(title: String, keys: Set[String]): String =
    s"""<link type="text/css" href="/admin/files/css/metric-query.css" rel="stylesheet"/>
        <script type="application/javascript" src="/admin/files/js/chart-renderer.js"></script>
        <script type="application/javascript" src="/admin/files/js/metric-query.js"></script>
        <div id="metrics-grid" class="row" data-refresh-uri="/admin/metrics">
          <div class="col-md-4 snuggle-right">
            <ul id="metrics" class="list-unstyled">
              ${(for (key <- keys.toSeq.sorted) yield {
      s"""<li id="${key.replace("/", "-")}">${escapeHtml(key)}</li>"""
    }).mkString("\n")}
            </ul>
          </div>
          <div class="col-md-8 snuggle-left">
            <div id="chart-div"></div>
          </div>
        </div>"""
}

/**
 * A handler which accepts metrics queries via http query strings and returns
 * json encoded metrics.
 */
class MetricQueryHandler(source: MetricSource = new MetricSource)
    extends Service[Request, Response] {
  import MetricQueryHandler._

  private[this] def query(keys: Iterable[String]) =
    for (k <- keys; e <- source.get(k)) yield e

  def apply(req: Request): Future[Response] = {
    val uri = Uri.fromRequest(req)

    if (uri.params.contains("m")) {
      val someKeys = uri.params.getAll("m")
      newResponse(
        contentType = MediaType.JsonUtf8,
        content = Buf.Utf8(AdminJsonConverter.writeToString(query(someKeys)))
      )
    } else {
      newResponse(
        contentType = "text/html;charset=UTF-8",
        content = Buf.Utf8(render("Test", source.keySet))
      )
    }
  }
}
