package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.HttpMuxHandler
import com.twitter.util.Future
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http._

class FailureHandler(msg: String) extends WebHandler {
  def apply(req: HttpRequest): Future[HttpResponse] = {
    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND)
    response.setContent(ChannelBuffers.wrappedBuffer(msg.getBytes))
    Future.value(response)

    makeHttpFriendlyResponse(req, msg, msg) map { rep =>
      rep.setStatus(HttpResponseStatus.NOT_FOUND)
      rep
    }
  }
}
