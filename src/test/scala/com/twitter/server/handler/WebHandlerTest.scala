package com.twitter.server.handler

import com.twitter.finagle.http.{Response, Request}
import com.twitter.util.Await
import org.jboss.netty.handler.codec.http.{HttpResponseStatus, HttpHeaders}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class WebHandlerTest extends FunSuite {

  test("ServerInfo handler renders in html for browsers") {
    val req = Request("/")
    req.headers().set(
      HttpHeaders.Names.USER_AGENT,
      "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_4) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/37.0.2062.94 Safari/537.36")

    val handler = new RequestHandlerJson(new ServerInfoHandler(this))
    val res = Response(Await.result(handler(req)))

    assert(res.status == HttpResponseStatus.OK)
    val info = res.contentString
    assert(info.contains("<html>"))
  }

  test("ServerInfo handler renders in json for curl") {
    val req = Request("/")
    req.headers().set(
      HttpHeaders.Names.USER_AGENT,
      "curl/7.9.8 (i686-pc-linux-gnu) libcurl 7.9.8 (OpenSSL 0.9.6b) (ipv6 enabled)")

    val handler = new RequestHandlerJson(new ServerInfoHandler(this))
    val res = Response(Await.result(handler(req)))

    assert(res.status == HttpResponseStatus.OK)
    val info = res.contentString
    assert(!info.contains("<html>"))
  }

}
