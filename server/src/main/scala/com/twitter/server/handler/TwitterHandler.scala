package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils.newResponse
import com.twitter.util.Future
import com.twitter.util.logging.Logger

trait TwitterHandler extends Service[Request, Response] {
  private[this] val log = Logger[TwitterHandler]

  def respond(msg: String, status: Status = Status.Ok): Future[Response] =
    newResponse(
      contentType = "text/plain;charset=UTF-8",
      status = status,
      content = Buf.Utf8(msg)
    )

  protected def log(req: Request, msg: String): Unit = {
    log.info("[%s %s] %s".format(req.method, req.uri, msg))
  }
}
