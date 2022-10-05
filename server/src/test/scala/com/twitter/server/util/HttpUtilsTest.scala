package com.twitter.server.util

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Status
import com.twitter.finagle.http.Version
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils._
import com.twitter.util.Await
import com.twitter.util.Future
import org.scalatest.funsuite.AnyFunSuite

class HttpUtilsTest extends AnyFunSuite {
  private[this] def await[A](a: Future[A]): A = Await.result(a, 5.seconds)

  test("expects") {
    val req1 = Request("/")
    req1.headerMap
      .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")

    val req2 = Request("/admin/threads.json?k=v")
    req2.headerMap.set("Accept", "text/html,application/json;q=0.9,*/*;q=0.8")

    val req3 = Request("/admin/threads.json")
    req3.headerMap.set("Accept", "*/*")

    assert(expectsHtml(req1))
    assert(!expectsJson(req1))

    assert(expectsHtml(req2))
    assert(expectsJson(req2))

    assert(!expectsHtml(req3))
    assert(expectsJson(req3))
  }

  test("newResponse") {
    val res = await(
      newResponse(
        version = Version.Http11,
        status = Status.Ok,
        headers = Seq(("host", "localhost")),
        contentType = "application/json;charset=UTF-8",
        content = Buf.Utf8("[]")
      )
    )
    assert(res.version == Version.Http11)
    assert(res.status == Status.Ok)
    assert(res.headerMap.get("content-type") == Some("application/json;charset=UTF-8"))
    assert(res.contentString == "[]")
  }

  test("newOk") {
    val res = await(newOk("hello"))
    assert(res.status == Status.Ok)
    assert(res.headerMap.get("content-type") == Some("text/plain;charset=UTF-8"))
    assert(res.contentString == "hello")
  }

  test("new404") {
    val res = await(new404("not found"))
    assert(res.status == Status.NotFound)
    assert(res.headerMap.get("content-type") == Some("text/plain;charset=UTF-8"))
    assert(res.contentString == "not found")
  }

}
