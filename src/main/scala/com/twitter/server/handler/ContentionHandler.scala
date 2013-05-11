package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.jvm.ContentionSnapshot
import com.twitter.util.Future
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http._

class ContentionHandler extends Service[HttpRequest, HttpResponse] {
  private[this] val snapshotter = new ContentionSnapshot
  def apply(req: HttpRequest) = {
    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    response.setHeader("Content-Type", "text/plain")

    val snap = snapshotter.snap
    val msg = "Blocked:\n%s\n\nLock Owners:\n%s".format(
      snap.blockedThreads.mkString("\n"),
      snap.lockOwners.mkString("\n"))
    response.setContent(ChannelBuffers.wrappedBuffer(msg.getBytes))

    Future.value(response)
  }
}
