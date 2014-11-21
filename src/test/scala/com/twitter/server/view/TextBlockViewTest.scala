package com.twitter.server.view

import com.twitter.finagle.http
import com.twitter.finagle.Service
import com.twitter.io.{Buf, Charsets}
import com.twitter.server.util.HttpUtils._
import com.twitter.util.Await
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class TextBlockViewTest extends FunSuite {
  test("wraps content based on user-agent") {
    val handler = new Service[Request, Response] {
      def apply(req: Request) = newResponse(
        contentType = "text/plain;charset=UTF-8",
        content = Buf.Utf8("hello"))
    }

    val svc = new TextBlockView andThen handler

    val req0 = http.Request("/")
    req0.headers().set("User-Agent", "Mozilla")
    val res0 = Await.result(svc(req0))
    assert(res0.headers.get("content-type") === "text/html;charset=UTF-8")
    assert(res0.getContent.toString(Charsets.Utf8) === "<pre>hello</pre>")

    val req = http.Request("/")
    val res = Await.result(svc(req))
    assert(res.headers.get("content-type") === "text/plain;charset=UTF-8")
    assert(res.getContent.toString(Charsets.Utf8) === "hello")
  }
}
