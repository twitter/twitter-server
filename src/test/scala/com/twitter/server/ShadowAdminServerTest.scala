package com.twitter.server

import com.twitter.finagle.Http
import com.twitter.finagle.http.{HttpMuxHandler, Request}
import com.twitter.finagle.{ListeningServer, NullServer}
import com.twitter.io.Charsets
import com.twitter.server.util.HttpUtils._
import com.twitter.util.{Await, Future}
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.http._
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

class MockMetricsExporter extends HttpMuxHandler {
  val pattern = "/admin/metrics.json"
  def apply(req: HttpRequest): Future[HttpResponse] =
    newOk("metrics!")
}

class MockOstrichExporter extends HttpMuxHandler {
  val pattern = "/stats.json"
  def apply(req: HttpRequest): Future[HttpResponse] =
    newOk("metrics!")
}


@RunWith(classOf[JUnitRunner])
class ShadowAdminServerTest extends FunSuite {
  test("BlackBox server serves") (new TestTwitterServer with ShadowAdminServer {
    override def main() {
      val port = shadowHttpServer.boundAddress.asInstanceOf[InetSocketAddress].getPort
      val client = Http.client.newService(s"localhost:${port}")

      val resp0 = Await.result(client(Request("/stats.json")))
      assert(resp0.getContent.toString(Charsets.Utf8).contains("metrics!"))

      val resp = Await.result(client(Request("/admin/metrics.json")))
      assert(resp0.getContent.toString(Charsets.Utf8).contains("metrics!"))

      Await.result(close())
    }
  })
}