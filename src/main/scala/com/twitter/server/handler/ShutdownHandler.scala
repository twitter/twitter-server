package com.twitter.server.handler

import com.twitter.app.App
import com.twitter.finagle.http.{Method, Request, Response, Status}
import com.twitter.finagle.Service
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils.{parse, newOk, newResponse}
import com.twitter.util.{Duration, Future}
import java.util.logging.Logger

class ShutdownHandler(app: App) extends Service[Request, Response] {
  private[this] val log = Logger.getLogger(getClass.getName)

  protected def getGraceParam(uri: String): Option[String] =
    parse(uri)._2.get("grace").flatMap(_.headOption)

  def apply(req: Request): Future[Response] = {
    if (req.method == Method.Post) {
      log.info(s"[${req.uri}] from ${req.remoteAddress.getHostAddress} quitting")
      val grace = getGraceParam(req.uri) map { d =>
        try Duration.parse(d) catch {
          case e: NumberFormatException =>
            val msg = "could not parse 'grace' parameter: %s is not a valid duration".format(d)
            return newResponse(
              status = Status.BadRequest,
              contentType = "text/plain;charset=UTF-8",
              content = Buf.Utf8(msg)
            )
        }
      }
      app.close(grace getOrElse app.defaultCloseGracePeriod)
      newOk("quitting\n")
    } else {
      log.info(s"ignoring [${req.uri}] from ${req.remoteAddress.getHostAddress}, because it is a ${req.method}, not a POST")
      newResponse(
        status = Status.MethodNotAllowed,
        headers = Seq(("Allow", "POST")),
        contentType = "text/plain;charset=UTF-8",
        content = Buf.Empty
      )
    }
  }
}
