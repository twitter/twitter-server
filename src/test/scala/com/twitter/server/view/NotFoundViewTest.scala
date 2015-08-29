package com.twitter.server.view

import com.twitter.finagle.httpx
import com.twitter.finagle.Service
import com.twitter.io.{Buf, Charsets}
import com.twitter.server.util.HttpUtils._
import com.twitter.util.Await
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class NotFoundViewTest extends FunSuite {
  test("wraps content based on http status") {
    val handler = new Service[Request, Response] {
      def apply(req: Request) = newResponse(
        contentType = "text/plain;charset=UTF-8",
        status = httpx.Status.NotFound,
        content = Buf.Utf8("hello"))
    }

    val svc = new NotFoundView andThen handler

    val req0 = httpx.Request("/")
    req0.headerMap.add("Accept", "text/html")
    val res0 = Await.result(svc(req0))
    assert(res0.headerMap.get("content-type") === Some("text/html;charset=UTF-8"))
    assert(res0.status === httpx.Status.NotFound)
    assert(res0.contentString.contains("<html>"))
  }
}
