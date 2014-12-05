package com.twitter.server.handler

import com.twitter.conversions.time._
import com.twitter.finagle.client.StackClient
import com.twitter.finagle.util.StackRegistry
import com.twitter.finagle.{http, Stack}
import com.twitter.io.Charsets
import com.twitter.server.util.HttpUtils._
import com.twitter.server.util.MetricSourceTest
import com.twitter.util.Await
import com.twitter.util.Time
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

@RunWith(classOf[JUnitRunner])
class ClientRegistryHandlerTest extends FunSuite {
  test("query a client") {
    val metricsCtx = new MetricSourceTest.Ctx
    import metricsCtx._

    val registry = new StackRegistry {}
    registry.register("client0", "localhost:8080", StackClient.newStack, Stack.Params.empty)
    val handler = new ClientRegistryHandler(source, registry)

    val res = Await.result(handler(http.Request("/client0")))
    assert(res.getStatus === http.Status.Ok)
    val content = res.getContent.toString(Charsets.Utf8)
    assert(content.contains("client0"))
    assert(content.contains("localhost:8080"))

    val res1 = Await.result(handler(http.Request("/client1")))
    assert(res1.getStatus === http.Status.NotFound)
  }

  test("client profile") {
    Time.withCurrentTimeFrozen { tc =>
      val metricsCtx = new MetricSourceTest.Ctx
      import metricsCtx._

      val registry = new StackRegistry {}
      registry.register("client0", "localhost:8080", StackClient.newStack, Stack.Params.empty)

      val handler = new ClientRegistryHandler(source, registry)

      tc.advance(1.second)
      val req = http.Request("/index.html")
      assert(Await.result(handler(req)).getContent.toString(Charsets.Utf8) === "")

      underlying = Map(
        "clnt/client0/loadbalancer/adds" -> Entry(10.0, 10.0),
        "clnt/client0/loadbalancer/size" -> Entry(10.0, 10.0),
        "clnt/client0/loadbalancer/available" -> Entry(5.0, 5.0),
        "clnt/client0/requests" -> Entry(50, 50),
        "clnt/client0/failures" -> Entry(1, 1))

      tc.advance(2.seconds)
      val res = Await.result(handler(req))
      val html = res.getContent.toString(Charsets.Utf8)
      assert(html.contains("client0"))
      assert(html.contains("localhost:8080"))
      assert(html.contains("5 unavailable"))
      assert(html.contains("98.00%"))
    }
  }
}