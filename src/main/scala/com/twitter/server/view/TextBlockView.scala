package com.twitter.server.view

import com.twitter.finagle.{Service, SimpleFilter}
import com.twitter.io.{Buf, Charsets}
import com.twitter.server.util.HttpUtils._

class TextBlockView extends SimpleFilter[Request, Response] {
  def apply(req: Request, svc: Service[Request, Response]) =
    if (!isWebBrowser(req)) svc(req)
    else svc(req) flatMap { res =>
      val html = s"<pre>${res.getContent.toString(Charsets.Utf8)}</pre>"
      newResponse(
        contentType = "text/html;charset=UTF-8",
        content = Buf.Utf8(html)
      )
    }
}