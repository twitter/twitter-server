package com.twitter.server.handler

import com.twitter.app.App
import com.twitter.finagle.Service
import com.twitter.finagle.http.Status
import com.twitter.util._
import java.util.logging.Logger
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http._
import scala.collection.JavaConverters._
import scala.collection.{Map, Seq}

trait TwitterHandler extends Service[HttpRequest, HttpResponse] {
  private[this] val log = Logger.getLogger(getClass.getName)

  def respond(msg: String, status: HttpResponseStatus = Status.Ok) = {
    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status)
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

  protected[handler] def getParams(uri: String): Map[String, Seq[String]] =
    new QueryStringDecoder(uri).getParameters.asScala.mapValues { _.asScala.toSeq }

  protected def log(req: HttpRequest, msg: String) {
    log.info("[%s %s] %s".format(req.getMethod, req.getUri, msg))
  }
}

class ShutdownHandler(app: App) extends TwitterHandler {
  protected[handler] def getGraceParam(uri: String): Option[String] =
    getParams(uri).get("grace") collect {
      case Seq(hd, _*) if hd.nonEmpty => hd
    }

  def apply(req: HttpRequest): Future[HttpResponse] = {
    log(req, "quitting")
    val grace = getGraceParam(req.getUri) map { d =>
      try Duration.parse(d) catch { case e: NumberFormatException =>
        val msg = "could not parse 'grace' parameter: %s is not a valid duration".format(d)
        return respond(msg, Status.BadRequest)
      }
    }
    app.close(grace getOrElse app.defaultCloseGracePeriod)
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
