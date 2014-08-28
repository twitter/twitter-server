package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.io.Charsets
import com.twitter.server.responder.ResponderUtils
import com.twitter.util.Future
import java.io.FileNotFoundException
import org.jboss.netty.buffer.ChannelBuffers.wrappedBuffer
import org.jboss.netty.handler.codec.http._
import scala.io.Source

/**
 * A simple resource loader designed to load css/js resources for twitter-server
 * admin pages. The handler parses the query string and follows a simple scheme.
 * For example, the query string "name=Index&type=css" will look for the resource
 * "css/Index.css" in the packages resource directory.
 */
private[server] object ResourceHandler extends WebHandler {
  def apply(req: HttpRequest): Future[HttpResponse] = {
    val query = req.getUri()
    val name = ResponderUtils.extractQueryValue("name", query)
    val fileType = ResponderUtils.extractQueryValue("type", query)
    val filePath = fileType + "/" + name + "." + fileType
    val is = getClass.getClassLoader.getResourceAsStream(filePath)

    if (is == null) new404(req.getProtocolVersion) else {
      val source = Source.fromInputStream(is, Charsets.Utf8.toString)
      val content = source.mkString.getBytes(Charsets.Utf8)
      source.close()
      newResponse(req.getProtocolVersion, HttpResponseStatus.OK, content)
    }
  }
}