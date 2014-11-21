package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.server.util.HttpUtils._
import com.twitter.util.Future
import java.util.logging.Logger

class AbortHandler extends Service[Request, Response] {
  private[this] val log = Logger.getLogger(getClass.getName)

  private[this] def background(f: => Unit) {
    (new Thread("lifecycle") {
      override def run() {
        Thread.sleep(10)
        f
      }
    }).start()
  }

  def apply(req: Request): Future[Response] = {
    log.info(s"[${req.getUri}] aborting")
    background { Runtime.getRuntime.halt(0) }
    newOk("aborting\n")
  }
}
