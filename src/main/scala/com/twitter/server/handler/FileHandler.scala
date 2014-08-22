package com.twitter.server.handler

import com.twitter.server.responder.ResponderUtils
import com.twitter.finagle.Service
import com.twitter.util.Future
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.buffer.ChannelBuffers.copiedBuffer
import org.jboss.netty.util.CharsetUtil.UTF_8
import scala.util.Random
import scala.io._

/**
 * Gets a list of clients from the [[com.twitter.finagle.client.ClientRegistry ClientRegistry]] and
 * displays individual client information at <baseUrl><client name>
 */
class FileHandler(baseUrl: String, fileDirectory: String) extends WebHandler {
  def apply(req: HttpRequest) = {
    val response = new DefaultHttpResponse(req.getProtocolVersion, HttpResponseStatus.OK)
    val query = req.getUri().stripPrefix(baseUrl)
    val name = ResponderUtils.extractQueryValue("name", query)
    val fileType = ResponderUtils.extractQueryValue("type", query)
    val filePath = fileDirectory + "/" + fileType + "/" + name + "." + fileType
    val source = scala.io.Source.fromFile(filePath)
    val content = source.mkString
    source.close()
    response.setContent(copiedBuffer(content, UTF_8))
    Future.value(response)
  }
}

