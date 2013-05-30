package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.HttpMuxer
import com.twitter.util.Future
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http._

class IndexHandler(prefix: String = "/") extends Service[HttpRequest, HttpResponse] {
  def apply(req: HttpRequest) = {
    val paths = HttpMuxer.patterns filter(_.startsWith(prefix))

    val msg = Option(req.getHeader("User-Agent")) match {
      case Some(ua) if ua.startsWith("curl") =>
        paths.mkString("\n")

      case _ =>
        val links = paths map { p => "<a href='%s'>%s</a>".format(p, p) }
        "<html><body>\n%s\n</body></html>".format(links.mkString("<br />\n"))
    }

    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    response.setHeader("Content-Language", "en")
    response.setContent(ChannelBuffers.wrappedBuffer(msg.getBytes))
    Future.value(response)
  }
}
