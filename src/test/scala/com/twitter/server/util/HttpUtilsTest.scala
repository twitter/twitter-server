package com.twitter.server.util

import com.twitter.conversions.time._
import com.twitter.finagle.Service
import com.twitter.finagle.http.{HttpMuxer, Request, Response, Status, Version}
import com.twitter.io.Buf
import com.twitter.server.util.HttpUtils._
import com.twitter.util.{Await, Future}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class HttpUtilsTest extends FunSuite {
  private[this] def await[A](a: Future[A]): A = Await.result(a, 5.seconds)

  test("combine can combine two muxers") {
    val hello = new Service[Request, Response] {
      def apply(req: Request) = newOk("hello")
    }

    val world = new Service[Request, Response] {
      def apply(req: Request) = newOk("world")
    }

    val muxer0 = new HttpMuxer().withHandler("/hello", hello)
    val muxer1 = new HttpMuxer().withHandler("/world", world)

    val svc = combine(Seq(muxer0, muxer1))

    val res0 = await(svc(Request("/hello")))
    assert(res0.contentString == "hello")

    val res1 = await(svc(Request("/world")))
    assert(res1.contentString == "world")
  }

  test("combine order doesn't matter for different paths") {
    val hello = new Service[Request, Response] {
      def apply(req: Request) = newOk("hello")
    }

    val world = new Service[Request, Response] {
      def apply(req: Request) = newOk("world")
    }

    val muxer0 = new HttpMuxer().withHandler("/hello", hello)
    val muxer1 = new HttpMuxer().withHandler("/world", world)

    Seq(muxer0, muxer1).permutations.foreach { seq =>
      val svc = combine(seq)

      val res0 = await(svc(Request("/hello")))
      assert(res0.contentString == "hello")

      val res1 = await(svc(Request("/world")))
      assert(res1.contentString == "world")
    }
  }

  test("combine always chooses the longer prefix") {
    val hello = new Service[Request, Response] {
      def apply(req: Request) = newOk("hello")
    }

    val world = new Service[Request, Response] {
      def apply(req: Request) = newOk("world")
    }

    val muxer0 = new HttpMuxer().withHandler("/hello", hello)
    val muxer1 = new HttpMuxer().withHandler("/hello1", world)

    Seq(muxer0, muxer1).permutations.foreach { seq =>
      val svc = combine(seq)

      val res = await(svc(Request("/hello1")))
      assert(res.contentString == "world")
    }
  }

  test("combine order is respected for identical paths") {
    val hello = new Service[Request, Response] {
      def apply(req: Request) = newOk("hello")
    }

    val world = new Service[Request, Response] {
      def apply(req: Request) = newOk("world")
    }
    val muxer0 = new HttpMuxer().withHandler("/hello", hello)
    val muxer1 = new HttpMuxer().withHandler("/hello", world)

    val svcSeq0 = combine(Seq(muxer0, muxer1))
    val res0 = await(svcSeq0(Request("/hello")))
    assert(res0.contentString == "hello")

    val svcSeq1 = combine(Seq(muxer1, muxer0))
    val res1 = await(svcSeq1(Request("/hello")))
    assert(res1.contentString == "world")
  }

  test("combine can 404 properly") {
    val hello = new Service[Request, Response] {
      def apply(req: Request) = newOk("hello")
    }

    val world = new Service[Request, Response] {
      def apply(req: Request) = newOk("world")
    }
    val muxer0 = new HttpMuxer().withHandler("/hello", hello)
    val muxer1 = new HttpMuxer().withHandler("/world", world)

    val svc = combine(Seq(muxer0, muxer1))

    val res = await(svc(Request("/an404")))
    assert(res.status == Status.NotFound)
  }

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

  test("Extract query values") {
    val uri = "http://test.com/testing?foo=bar&baz=qux&hello=world"
    val (_, params) = parse(uri)
    assert(params("foo") == Seq("bar"))
    assert(params.get("fun") == None)
  }

  test("Extract multiple query values") {
    val uri = "http://test.com/testing?foo=bar&baz=qux&foo=world"
    val (_, params) = parse(uri)
    assert(params("foo") == Seq("bar", "world"))
    assert(params("baz") == Seq("qux"))
    assert(params.get("fun") == None)
  }
}
