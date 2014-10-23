package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.HttpMuxer
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http._

/**
 * A [[com.twitter.server.handler.WebHandler]] for index pages.
 */
class IndexHandler(
  prefix: String = "/",
  patterns: Seq[String] = HttpMuxer.patterns
) extends WebHandler {
  def apply(req: HttpRequest): Future[HttpResponse] = {
    val paths = patterns.filter(_.startsWith(prefix))
    val links = paths map { p => "<a href='%s'>%s</a>".format(p, p) }
    makeHttpFriendlyResponse(req, paths.mkString("\n"), links.mkString("<br />\n"))
  }
}
