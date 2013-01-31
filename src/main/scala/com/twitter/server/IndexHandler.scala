package com.twitter.server

import com.twitter.finagle.Service
import com.twitter.finagle.http.HttpMuxer
import com.twitter.util.Future
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http._

class IndexHandler extends Service[HttpRequest, HttpResponse] {
  def apply(req: HttpRequest) = {
    val links = HttpMuxer.patterns filter { _ != "//" } map { p =>
      "<a href='%s'>%s</a>".format(p, p)
    }
    val msg = "<html><body>\n%s\n</body></html>".format(links.mkString("<br />\n"))

    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    response.setContent(ChannelBuffers.wrappedBuffer(msg.getBytes))
    Future.value(response)
  }
}
