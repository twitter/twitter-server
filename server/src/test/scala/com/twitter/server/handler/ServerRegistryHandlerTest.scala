package com.twitter.server.handler

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.server.StackServer
import com.twitter.finagle.{Stack, http, param}
import com.twitter.finagle.util.StackRegistry
import com.twitter.server.util.MetricSourceTest
import com.twitter.util.{Await, Awaitable}
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.scalatest.funsuite.AnyFunSuite

class ServerRegistryHandlerTest extends AnyFunSuite {

  private[this] def await[T](a: Awaitable[T]): T = Await.result(a, 2.seconds)

  test("query a server") {
    val metricsCtx = new MetricSourceTest.Ctx
    import metricsCtx._

    val registry = new StackRegistry { def registryName: String = "server" }
    registry.register(":8080", StackServer.newStack, Stack.Params.empty + param.Label("server0"))
    registry.register(":8081", StackServer.newStack, Stack.Params.empty + param.Label("s/bar/foo"))
    val handler = new ServerRegistryHandler("/admin/servers/", source, registry)

    val res = await(handler(http.Request("/admin/servers/server0")))
    assert(res.status == http.Status.Ok)
    val content = res.contentString
    assert(content.contains("server0"))
    assert(content.contains(":8080"))

    val res2 = await(handler(http.Request("/admin/servers/s/bar/foo")))
    assert(res2.status == http.Status.Ok)
    val content2 = res2.contentString
    assert(content2.contains("s/bar/foo"))
    assert(content2.contains(":8081"))

    val res1 = await(handler(http.Request("/admin/servers/server1")))
    assert(res1.status == http.Status.NotFound)
  }

  test("server profile") {
    val metricsCtx = new MetricSourceTest.Ctx
    import metricsCtx._

    val registry = new StackRegistry { def registryName: String = "server" }
    registry.register(":8080", StackServer.newStack, Stack.Params.empty + param.Label("server0"))

    val handler = new ServerRegistryHandler("/admin/servers/", source, registry)

    val res = await(handler(http.Request("/admin/servers/index.html")))
    assert(res.status == http.Status.Ok)
  }

  test("server name is decoded in handler") {
    val metricsCtx = new MetricSourceTest.Ctx
    import metricsCtx._

    val registry = new StackRegistry { def registryName: String = "server" }
    val serverName = "server@1"
    registry.register(":8080", StackServer.newStack, Stack.Params.empty + param.Label(serverName))

    val handler = new ServerRegistryHandler("/admin/servers/", source, registry)

    val res = await(handler(
      http.Request("/admin/servers/" + URLEncoder.encode(serverName, StandardCharsets.UTF_8.name))))
    assert(res.status == http.Status.Ok)
  }
}
