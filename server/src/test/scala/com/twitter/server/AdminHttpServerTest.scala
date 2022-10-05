package com.twitter.server

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.Http
import com.twitter.finagle.Service
import com.twitter.finagle.ListeningServer
import com.twitter.finagle.http._
import com.twitter.server.util.HttpUtils._
import com.twitter.util._
import java.net.InetSocketAddress
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.funsuite.AnyFunSuite

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

object MockClosableHandler {
  @volatile var closed = false
}

class MockClosableHandler extends HttpMuxHandler {
  MockClosableHandler.closed = false

  val pattern = "/admin/closable.json"
  def route: Route = Route(pattern, this)
  def apply(req: Request): Future[Response] =
    newOk("closable!")

  override def close(deadline: Time): Future[Unit] = {
    MockClosableHandler.closed = true
    Future.Done
  }
}

class AdminHttpServerTest extends AnyFunSuite with Eventually with IntegrationPatience {
  private[this] def await[A](a: Future[A]): A = Await.result(a, 5.seconds)

  def checkServer(server: ListeningServer, shadow: Boolean = false): Unit = {
    val port = server.boundAddress.asInstanceOf[InetSocketAddress].getPort
    val client = Http.client.newService(s"localhost:$port")

    val ostrich = await(client(Request("/stats.json")))
    assert(ostrich.contentString.contains("metrics!"))

    val commons = await(client(Request("/vars.json")))
    assert(commons.contentString.contains("commons stats!"))

    val resp = await(client(Request("/admin/metrics.json")))
    assert(resp.contentString.contains("standard metrics!"))

    val resp1 = await(client(Request("/admin/per_host_metrics.json")))
    assert(resp1.contentString.contains("per host metrics!"))

    if (!shadow) {
      val resp2 = await(client(Request("/admin/closable.json")))
      assert(resp2.contentString.contains("closable!"))
    }
  }

  def closeServer(twitterServer: TwitterServer, adminServer: ListeningServer): Unit = {
    val adminServerBoundPort = adminServer.boundAddress.asInstanceOf[InetSocketAddress].getPort
    assert(adminServerBoundPort == twitterServer.adminBoundAddress.getPort)
    val client = Http.client.newService(s"localhost:$adminServerBoundPort")
    await(client(Request(Method.Post, "/quitquitquit")))

    // throws if adminHttpServer does not exit before the grace period,
    // which indicates that we have not closed it properly.
    Await.result(adminServer, 2.seconds)
  }

  test("server serves and is closed properly") {
    val server = new TestTwitterServer {
      override def main(): Unit = {
        checkServer(adminHttpServer)

        closeServer(this, adminHttpServer)
        Await.result(close(5.seconds), 7.seconds)
      }
    }
    server.main(args = Array.empty[String])
    assert(MockClosableHandler.closed)
  }

  test("a closed server cannot be restarted") {
    val server = new TestTwitterServer {
      override def main(): Unit = {
        Await.result(adminHttpServer.close(5.seconds), 7.seconds)
        intercept[Exception] {
          checkServer(adminHttpServer)
        }

        val adminServerBoundPort =
          adminHttpServer.boundAddress.asInstanceOf[InetSocketAddress].getPort
        assert(adminServerBoundPort == adminBoundAddress.getPort)
        val client = Http.client.newService(s"localhost:$adminServerBoundPort")
        intercept[Exception] { // the admin is already closed, this should error
          Await.result(client(Request(Method.Post, "/quitquitquit")), 1.second)
        }

        Await.result(close(5.seconds), 7.seconds)
      }
    }
    server.main(args = Array.empty[String])
    assert(MockClosableHandler.closed)
  }

  test("a disabled server cannot be started") {
    val server = new TestTwitterServer {
      override protected def disableAdminHttpServer: Boolean = true
      override def main(): Unit = {
        intercept[Exception] {
          checkServer(adminHttpServer)
        }

        val adminServerBoundPort =
          adminHttpServer.boundAddress.asInstanceOf[InetSocketAddress].getPort
        assert(adminServerBoundPort == adminBoundAddress.getPort)
        val client = Http.client.newService(s"localhost:$adminServerBoundPort")
        intercept[Exception] { // the admin is already closed, this should error
          Await.result(client(Request(Method.Post, "/quitquitquit")), 1.second)
        }

        Await.result(close(5.seconds), 7.seconds)
      }
    }
    server.main(args = Array.empty[String])
    assert(MockClosableHandler.closed)
  }

  test("GET does not close server") {
    val server = new TestTwitterServer {
      override def main(): Unit = {
        checkServer(adminHttpServer)
        // Try to close the server with a GET
        val adminServerBoundPort =
          adminHttpServer.boundAddress.asInstanceOf[InetSocketAddress].getPort
        assert(adminServerBoundPort == this.adminBoundAddress.getPort)
        val client = Http.client.newService(s"localhost:$adminServerBoundPort")
        val res = Await.result(client(Request(Method.Get, "/quitquitquit")), 1.second)
        assert(res.status == Status.MethodNotAllowed)
        // Check that the server is still up
        checkServer(adminHttpServer)
        Await.result(close(5.seconds), 7.seconds)
      }
    }
    server.main(args = Array.empty[String])
  }

  test("admin server exits last") {
    Time.withCurrentTimeFrozen { _ =>
      val server = new TestTwitterServer {
        override protected lazy val shutdownTimer = new MockTimer

        override def main(): Unit = {
          val p = new Promise[Unit]
          var sawClose = false
          val drainingClosable = Closable.make { _ => sawClose = true; p }
          closeOnExit(drainingClosable)
          val closeF: Future[Unit] = close(Time.now + 10.seconds)
          assert(sawClose)
          // `drainingClosable` keeps the admin server from shutting down
          assert(!closeF.isDefined)
          shutdownTimer.tick()

          // metrics endpoint still responding
          checkServer(adminHttpServer)

          // last closable is done, admin server shuts down
          p.setDone()
          eventually { assert(closeF.isDefined) }
        }
      }
      server.main(args = Array.empty[String])
    }
  }

  test("admin server respects deadline") {
    Time.withCurrentTimeFrozen { ctl =>
      val server = new TestTwitterServer {

        override protected lazy val shutdownTimer = new MockTimer

        override protected def exitOnError(reason: String): Unit = ()
        override protected def exitOnError(throwable: Throwable): Unit = ()

        override def main(): Unit = {
          val drainingClosable = Closable.make { _ => Future.never }
          closeOnExit(drainingClosable)
          val closeF: Future[Unit] = close(Time.now + 10.seconds)
          // `drainingClosable` keeps the admin server from shutting down
          assert(!closeF.isDefined)

          // metrics endpoint still responding
          checkServer(adminHttpServer)

          // deadline exhausted
          ctl.advance(11.seconds)
          shutdownTimer.tick()

          val port = adminHttpServer.boundAddress.asInstanceOf[InetSocketAddress].getPort
          val client = Http.client.newService(s"localhost:$port")
          val resp: Future[Response] = client(Request("/admin/metrics.json"))
          Await.ready(resp, 1.second)
          val Some(Throw(ex)) = resp.poll
          assert(ex.getMessage.startsWith("Connection refused"))
          eventually { assert(closeF.isDefined) }
        }
      }
      server.main(args = Array.empty[String])
    }
  }

  test("AdminServerInterface.Param for Admin server isAdmin=true") {

    class SpyServer extends TestTwitterServer {
      var isAdminServer: Boolean = false

      override def configureAdminHttpServer(server: Http.Server): Http.Server = {
        isAdminServer = server.params[AdminServerInterface.Param].isAdmin
        server
      }
    }

    val server = new SpyServer {

      override def main(): Unit = {
        // not used
      }
    }
    server.main(args = Array.empty[String])
    assert(server.isAdminServer)
  }

  test("combine can combine two muxers") {
    val hello = new Service[Request, Response] {
      def apply(req: Request) = newOk("hello")
    }

    val world = new Service[Request, Response] {
      def apply(req: Request) = newOk("world")
    }

    val muxer0 = new HttpMuxer().withHandler("/hello", hello)
    val muxer1 = new HttpMuxer().withHandler("/world", world)

    val svc = AdminHttpServer.combine(Seq(muxer0, muxer1), () => Seq.empty)

    val res0 = await(svc(Request("/hello")))
    assert(res0.contentString == "hello")

    val res1 = await(svc(Request("/world")))
    assert(res1.contentString == "world")
  }

  test("combine order doesn't matter for different paths") {
    val hello = new Service[Request, Response] {
      def apply(req: Request) = newOk("hello")
    }

    val world = new Service[Request, Response] {
      def apply(req: Request) = newOk("world")
    }

    val muxer0 = new HttpMuxer().withHandler("/hello", hello)
    val muxer1 = new HttpMuxer().withHandler("/world", world)

    Seq(muxer0, muxer1).permutations.foreach { seq =>
      val svc = AdminHttpServer.combine(seq, () => Seq.empty)

      val res0 = await(svc(Request("/hello")))
      assert(res0.contentString == "hello")

      val res1 = await(svc(Request("/world")))
      assert(res1.contentString == "world")
    }
  }

  test("combine always chooses the longer prefix") {
    val hello = new Service[Request, Response] {
      def apply(req: Request) = newOk("hello")
    }

    val world = new Service[Request, Response] {
      def apply(req: Request) = newOk("world")
    }

    val muxer0 = new HttpMuxer().withHandler("/hello", hello)
    val muxer1 = new HttpMuxer().withHandler("/hello1", world)

    Seq(muxer0, muxer1).permutations.foreach { seq =>
      val svc = AdminHttpServer.combine(seq, () => Seq.empty)

      val res = await(svc(Request("/hello1")))
      assert(res.contentString == "world")
    }
  }

  test("combine order is respected for identical paths") {
    val hello = new Service[Request, Response] {
      def apply(req: Request) = newOk("hello")
    }

    val world = new Service[Request, Response] {
      def apply(req: Request) = newOk("world")
    }
    val muxer0 = new HttpMuxer().withHandler("/hello", hello)
    val muxer1 = new HttpMuxer().withHandler("/hello", world)

    val svcSeq0 = AdminHttpServer.combine(Seq(muxer0, muxer1), () => Seq.empty)
    val res0 = await(svcSeq0(Request("/hello")))
    assert(res0.contentString == "hello")

    val svcSeq1 = AdminHttpServer.combine(Seq(muxer1, muxer0), () => Seq.empty)
    val res1 = await(svcSeq1(Request("/hello")))
    assert(res1.contentString == "world")
  }

  test("combine can 404 properly") {
    val hello = new Service[Request, Response] {
      def apply(req: Request) = newOk("hello")
    }

    val world = new Service[Request, Response] {
      def apply(req: Request) = newOk("world")
    }
    val muxer0 = new HttpMuxer().withHandler("/hello", hello)
    val muxer1 = new HttpMuxer().withHandler("/world", world)

    val svc = AdminHttpServer.combine(Seq(muxer0, muxer1), () => Seq.empty)

    val res = await(svc(Request("/an404")))
    assert(res.status == Status.NotFound)
  }

  test("combine can close the underlying services") {
    var closed1 = false
    var closed2 = false

    val hello = new Service[Request, Response] {
      def apply(req: Request) = newOk("hello")
      override def close(deadline: Time): Future[Unit] = {
        closed1 = true
        Future.Done
      }
    }

    val world = new Service[Request, Response] {
      def apply(req: Request) = newOk("world")
      override def close(deadline: Time): Future[Unit] = {
        closed2 = true
        Future.Done
      }
    }
    val muxer0 = new HttpMuxer().withHandler("/hello", hello)
    val muxer1 = new HttpMuxer().withHandler("/world", world)

    val svc = AdminHttpServer.combine(Seq(muxer0, muxer1), () => Seq.empty)
    await(svc.close())

    assert(closed1)
    assert(closed2)
  }
}
