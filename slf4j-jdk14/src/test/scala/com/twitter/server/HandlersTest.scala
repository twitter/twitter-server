package com.twitter.server

import com.twitter.finagle.filter.OffloadFilter
import com.twitter.finagle.http.Request
import com.twitter.finagle.http.Response
import com.twitter.finagle.Http
import com.twitter.finagle.Service
import com.twitter.util.Await
import com.twitter.util.Future
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.logging.Logger
import java.util.logging.SimpleFormatter
import java.util.logging.StreamHandler
import scala.collection.mutable
import org.scalatest.funsuite.AnyFunSuite

/** Test TwitterServer which overrides the admin.port to localhost ephemeral port */
class TestTwitterServer extends TwitterServer {
  override val defaultAdminPort = 0

  val bootstrapSeq: mutable.ArrayBuffer[Symbol] = mutable.ArrayBuffer.empty[Symbol]

  def main(): Unit = {
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

class HandlersTest extends AnyFunSuite {

  test("Exceptions thrown in handlers include stack traces") {
    val twitterServer: TwitterServer = new TestTwitterServer {
      val mockExceptionHandler = new MockExceptionHandler

      override protected def configureAdminHttpServer(server: Http.Server): Http.Server = {
        // TODO: with offload filter there appears to be a race condition in the test.
        super.configureAdminHttpServer(server).configured(OffloadFilter.Param.Disabled)
      }

      override def main(): Unit = {
        addAdminRoute(
          AdminHttpServer.mkRoute(
            path = "/exception_please.json",
            handler = mockExceptionHandler,
            alias = "mockExceptionHandler",
            group = None,
            includeInIndex = false
          )
        )

        val port = adminHttpServer.boundAddress.asInstanceOf[InetSocketAddress].getPort

        val log = Logger.getLogger(getClass.getName)
        val stream = new ByteArrayOutputStream
        val handler = new StreamHandler(stream, new SimpleFormatter)
        log.addHandler(handler)

        val client = Http.client.newService(s"localhost:$port")
        stream.reset()
        Await.ready {
          client(Request("/exception_please.json"))
        }
        assert(stream.toString.contains("at com.twitter.server.MockExceptionHandler.apply"))
      }
    }
    twitterServer.main(args = Array.empty[String])
  }
}
