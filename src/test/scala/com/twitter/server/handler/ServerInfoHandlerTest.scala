package com.twitter.server.handler

import com.twitter.finagle.http.{Request, Response}
import com.twitter.util.Await
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ServerInfoHandlerTest extends FunSuite {
  test("ServerInfo handler display server information") {
    val handler = new ServerInfoHandler(this)
    val req = Request("/")
    val res = Response(Await.result(handler(req)))

    assert(res.status == HttpResponseStatus.OK)
    val info = res.contentString
    assert(info contains("\"build\" : \"unknown\""))
    assert(info contains("\"build_revision\" : \"unknown\""))
    assert(info contains("\"name\" : \"twitter-server\""))
    assert(info contains("\"version\" : \"0.0.0\""))
    assert(info contains("\"start_time\" :"))
    assert(info contains("\"uptime\" :"))
    // user-defined properties
    assert(info contains("\"foo\" : \"bar\""))
  }

  test("content-type") {
    val handler = new ServerInfoHandler(this)
    val req = Request("/")
    val res = Response(Await.result(handler(req)))
    assert(res.contentType === Some("application/json;charset=UTF-8"))
  }
}
