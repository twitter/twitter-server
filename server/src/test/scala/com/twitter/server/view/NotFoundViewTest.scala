package com.twitter.server.view

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.http.{Request, Response, Status}
import com.twitter.finagle.Service
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils.newResponse
import com.twitter.util.{Await, Awaitable}
import org.scalatest.funsuite.AnyFunSuite

class NotFoundViewTest extends AnyFunSuite {

  private[this] def await[T](a: Awaitable[T]): T = Await.result(a, 2.seconds)

  test("wraps content based on http status") {
    val handler = new Service[Request, Response] {
      def apply(req: Request) =
        newResponse(
          contentType = "text/plain;charset=UTF-8",
          status = Status.NotFound,
          content = Buf.Utf8("hello")
        )
    }

    val svc = new NotFoundView andThen handler

    val req0 = Request("/")
    req0.headerMap.add("Accept", "text/html")
    val res0 = await(svc(req0))
    assert(res0.headerMap.get("content-type") == Some("text/html;charset=UTF-8"))
    assert(res0.status == Status.NotFound)
    assert(res0.contentString.contains("<html>"))
  }
}
