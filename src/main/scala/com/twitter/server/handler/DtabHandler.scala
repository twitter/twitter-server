package com.twitter.server.handler

import com.twitter.finagle.{Dtab, Announcer, Service}
import com.twitter.util.Future
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http._

/**
 * Dumps a simple string representation of the current Dtab.
 *
 * From the Dtab docs: A Dtab--short for delegation table--comprises a sequence
 * of delegation rules. Together, these describe how to bind a
 * path to an Addr.
 */
class DtabHandler extends Service[HttpRequest, HttpResponse] {
  def apply(req: HttpRequest): Future[HttpResponse] = {
    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    response.setContent(ChannelBuffers.wrappedBuffer(Dtab().toString().getBytes()))
    Future.value(response)
  }
}
