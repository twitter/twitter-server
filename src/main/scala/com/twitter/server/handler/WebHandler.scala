package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.util.Future
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http._

trait WebHandler extends Service[HttpRequest, HttpResponse] {

  /**
   * Extract the value of a parameter for a string of the format: 
   * "....param1=value1&param2=value2..."
   */
  def extractQueryValue(parameter: String, string: String): String = {
    val regex = (""".*""" + parameter + """=([^&]*).*""").r
    string match {
      case regex(value) => value
      case _ => ""
    }
  }

  def makeHttpFriendlyResponse(req: HttpRequest, curl: String, web: String): Future[HttpResponse] = {
    val msg = Option(req.getHeader("User-Agent")) match {
      case Some(ua) if ua.startsWith("curl") =>
        curl
      case _ =>
        "<html>\n\t<body>\n\t\t%s\n\t</body>\n</html>".format(web)
    }

    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    response.setHeader("Content-Language", "en")
    response.setContent(ChannelBuffers.wrappedBuffer(msg.getBytes))
    Future.value(response)
  }
}
