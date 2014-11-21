package com.twitter.server.handler

import com.twitter.finagle.server.StackServer
import com.twitter.finagle.{http, Stack}
import com.twitter.finagle.util.StackRegistry
import com.twitter.io.Charsets
import com.twitter.server.util.HttpUtils._
import com.twitter.server.util.MetricSourceTest
import com.twitter.util.Await
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ServerRegistryHandlerTest extends FunSuite {
  test("query a server") {
    val metricsCtx = new MetricSourceTest.Ctx
    import metricsCtx._

    val registry = new StackRegistry {}
    registry.register("server0", ":8080", StackServer.newStack, Stack.Params.empty)
    val handler = new ServerRegistryHandler(source, registry)

    val res = Await.result(handler(http.Request("/server0")))
    assert(res.getStatus === http.Status.Ok)
    val content = res.getContent.toString(Charsets.Utf8)
    assert(content.contains("server0"))
    assert(content.contains(":8080"))

    val res1 = Await.result(handler(http.Request("/server1")))
    assert(res1.getStatus === http.Status.NotFound)
  }
}