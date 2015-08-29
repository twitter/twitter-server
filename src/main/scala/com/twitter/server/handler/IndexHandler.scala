package com.twitter.server.handler

import com.twitter.finagle.httpx.HttpMuxer
import com.twitter.finagle.{Service, httpx}
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils._
import com.twitter.util.Future

/**
 * A handler which outputs `patterns` as html anchors. By default,
 * it outputs the patterns from the globals [[com.twitter.finagle.httpx.HttpMuxer]]
 * and [[com.twitter.finagle.httpx.HttpMuxer]].
 */
class IndexHandler(
    prefix: String = "/",
    patterns: Seq[String] = HttpMuxer.patterns ++ httpx.HttpMuxer.patterns)
  extends Service[Request, Response] {
  def apply(req: Request): Future[Response] = {
    val paths = patterns.filter(_.startsWith(prefix))
    val links = paths map { p => s"<a href='$p'>$p</a>" }
    if (!expectsHtml(req)) newOk(paths.mkString("\n"))
    else newResponse(
      contentType = "text/html;charset=UTF-8",
      content = Buf.Utf8(links.mkString("<br />\n"))
    )
  }
}