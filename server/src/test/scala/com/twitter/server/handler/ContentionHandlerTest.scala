package com.twitter.server.handler

import com.twitter.conversions.DurationOps._
import com.twitter.finagle.http.{Request, Status}
import com.twitter.util.{Await, Awaitable, Promise}
import java.util.concurrent.locks.ReentrantLock
import org.scalatest.concurrent.Eventually
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.time.{Millis, Seconds, Span}

object ContentionHandlerTest {
  class Philosopher {
    val ready = new Promise[Unit]
    private val lock = new ReentrantLock()
    def dine(neighbor: Philosopher): Unit = {
      lock.lockInterruptibly()
      ready.setValue(())
      Await.ready(neighbor.ready)
      neighbor.dine(this)
      lock.unlock()
    }
  }
}

class ContentionHandlerTest extends AnyFunSuite with Eventually {
  import ContentionHandlerTest._

  implicit override val patienceConfig: PatienceConfig = {
    PatienceConfig(timeout = scaled(Span(15, Seconds)), interval = scaled(Span(5, Millis)))
  }

  test("ContentionHandler#instantiation") {
    new ContentionHandler
  }

  test("ContentionHandler#detect deadlocks") {
    val handler = new ContentionHandler

    val descartes = new Philosopher()
    val plato = new Philosopher()

    val d = new Thread(new Runnable() {
      def run(): Unit = { descartes.dine(plato) }
    })
    d.start()

    val p = new Thread(new Runnable() {
      def run(): Unit = { plato.dine(descartes) }
    })
    p.start()
    Await.all(descartes.ready, plato.ready)

    eventually {
      assertResponseMessage(handler, "DEADLOCKS:")
    }
    d.interrupt()
    p.interrupt()
    p.join()
    d.join()
    assertResponseMessage(handler, "")
  }

  private[this] def await[T](a: Awaitable[T]): T = Await.result(a, 2.seconds)

  private[this] def assertResponseMessage(handler: ContentionHandler, message: String): Unit = {
    val request = Request("/")
    val response = await(handler.apply(request))
    assert(response.status == Status.Ok)
    val responseBody = response.contentString
    assert(responseBody.startsWith(message))
  }
}
