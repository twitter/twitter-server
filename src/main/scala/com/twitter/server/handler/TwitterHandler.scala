package com.twitter.server.handler

import com.twitter.finagle.http.Status
import com.twitter.finagle.Service
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils._
import com.twitter.util.Future
import java.util.logging.Logger
import org.jboss.netty.handler.codec.http._
import scala.collection.{Map, Seq}

trait TwitterHandler extends Service[HttpRequest, HttpResponse] {
  private[this] val log = Logger.getLogger(getClass.getName)

  def respond(msg: String, status: HttpResponseStatus = Status.Ok): Future[HttpResponse] =
    newResponse(
      contentType = "text/plain;charset=UTF-8",
      status = status,
      content = Buf.Utf8(msg)
    )

  protected[handler] def getParams(uri: String): Map[String, Seq[String]] =
    parse(uri)._2

  protected def log(req: HttpRequest, msg: String): Unit = {
    log.info("[%s %s] %s".format(req.getMethod, req.getUri, msg))
  }
}