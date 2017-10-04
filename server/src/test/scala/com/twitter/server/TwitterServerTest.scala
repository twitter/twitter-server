package com.twitter.server

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Http, Service}
import com.twitter.util._
import java.io.ByteArrayOutputStream
import java.net.{InetAddress, InetSocketAddress}
import java.util.logging.{Logger, StreamHandler, SimpleFormatter}
import org.junit.runner.RunWith
import org.scalatest.FunSuite
import org.scalatest.junit.JUnitRunner
import scala.collection.mutable.MutableList

class TestTwitterServer extends TwitterServer {
  override val adminPort =
    flag("admin.port", new InetSocketAddress(InetAddress.getLoopbackAddress, 0), "")

  val bootstrapSeq = MutableList.empty[Symbol]

  def main() {
    bootstrapSeq += 'Main
  }

  init {
    bootstrapSeq += 'Init
  }

  premain {
    bootstrapSeq += 'PreMain
  }

  onExit {
    bootstrapSeq += 'Exit
  }

  postmain {
    bootstrapSeq += 'PostMain
  }
}

class MockExceptionHandler extends Service[Request, Response] {
  val pattern = "/exception_please.json"
  def apply(req: Request): Future[Response] = {
    throw new Exception("test exception")
  }
}
@RunWith(classOf[JUnitRunner])
class TwitterServerTest extends FunSuite {

  test("TwitterServer does not prematurely execute lifecycle hooks") {
    val twitterServer = new TestTwitterServer
    assert(twitterServer.bootstrapSeq.isEmpty)
  }

  test("TwitterServer.main(args) executes without error") {
    val twitterServer = new TestTwitterServer
    twitterServer.main(args = Array.empty[String])
    assert(
      twitterServer.bootstrapSeq ==
        Seq('Init, 'PreMain, 'Main, 'PostMain, 'Exit)
    )
  }

  test("TwitterServer.main(args) executes without error when closed explicitly") {
    val twitterServer = new TestTwitterServer {
      override def main() {
        super.main()
        Await.result(close())
      }
    }

    twitterServer.main(args = Array.empty[String])
    assert(twitterServer.bootstrapSeq == Seq('Init, 'PreMain, 'Main, 'Exit, 'PostMain))
  }

  test("Exceptions thrown in handlers include stack traces") {
    val twitterServer = new TestTwitterServer {
      val mockExceptionHandler = new MockExceptionHandler

      override def main() {
        addAdminRoute(
          AdminHttpServer.mkRoute(
            "/exception_please.json",
            mockExceptionHandler,
            "mockExceptionHandler",
            None,
            false
          )
        )

        val port = adminHttpServer.boundAddress.asInstanceOf[InetSocketAddress].getPort

        val logger = Logger.getLogger(getClass.getName)
        val stream = new ByteArrayOutputStream
        val handler = new StreamHandler(stream, new SimpleFormatter)
        logger.addHandler(handler)

        val client = Http.client.newService(s"localhost:${port}")
        stream.reset()
        Await.ready(client(Request("/exception_please.json")))
        assert(stream.toString.contains("at com.twitter.server.MockExceptionHandler.apply"))
      }
    }
    twitterServer.main(args = Array.empty[String])
  }
}
