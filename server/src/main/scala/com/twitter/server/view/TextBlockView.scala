package com.twitter.server.view

import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.finagle.http.{Request, Response}
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils.{expectsHtml, newResponse}
import com.twitter.util.Future

class TextBlockView extends SimpleFilter[Request, Response] {
  def apply(req: Request, svc: Service[Request, Response]): Future[Response] = {
    val serviced = svc(req)
    if (!expectsHtml(req)) {
      serviced
    } else {
      serviced.flatMap { res =>
        val html = s"<pre>${res.contentString}</pre>"
        newResponse(
          contentType = "text/html;charset=UTF-8",
          content = Buf.Utf8(html)
        )
      }
    }
  }
}
