package com.twitter.server.handler

import com.twitter.app.App
import com.twitter.finagle.Service
import com.twitter.finagle.http.{Method, Request, Response, Status, Uri}
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils.{newOk, newResponse}
import com.twitter.util.logging.Logger
import com.twitter.util.{Duration, Future}

class ShutdownHandler(app: App) extends Service[Request, Response] {
  private[this] val log = Logger[ShutdownHandler]

  protected def getGraceParam(request: Request): Option[String] =
    Uri.fromRequest(request).params.get("grace")

  def apply(req: Request): Future[Response] = {
    if (req.method == Method.Post) {
      val specifiedGracePeriod = getGraceParam(req).map { d =>
        try Duration.parse(d)
        catch {
          case e: NumberFormatException =>
            val msg = s"could not parse 'grace' parameter: $d is not a valid duration"
            log.info(
              s"[${req.uri}] from ${req.remoteAddress.getHostAddress} " +
                s"failed to quit due to invalid grace period format",
              e
            )
            return newResponse(
              status = Status.BadRequest,
              contentType = "text/plain;charset=UTF-8",
              content = Buf.Utf8(msg)
            )
        }
      }
      val grace = specifiedGracePeriod.getOrElse(app.defaultCloseGracePeriod)
      log.info(
        s"[${req.uri}] from ${req.remoteAddress.getHostAddress} " +
          s"quitting with grace period $grace"
      )
      app.close(grace)
      newOk("quitting\n")
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
