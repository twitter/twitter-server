package com.twitter.server.handler

import com.twitter.conversions.time._
import com.twitter.finagle.client.StackClient
import com.twitter.finagle.util.StackRegistry
import com.twitter.finagle.{httpx, Stack, param}
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

    val registry = new StackRegistry { def registryName: String = "client" }
    registry.register("localhost:8080", StackClient.newStack, Stack.Params.empty + param.Label("client0"))
    val handler = new ClientRegistryHandler(source, registry)

    val res = Await.result(handler(httpx.Request("/client0")))
    assert(res.status === httpx.Status.Ok)
    val content = res.contentString
    assert(content.contains("client0"))
    assert(content.contains("localhost:8080"))

    val res1 = Await.result(handler(httpx.Request("/client1")))
    assert(res1.status === httpx.Status.NotFound)
  }

  test("client profile") {
    Time.withCurrentTimeFrozen { tc =>
      val metricsCtx = new MetricSourceTest.Ctx
      import metricsCtx._

      val registry = new StackRegistry { def registryName: String = "client"}
      registry.register("localhost:8080", StackClient.newStack, Stack.Params.empty + param.Label("client0"))

      val handler = new ClientRegistryHandler(source, registry)

      tc.advance(1.second)
      val req = httpx.Request("/index.html")
      assert(Await.result(handler(req)).contentString === "")

      underlying = Map(
        "clnt/client0/loadbalancer/adds" -> Entry(10.0, 10.0),
        "clnt/client0/loadbalancer/size" -> Entry(10.0, 10.0),
        "clnt/client0/loadbalancer/available" -> Entry(5.0, 5.0),
        "clnt/client0/requests" -> Entry(50, 50),
        "clnt/client0/failures" -> Entry(1, 1))

      tc.advance(2.seconds)
      val res = Await.result(handler(req))
      val html = res.contentString
      assert(html.contains("client0"))
      assert(html.contains("localhost:8080"))
      assert(html.contains("5 unavailable"))
      assert(html.contains("98.00%"))
    }
  }
}
