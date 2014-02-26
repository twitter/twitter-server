package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.HttpMuxHandler
import com.twitter.util.Future
import java.util.logging.Logger
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http._

trait TwitterHandler extends Service[HttpRequest, HttpResponse] {
  private[this] val log = Logger.getLogger(getClass.getName)

  def respond(msg: String) = {
    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
    response.setHeader("Content-Length", msg.getBytes.length)
    response.setContent(ChannelBuffers.wrappedBuffer(msg.getBytes))
    Future.value(response)
  }

  def background(f: => Unit) {
    (new Thread("lifecycle") {
      override def run() {
        Thread.sleep(10)
        f
      }
    }).start()
  }

  protected def log(req: HttpRequest, msg: String) {
    log.info("[%s %s] %s".format(req.getMethod, req.getUri, msg))
  }
}

class ShutdownHandler extends TwitterHandler {
  def apply(req: HttpRequest) = {
    log(req, "quitting")
    background { System.exit(0) }
    respond("quitting\n")
  }
}

class AbortHandler extends TwitterHandler {
  def apply(req: HttpRequest) = {
    log(req, "aborting")
    background { Runtime.getRuntime.halt(0) }
    respond("aborting\n")
  }
}

class ReplyHandler(msg: String) extends TwitterHandler {
  def apply(req: HttpRequest) = respond(msg)
}
