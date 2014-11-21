package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.io.Buf
import com.twitter.io.Charsets
import com.twitter.server.util.HttpUtils._
import com.twitter.server.util.{JsonConverter, MetricSource}
import com.twitter.util.{Future, Time}

private object MetricQueryHandler {
  def render(title: String, keys: Set[String]): String =
    s"""<link type="text/css" href="/admin/files/css/metric-query.css" rel="stylesheet"/>
        <script type="application/javascript" src="/admin/files/js/metric-query.js"></script>
        <script type="application/javascript" src="/admin/files/js/chart-renderer.js"></script>
        <div id="metrics-grid" class="row" data-refresh-uri="/admin/metrics">
          <div class="col-md-4 snuggle-right">
            <ul id="metrics" class="list-unstyled">
              ${ (for (key <- keys.toSeq.sorted) yield {
                    s"""<li id="${key.replace("/", "-")}">$key</li>"""
                  }).mkString("\n") }
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

  private[this] def query(keys: Seq[String]) =
    for (k <- keys; e <- source.get(k)) yield e

  def apply(req: Request): Future[Response] = {
    val (_, params) = parse(req.getUri)

    params.getOrElse("m", Nil) match {
      case Nil =>
        newResponse(
          contentType = "text/html;charset=UTF-8",
          content = Buf.Utf8(render("Test", source.keySet))
        )

      case someKeys =>
        newResponse(
          contentType = "application/json;charset=UTF-8",
          content = Buf.Utf8(JsonConverter.writeToString(query(someKeys)))
        )
    }
  }
}