package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{HttpMuxer, Request, Response}
import com.twitter.io.Buf
import com.twitter.server.util.HtmlUtils.escapeHtml
import com.twitter.server.util.HttpUtils.{expectsHtml, newOk, newResponse}
import com.twitter.util.Future

/**
 * A handler which outputs `patterns` as html anchors. By default,
 * it outputs the patterns from the globals [[com.twitter.finagle.http.HttpMuxer]]
 * and [[com.twitter.finagle.http.HttpMuxer]].
 */
class IndexHandler(prefix: String = "/", patterns: Seq[String] = HttpMuxer.patterns)
    extends Service[Request, Response] {
  def apply(req: Request): Future[Response] = {
    val paths = patterns.filter(_.startsWith(prefix))
    val links = paths map { p => s"<a href='$p'>${escapeHtml(p)}</a>" }
    if (!expectsHtml(req)) newOk(paths.mkString("\n"))
    else
      newResponse(
        contentType = "text/html;charset=UTF-8",
        content = Buf.Utf8(links.mkString("<br />\n"))
      )
  }
}
