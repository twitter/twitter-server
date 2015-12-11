package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Fields, Request, Response, Status}
import com.twitter.io.Buf
import com.twitter.server.Admin.Path
import com.twitter.server.util.HttpUtils.newResponse
import com.twitter.util.Future
import java.net.URI

class AdminRedirectHandler extends Service[Request, Response] {

  def apply(req: Request): Future[Response] = {
    newResponse(
      status = Status.TemporaryRedirect,
      contentType = "text/plain;charset=UTF-8",
      content = Buf.Empty,
      headers = Seq((Fields.Location, URI.create(Path.Admin)))
    )
  }
}
