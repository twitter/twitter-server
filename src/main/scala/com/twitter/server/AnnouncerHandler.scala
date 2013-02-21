package com.twitter.server

import com.twitter.finagle.{Announcer, Service}
import com.twitter.util.Future
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http._

class AnnouncerHandler extends Service[HttpRequest, HttpResponse] {
  def apply(req: HttpRequest): Future[HttpResponse] = {
    val msg = Announcer.announcements map { case (addr, targets) =>
      val indented = targets.zipWithIndex map { case (t, i) => (" " * i * 2) + t }
      addr.toString + "\n" + indented.mkString("\n")
    } mkString("\n\n")

    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    response.setContent(ChannelBuffers.wrappedBuffer(msg.getBytes))
    Future.value(response)
  }
}
