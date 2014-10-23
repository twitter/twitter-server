package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.util.Future
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http._

trait WebHandler extends Service[HttpRequest, HttpResponse] {

  /**
   * Uses basic heuristics to determine if the request is coming from a web browser.
   */
  private[this] def isWebBrowser(userAgent: String): Boolean =
    userAgent.startsWith("Mozilla")

  def makeHttpFriendlyResponse(req: HttpRequest, curl: String, web: String): Future[HttpResponse] = {
    val msg = Option(req.headers.get(HttpHeaders.Names.USER_AGENT)) match {
      case Some(ua) if isWebBrowser(ua) => "<html>\n\t<body>\n\t\t%s\n\t</body>\n</html>".format(web)
      case _ => curl
    }
    newResponse(req.getProtocolVersion, HttpResponseStatus.OK, msg.getBytes)
  }

  /**
   * Create a netty HttpResponse. Some of the headers like
   * content length are inferred.
   *
   * @param version The HTTP version for this response.
   * @param status The HTTP status code.
   * @param content The content body of the HTTP response.
   * @param headers Additional headers to include in the response.
   */
  protected def newResponse(
    version: HttpVersion,
    status: HttpResponseStatus,
    content: Array[Byte] = Array.empty,
    headers: Iterable[(String, Object)] = Seq()
  ): Future[HttpResponse] = {
    val response = new DefaultHttpResponse(version, status)
    response.setContent(ChannelBuffers.wrappedBuffer(content))
    for ((k, v) <- headers) response.headers.set(k, v.toString)
    response.headers.set(HttpHeaders.Names.CONTENT_LANGUAGE , "en")
    response.headers.set(HttpHeaders.Names.CONTENT_LENGTH, content.size.toString)
    Future.value(response)
  }

  protected def new404(version: HttpVersion): Future[HttpResponse] =
    newResponse(version, HttpResponseStatus.NOT_FOUND)
}
