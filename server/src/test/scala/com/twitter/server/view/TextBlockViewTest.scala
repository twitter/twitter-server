package com.twitter.server.view

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.Service
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils._
import com.twitter.util.Await
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TextBlockViewTest extends FunSuite {
  test("wraps content based on user-agent") {
    val handler = new Service[Request, Response] {
      def apply(req: Request) =
        newResponse(contentType = "text/plain;charset=UTF-8", content = Buf.Utf8("hello"))
    }

    val svc = new TextBlockView andThen handler

    val req0 = Request("/")
    req0.headerMap.set("Accept", "text/html")
    val res0 = Await.result(svc(req0))
    assert(res0.headerMap.get("content-type") == Some("text/html;charset=UTF-8"))
    assert(res0.contentString == "<pre>hello</pre>")

    val req = Request("/")
    val res = Await.result(svc(req))
    assert(res.headerMap.get("content-type") == Some("text/plain;charset=UTF-8"))
    assert(res.contentString == "hello")
  }
}
