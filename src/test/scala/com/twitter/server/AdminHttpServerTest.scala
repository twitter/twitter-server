package com.twitter.server

import com.twitter.conversions.time._
import com.twitter.finagle.{Http, ListeningServer}
import com.twitter.finagle.http._
import com.twitter.server.util.HttpUtils._
import com.twitter.util.{Await, Future}
import java.net.{InetAddress, InetSocketAddress}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner

class MockMetricsExporter extends HttpMuxHandler {
  val pattern = "/admin/metrics.json"
  def route: Route = Route(pattern, this)
  def apply(req: Request): Future[Response] =
    newOk("standard metrics!")
}

class MockOstrichExporter extends HttpMuxHandler {
  val pattern = "/stats.json"
  def route: Route = Route(pattern, this)
  def apply(req: Request): Future[Response] =
    newOk("metrics!")
}

class MockCommonsExporter extends HttpMuxHandler {
  val pattern = "/vars.json"
  def route: Route = Route(pattern, this)
  def apply(req: Request): Future[Response] =
    newOk("commons stats!")
}

class MockHostMetricsExporter extends HttpMuxHandler {
  val pattern = "/admin/per_host_metrics.json"
  def route: Route = Route(pattern, this)
  def apply(req: Request): Future[Response] =
    newOk("per host metrics!")
}


@RunWith(classOf[JUnitRunner])
class AdminHttpServerTest extends FunSuite  {

  def checkServer(server: ListeningServer): Unit = {
    val port = server.boundAddress.asInstanceOf[InetSocketAddress].getPort
    val client = Http.client.newService(s"localhost:$port")

    val ostrich = Await.result(client(Request("/stats.json")), 1.second)
    assert(ostrich.contentString.contains("metrics!"))

    val commons = Await.result(client(Request("/vars.json")), 1.second)
    assert(commons.contentString.contains("commons stats!"))

    val resp = Await.result(client(Request("/admin/metrics.json")), 1.second)
    assert(resp.contentString.contains("standard metrics!"))

    val resp1 = Await.result(client(Request("/admin/per_host_metrics.json")), 1.second)
    assert(resp1.contentString.contains("per host metrics!"))
  }

  def closeServer(twitterServer: TwitterServer, adminServer: ListeningServer): Unit = {
    val adminServerBoundPort = adminServer.boundAddress.asInstanceOf[InetSocketAddress].getPort
    assert(adminServerBoundPort == twitterServer.adminBoundAddress.getPort)
    val client = Http.client.newService(s"localhost:$adminServerBoundPort")
    Await.result(client(Request(Method.Post, "/quitquitquit")), 1.second)

    // throws if adminHttpServer does not exit before the grace period,
    // which indicates that we have not closed it properly.
    Await.result(adminServer, 2.seconds)
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

  test("GET does not close server") {
    val server = new TestTwitterServer {
      override def main(): Unit = {
        checkServer(adminHttpServer)
        // Try to close the server with a GET
        val adminServerBoundPort = adminHttpServer.boundAddress.asInstanceOf[InetSocketAddress].getPort
        assert(adminServerBoundPort == this.adminBoundAddress.getPort)
        val client = Http.client.newService(s"localhost:$adminServerBoundPort")
        val res = Await.result(client(Request(Method.Get, "/quitquitquit")), 1.second)
        assert(res.status == Status.MethodNotAllowed)
        // Check that the server is still up
        checkServer(adminHttpServer)
        Await.result(close(5.seconds))
      }
    }
    server.main(args = Array.empty[String])
  }
} 
