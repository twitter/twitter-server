package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Method, Request, Response, Status}
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils._
import com.twitter.util.Future
import com.twitter.util.logging.Logger

class AbortHandler extends Service[Request, Response] {
  private[this] val log = Logger[AbortHandler]

  private[this] def background(f: => Unit): Unit = {
    new Thread("lifecycle") {
      override def run(): Unit = {
        Thread.sleep(10)
        f
      }
    }.start()
  }

  def apply(req: Request): Future[Response] = {
    if (req.method == Method.Post) {
      log.info(s"[${req.uri}] from ${req.remoteAddress.getHostAddress} aborting")
      background {
        Runtime.getRuntime.halt(0)
      }
      newOk("aborting\n")
    } else {
      log.info(
        s"ignoring [${req.uri}] from ${req.remoteAddress.getHostAddress}, because it is a ${req.method}, not a POST"
      )
      newResponse(
        status = Status.MethodNotAllowed,
        headers = Seq(("Allow", "POST")),
        contentType = "text/plain;charset=UTF-8",
        content = Buf.Empty
      )
    }
  }
}
