package com.twitter.server.handler

import com.twitter.finagle.{Group, Resolver, Service}
import com.twitter.finagle.http.Request
import com.twitter.util.{Future, NonFatal}
import java.net.{SocketAddress, URLEncoder}
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http._
import scala.collection.mutable

class ResolverHandler extends Service[HttpRequest, HttpResponse] {
  private[this] val groups = mutable.Map.empty[String, Group[SocketAddress]]

  private[this] val resolveHTML =
    """<html><head><meta http-equiv="refresh" content="1"></head>
      <body>This page will poll once a second.<br /><br />%s</body></html>"""

  def apply(request: HttpRequest): Future[HttpResponse] = {
    val req = Request(request)
    try {
      val name = req.getParam("name", "")
      val group = synchronized {
        groups.getOrElseUpdate(name, Resolver.resolve(name)())
      }

      val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
      val msg = resolveHTML.format(group().map(_.toString).mkString("<br />"))
      response.setHeader("Content-Language", "en")
      response.setContent(ChannelBuffers.wrappedBuffer(msg.getBytes))
      Future.value(response)

    } catch { case NonFatal(e) =>
      val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
      val msg = "Error: %s".format(e)
      response.setContent(ChannelBuffers.wrappedBuffer(msg.getBytes))
      Future.value(response)
    }
  }
}

class ResolutionsHandler(resolvePath: String) extends Service[HttpRequest, HttpResponse] {
  private[this] val html = "<html><body>%s</body></html>"
  private[this] val resolutionLink = """<a href="%s?name=%s">%s</a>"""

  def apply(req: HttpRequest): Future[HttpResponse] = {
    val resolutions = Resolver.resolutions map { chain =>
      chain
        .zipWithIndex
        .map { case (r, i) =>
          val link = resolutionLink.format(resolvePath, URLEncoder.encode(r, "UTF-8"), r)
          ("&nbsp;" * i) + link
        } mkString("<br />")
    } mkString("<br /><br />")

    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    response.setHeader("Content-Language", "en")
    response.setContent(ChannelBuffers.wrappedBuffer(html.format(resolutions).getBytes))
    Future.value(response)
  }
}
