package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response}
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils.{expectsHtml, newOk, newResponse}
import com.twitter.util.Future

private object SummaryHandler {
  val TextResponse = "Visit twitter-server's admin pages via browser for a richer experience."

  def render(finagleVersion: String, procInfo: Seq[String]): String =
    s"""<script type="application/javascript" src="/admin/files/js/summary.js"></script>
      <link type="text/css" href="/admin/files/css/summary.css" rel="stylesheet">
      <div id="lint-warnings" data-refresh-uri="/admin/failedlint"></div>
      <div id="process-info" class="text-center well well-sm" data-refresh-uri="/admin/metrics">
        <ul class="list-inline">
          <li><span class="glyphicon glyphicon-info-sign"/></li>
          ${(for (key <- procInfo) yield {
      s"""<li data-key="$key">
                    <div>
                      <a href="/admin/metrics#${key}">${key}:</a>
                      <span id="${key.replace("/", "-")}">...</span>
                      &middot;
                    </div>
                  </li>"""
    }).mkString("\n")}
          <li><div><b>Finagle Ver: </b><span>${finagleVersion}</span><div></li>
        </ul>
      </div>
      <div id="server-info" data-refresh-uri="/admin/servers/index.txt"></div>
      <div id="client-info" data-refresh-uri="/admin/clients/index.txt"></div>"""
}

class SummaryHandler extends Service[Request, Response] {
  import SummaryHandler._

  override def apply(req: Request): Future[Response] =
    if (!expectsHtml(req)) newOk(TextResponse)
    else {
      val finagleVersion = com.twitter.finagle.Init.finagleVersion

      val procInfo = Seq("jvm/uptime", "jvm/thread/count", "jvm/mem/current/used", "jvm/gc/msec")

      val html = render(finagleVersion, procInfo)

      newResponse(
        contentType = "text/html;charset=UTF-8",
        content = Buf.Utf8(html)
      )
    }
}
