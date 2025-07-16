package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Response
import com.twitter.finagle.http.Status
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils.newOk
import com.twitter.server.util.HttpUtils.newResponse
import com.twitter.util.Future

class ReadinessHandler(isReady: Boolean) extends Service[Request, Response] {
  def apply(req: Request): Future[Response] =
    if (isReady) newOk("Ready")
    else
      newResponse(
        status = Status.ServiceUnavailable,
        contentType = "text/plain;charset=UTF-8",
        content = Buf.Utf8("Not ready. Warming up."))
}
