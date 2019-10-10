package com.twitter.server.view

import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils.{expectsHtml, newResponse}
import com.twitter.util.Future

object NotFoundView {
  private val NotFoundHtml: String =
    s"""<html>
          <head>
            <title>404 &middot; Twitter Server Admin</title>
            <link type="text/css" href="/admin/files/css/bootstrap.min.css" rel="stylesheet"/>
          </head>
          <style>
            body { margin: 20px; }
          </style>
          <body>
            <h3>404 <small>The page you requested could not be found.</small></h3>
            <hr/>
            <h6>
              <em>
              Some endpoints are only available when the correct dependencies are added
              to your class path.
              <br/>
              For more information, please see
              <a href="https://twitter.github.io/twitter-server/Features.html#http-admin-interface">
                the twitter-server docs
              </a> or return to <a href="/admin">/admin</a>.
              </em>
            </h6>
          </body>
        </html>"""
}

class NotFoundView extends SimpleFilter[Request, Response] {
  import NotFoundView._

  def apply(req: Request, svc: Service[Request, Response]): Future[Response] =
    if (!expectsHtml(req)) svc(req)
    else
      svc(req) flatMap { res =>
        if (res.status != Status.NotFound) Future.value(res)
        else {
          newResponse(
            contentType = "text/html;charset=UTF-8",
            status = Status.NotFound,
            content = Buf.Utf8(NotFoundHtml)
          )
        }
      }
}
