package com.twitter.server

import com.twitter.app.App
import com.twitter.conversions.time._
import com.twitter.finagle.Http
import com.twitter.finagle.http.{HttpMuxHandler, Request, Response}
import com.twitter.finagle.ListeningServer
import com.twitter.server.util.HttpUtils._
import com.twitter.util.{Duration, Await, Future}
import java.net.{InetAddress, InetSocketAddress}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.concurrent.Eventually
import org.scalatest.junit.JUnitRunner
import scala.language.reflectiveCalls

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
class AdminHttpServerTest extends FunSuite  {

  def checkServer(server: ListeningServer): Unit = {
    val port = server.boundAddress.asInstanceOf[InetSocketAddress].getPort
    val client = Http.client.newService(s"localhost:$port")

    val resp0 = Await.result(client(Request("/stats.json")), 1.second)
    assert(resp0.contentString.contains("metrics!"))

    val resp = Await.result(client(Request("/admin/metrics.json")), 1.second)
    assert(resp.contentString.contains("standard metrics!"))

    val resp1 = Await.result(client(Request("/admin/per_host_metrics.json")), 1.second)
    assert(resp1.contentString.contains("per host metrics!"))
  }

  def closeServer(app: App, server: ListeningServer): Unit = {
    val port = server.boundAddress.asInstanceOf[InetSocketAddress].getPort
    val client = Http.client.newService(s"localhost:$port")

    Await.result(client(Request("/quitquitquit")), 1.second)

    // throws if adminHttpServer does not exit before the grace period,
    // which indicates that we have not closed it properly.
    Await.result(server, 2.seconds)
  }

  test("server serves and is closed properly") {
    val server = new TestTwitterServer {
      override def main() {
        checkServer(adminHttpServer)
        closeServer(this, adminHttpServer)
        Await.result(close(5.seconds))
      }
    }
    server.main(args = Array.empty[String])
  }

  test("shadow server serves and is closed properly") {
    val server = new TestTwitterServer with ShadowAdminServer {
      override def main() {
        checkServer(shadowHttpServer)
        // ShadowAdminServer does not listen for /quitquitquit
        // so send it to the admin server
        closeServer(this, adminHttpServer)
        Await.result(close(5.seconds))
      }
    }
    server.shadowAdminPort.let(new InetSocketAddress(InetAddress.getLoopbackAddress, 0)) {
      server.main(args = Array.empty[String])
    }
  }
}
