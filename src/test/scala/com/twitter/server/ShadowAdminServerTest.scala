package com.twitter.server

import com.twitter.finagle.Httpx
import com.twitter.finagle.httpx.{HttpMuxHandler, Request, Response}
import com.twitter.finagle.{ListeningServer, NullServer}
import com.twitter.io.Charsets
import com.twitter.server.util.HttpUtils._
import com.twitter.util.{Await, Future}
import java.net.InetSocketAddress
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

class MockMetricsExporter extends HttpMuxHandler {
  val pattern = "/admin/metrics.json"
  def apply(req: Request): Future[Response] =
    newOk("standard metrics!")
}

class MockOstrichExporter extends HttpMuxHandler {
  val pattern = "/stats.json"
  def apply(req: Request): Future[Response] =
    newOk("metrics!")
}

class MockHostMetricsExporter extends HttpMuxHandler {
  val pattern = "/admin/per_host_metrics.json"
  def apply(req: Request): Future[Response] =
    newOk("per host metrics!")
}


@RunWith(classOf[JUnitRunner])
class ShadowAdminServerTest extends FunSuite {
  test("BlackBox server serves") (new TestTwitterServer with ShadowAdminServer {
    override def main() {
      val port = shadowHttpServer.boundAddress.asInstanceOf[InetSocketAddress].getPort
      val client = Httpx.client.newService(s"localhost:${port}")

      val resp0 = Await.result(client(Request("/stats.json")))
      assert(resp0.contentString.contains("metrics!"))

      val resp = Await.result(client(Request("/admin/metrics.json")))
      assert(resp.contentString.contains("standard metrics!"))

      val resp1 = Await.result(client(Request("/admin/per_host_metrics.json")))
      assert(resp1.contentString.contains("per host metrics!"))

      Await.result(close())
    }
  })
}
