package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.HttpMuxHandler
import com.twitter.util.Future
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http._

class FailureHandler(msg: String) extends WebHandler {
  def apply(req: HttpRequest): Future[HttpResponse] = {
    newResponse(req.getProtocolVersion, HttpResponseStatus.NOT_FOUND, msg.getBytes)
  }
}
