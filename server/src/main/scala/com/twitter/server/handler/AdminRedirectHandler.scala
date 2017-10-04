package com.twitter.server.handler

import com.twitter.finagle.Service
import com.twitter.finagle.http.{Fields, Request, Response, Status}
import com.twitter.io.Buf
import com.twitter.server.Admin.Path
import com.twitter.server.util.HttpUtils.newResponse
import com.twitter.util.Future
import java.net.URI

class AdminRedirectHandler private (pathMatcher: Option[String => Boolean])
    extends Service[Request, Response] {

  def this() = this(None)
  def this(pathMatcher: String => Boolean) = this(Some(pathMatcher))

  private[this] def mkResponse(status: Status): Future[Response] =
    newResponse(
      status = status,
      contentType = "text/plain;charset=UTF-8",
      content = Buf.Empty,
      headers = Seq((Fields.Location, URI.create(Path.Admin)))
    )

  def apply(req: Request): Future[Response] = pathMatcher match {
    case Some(pred) =>
      if (pred(req.path)) mkResponse(Status.TemporaryRedirect)
      else mkResponse(Status.NotFound)
    case None =>
      mkResponse(Status.TemporaryRedirect)
  }
}
