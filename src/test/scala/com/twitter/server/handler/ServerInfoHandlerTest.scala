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
    assert(info contains("\"build\" :"))
    assert(info contains("\"build_revision\" :"))
    assert(info contains("\"name\" :"))
    assert(info contains("\"version\" :"))
    assert(info contains("\"start_time\" :"))
    assert(info contains("\"uptime\" :"))
  }
}
