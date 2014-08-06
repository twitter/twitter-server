package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.HttpMuxer
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http._

class IndexHandler(prefix: String = "/") extends WebHandler {
  def apply(req: HttpRequest): Future[HttpResponse] = {
    val paths = HttpMuxer.patterns.filter(_.startsWith(prefix))
   	val links = paths map { p => "<a href='%s'>%s</a>".format(p, p) }
    makeHttpFriendlyResponse(req, paths.mkString("\n"), links.mkString("<br />\n"))
  }
}
