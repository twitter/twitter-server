package com.twitter.server

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Http, Service}
import com.twitter.util.{Await, Future}
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.util.logging.{Logger, SimpleFormatter, StreamHandler}
import org.scalatest.FunSuite
import scala.collection.mutable

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

class HandlersTest extends FunSuite {

  test("Exceptions thrown in handlers include stack traces") {
    val twitterServer: TwitterServer = new TestTwitterServer {
      val mockExceptionHandler = new MockExceptionHandler

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
